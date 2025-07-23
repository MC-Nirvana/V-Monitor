package cn.nirvana.vMonitor.loader;

import cn.nirvana.vMonitor.util.TimeUtil;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.google.gson.JsonSyntaxException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonParseException;
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
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.Locale;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.lang.reflect.Type;

public class DataFileLoader {
    private final Logger logger;
    private final Path dataDirectory;
    private final String playerDataFileName = "data.json";
    private final Path playerDataFilePath;

    private RootData rootData;

    private final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(RootData.class, new RootDataSerializer())
            .registerTypeAdapter(RootData.class, new RootDataDeserializer())
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .registerTypeAdapter(PlayerData.class, new PlayerDataSerializer())
            .registerTypeAdapter(PlayerData.class, new PlayerDataDeserializer())
            .create();

    // 添加LocalDateTime的适配器
    private static class LocalDateTimeAdapter extends TypeAdapter<LocalDateTime> {
        @Override
        public void write(JsonWriter out, LocalDateTime value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(TimeUtil.DateTimeConverter.fromTimestamp(value.toEpochSecond(java.time.ZoneOffset.UTC)));
            }
        }

        @Override
        public LocalDateTime read(JsonReader in) throws IOException {
            String str = in.nextString();
            if (str == null || str.isEmpty()) {
                return null;
            }
            long timestamp = TimeUtil.DateTimeConverter.toTimestamp(str);
            return LocalDateTime.ofEpochSecond(timestamp, 0, java.time.ZoneOffset.UTC);
        }
    }

    // RootData 序列化适配器
    private static class RootDataSerializer implements JsonSerializer<RootData> {
        @Override
        public JsonElement serialize(RootData src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject rootObject = new JsonObject();

            // 序列化 server 部分
            JsonObject serverObject = new JsonObject();
            serverObject.addProperty("bootTime", src.server.bootTime);
            serverObject.addProperty("lastReportGenerationDate", src.server.lastReportGenerationDate);

            // newPlayersToday
            serverObject.add("newPlayersToday", context.serialize(src.server.newPlayersToday));

            // totalLoginCountsInDay
            serverObject.add("totalLoginCountsInDay", context.serialize(src.server.totalLoginCountsInDay));

            // totalPlayTimesInDay - 转换为 HH:mm:ss 格式
            JsonObject totalPlayTimesInDayObject = new JsonObject();
            for (Map.Entry<String, Long> entry : src.server.totalPlayTimesInDay.entrySet()) {
                totalPlayTimesInDayObject.addProperty(entry.getKey(), TimeUtil.TimePeriodConverter.fromSeconds(entry.getValue()));
            }
            serverObject.add("totalPlayTimesInDay", totalPlayTimesInDayObject);

            // totalServerLoginCounts
            serverObject.add("totalServerLoginCounts", context.serialize(src.server.totalServerLoginCounts));

            // dailyServerLoginCounts
            serverObject.add("dailyServerLoginCounts", context.serialize(src.server.dailyServerLoginCounts));

            rootObject.add("server", serverObject);

            // 序列化 players 部分
            JsonObject playersObject = new JsonObject();
            for (Map.Entry<UUID, PlayerData> entry : src.players.entrySet()) {
                playersObject.add(entry.getKey().toString(), context.serialize(entry.getValue()));
            }
            rootObject.add("players", playersObject);

            return rootObject;
        }
    }

    // RootData 反序列化适配器
    private static class RootDataDeserializer implements JsonDeserializer<RootData> {
        @Override
        public RootData deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            RootData rootData = new RootData();
            JsonObject rootObject = json.getAsJsonObject();

            if (rootObject.has("server")) {
                JsonObject serverObject = rootObject.getAsJsonObject("server");
                ServerData serverData = new ServerData();

                if (serverObject.has("bootTime")) {
                    serverData.bootTime = serverObject.get("bootTime").getAsString();
                }

                if (serverObject.has("lastReportGenerationDate")) {
                    serverData.lastReportGenerationDate = serverObject.get("lastReportGenerationDate").getAsString();
                }

                if (serverObject.has("newPlayersToday")) {
                    serverData.newPlayersToday = context.deserialize(serverObject.get("newPlayersToday"), serverData.newPlayersToday.getClass());
                }

                if (serverObject.has("totalLoginCountsInDay")) {
                    serverData.totalLoginCountsInDay = context.deserialize(serverObject.get("totalLoginCountsInDay"), serverData.totalLoginCountsInDay.getClass());
                }

                if (serverObject.has("totalPlayTimesInDay")) {
                    JsonObject totalPlayTimesInDayObject = serverObject.getAsJsonObject("totalPlayTimesInDay");
                    for (Map.Entry<String, JsonElement> entry : totalPlayTimesInDayObject.entrySet()) {
                        String timeStr = entry.getValue().getAsString();
                        long seconds = TimeUtil.TimePeriodConverter.toSeconds(timeStr);
                        serverData.totalPlayTimesInDay.put(entry.getKey(), seconds);
                    }
                }

                if (serverObject.has("totalServerLoginCounts")) {
                    serverData.totalServerLoginCounts = context.deserialize(serverObject.get("totalServerLoginCounts"), serverData.totalServerLoginCounts.getClass());
                }

                if (serverObject.has("dailyServerLoginCounts")) {
                    serverData.dailyServerLoginCounts = context.deserialize(serverObject.get("dailyServerLoginCounts"), serverData.dailyServerLoginCounts.getClass());
                }

                rootData.server = serverData;
            }

            if (rootObject.has("players")) {
                JsonObject playersObject = rootObject.getAsJsonObject("players");
                for (Map.Entry<String, JsonElement> entry : playersObject.entrySet()) {
                    UUID uuid = UUID.fromString(entry.getKey());
                    PlayerData playerData = context.deserialize(entry.getValue(), PlayerData.class);
                    rootData.players.put(uuid, playerData);
                }
            }

            return rootData;
        }
    }

    // PlayerData 序列化适配器
    private static class PlayerDataSerializer implements JsonSerializer<PlayerData> {
        @Override
        public JsonElement serialize(PlayerData src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject playerObject = new JsonObject();
            playerObject.addProperty("playerName", src.playerName);
            playerObject.addProperty("firstJoinTime", src.firstJoinTime);
            playerObject.addProperty("lastLoginTime", src.lastLoginTime);
            playerObject.addProperty("lastQuitTime", src.lastQuitTime);
            playerObject.addProperty("totalPlayTime", TimeUtil.TimePeriodConverter.fromSeconds(src.totalPlayTime));
            playerObject.addProperty("totalLoginCount", src.totalLoginCount);

            // dailyLogins - 转换 totalPlayTimeInDay 为 HH:mm:ss 格式
            JsonObject dailyLoginsObject = new JsonObject();
            for (Map.Entry<String, DailyLoginData> entry : src.dailyLogins.entrySet()) {
                JsonObject dailyLoginObject = new JsonObject();
                dailyLoginObject.addProperty("loginCount", entry.getValue().loginCount);
                dailyLoginObject.addProperty("totalPlayTimeInDay", TimeUtil.TimePeriodConverter.fromSeconds(entry.getValue().totalPlayTimeInDay));
                dailyLoginObject.addProperty("lastLoginTime", entry.getValue().lastLoginTime);
                dailyLoginsObject.add(entry.getKey(), dailyLoginObject);
            }
            playerObject.add("dailyLogins", dailyLoginsObject);

            // weeklyLogins - 转换 totalPlayTimeInWeek 为 HH:mm:ss 格式
            JsonObject weeklyLoginsObject = new JsonObject();
            for (Map.Entry<String, WeeklyLoginData> entry : src.weeklyLogins.entrySet()) {
                JsonObject weeklyLoginObject = new JsonObject();
                weeklyLoginObject.addProperty("loginCount", entry.getValue().loginCount);
                weeklyLoginObject.addProperty("totalPlayTimeInWeek", TimeUtil.TimePeriodConverter.fromSeconds(entry.getValue().totalPlayTimeInWeek));
                weeklyLoginObject.addProperty("lastLoginTime", entry.getValue().lastLoginTime);
                weeklyLoginsObject.add(entry.getKey(), weeklyLoginObject);
            }
            playerObject.add("weeklyLogins", weeklyLoginsObject);

            playerObject.add("loggedInServerLoginCounts", context.serialize(src.loggedInServerLoginCounts));
            playerObject.add("lastLoginServerTimes", context.serialize(src.lastLoginServerTimes));

            return playerObject;
        }
    }

    // PlayerData 反序列化适配器
    private static class PlayerDataDeserializer implements JsonDeserializer<PlayerData> {
        @Override
        public PlayerData deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject playerObject = json.getAsJsonObject();
            PlayerData playerData = new PlayerData("");

            if (playerObject.has("playerName")) {
                playerData.playerName = playerObject.get("playerName").getAsString();
            }

            if (playerObject.has("firstJoinTime")) {
                playerData.firstJoinTime = playerObject.get("firstJoinTime").getAsString();
            }

            if (playerObject.has("lastLoginTime")) {
                playerData.lastLoginTime = playerObject.get("lastLoginTime").getAsString();
            }

            if (playerObject.has("lastQuitTime")) {
                playerData.lastQuitTime = playerObject.get("lastQuitTime").getAsString();
            }

            if (playerObject.has("totalPlayTime")) {
                String timeStr = playerObject.get("totalPlayTime").getAsString();
                playerData.totalPlayTime = TimeUtil.TimePeriodConverter.toSeconds(timeStr);
            }

            if (playerObject.has("totalLoginCount")) {
                playerData.totalLoginCount = playerObject.get("totalLoginCount").getAsInt();
            }

            if (playerObject.has("dailyLogins")) {
                JsonObject dailyLoginsObject = playerObject.getAsJsonObject("dailyLogins");
                for (Map.Entry<String, JsonElement> entry : dailyLoginsObject.entrySet()) {
                    JsonObject dailyLoginObject = entry.getValue().getAsJsonObject();
                    DailyLoginData dailyLoginData = new DailyLoginData();

                    if (dailyLoginObject.has("loginCount")) {
                        dailyLoginData.loginCount = dailyLoginObject.get("loginCount").getAsInt();
                    }

                    if (dailyLoginObject.has("totalPlayTimeInDay")) {
                        String timeStr = dailyLoginObject.get("totalPlayTimeInDay").getAsString();
                        dailyLoginData.totalPlayTimeInDay = TimeUtil.TimePeriodConverter.toSeconds(timeStr);
                    }

                    if (dailyLoginObject.has("lastLoginTime")) {
                        dailyLoginData.lastLoginTime = dailyLoginObject.get("lastLoginTime").getAsString();
                    }

                    playerData.dailyLogins.put(entry.getKey(), dailyLoginData);
                }
            }

            if (playerObject.has("weeklyLogins")) {
                JsonObject weeklyLoginsObject = playerObject.getAsJsonObject("weeklyLogins");
                for (Map.Entry<String, JsonElement> entry : weeklyLoginsObject.entrySet()) {
                    JsonObject weeklyLoginObject = entry.getValue().getAsJsonObject();
                    WeeklyLoginData weeklyLoginData = new WeeklyLoginData();

                    if (weeklyLoginObject.has("loginCount")) {
                        weeklyLoginData.loginCount = weeklyLoginObject.get("loginCount").getAsInt();
                    }

                    if (weeklyLoginObject.has("totalPlayTimeInWeek")) {
                        String timeStr = weeklyLoginObject.get("totalPlayTimeInWeek").getAsString();
                        weeklyLoginData.totalPlayTimeInWeek = TimeUtil.TimePeriodConverter.toSeconds(timeStr);
                    }

                    if (weeklyLoginObject.has("lastLoginTime")) {
                        weeklyLoginData.lastLoginTime = weeklyLoginObject.get("lastLoginTime").getAsString();
                    }

                    playerData.weeklyLogins.put(entry.getKey(), weeklyLoginData);
                }
            }

            if (playerObject.has("loggedInServerLoginCounts")) {
                playerData.loggedInServerLoginCounts = context.deserialize(
                        playerObject.get("loggedInServerLoginCounts"),
                        playerData.loggedInServerLoginCounts.getClass()
                );
            }

            if (playerObject.has("lastLoginServerTimes")) {
                playerData.lastLoginServerTimes = context.deserialize(
                        playerObject.get("lastLoginServerTimes"),
                        playerData.lastLoginServerTimes.getClass()
                );
            }

            return playerData;
        }
    }

    public static class RootData {
        public ServerData server;
        public Map<UUID, PlayerData> players;

        public RootData() {
            this.server = new ServerData();
            this.players = new ConcurrentHashMap<>();
        }
    }

    public static class ServerData {
        public String bootTime; // 改为仅日期格式
        public String lastReportGenerationDate;
        public Map<String, DailyNewPlayersData> newPlayersToday; // 保持 DailyNewPlayersData 类型
        public Map<String, Integer> totalLoginCountsInDay; // 重命名
        public Map<String, Long> totalPlayTimesInDay; // 保持为Long类型，但需要格式化输出
        public Map<String, Integer> totalServerLoginCounts; // 新增字段
        public Map<String, Map<String, Integer>> dailyServerLoginCounts;

        public ServerData() {
            long currentTime = TimeUtil.SystemTime.getCurrentTimestamp();
            this.bootTime = TimeUtil.DateConverter.fromTimestamp(currentTime); // 改为仅日期格式
            this.lastReportGenerationDate = TimeUtil.DateConverter.fromTimestamp(currentTime);
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
            long currentTime = TimeUtil.SystemTime.getCurrentTimestamp();
            this.firstJoinTime = TimeUtil.DateTimeConverter.fromTimestamp(currentTime);
            this.lastLoginTime = TimeUtil.DateTimeConverter.fromTimestamp(currentTime);
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

        long currentTime = TimeUtil.SystemTime.getCurrentTimestamp();
        String currentDate = TimeUtil.DateConverter.fromTimestamp(currentTime);
        // 更新 DailyNewPlayersData
        DailyNewPlayersData dailyNewPlayers = rootData.server.newPlayersToday.computeIfAbsent(currentDate, k -> new DailyNewPlayersData());
        dailyNewPlayers.players.put(uuid, playerName);
        dailyNewPlayers.totalNewPlayersInDay++; // 更新新玩家总数

        // 更新 DailyLoginData for player's first login
        DailyLoginData dailyLogin = new DailyLoginData();
        dailyLogin.loginCount = 1;
        dailyLogin.lastLoginTime = TimeUtil.DateTimeConverter.fromTimestamp(currentTime);
        newPlayer.dailyLogins.put(currentDate, dailyLogin);

        // 更新 WeeklyLoginData for player's first login
        String currentWeek = getCurrentWeekString();
        WeeklyLoginData weeklyLogin = new WeeklyLoginData();
        weeklyLogin.loginCount = 1;
        weeklyLogin.lastLoginTime = TimeUtil.DateTimeConverter.fromTimestamp(currentTime);
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

        long currentTime = TimeUtil.SystemTime.getCurrentTimestamp();
        playerData.playerName = playerName; // 更新玩家名称
        playerData.lastLoginTime = TimeUtil.DateTimeConverter.fromTimestamp(currentTime);
        playerData.totalLoginCount++; // 更新总登录次数

        String currentDate = TimeUtil.DateConverter.fromTimestamp(currentTime);
        // 更新玩家每日登录数据
        DailyLoginData dailyLogin = playerData.dailyLogins.computeIfAbsent(currentDate, k -> new DailyLoginData());
        dailyLogin.loginCount++;
        dailyLogin.lastLoginTime = playerData.lastLoginTime;

        // 更新玩家每周登录数据
        String currentWeek = getCurrentWeekString();
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

        long currentTime = TimeUtil.SystemTime.getCurrentTimestamp();
        playerData.lastQuitTime = TimeUtil.DateTimeConverter.fromTimestamp(currentTime); // 更新最后退出时间
        playerData.totalPlayTime += sessionDuration.getSeconds(); // 更新总游戏时长

        String currentDate = TimeUtil.DateConverter.fromTimestamp(currentTime);
        // 更新玩家每日游戏时长
        DailyLoginData dailyLogin = playerData.dailyLogins.computeIfAbsent(currentDate, k -> new DailyLoginData());
        dailyLogin.totalPlayTimeInDay += sessionDuration.getSeconds();

        // 更新玩家每周游戏时长
        String currentWeek = getCurrentWeekString();
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
        long currentTime = TimeUtil.SystemTime.getCurrentTimestamp();
        String currentDate = TimeUtil.DateConverter.fromTimestamp(currentTime);

        // 更新玩家在该服务器的登录次数
        playerData.loggedInServerLoginCounts.merge(serverName, 1, Integer::sum);
        playerData.lastLoginServerTimes.put(serverName, TimeUtil.DateTimeConverter.fromTimestamp(currentTime)); // 更新最后一次登录该服务器的时间

        // 更新服务器总登录次数 (所有时间段)
        rootData.server.totalServerLoginCounts.merge(serverName, 1, Integer::sum);

        // 更新服务器每日登录次数
        Map<String, Integer> dailyServerLogins = rootData.server.dailyServerLoginCounts.computeIfAbsent(currentDate, k -> new ConcurrentHashMap<>());
        dailyServerLogins.merge(serverName, 1, Integer::sum);
        savePlayerData();
    }

    private String getCurrentWeekString() {
        java.time.LocalDate now = java.time.LocalDate.now();
        WeekFields weekFields = WeekFields.of(Locale.getDefault());
        int weekOfWeekYear = now.get(weekFields.weekOfWeekBasedYear());
        int weekYear = now.get(weekFields.weekBasedYear());
        return String.format("%d-W%02d", weekYear, weekOfWeekYear);
    }
}
