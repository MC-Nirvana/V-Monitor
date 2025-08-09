package cn.nirvana.vMonitor.functional_module;

import cn.nirvana.vMonitor.loader.ConfigLoader;
import cn.nirvana.vMonitor.loader.DataLoader;

import org.slf4j.Logger;

import java.io.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import java.util.Calendar;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

public class ReportModule {
    private final Logger logger;
    private final ConfigLoader configLoader;
    private final DataLoader dataLoader;
    private final Path dataDirectory;

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    // 报表生成任务
    private Thread reportGenerationTask;
    private volatile boolean running = false;

    public ReportModule(Logger logger, ConfigLoader configLoader, DataLoader dataLoader, Path dataDirectory) {
        this.logger = logger;
        this.configLoader = configLoader;
        this.dataLoader = dataLoader;
        this.dataDirectory = dataDirectory;
    }

    /**
     * 启动报表模块
     */
    public void start() {
        if (!configLoader.isReportEnabled()) {
            logger.info("Report generation is disabled in configuration.");
            return;
        }

        running = true;

        // 启动报表生成任务
        reportGenerationTask = new Thread(this::scheduleReportGeneration);
        reportGenerationTask.setName("ReportGenerationThread");
        reportGenerationTask.setDaemon(true);
        reportGenerationTask.start();

        logger.info("Report module started successfully.");
    }

    /**
     * 停止报表模块
     */
    public void stop() {
        running = false;
        if (reportGenerationTask != null && reportGenerationTask.isAlive()) {
            reportGenerationTask.interrupt();
        }
        logger.info("Report module stopped.");
    }

    /**
     * 调度报表生成任务
     */
    private void scheduleReportGeneration() {
        try {
            // 获取报表生成时间配置
            String scheduleTime = configLoader.getReportScheduleTime();
            String[] timeParts = scheduleTime.split(":");
            int hour = Integer.parseInt(timeParts[0]);
            int minute = Integer.parseInt(timeParts[1]);

            while (running) {
                // 计算下次执行时间
                long delay = calculateNextExecutionDelay(hour, minute);

                // 等待到下次执行时间
                if (delay > 0) {
                    Thread.sleep(delay);
                }

                if (!running) {
                    break;
                }

                // 生成报表
                generateReport();

                // 如果启用了自动清理，则执行清理
                if (configLoader.isAutoCleanReport()) {
                    cleanOldReports();
                }
            }
        } catch (InterruptedException e) {
            logger.info("Report generation scheduler interrupted.");
        } catch (Exception e) {
            logger.error("Error in report generation scheduler: ", e);
        }
    }

    /**
     * 计算下次执行的延迟时间（毫秒）
     */
    private long calculateNextExecutionDelay(int targetHour, int targetMinute) {
        Calendar now = Calendar.getInstance();
        Calendar nextRun = Calendar.getInstance();

        nextRun.set(Calendar.HOUR_OF_DAY, targetHour);
        nextRun.set(Calendar.MINUTE, targetMinute);
        nextRun.set(Calendar.SECOND, 0);
        nextRun.set(Calendar.MILLISECOND, 0);

        // 如果今天的时间已经过了目标时间，则安排在明天执行
        if (nextRun.before(now)) {
            nextRun.add(Calendar.DAY_OF_MONTH, 1);
        }

        return nextRun.getTimeInMillis() - now.getTimeInMillis();
    }

    /**
     * 生成报表
     */
    public void generateReport() {
        try {
            logger.info("Starting report generation...");

            // 创建输出目录
            String outputDir = configLoader.getReportOutputDirectory();
            Path reportDir = dataDirectory.resolve(outputDir);
            Files.createDirectories(reportDir);

            // 生成报表文件名
            String fileName = "report_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".html";
            Path reportPath = reportDir.resolve(fileName);

            // 根据语言设置选择模板
            String language = configLoader.getLanguageKey();
            String templatePath = getTemplatePathByLanguage(language);

            // 读取模板
            String template = loadTemplate(templatePath);

            // 填充数据
            String filledTemplate = fillTemplateWithData(template);

            // 写入文件
            try (BufferedWriter writer = Files.newBufferedWriter(reportPath, StandardCharsets.UTF_8)) {
                writer.write(filledTemplate);
            }

            // 更新最后报表生成时间
            dataLoader.updateLastReportGenerationTime();

            logger.info("Report generated successfully at: {}", reportPath.toAbsolutePath());
        } catch (Exception e) {
            logger.error("Failed to generate report: ", e);
        }
    }

    /**
     * 根据语言设置获取模板路径
     */
    private String getTemplatePathByLanguage(String language) {
        switch (language.toLowerCase()) {
            case "zh_cn":
                return "report/report_template_zh_cn.html";
            case "zh_tw":
                return "report/report_template_zh_tw.html";
            case "en_us":
                return "report/report_template_en_us.html";
            default:
                return "report/report_template_zh_cn.html"; // 默认使用简体中文模板
        }
    }

