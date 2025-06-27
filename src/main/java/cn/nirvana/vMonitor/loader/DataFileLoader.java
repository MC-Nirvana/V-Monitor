// File: src/main/java/cn/nirvana/vMonitor/config/DataFileLoader.java
package cn.nirvana.vMonitor.loader;

import cn.nirvana.vMonitor.util.TimeUtil;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.google.gson.JsonSyntaxException;
import org.slf4j.Logger;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DataFileLoader {
    private final Logger logger;
    private final Path dataDirectory;
    private final String playerDataFileName = "data.json";

    private RootData rootData;

    private final DateTimeFormatter dateFormatNoTime = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final DateTimeFormatter dateFormatWithTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final DateTimeFormatter dateFormatWithSeconds = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final DateTimeFormatter weekFormatter = DateTimeFormatter.ofPattern("yyyy-'W'ww", Locale.ENGLISH);

    private final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(Long.class, new TimeUtil())
            .create();

    public static class RootData {
        public ServerData server;
        public Map<UUID, PlayerData> players;

        public RootData() {
            this.server = new ServerData();
            this.players = new ConcurrentHashMap<>();
        }
    }

    public static class ServerData {
        public String bootTime;
        public String lastReportGenerationDate;
        public Map<String, DailyNewPlayersData> newPlayersToday;
        public Map<String, Integer> totalLoginCountsInDay;
        public Map<String, Long> totalPlayTimesInDay;

        public ServerData() {
            this.lastReportGenerationDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            this.newPlayersToday = new ConcurrentHashMap<>();
            this.totalLoginCountsInDay = new ConcurrentHashMap<>();
            this.totalPlayTimesInDay = new ConcurrentHashMap<>();
        }
    }

    public static class DailyNewPlayersData {
        public int totalNewPlayersInDay;
        public Map<UUID, String> players;

        public DailyNewPlayersData() {
            this.totalNewPlayersInDay = 0;
            this.players = new ConcurrentHashMap<>();
        }
    }

    public static class PlayerData {
        public String playerName;
        public String firstJoinTime;
        public String lastLoginTime;
        public String lastQuitTime;
        public Long totalPlayTime;
        public int totalLoginCount;
        public Map<String, DailyLoginData> dailyLogins;
        public Map<String, WeeklyLoginData> weeklyLogins;

        public PlayerData(String playerName, String firstJoinTime) {
            this.playerName = playerName;
            this.firstJoinTime = firstJoinTime;
            this.lastLoginTime = "";
            this.lastQuitTime = "";
            this.totalPlayTime = 0L;
            this.totalLoginCount = 0;
            this.dailyLogins = new ConcurrentHashMap<>();
            this.weeklyLogins = new ConcurrentHashMap<>();
        }
    }

    public static class DailyLoginData {
        public int loginCount;
        public Long totalPlayTimeInDay;
        public String lastLoginTime;

        public DailyLoginData() {
            this.loginCount = 0;
            this.totalPlayTimeInDay = 0L;
            this.lastLoginTime = "";
        }
    }

    public static class WeeklyLoginData {
        public int loginCount;
        public Long totalPlayTimeInWeek;
        public String lastLoginTime;

        public WeeklyLoginData() {
            this.loginCount = 0;
            this.totalPlayTimeInWeek = 0L;
            this.lastLoginTime = "";
        }
    }

    public DataFileLoader(Logger logger, Path dataDirectory) {
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    /**
     * 加载玩家数据文件。如果文件不存在或解析失败，则抛出 DataFileLoader.PlayerDataLoadException。
     * @throws DataFileLoader.PlayerDataLoadException 如果玩家数据文件加载或解析失败
     */
    public void loadPlayerData() {
        Path playerDataFile = dataDirectory.resolve(playerDataFileName);

        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(playerDataFile.toFile()), StandardCharsets.UTF_8)) {
            TypeToken<RootData> typeToken = new TypeToken<>() {};
            this.rootData = gson.fromJson(reader, typeToken.getType());
            if (this.rootData == null) {
                // 如果文件为空或内容无法解析为 RootData，fromJson可能返回null
                throw new PlayerDataLoadException("Player data file is empty or malformed: " + playerDataFile.toAbsolutePath());
            }
            // 确保内部结构也非空，防止NPE
            if (this.rootData.server == null) {
                this.rootData.server = new ServerData();
            }
            if (this.rootData.players == null) {
                this.rootData.players = new ConcurrentHashMap<>();
            }
            if (this.rootData.server.newPlayersToday == null) {
                this.rootData.server.newPlayersToday = new ConcurrentHashMap<>();
            }
            if (this.rootData.server.totalLoginCountsInDay == null) {
                this.rootData.server.totalLoginCountsInDay = new ConcurrentHashMap<>();
            }
            if (this.rootData.server.totalPlayTimesInDay == null) {
                this.rootData.server.totalPlayTimesInDay = new ConcurrentHashMap<>();
            }
            logger.info("Player data loaded successfully.");
        } catch (IOException e) {
            // 文件不存在或I/O错误
            throw new PlayerDataLoadException("Failed to read player data file: " + playerDataFile.toAbsolutePath(), e);
        } catch (JsonSyntaxException e) {
            // JSON解析错误，表示文件内容损坏或语法错误
            throw new PlayerDataLoadException("Failed to parse player data file (JSON syntax error): " + playerDataFile.toAbsolutePath(), e);
        } catch (Exception e) {
            // 捕获其他任何未预期的运行时异常
            throw new PlayerDataLoadException("An unexpected error occurred while loading player data file: " + playerDataFile.toAbsolutePath(), e);
        }
    }

    public void savePlayerData() {
        if (rootData == null) {
            logger.warn("Attempted to save player data, but rootData is null. Skipping save.");
            return;
        }
        Path playerDataFile = dataDirectory.resolve(playerDataFileName);
        try (FileWriter writer = new FileWriter(playerDataFile.toFile(), StandardCharsets.UTF_8)) {
            gson.toJson(rootData, writer);
            logger.info("Player data saved successfully.");
        } catch (IOException e) {
            logger.error("Failed to save player data: " + e.getMessage());
        }
    }

    public void updatePlayerOnLogin(UUID uuid, String playerName) {
        String currentTime = LocalDateTime.now().format(dateFormatWithTime);
        String currentTimeWithSeconds = LocalDateTime.now().format(dateFormatWithSeconds);
        String todayDate = LocalDate.now().format(dateFormatNoTime);
        PlayerData data = rootData.players.computeIfAbsent(uuid, k -> {
            String firstJoinTime = LocalDateTime.now().format(dateFormatWithTime);
            PlayerData newPlayer = new PlayerData(playerName, firstJoinTime);
            DailyNewPlayersData dailyNewPlayers = rootData.server.newPlayersToday.computeIfAbsent(todayDate, d -> new DailyNewPlayersData());
            dailyNewPlayers.totalNewPlayersInDay++;
            dailyNewPlayers.players.put(uuid, playerName);
            return newPlayer;
        });
        data.playerName = playerName;
        if (!data.lastLoginTime.isEmpty() && data.lastQuitTime.isEmpty()) {
            try {
                LocalDateTime lastLogin = LocalDateTime.parse(data.lastLoginTime, dateFormatWithTime);
                LocalDateTime now = LocalDateTime.now();
                Duration sessionDuration = Duration.between(lastLogin, now);
                long sessionSeconds = sessionDuration.getSeconds();
                if (sessionSeconds < 0) sessionSeconds = 0;
                data.totalPlayTime += sessionSeconds;
                DailyLoginData dailyData = data.dailyLogins.computeIfAbsent(todayDate, k -> new DailyLoginData());
                dailyData.totalPlayTimeInDay += sessionSeconds;
                String currentWeek = LocalDate.now().format(weekFormatter);
                WeeklyLoginData weeklyData = data.weeklyLogins.computeIfAbsent(currentWeek, k -> new WeeklyLoginData());
                weeklyData.totalPlayTimeInWeek += sessionSeconds;
                rootData.server.totalPlayTimesInDay.merge(todayDate, sessionSeconds, Long::sum);
            } catch (Exception e) {
                logger.warn("Failed to parse last login time for player {} during new login (unrecorded session): {}", playerName, e.getMessage());
            }
        }
        data.lastLoginTime = currentTime;
        data.lastQuitTime = "";
        data.totalLoginCount++;
        DailyLoginData dailyData = data.dailyLogins.computeIfAbsent(todayDate, k -> new DailyLoginData());
        dailyData.loginCount++;
        dailyData.lastLoginTime = currentTimeWithSeconds;
        String currentWeek = LocalDate.now().format(weekFormatter);
        WeeklyLoginData weeklyData = data.weeklyLogins.computeIfAbsent(currentWeek, k -> new WeeklyLoginData());
        weeklyData.loginCount++;
        weeklyData.lastLoginTime = currentTimeWithSeconds;
        rootData.server.totalLoginCountsInDay.merge(todayDate, 1, Integer::sum);
        savePlayerData();
    }

    public void updatePlayerOnLogout(UUID uuid, String playerName) {
        String currentTime = LocalDateTime.now().format(dateFormatWithTime);
        String todayDate = LocalDate.now().format(dateFormatNoTime);
        PlayerData data = rootData.players.get(uuid);
        if (data != null) {
            if (!data.lastLoginTime.isEmpty() && data.lastQuitTime.isEmpty()) {
                data.lastQuitTime = currentTime;
                try {
                    LocalDateTime lastLogin = LocalDateTime.parse(data.lastLoginTime, dateFormatWithTime);
                    LocalDateTime currentLogout = LocalDateTime.parse(data.lastQuitTime, dateFormatWithTime);
                    Duration sessionDuration = Duration.between(lastLogin, currentLogout);
                    long sessionSeconds = sessionDuration.getSeconds();
                    if (sessionSeconds < 0) sessionSeconds = 0;
                    data.totalPlayTime += sessionSeconds;
                    DailyLoginData dailyData = data.dailyLogins.computeIfAbsent(todayDate, k -> new DailyLoginData());
                    dailyData.totalPlayTimeInDay += sessionSeconds;
                    String currentWeek = LocalDate.now().format(weekFormatter);
                    WeeklyLoginData weeklyData = data.weeklyLogins.computeIfAbsent(currentWeek, k -> new WeeklyLoginData());
                    weeklyData.totalPlayTimeInWeek += sessionSeconds;
                    rootData.server.totalPlayTimesInDay.merge(todayDate, sessionSeconds, Long::sum);
                } catch (Exception e) {
                    logger.warn("Failed to parse login/logout time for player {} during logout: {}", playerName, e.getMessage());
                }
            } else {
                logger.debug("Player {} not in an active login state (lastLoginTime empty or lastQuitTime already set). Skipping play time calculation on logout.", playerName);
            }
            savePlayerData();
        }
    }

    public void updatePlayerPlayTime(UUID uuid) {
        PlayerData data = rootData.players.get(uuid);
        String todayDate = LocalDate.now().format(dateFormatNoTime);
        if (data != null && !data.lastLoginTime.isEmpty() && data.lastQuitTime.isEmpty()) {
            long addedSeconds = 60;
            data.totalPlayTime += addedSeconds;
            DailyLoginData dailyData = data.dailyLogins.computeIfAbsent(todayDate, k -> new DailyLoginData());
            dailyData.totalPlayTimeInDay += addedSeconds;
            String currentWeek = LocalDate.now().format(weekFormatter);
            WeeklyLoginData weeklyData = data.weeklyLogins.computeIfAbsent(currentWeek, k -> new WeeklyLoginData());
            weeklyData.totalPlayTimeInWeek += addedSeconds;
            rootData.server.totalPlayTimesInDay.merge(todayDate, addedSeconds, Long::sum);
            savePlayerData();
        }
    }

    public void checkAndSetServerBootTime() {
        if (rootData.server == null) {
            rootData.server = new ServerData();
        }
        if (rootData.server.bootTime == null || rootData.server.bootTime.isEmpty()) {
            rootData.server.bootTime = LocalDate.now().format(dateFormatNoTime);
            logger.info("Server boot time initialized to: " + rootData.server.bootTime);
            rootData.server.lastReportGenerationDate = LocalDate.now().format(dateFormatNoTime);
            String currentBootDate = rootData.server.bootTime;
            rootData.server.newPlayersToday.putIfAbsent(currentBootDate, new DailyNewPlayersData());
            rootData.server.totalLoginCountsInDay.putIfAbsent(currentBootDate, 0);
            rootData.server.totalPlayTimesInDay.putIfAbsent(currentBootDate, 0L);

            savePlayerData();
        } else {
            String currentReportDate = LocalDate.now().format(dateFormatNoTime);
            if (!rootData.server.lastReportGenerationDate.equals(currentReportDate)) {
                rootData.server.lastReportGenerationDate = currentReportDate;
                savePlayerData();
                logger.info("Server last report generation date updated to: " + currentReportDate);
            }
            logger.info("Server boot time is already set to: " + rootData.server.bootTime + ". Not updating automatically.");
        }
    }

    public String getPlayerName(UUID uuid) {
        PlayerData data = rootData.players.get(uuid);
        return data != null ? data.playerName : null;
    }

    public String getPlayerFirstJoinTime(UUID uuid) {
        PlayerData data = rootData.players.get(uuid);
        return data != null ? data.firstJoinTime : null;
    }

    public Long getPlayerTotalPlayTime(UUID uuid) {
        PlayerData data = rootData.players.get(uuid);
        return data != null ? data.totalPlayTime : 0L;
    }

    public int getPlayerTotalLoginCount(UUID uuid) {
        PlayerData data = rootData.players.get(uuid);
        return data != null ? data.totalLoginCount : 0;
    }

    public DailyLoginData getPlayerDailyLoginData(UUID uuid, String date) {
        PlayerData data = rootData.players.get(uuid);
        return data != null ? data.dailyLogins.get(date) : null;
    }

    public int getPlayerDailyLoginCount(UUID uuid) {
        String todayDate = LocalDate.now().format(dateFormatNoTime);
        DailyLoginData dailyData = getPlayerDailyLoginData(uuid, todayDate);
        return dailyData != null ? dailyData.loginCount : 0;
    }

    public Long getPlayerDailyPlayTime(UUID uuid) {
        String todayDate = LocalDate.now().format(dateFormatNoTime);
        DailyLoginData dailyData = getPlayerDailyLoginData(uuid, todayDate);
        return dailyData != null ? dailyData.totalPlayTimeInDay : 0L;
    }

    public String getPlayerDailyLastLoginTime(UUID uuid) {
        String todayDate = LocalDate.now().format(dateFormatNoTime);
        DailyLoginData dailyData = getPlayerDailyLoginData(uuid, todayDate);
        return dailyData != null ? dailyData.lastLoginTime : null;
    }

    // --- Weekly Login Data Getters ---
    public WeeklyLoginData getPlayerWeeklyLoginData(UUID uuid, String week) {
        PlayerData data = rootData.players.get(uuid);
        return data != null ? data.weeklyLogins.get(week) : null;
    }

    public int getPlayerWeeklyLoginCount(UUID uuid) {
        String currentWeek = LocalDate.now().format(weekFormatter);
        WeeklyLoginData weeklyData = getPlayerWeeklyLoginData(uuid, currentWeek);
        return weeklyData != null ? weeklyData.loginCount : 0;
    }

    public Long getPlayerWeeklyPlayTime(UUID uuid) {
        String currentWeek = LocalDate.now().format(weekFormatter);
        WeeklyLoginData weeklyData = getPlayerWeeklyLoginData(uuid, currentWeek);
        return weeklyData != null ? weeklyData.totalPlayTimeInWeek : 0L;
    }

    public String getPlayerWeeklyLastLoginTime(UUID uuid) {
        String currentWeek = LocalDate.now().format(weekFormatter);
        WeeklyLoginData weeklyData = getPlayerWeeklyLoginData(uuid, currentWeek);
        return weeklyData != null ? weeklyData.lastLoginTime : null;
    }

    public String getServerBootTime() {
        return rootData.server != null ? rootData.server.bootTime : null;
    }

    public String getServerLastReportGenerationDate() {
        return rootData.server != null ? rootData.server.lastReportGenerationDate : null;
    }

    public Map<String, DailyNewPlayersData> getServerNewPlayersToday() {
        return rootData.server != null ? rootData.server.newPlayersToday : new ConcurrentHashMap<>();
    }

    public int getServerNewPlayersCountForDate(String date) {
        DailyNewPlayersData data = rootData.server.newPlayersToday.get(date);
        return data != null ? data.totalNewPlayersInDay : 0;
    }

    public Map<UUID, String> getServerNewPlayersListForDate(String date) {
        DailyNewPlayersData data = rootData.server.newPlayersToday.get(date);
        return data != null ? data.players : new ConcurrentHashMap<>();
    }

    public Map<String, Integer> getServerTotalLoginCountsInDay() {
        return rootData.server != null ? rootData.server.totalLoginCountsInDay : new ConcurrentHashMap<>();
    }

    public int getServerTotalLoginCountForDate(String date) {
        return rootData.server.totalLoginCountsInDay.getOrDefault(date, 0);
    }

    public Map<String, Long> getServerTotalPlayTimesInDay() {
        return rootData.server != null ? rootData.server.totalPlayTimesInDay : new ConcurrentHashMap<>();
    }

    public Long getServerTotalPlayTimeForDate(String date) {
        return rootData.server.totalPlayTimesInDay.getOrDefault(date, 0L);
    }

    public long getServerRunningDays() {
        if (rootData == null || rootData.server == null || rootData.server.bootTime == null || rootData.server.bootTime.isEmpty()) {
            return 0L;
        }
        return TimeUtil.getDaysBetweenStartDateAndNow(rootData.server.bootTime);
    }

    public static class PlayerDataLoadException extends RuntimeException {
        public PlayerDataLoadException(String message) {
            super(message);
        }

        public PlayerDataLoadException(String message, Throwable cause) {
            super(message, cause);
        }

        public PlayerDataLoadException(Throwable cause) {
            super(cause);
        }
    }
}