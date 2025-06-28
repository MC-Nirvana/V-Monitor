// File: src/main/java/cn/nirvana/vMonitor/loader/DataFileLoader.java
package cn.nirvana.vMonitor.loader;

import cn.nirvana.vMonitor.util.TimeUtil; // 确保导入 TimeUtil

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
import java.nio.file.Files; // 导入 Files
import java.nio.file.Path;

import java.time.Duration; // 重新导入 Duration
// 移除了不必要的 java.time.LocalDate, java.time.LocalDateTime, java.time.format.DateTimeFormatter
// 因为现在都通过 TimeUtil 处理

import java.util.Locale; // 仍然需要 Locale 用于某些特定情况，但这里不再直接使用 DateTimeFormatter
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DataFileLoader {
    private final Logger logger;
    private final Path dataDirectory;
    private final String playerDataFileName = "data.json"; // 重新添加此行
    private final Path playerDataFilePath; // 存储完整的文件路径

    private RootData rootData;

    private final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(Long.class, new TimeUtil()) // 确保 TimeUtil 能够处理 Long 类型
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
        public Map<String, Integer> totalServerLoginCounts; // 新增：服务器总登录次数
        public Map<String, Map<String, Integer>> dailyServerLoginCounts; // 新增：每日服务器登录次数

        public ServerData() {
            this.bootTime = ""; // 初始化为空字符串，将在 VMonitor 中设置
            this.lastReportGenerationDate = TimeUtil.getCurrentDateString(); // 使用 TimeUtil
            this.newPlayersToday = new ConcurrentHashMap<>();
            this.totalLoginCountsInDay = new ConcurrentHashMap<>();
            this.totalPlayTimesInDay = new ConcurrentHashMap<>();
            this.totalServerLoginCounts = new ConcurrentHashMap<>(); // 初始化
            this.dailyServerLoginCounts = new ConcurrentHashMap<>(); // 初始化
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
        public Map<String, Integer> loggedInServerLoginCounts; // 新增：玩家登录到各个服务器的次数

        public PlayerData(String playerName, String firstJoinTime) {
            this.playerName = playerName;
            this.firstJoinTime = firstJoinTime;
            this.lastLoginTime = "";
            this.lastQuitTime = "";
            this.totalPlayTime = 0L;
            this.totalLoginCount = 0;
            this.dailyLogins = new ConcurrentHashMap<>();
            this.weeklyLogins = new ConcurrentHashMap<>();
            this.loggedInServerLoginCounts = new ConcurrentHashMap<>(); // 初始化
        }
    }

    public static class DailyLoginData {
        public int loginCount;
        public Long totalPlayTimeInDay;
        public String lastLoginTime; // 包含秒数

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
        this.rootData = new RootData(); // 初始化 rootData
    }

    public RootData getRootData() {
        return rootData;
    }

    public PlayerData getPlayerData(UUID uuid) {
        return rootData.players.get(uuid);
    }

    /**
     * 加载玩家数据。
     * @throws PlayerDataLoadException 如果加载失败
     */
    public void loadPlayerData() throws PlayerDataLoadException {
        if (!Files.exists(playerDataFilePath)) {
            logger.debug("Player data file '{}' not found. Creating a new one.", playerDataFileName); // 降级为调试日志
            // 如果文件不存在，直接初始化新的 RootData 并保存
            this.rootData = new RootData();
            savePlayerData(); // 保存新创建的空数据
            return;
        }

        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(playerDataFilePath.toFile()), StandardCharsets.UTF_8)) {
            TypeToken<RootData> typeToken = new TypeToken<RootData>() {};
            RootData loadedData = gson.fromJson(reader, typeToken.getType());

            if (loadedData == null) {
                logger.warn("Player data file '{}' is empty or malformed. Initializing new data.", playerDataFileName);
                this.rootData = new RootData();
            } else {
                this.rootData = loadedData;
                // 确保新添加的字段在加载旧数据时被初始化
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
            // logger.info("Player data loaded successfully."); // 将此日志移到 VMonitor
        } catch (IOException | JsonSyntaxException e) {
            logger.error("Failed to load player data from '{}'. Initializing new data. Error: {}", playerDataFileName, e.getMessage());
            this.rootData = new RootData(); // 加载失败时初始化新数据
            throw new PlayerDataLoadException("Failed to load player data", e);
        }
    }

    /**
     * 保存玩家数据。
     */
    public void savePlayerData() {
        try (FileWriter writer = new FileWriter(playerDataFilePath.toFile(), StandardCharsets.UTF_8)) {
            gson.toJson(rootData, writer);
            logger.debug("Player data saved successfully."); // 调试级别日志，避免频繁输出
        } catch (IOException e) {
            logger.error("Failed to save player data to '{}': {}", playerDataFileName, e.getMessage());
        }
    }

    /**
     * 处理玩家登录事件。
     * @param uuid 玩家UUID
     * @param playerName 玩家名称
     */
    public void updatePlayerOnLogin(UUID uuid, String playerName) {
        String currentDate = TimeUtil.getCurrentDateString(); // 使用 TimeUtil
        String currentDateTimeSecond = TimeUtil.getCurrentDateTimeSecondString(); // 使用 TimeUtil
        String currentWeek = TimeUtil.getCurrentWeekString(); // 使用 TimeUtil

        PlayerData playerData = rootData.players.computeIfAbsent(uuid, k -> {
            logger.info("New player detected: {}. Initializing data.", playerName);
            // 首次加入时间
            String firstJoinTime = currentDateTimeSecond;
            PlayerData newPlayer = new PlayerData(playerName, firstJoinTime);

            // 更新服务器每日新玩家统计
            DailyNewPlayersData dailyNewPlayers = rootData.server.newPlayersToday.computeIfAbsent(currentDate, d -> new DailyNewPlayersData());
            dailyNewPlayers.totalNewPlayersInDay++;
            dailyNewPlayers.players.put(uuid, playerName);
            return newPlayer;
        });

        // 确保玩家名称是最新的
        playerData.playerName = playerName;
        playerData.lastLoginTime = currentDateTimeSecond; // 更新上次登录时间
        playerData.totalLoginCount++; // 总登录次数增加

        // 更新每日登录数据
        DailyLoginData dailyLogin = playerData.dailyLogins.computeIfAbsent(currentDate, d -> new DailyLoginData());
        dailyLogin.loginCount++;
        dailyLogin.lastLoginTime = currentDateTimeSecond; // 每日登录的上次登录时间

        // 更新每周登录数据
        WeeklyLoginData weeklyLogin = playerData.weeklyLogins.computeIfAbsent(currentWeek, w -> new WeeklyLoginData());
        weeklyLogin.loginCount++;

        // 更新服务器总登录次数（每日）
        rootData.server.totalLoginCountsInDay.merge(currentDate, 1, Integer::sum);

        savePlayerData(); // 立即保存数据
    }

    /**
     * 处理玩家退出事件。
     * @param uuid 玩家UUID
     * @param playerName 玩家名称
     * @param disconnectedFromServer 玩家断开连接时所在的服务器
     * @param sessionDuration 本次会话时长
     */
    public void updatePlayerOnQuit(UUID uuid, String playerName, com.velocitypowered.api.proxy.server.RegisteredServer disconnectedFromServer, Duration sessionDuration) {
        PlayerData playerData = rootData.players.get(uuid);
        if (playerData == null) {
            logger.warn("Attempted to update data for unknown player: {}({}).", playerName, uuid);
            return;
        }

        String currentDateTimeSecond = TimeUtil.getCurrentDateTimeSecondString(); // 使用 TimeUtil
        String currentDate = TimeUtil.getCurrentDateString(); // 使用 TimeUtil
        String currentWeek = TimeUtil.getCurrentWeekString(); // 使用 TimeUtil

        playerData.lastQuitTime = currentDateTimeSecond; // 更新上次退出时间
        playerData.totalPlayTime += sessionDuration.getSeconds(); // 增加总游玩时间

        // 更新每日游玩时间
        DailyLoginData dailyLogin = playerData.dailyLogins.get(currentDate);
        if (dailyLogin != null) {
            dailyLogin.totalPlayTimeInDay += sessionDuration.getSeconds();
        } else {
            // 如果玩家在同一天登录但没有 DailyLoginData（例如，数据损坏或插件重启），则创建
            dailyLogin = new DailyLoginData();
            dailyLogin.totalPlayTimeInDay = sessionDuration.getSeconds();
            playerData.dailyLogins.put(currentDate, dailyLogin);
        }

        // 更新每周游玩时间
        WeeklyLoginData weeklyLogin = playerData.weeklyLogins.get(currentWeek);
        if (weeklyLogin != null) {
            weeklyLogin.totalPlayTimeInWeek += sessionDuration.getSeconds();
        } else {
            // 如果玩家在同一周登录但没有 WeeklyLoginData
            weeklyLogin = new WeeklyLoginData();
            weeklyLogin.totalPlayTimeInWeek = sessionDuration.getSeconds();
            playerData.weeklyLogins.put(currentWeek, weeklyLogin);
        }

        // 更新服务器总游玩时间（每日）
        rootData.server.totalPlayTimesInDay.merge(currentDate, sessionDuration.getSeconds(), Long::sum);

        savePlayerData(); // 立即保存数据
    }

    /**
     * 处理玩家连接到特定服务器的事件。
     * @param uuid 玩家UUID
     * @param serverName 服务器名称
     */
    public void updatePlayerServerLogin(UUID uuid, String serverName) {
        PlayerData playerData = rootData.players.get(uuid);
        if (playerData == null) {
            logger.warn("Attempted to update server login for unknown player: {}.", uuid);
            return;
        }

        String currentDate = TimeUtil.getCurrentDateString(); // 使用 TimeUtil

        // 更新玩家自身的服务器登录计数
        playerData.loggedInServerLoginCounts.merge(serverName, 1, Integer::sum);

        // 更新服务器的总登录计数
        rootData.server.totalServerLoginCounts.merge(serverName, 1, Integer::sum);

        // 更新服务器的每日登录计数
        Map<String, Integer> dailyServerLogins = rootData.server.dailyServerLoginCounts.computeIfAbsent(currentDate, k -> new ConcurrentHashMap<>());
        dailyServerLogins.merge(serverName, 1, Integer::sum);

        savePlayerData(); // 立即保存数据
    }


    public static class PlayerDataLoadException extends Exception {
        public PlayerDataLoadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}