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
import java.nio.file.Files;
import java.nio.file.Path;

import java.time.Duration;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DataFileLoader {
    private final Logger logger;
    private final Path dataDirectory;
    private final String playerDataFileName = "data.json";
    private final Path playerDataFilePath;

    private RootData rootData;

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
            this.bootTime = "";
            this.lastReportGenerationDate = TimeUtil.getCurrentDateString();
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

        public WeeklyLoginData() {
            this.loginCount = 0;
            this.totalPlayTimeInWeek = 0L;
        }
    }

    public DataFileLoader(Logger logger, Path dataDirectory) {
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.playerDataFilePath = dataDirectory.resolve(playerDataFileName);
        this.rootData = new RootData();
    }

    public RootData getRootData() {
        return rootData;
    }

    public PlayerData getPlayerData(UUID uuid) {
        return rootData.players.get(uuid);
    }

    public void loadPlayerData() throws PlayerDataLoadException {
        if (!Files.exists(playerDataFilePath)) {
            logger.error("Player data file '{}' does not exist after initial copy. This is a critical error.", playerDataFileName);
            throw new PlayerDataLoadException("Player data file not found after initialization.");
        }
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(playerDataFilePath.toFile()), StandardCharsets.UTF_8)) {
            TypeToken<RootData> typeToken = new TypeToken<RootData>() {};
            RootData loadedData = gson.fromJson(reader, typeToken.getType());
            if (loadedData == null) {
                logger.warn("Player data file '{}' is empty or malformed. Initializing new data in memory.", playerDataFileName);
                this.rootData = new RootData();
            } else {
                this.rootData = loadedData;
                if (this.rootData.server.totalServerLoginCounts == null) {
                    this.rootData.server.totalServerLoginCounts = new ConcurrentHashMap<>();
                }
                if (this.rootData.server.dailyServerLoginCounts == null) {
                    this.rootData.server.dailyServerLoginCounts = new ConcurrentHashMap<>();
                }
                this.rootData.players.values().forEach(playerData -> {
                    if (playerData.loggedInServerLoginCounts == null) {
                        playerData.loggedInServerLoginCounts = new ConcurrentHashMap<>();
                    }
                });
            }
        } catch (IOException | JsonSyntaxException e) {
            logger.error("Failed to load player data from '{}'. Initializing new data in memory. Error: {}", playerDataFileName, e.getMessage());
            this.rootData = new RootData(); // 加载失败时初始化新数据
            throw new PlayerDataLoadException("Failed to load player data", e);
        }
    }

    public void savePlayerData() {
        try (FileWriter writer = new FileWriter(playerDataFilePath.toFile(), StandardCharsets.UTF_8)) {
            gson.toJson(rootData, writer);
            logger.debug("Player data saved successfully.");
        } catch (IOException e) {
            logger.error("Failed to save player data to '{}': {}", playerDataFileName, e.getMessage());
        }
    }

    public void updatePlayerOnLogin(UUID uuid, String playerName) {
        String currentDate = TimeUtil.getCurrentDateString();
        String currentDateTimeSecond = TimeUtil.getCurrentDateTimeSecondString();
        String currentWeek = TimeUtil.getCurrentWeekString();
        PlayerData playerData = rootData.players.computeIfAbsent(uuid, k -> {
            logger.info("New player detected: {}. Initializing data.", playerName);
            String firstJoinTime = currentDateTimeSecond;
            PlayerData newPlayer = new PlayerData(playerName, firstJoinTime);
            DailyNewPlayersData dailyNewPlayers = rootData.server.newPlayersToday.computeIfAbsent(currentDate, d -> new DailyNewPlayersData());
            dailyNewPlayers.totalNewPlayersInDay++;
            dailyNewPlayers.players.put(uuid, playerName);
            return newPlayer;
        });
        playerData.playerName = playerName;
        playerData.lastLoginTime = currentDateTimeSecond;
        playerData.totalLoginCount++;
        DailyLoginData dailyLogin = playerData.dailyLogins.computeIfAbsent(currentDate, d -> new DailyLoginData());
        dailyLogin.loginCount++;
        dailyLogin.lastLoginTime = currentDateTimeSecond;
        WeeklyLoginData weeklyLogin = playerData.weeklyLogins.computeIfAbsent(currentWeek, w -> new WeeklyLoginData());
        weeklyLogin.loginCount++;
        rootData.server.totalLoginCountsInDay.merge(currentDate, 1, Integer::sum);
        savePlayerData();
    }

    public void updatePlayerOnQuit(UUID uuid, String playerName, com.velocitypowered.api.proxy.server.RegisteredServer disconnectedFromServer, Duration sessionDuration) {
        PlayerData playerData = rootData.players.get(uuid);
        if (playerData == null) {
            logger.warn("Attempted to update data for unknown player: {}({}).", playerName, uuid);
            return;
        }
        String currentDateTimeSecond = TimeUtil.getCurrentDateTimeSecondString();
        String currentDate = TimeUtil.getCurrentDateString();
        String currentWeek = TimeUtil.getCurrentWeekString();
        playerData.lastQuitTime = currentDateTimeSecond;
        playerData.totalPlayTime += sessionDuration.getSeconds();
        DailyLoginData dailyLogin = playerData.dailyLogins.get(currentDate);
        if (dailyLogin != null) {
            dailyLogin.totalPlayTimeInDay += sessionDuration.getSeconds();
        } else {
            dailyLogin = new DailyLoginData();
            dailyLogin.totalPlayTimeInDay = sessionDuration.getSeconds();
            playerData.dailyLogins.put(currentDate, dailyLogin);
        }
        WeeklyLoginData weeklyLogin = playerData.weeklyLogins.get(currentWeek);
        if (weeklyLogin != null) {
            weeklyLogin.totalPlayTimeInWeek += sessionDuration.getSeconds();
        } else {
            weeklyLogin = new WeeklyLoginData();
            weeklyLogin.totalPlayTimeInWeek = sessionDuration.getSeconds();
            playerData.weeklyLogins.put(currentWeek, weeklyLogin);
        }
        rootData.server.totalPlayTimesInDay.merge(currentDate, sessionDuration.getSeconds(), Long::sum);
        savePlayerData();
    }

    public void updatePlayerServerLogin(UUID uuid, String serverName) {
        PlayerData playerData = rootData.players.get(uuid);
        if (playerData == null) {
            logger.warn("Attempted to update server login for unknown player: {}.", uuid);
            return;
        }
        String currentDate = TimeUtil.getCurrentDateString();
        playerData.loggedInServerLoginCounts.merge(serverName, 1, Integer::sum);
        rootData.server.totalServerLoginCounts.merge(serverName, 1, Integer::sum);
        Map<String, Integer> dailyServerLogins = rootData.server.dailyServerLoginCounts.computeIfAbsent(currentDate, k -> new ConcurrentHashMap<>());
        dailyServerLogins.merge(serverName, 1, Integer::sum);
        savePlayerData();
    }

    public static class PlayerDataLoadException extends Exception {
        public PlayerDataLoadException(String message) {
            super(message);
        }

        public PlayerDataLoadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}