    /**
     * 加载HTML模板
     */
    private String loadTemplate(String templatePath) throws IOException {
        // 从resources目录加载模板
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(templatePath)) {
            if (is == null) {
                throw new FileNotFoundException("Report template not found: " + templatePath);
            }

            ByteArrayOutputStream result = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int length;
            while ((length = is.read(buffer)) != -1) {
                result.write(buffer, 0, length);
            }

            return result.toString(StandardCharsets.UTF_8.name());
        }
    }

    /**
     * 填充模板数据
     */
    private String fillTemplateWithData(String template) {
        try {
            // 基本信息填充
            template = template.replace("{{server_name}}", configLoader.getServerName());

            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.minusDays(30);
            template = template.replace("{{report_date_range}}",
                    startDate.format(DateTimeFormatter.ofPattern("MM月dd日")) +
                            "-" +
                            endDate.format(DateTimeFormatter.ofPattern("MM月dd日")));

            // 构建JSON数据对象
            JsonObject jsonData = new JsonObject();

            // 1. 每日峰值在线人数数据
            Map<String, Integer> dailyPeakOnline = dataLoader.getDailyPeakOnlinePlayers(startDate, endDate);
            jsonData.add("dailyPeakOnline", gson.toJsonTree(dailyPeakOnline));

            // 2. 玩家总数和新玩家数
            int totalPlayers = dataLoader.getTotalPlayerCount();
            int newPlayers = dataLoader.getNewPlayerCount(startDate, endDate);
            int oldPlayers = totalPlayers - newPlayers;

            JsonObject playerStats = new JsonObject();
            playerStats.addProperty("total", totalPlayers);
            playerStats.addProperty("new", newPlayers);
            playerStats.addProperty("old", oldPlayers);
            jsonData.add("playerStats", playerStats);

            // 3. 核心玩家和流失风险玩家
            int corePlayers = dataLoader.getCorePlayerCount(15); // 15天以上为核心玩家
            int atRiskPlayers = dataLoader.getAtRiskPlayerCount(7); // 7天未登录为流失风险

            JsonObject playerActivity = new JsonObject();
            playerActivity.addProperty("corePlayers", corePlayers);
            playerActivity.addProperty("atRiskPlayers", atRiskPlayers);
            jsonData.add("playerActivity", playerActivity);

            // 4. DAU数据
            double avgDAU = dataLoader.getAverageDAU(startDate, endDate);
            int historicalPeak = dataLoader.getHistoricalPeakOnline();

            JsonObject dauData = new JsonObject();
            dauData.addProperty("average", avgDAU);
            dauData.addProperty("historicalPeak", historicalPeak);
            jsonData.add("dauData", dauData);

            // 5. 玩家上线时间段分布（按小时）
            Map<Integer, Integer> hourlyDistribution = dataLoader.getHourlyPlayerDistribution(startDate, endDate);
            jsonData.add("hourlyDistribution", gson.toJsonTree(hourlyDistribution));

            // 6. 玩家登录星期分布
            Map<Integer, Integer> weeklyDistribution = dataLoader.getWeeklyPlayerDistribution(startDate, endDate);
            jsonData.add("weeklyDistribution", gson.toJsonTree(weeklyDistribution));

            // 7. 服务器分布数据
            Map<String, Integer> serverDistribution = dataLoader.getServerDistribution(startDate, endDate);
            jsonData.add("serverDistribution", gson.toJsonTree(serverDistribution));

            // 8. 最长在线时间的玩家TOP列表
            List<DataLoader.TopPlayerByPlayTime> topPlayers = dataLoader.getTopPlayersByPlayTime(3);
            jsonData.add("topPlayers", gson.toJsonTree(topPlayers));

            // 9. 每日玩家数量最多的几天
            Map<String, Integer> topPlayerDays = dataLoader.getTopPlayerDays(3, startDate, endDate);
            jsonData.add("topPlayerDays", gson.toJsonTree(topPlayerDays));

            // 10. 最受欢迎的服务器列表
            List<DataLoader.PopularServer> popularServers = dataLoader.getPopularServers(3, startDate, endDate);
            jsonData.add("popularServers", gson.toJsonTree(popularServers));

            // 将JSON数据注入模板
            template = template.replace("{{report_data}}", gson.toJson(jsonData));

            return template;
        } catch (Exception e) {
            logger.error("Failed to fill template with data: ", e);
            return template; // 返回未填充的模板
        }
    }

    /**
     * 清理旧报表
     */
    private void cleanOldReports() {
        try {
            String outputDir = configLoader.getReportOutputDirectory();
            Path reportDir = dataDirectory.resolve(outputDir);

            if (!Files.exists(reportDir)) {
                return;
            }

            // 保留30天内的报表
            LocalDate cutoffDate = LocalDate.now().minusDays(30);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

            Files.list(reportDir)
                    .filter(path -> path.toString().endsWith(".html"))
                    .filter(path -> {
                        String fileName = path.getFileName().toString();
                        // 提取日期部分
                        if (fileName.startsWith("report_") && fileName.length() >= 18) {
                            try {
                                String dateStr = fileName.substring(7, 17); // 提取 yyyy-MM-dd 部分
                                LocalDate fileDate = LocalDate.parse(dateStr, formatter);
                                return fileDate.isBefore(cutoffDate);
                            } catch (Exception e) {
                                return false;
                            }
                        }
                        return false;
                    })
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                            logger.info("Deleted old report: {}", path.getFileName());
                        } catch (IOException e) {
                            logger.warn("Failed to delete old report: {}", path.getFileName(), e);
                        }
                    });

            logger.info("Old reports cleanup completed.");
        } catch (Exception e) {
            logger.error("Error during reports cleanup: ", e);
        }
    }
}
