// PlayerSwitchModule.java
package cn.nirvana.vMonitor.command_module;

import cn.nirvana.vMonitor.loader.DataFileLoader;
import cn.nirvana.vMonitor.loader.LanguageFileLoader;
import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public class PlayerSwitchModule {
    private final DataFileLoader dataFileLoader;
    private final LanguageFileLoader languageFileLoader;
    private final MiniMessage miniMessage;

    public PlayerSwitchModule(DataFileLoader dataFileLoader, LanguageFileLoader languageFileLoader, MiniMessage miniMessage) {
        this.dataFileLoader = dataFileLoader;
        this.languageFileLoader = languageFileLoader;
        this.miniMessage = miniMessage;
    }

    public void executePlayerSwitch(CommandSource source, String playerName) {
        // 查找玩家数据
        DataFileLoader.RootData rootData = dataFileLoader.getRootData();
        DataFileLoader.PlayerData playerData = null;

        // 遍历所有玩家数据查找匹配的玩家名
        for (DataFileLoader.PlayerData player : rootData.playerData) {
            if (player.username.equalsIgnoreCase(playerName)) {
                playerData = player;
                break;
            }
        }

        // 如果未找到玩家数据
        if (playerData == null) {
            String notFoundMessage = languageFileLoader.getMessage("commands.player.not_found")
                    .replace("{player}", playerName);
            source.sendMessage(miniMessage.deserialize(notFoundMessage));
            return;
        }

        // 获取语言文件中的玩家切换日志格式
        String switchLogHeader = languageFileLoader.getMessage("commands.player.switch.header");
        String switchLogEntryFormat = languageFileLoader.getMessage("commands.player.switch.entry_format");
        String noSwitchLogsMessage = languageFileLoader.getMessage("commands.player.switch.no_logs");
        String switchLogFormat = languageFileLoader.getMessage("commands.player.switch.format");

        // 构建切换日志内容
        StringBuilder switchLogBuilder = new StringBuilder();
        boolean hasLogs = false;

        // 遍历玩家的所有服务器路径日志
        for (Map.Entry<String, List<DataFileLoader.ServerPathData>> entry : playerData.dailyServerPaths.entrySet()) {
            String date = entry.getKey();
            List<DataFileLoader.ServerPathData> paths = entry.getValue();

            if (!paths.isEmpty()) {
                hasLogs = true;
                for (DataFileLoader.ServerPathData path : paths) {
                    // 将时间转换为 ISO8601 标准格式
                    String iso8601Time = convertToISO8601(date, path.time);

                    // 只替换新的 ISO8601 格式占位符
                    String entryMessage = switchLogEntryFormat
                            .replace("{iso8601_time}", iso8601Time)
                            .replace("{from}", path.from)
                            .replace("{to}", path.to);

                    switchLogBuilder.append(entryMessage).append("\n");
                }
            }
        }

        // 如果没有日志记录
        if (!hasLogs) {
            switchLogBuilder.append(noSwitchLogsMessage);
        }

        // 使用 format 模板格式化最终输出
        String finalMessage = switchLogFormat
                .replace("{player_name}", playerData.username)
                .replace("{switch_log}", switchLogBuilder.toString().trim());

        source.sendMessage(miniMessage.deserialize(finalMessage));
    }

    /**
     * 将日期和时间转换为 ISO8601 标准格式
     * @param date 日期 (yyyy-MM-dd)
     * @param time 时间 (HH:mm:ss)
     * @return ISO8601 格式的时间戳
     */
    private String convertToISO8601(String date, String time) {
        LocalDate localDate = LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        LocalTime localTime = LocalTime.parse(time, DateTimeFormatter.ofPattern("HH:mm:ss"));
        ZonedDateTime zonedDateTime = ZonedDateTime.of(localDate, localTime, ZoneId.systemDefault());
        return zonedDateTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
