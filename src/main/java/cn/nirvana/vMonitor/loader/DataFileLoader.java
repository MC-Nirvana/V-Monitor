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
import java.time.LocalDateTime;

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
            .registerTypeAdapter(Long.class, new TimeUtil()) // For TimeUtil's formatSecondsToHHmm
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
        public Map<String, DailyNewPlayersData> newPlayersToday; // 保持 DailyNewPlayersData 类型
        public Map<String, Integer> totalLoginCountsInDay; // 重命名
        public Map<String, Long> totalPlayTimesInDay;
        public Map<String, Integer> totalServerLoginCounts; // 新增字段
        public Map<String, Map<String, Integer>> dailyServerLoginCounts;

        public ServerData() {
            this.bootTime = TimeUtil.getCurrentDateTimeSecondString();
            this.lastReportGenerationDate = TimeUtil.getCurrentDateString();
            this.newPlayersToday = new ConcurrentHashMap<>();
            this.totalLoginCountsInDay = new ConcurrentHashMap<>(); // 初始化
            this.totalPlayTimesInDay = new ConcurrentHashMap<>();
            this.totalServerLoginCounts = new ConcurrentHashMap<>(); // 初始化
            this.dailyServerLoginCounts = new ConcurrentHashMap<>();
        }
    }

    public static class DailyNewPlayersData {
        public int totalNewPlayersInDay; // 重命名 'count' 为 'totalNewPlayersInDay'
        public Map<UUID, String> players; // 重命名 'newPlayers' 为 'players'

        public DailyNewPlayersData() {
            this.totalNewPlayersInDay = 0;
            this.players = new ConcurrentHashMap<>();
        }
    }

    public static class PlayerData {
        public String playerName; // 重命名 'lastKnownName' 为 'playerName'
        public String firstJoinTime; // 重命名 'firstLoginTime' 为 'firstJoinTime'
        public String lastLoginTime;
        public String lastQuitTime; // 新增字段
        public long totalPlayTime; // 总游戏时长，秒
        public int totalLoginCount; // 新增字段
        public Map<String, DailyLoginData> dailyLogins; // 更改类型和名称
        public Map<String, WeeklyLoginData> weeklyLogins;
        public Map<String, Integer> loggedInServerLoginCounts;
        public Map<String, String> lastLoginServerTimes;

        public PlayerData(String name) {
            this.playerName = name;
            this.firstJoinTime = TimeUtil.getCurrentDateTimeSecondString();
            this.lastLoginTime = TimeUtil.getCurrentDateTimeSecondString();
            this.lastQuitTime = ""; // 初始为空
            this.totalPlayTime = 0;
            this.totalLoginCount = 0; // 初始化
            this.dailyLogins = new ConcurrentHashMap<>(); // 初始化
            this.weeklyLogins = new ConcurrentHashMap<>();
            this.loggedInServerLoginCounts = new ConcurrentHashMap<>();
            this.lastLoginServerTimes = new ConcurrentHashMap<>();
        }
    }

    public static class DailyLoginData {
        public int loginCount;
        public long totalPlayTimeInDay; // 秒
        public String lastLoginTime; // 格式 "yyyy-MM-dd HH:mm:ss"

        public DailyLoginData() {
            this.loginCount = 0;
            this.totalPlayTimeInDay = 0;
            this.lastLoginTime = "";
        }
    }

    public static class WeeklyLoginData {
        public int loginCount; // 新增字段
        public long totalPlayTimeInWeek; // 秒
        public String lastLoginTime; // 新增字段

        public WeeklyLoginData() {
            this.loginCount = 0;
            this.totalPlayTimeInWeek = 0;
            this.lastLoginTime = "";
        }
    }

    public DataFileLoader(Logger logger, Path dataDirectory) {
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.playerDataFilePath = dataDirectory.resolve(playerDataFileName);
        this.rootData = new RootData(); // 初始化一个空RootData，防止NPE
    }

    /**
     * 从提供的 InputStreamReader 加载数据文件。
     * 此方法现在只关注解析逻辑，将文件I/O异常和JSON解析异常抛出，由调用者处理。
     *
     * @param reader 用于读取数据文件的 InputStreamReader。
     * @throws JsonSyntaxException 如果JSON文件语法错误。
     */
    public void loadData(InputStreamReader reader) {
        TypeToken<RootData> typeToken = new TypeToken<RootData>() {};
        this.rootData = gson.fromJson(reader, typeToken.getType());
    }

    public void savePlayerData() {
        try (FileWriter writer = new FileWriter(playerDataFilePath.toFile(), StandardCharsets.UTF_8)) {
            gson.toJson(rootData, writer);
        } catch (IOException e) {
            logger.error("Failed to save player data to '{}': {}", playerDataFileName, e.getMessage());
        }
    }

    public RootData getRootData() {
        return rootData;
    }

    public PlayerData getPlayerData(UUID uuid) {
        return rootData.players.get(uuid);
    }

    public void createPlayerData(UUID uuid, String playerName) {
        PlayerData newPlayer = new PlayerData(playerName);
        rootData.players.put(uuid, newPlayer);

        String currentDate = TimeUtil.getCurrentDateString();
        // 更新 DailyNewPlayersData
        DailyNewPlayersData dailyNewPlayers = rootData.server.newPlayersToday.computeIfAbsent(currentDate, k -> new DailyNewPlayersData());
        dailyNewPlayers.players.put(uuid, playerName);
        dailyNewPlayers.totalNewPlayersInDay++; // 更新新玩家总数

        // 更新 DailyLoginData for player's first login
        DailyLoginData dailyLogin = new DailyLoginData();
        dailyLogin.loginCount = 1;
        dailyLogin.lastLoginTime = TimeUtil.getCurrentDateTimeSecondString();
        newPlayer.dailyLogins.put(currentDate, dailyLogin);

        // 更新 WeeklyLoginData for player's first login
        String currentWeek = TimeUtil.getCurrentWeekString();
        WeeklyLoginData weeklyLogin = new WeeklyLoginData();
        weeklyLogin.loginCount = 1;
        weeklyLogin.lastLoginTime = TimeUtil.getCurrentDateTimeSecondString();
        newPlayer.weeklyLogins.put(currentWeek, weeklyLogin);

        // 更新服务器的总登录次数（每日）
        rootData.server.totalLoginCountsInDay.merge(currentDate, 1, Integer::sum);

        savePlayerData();
    }

    public void updatePlayerOnLogin(UUID uuid, String playerName) {
        PlayerData playerData = rootData.players.get(uuid);
        if (playerData == null) {
            logger.warn("Attempted to update login for unknown player: {}. Creating new data.", uuid);
            createPlayerData(uuid, playerName);
            playerData = rootData.players.get(uuid); // Re-fetch after creation
        }

        playerData.playerName = playerName; // 更新玩家名称
        playerData.lastLoginTime = TimeUtil.getCurrentDateTimeSecondString();
        playerData.totalLoginCount++; // 更新总登录次数

        String currentDate = TimeUtil.getCurrentDateString();
        // 更新玩家每日登录数据
        DailyLoginData dailyLogin = playerData.dailyLogins.computeIfAbsent(currentDate, k -> new DailyLoginData());
        dailyLogin.loginCount++;
        dailyLogin.lastLoginTime = playerData.lastLoginTime;

        // 更新玩家每周登录数据
        String currentWeek = TimeUtil.getCurrentWeekString();
        WeeklyLoginData weeklyLogin = playerData.weeklyLogins.computeIfAbsent(currentWeek, k -> new WeeklyLoginData());
        weeklyLogin.loginCount++;
        weeklyLogin.lastLoginTime = playerData.lastLoginTime;

        // 更新服务器的总登录次数（每日）
        rootData.server.totalLoginCountsInDay.merge(currentDate, 1, Integer::sum);
        savePlayerData();
    }

    public void updatePlayerOnQuit(UUID uuid, String playerName, String disconnectedFromServer, Duration sessionDuration) {
        PlayerData playerData = rootData.players.get(uuid);
        if (playerData == null) {
            logger.warn("Attempted to update quit for unknown player: {}. Skipping.", uuid);
            return;
        }

        playerData.lastQuitTime = TimeUtil.getCurrentDateTimeSecondString(); // 更新最后退出时间
        playerData.totalPlayTime += sessionDuration.getSeconds(); // 更新总游戏时长

        String currentDate = TimeUtil.getCurrentDateString();
        // 更新玩家每日游戏时长
        DailyLoginData dailyLogin = playerData.dailyLogins.computeIfAbsent(currentDate, k -> new DailyLoginData());
        dailyLogin.totalPlayTimeInDay += sessionDuration.getSeconds();

        // 更新玩家每周游戏时长
        String currentWeek = TimeUtil.getCurrentWeekString();
        WeeklyLoginData weeklyLogin = playerData.weeklyLogins.computeIfAbsent(currentWeek, k -> new WeeklyLoginData());
        weeklyLogin.totalPlayTimeInWeek += sessionDuration.getSeconds();

        // 更新服务器总游戏时长（每日）
        rootData.server.totalPlayTimesInDay.merge(currentDate, sessionDuration.getSeconds(), Long::sum);
        savePlayerData();
    }

    public void updatePlayerServerLogin(UUID uuid, String serverName) {
        PlayerData playerData = rootData.players.get(uuid);
        if (playerData == null) {
            logger.warn("Attempted to update server login for unknown player: {}. Please ensure player data is created.", uuid);
            return;
        }
        String currentDate = TimeUtil.getCurrentDateString();

        // 更新玩家在该服务器的登录次数
        playerData.loggedInServerLoginCounts.merge(serverName, 1, Integer::sum);
        playerData.lastLoginServerTimes.put(serverName, TimeUtil.getCurrentDateTimeSecondString()); // 更新最后一次登录该服务器的时间

        // 更新服务器总登录次数 (所有时间段)
        rootData.server.totalServerLoginCounts.merge(serverName, 1, Integer::sum);

        // 更新服务器每日登录次数
        Map<String, Integer> dailyServerLogins = rootData.server.dailyServerLoginCounts.computeIfAbsent(currentDate, k -> new ConcurrentHashMap<>());
        dailyServerLogins.merge(serverName, 1, Integer::sum);
        savePlayerData();
    }
}