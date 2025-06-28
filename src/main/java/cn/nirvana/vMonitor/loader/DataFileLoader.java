package cn.nirvana.vMonitor.loader;

import cn.nirvana.vMonitor.util.TimeUtil;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.google.gson.JsonSyntaxException;

import com.velocitypowered.api.proxy.server.RegisteredServer;

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
        public Map<String, Integer> totalServerLoginCounts;
        public Map<String, Map<String, Integer>> dailyServerLoginCounts;

        public ServerData() {
            this.lastReportGenerationDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            this.newPlayersToday = new ConcurrentHashMap<>();
            this.totalLoginCountsInDay = new ConcurrentHashMap<>();
            this.totalPlayTimesInDay = new ConcurrentHashMap<>();
            this.totalServerLoginCounts = new ConcurrentHashMap<>();
            this.dailyServerLoginCounts = new ConcurrentHashMap<>();
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
        public Map<String, Integer> loggedInServerLoginCounts;

        public PlayerData(String playerName, String firstJoinTime) {
            this.playerName = playerName;
            this.firstJoinTime = firstJoinTime;
            this.lastLoginTime = "";
            this.lastQuitTime = "";
            this.totalPlayTime = 0L;
            this.totalLoginCount = 0;
            this.dailyLogins = new ConcurrentHashMap<>();
            this.weeklyLogins = new ConcurrentHashMap<>();
            this.loggedInServerLoginCounts = new ConcurrentHashMap<>();
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

    public void loadPlayerData() {
        Path playerDataFile = dataDirectory.resolve(playerDataFileName);
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(playerDataFile.toFile()), StandardCharsets.UTF_8)) {
            TypeToken<RootData> typeToken = new TypeToken<>() {};
            this.rootData = gson.fromJson(reader, typeToken.getType());
            if (this.rootData == null) {
                throw new PlayerDataLoadException("Player data file is empty or malformed: " + playerDataFile.toAbsolutePath());
            }
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
            if (this.rootData.server.totalServerLoginCounts == null) {
                this.rootData.server.totalServerLoginCounts = new ConcurrentHashMap<>();
            }
            if (this.rootData.server.dailyServerLoginCounts == null) {
                this.rootData.server.dailyServerLoginCounts = new ConcurrentHashMap<>();
            }
            for (PlayerData player : this.rootData.players.values()) {
                if (player.dailyLogins == null) {
                    player.dailyLogins = new ConcurrentHashMap<>();
                }
                if (player.weeklyLogins == null) {
                    player.weeklyLogins = new ConcurrentHashMap<>();
                }
                if (player.loggedInServerLoginCounts == null) {
                    player.loggedInServerLoginCounts = new ConcurrentHashMap<>();
                }
            }
            logger.info("Player data loaded successfully.");
        } catch (IOException e) {
            throw new PlayerDataLoadException("Failed to read player data file: " + playerDataFile.toAbsolutePath(), e);
        } catch (JsonSyntaxException e) {
            throw new PlayerDataLoadException("Failed to parse player data file (JSON syntax error): " + playerDataFile.toAbsolutePath(), e);
        } catch (Exception e) {
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
        String currentWeek = LocalDate.now().format(weekFormatter);
        PlayerData data = rootData.players.computeIfAbsent(uuid, k -> {
            String firstJoinTime = LocalDateTime.now().format(dateFormatWithTime);
            PlayerData newPlayer = new PlayerData(playerName, firstJoinTime);
            DailyNewPlayersData dailyNewPlayers = rootData.server.newPlayersToday.computeIfAbsent(todayDate, d -> new DailyNewPlayersData());
            dailyNewPlayers.totalNewPlayersInDay++;
            dailyNewPlayers.players.put(uuid, playerName);
            return newPlayer;
        });
        data.playerName = playerName;
        data.lastLoginTime = currentTime;
        data.totalLoginCount++;
        DailyLoginData dailyData = data.dailyLogins.computeIfAbsent(todayDate, d -> new DailyLoginData());
        dailyData.loginCount++;
        dailyData.lastLoginTime = currentTimeWithSeconds;
        WeeklyLoginData weeklyData = data.weeklyLogins.computeIfAbsent(currentWeek, w -> new WeeklyLoginData());
        weeklyData.loginCount++;
        weeklyData.lastLoginTime = currentTimeWithSeconds;
        rootData.server.totalLoginCountsInDay.merge(todayDate, 1, Integer::sum);
    }

    public void updatePlayerOnQuit(UUID uuid, String playerName, RegisteredServer server, Duration sessionDuration) {
        String currentTime = LocalDateTime.now().format(dateFormatWithTime);
        String todayDate = LocalDate.now().format(dateFormatNoTime);
        String currentWeek = LocalDate.now().format(weekFormatter);
        PlayerData data = rootData.players.get(uuid);
        if (data != null) {
            data.lastQuitTime = currentTime;
            data.totalPlayTime += sessionDuration.getSeconds();
            DailyLoginData dailyData = data.dailyLogins.get(todayDate);
            if (dailyData != null) {
                dailyData.totalPlayTimeInDay += sessionDuration.getSeconds();
            }
            WeeklyLoginData weeklyData = data.weeklyLogins.get(currentWeek);
            if (weeklyData != null) {
                weeklyData.totalPlayTimeInWeek += sessionDuration.getSeconds();
            }
            rootData.server.totalPlayTimesInDay.merge(todayDate, sessionDuration.getSeconds(), Long::sum);
        }
    }

    public void updatePlayerServerLogin(UUID uuid, String serverName) {
        String todayDate = LocalDate.now().format(dateFormatNoTime);
        String currentTimeWithSeconds = LocalDateTime.now().format(dateFormatWithSeconds);

        PlayerData playerData = rootData.players.get(uuid);
        if (playerData != null) {
            playerData.loggedInServerLoginCounts.merge(serverName, 1, Integer::sum);
            rootData.server.totalServerLoginCounts.merge(serverName, 1, Integer::sum);
            Map<String, Integer> dailyCountsForServer = rootData.server.dailyServerLoginCounts.computeIfAbsent(todayDate, k -> new ConcurrentHashMap<>());
            dailyCountsForServer.merge(serverName, 1, Integer::sum);
            String currentWeek = LocalDate.now().format(weekFormatter);
            DailyLoginData dailyData = playerData.dailyLogins.get(todayDate);
            if (dailyData != null) {
                dailyData.lastLoginTime = currentTimeWithSeconds;
            }
            WeeklyLoginData weeklyData = playerData.weeklyLogins.get(currentWeek);
            if (weeklyData != null) {
                weeklyData.lastLoginTime = currentTimeWithSeconds;
            }
        }
    }

    public RootData getRootData() {
        return rootData;
    }

    public PlayerData getPlayerData(UUID uuid) {
        return rootData.players.get(uuid);
    }

    public Map<String, DailyNewPlayersData> getNewPlayersTodayData() {
        return rootData.server.newPlayersToday;
    }

    public Map<String, Integer> getTotalLoginCountsInDay() {
        return rootData.server.totalLoginCountsInDay;
    }

    public Map<String, Long> getTotalPlayTimesInDay() {
        return rootData.server.totalPlayTimesInDay;
    }

    public String getLastReportGenerationDate() {
        return rootData.server.lastReportGenerationDate;
    }

    public void setLastReportGenerationDate(String date) {
        rootData.server.lastReportGenerationDate = date;
    }

    public Map<String, Integer> getTotalServerLoginCounts() {
        return rootData.server.totalServerLoginCounts;
    }

    public Map<String, Map<String, Integer>> getDailyServerLoginCounts() {
        return rootData.server.dailyServerLoginCounts;
    }

    public DailyLoginData getPlayerDailyLoginData(UUID uuid, String date) {
        PlayerData playerData = getPlayerData(uuid);
        return playerData != null ? playerData.dailyLogins.get(date) : null;
    }

    public WeeklyLoginData getPlayerWeeklyLoginData(UUID uuid, String week) {
        PlayerData playerData = getPlayerData(uuid);
        return playerData != null ? playerData.weeklyLogins.get(week) : null;
    }

    public Map<String, Integer> getPlayerLoggedInServerLoginCounts(UUID uuid) {
        PlayerData playerData = getPlayerData(uuid);
        return playerData != null ? playerData.loggedInServerLoginCounts : new ConcurrentHashMap<>();
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
    }
}