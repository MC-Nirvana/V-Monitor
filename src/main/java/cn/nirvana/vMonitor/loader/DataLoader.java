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
import com.google.gson.JsonArray;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonParseException;
import org.slf4j.Logger;

import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import java.time.Duration;
import java.time.LocalDateTime;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.ArrayList;

import java.lang.reflect.Type;

public class DataLoader {
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

            // 序列化 server_info 部分
            JsonObject serverInfoObject = new JsonObject();
            serverInfoObject.addProperty("startup_time", src.serverInfo.startupTime);
            serverInfoObject.addProperty("last_report_generation_time", src.serverInfo.lastReportGenerationTime);
            rootObject.add("server_info", serverInfoObject);

            // 序列化 server_tracking 部分
            JsonObject serverTrackingObject = new JsonObject();
            serverTrackingObject.addProperty("historical_peak_online", src.serverTracking.historicalPeakOnline);

            // daily_peak_online
            JsonObject dailyPeakOnlineObject = new JsonObject();
            for (Map.Entry<String, DailyPeakOnlineData> entry : src.serverTracking.dailyPeakOnline.entrySet()) {
                JsonObject datePeakObject = new JsonObject();
                datePeakObject.addProperty("overall", entry.getValue().overall);

                // sub_server 应该是数组
                JsonArray subServerArray = new JsonArray();
                for (SubServerPeakData subServer : entry.getValue().subServer) {
                    JsonObject subServerObject = new JsonObject();
                    subServerObject.addProperty("server_name", subServer.serverName);
                    subServerObject.addProperty("peak_online", subServer.peakOnline);
                    subServerArray.add(subServerObject);
                }
                datePeakObject.add("sub_server", subServerArray);
                dailyPeakOnlineObject.add(entry.getKey(), datePeakObject);
            }
            serverTrackingObject.add("daily_peak_online", dailyPeakOnlineObject);

            // daily_new_players
            JsonObject dailyNewPlayersObject = new JsonObject();
            for (Map.Entry<String, DailyNewPlayersData> entry : src.serverTracking.dailyNewPlayers.entrySet()) {
                JsonObject dateNewPlayersObject = new JsonObject();
                dateNewPlayersObject.addProperty("overall", entry.getValue().overall);

                // players 应该是数组
                JsonArray playersArray = new JsonArray();
                for (NewPlayerData player : entry.getValue().players) {
                    JsonObject playerObject = new JsonObject();
                    playerObject.addProperty("uuid", player.uuid.toString());
                    playerObject.addProperty("time", player.time);
                    playersArray.add(playerObject);
                }
                dateNewPlayersObject.add("players", playersArray);
                dailyNewPlayersObject.add(entry.getKey(), dateNewPlayersObject);
            }
            serverTrackingObject.add("daily_new_players", dailyNewPlayersObject);

            rootObject.add("server_tracking", serverTrackingObject);

            // 序列化 player_data 部分 (应该是一个数组而不是对象)
            JsonArray playersArray = new JsonArray();
            for (PlayerData player : src.playerData) {
                JsonObject playerObject = new JsonObject();
                playerObject.addProperty("id", player.id);
                playerObject.addProperty("uuid", player.uuid.toString());
                playerObject.addProperty("username", player.username);
                playerObject.addProperty("first_join_time", player.firstJoinTime);
                playerObject.addProperty("last_login_time", player.lastLoginTime);
                playerObject.addProperty("play_time", TimeUtil.TimePeriodConverter.fromSeconds(player.playTime));

                // daily_server_paths
                JsonObject dailyServerPathsObject = new JsonObject();
                for (Map.Entry<String, List<ServerPathData>> pathEntry : player.dailyServerPaths.entrySet()) {
                    // 应该是数组
                    JsonArray pathsArray = new JsonArray();
                    for (ServerPathData path : pathEntry.getValue()) {
                        JsonObject pathObject = new JsonObject();
                        pathObject.addProperty("time", path.time);
                        pathObject.addProperty("from", path.from);
                        pathObject.addProperty("to", path.to);
                        pathsArray.add(pathObject);
                    }
                    dailyServerPathsObject.add(pathEntry.getKey(), pathsArray);
                }
                playerObject.add("daily_server_paths", dailyServerPathsObject);

                playersArray.add(playerObject);
            }
            rootObject.add("player_data", playersArray);

            return rootObject;
        }
    }

    // RootData 反序列化适配器
    private static class RootDataDeserializer implements JsonDeserializer<RootData> {
        @Override
        public RootData deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            RootData rootData = new RootData();
            JsonObject rootObject = json.getAsJsonObject();

            // 解析 server_info
            if (rootObject.has("server_info")) {
                JsonObject serverInfoObject = rootObject.getAsJsonObject("server_info");
                rootData.serverInfo = new ServerInfoData();
                if (serverInfoObject.has("startup_time")) {
                    rootData.serverInfo.startupTime = serverInfoObject.get("startup_time").getAsString();
                }
                if (serverInfoObject.has("last_report_generation_time")) {
                    rootData.serverInfo.lastReportGenerationTime = serverInfoObject.get("last_report_generation_time").getAsString();
                }
            }

            // 解析 server_tracking
            if (rootObject.has("server_tracking")) {
                JsonObject serverTrackingObject = rootObject.getAsJsonObject("server_tracking");
                rootData.serverTracking = new ServerTrackingData();

                if (serverTrackingObject.has("historical_peak_online")) {
                    rootData.serverTracking.historicalPeakOnline = serverTrackingObject.get("historical_peak_online").getAsInt();
                }

                // 解析 daily_peak_online
                if (serverTrackingObject.has("daily_peak_online")) {
                    JsonObject dailyPeakOnlineObject = serverTrackingObject.getAsJsonObject("daily_peak_online");
                    rootData.serverTracking.dailyPeakOnline = new ConcurrentHashMap<>();
                    for (Map.Entry<String, JsonElement> entry : dailyPeakOnlineObject.entrySet()) {
                        JsonObject dateObject = entry.getValue().getAsJsonObject();
                        DailyPeakOnlineData dailyData = new DailyPeakOnlineData();
                        if (dateObject.has("overall")) {
                            dailyData.overall = dateObject.get("overall").getAsInt();
                        }
                        if (dateObject.has("sub_server")) {
                            // sub_server 是数组
                            JsonArray subServersArray = dateObject.getAsJsonArray("sub_server");
                            dailyData.subServer = new ArrayList<>();
                            for (JsonElement subServerElement : subServersArray) {
                                JsonObject subServerObject = subServerElement.getAsJsonObject();
                                SubServerPeakData subServerData = new SubServerPeakData();
                                if (subServerObject.has("server_name")) {
                                    subServerData.serverName = subServerObject.get("server_name").getAsString();
                                }
                                if (subServerObject.has("peak_online")) {
                                    subServerData.peakOnline = subServerObject.get("peak_online").getAsInt();
                                }
                                dailyData.subServer.add(subServerData);
                            }
                        }
                        rootData.serverTracking.dailyPeakOnline.put(entry.getKey(), dailyData);
                    }
                }

                // 解析 daily_new_players
                if (serverTrackingObject.has("daily_new_players")) {
                    JsonObject dailyNewPlayersObject = serverTrackingObject.getAsJsonObject("daily_new_players");
                    rootData.serverTracking.dailyNewPlayers = new ConcurrentHashMap<>();
                    for (Map.Entry<String, JsonElement> entry : dailyNewPlayersObject.entrySet()) {
                        JsonObject dateObject = entry.getValue().getAsJsonObject();
                        DailyNewPlayersData dailyData = new DailyNewPlayersData();
                        if (dateObject.has("overall")) {
                            dailyData.overall = dateObject.get("overall").getAsInt();
                        }
                        if (dateObject.has("players")) {
                            // players 是数组
                            JsonArray playersArray = dateObject.getAsJsonArray("players");
                            dailyData.players = new ArrayList<>();
                            for (JsonElement playerElement : playersArray) {
                                JsonObject playerObject = playerElement.getAsJsonObject();
                                NewPlayerData playerData = new NewPlayerData();
                                if (playerObject.has("uuid")) {
                                    playerData.uuid = UUID.fromString(playerObject.get("uuid").getAsString());
                                }
                                if (playerObject.has("time")) {
                                    playerData.time = playerObject.get("time").getAsString();
                                }
                                dailyData.players.add(playerData);
                            }
                        }
                        rootData.serverTracking.dailyNewPlayers.put(entry.getKey(), dailyData);
                    }
                }
            }

            // 解析 player_data
            if (rootObject.has("player_data")) {
                // player_data 是数组
                JsonArray playersArray = rootObject.getAsJsonArray("player_data");
                rootData.playerData = new ArrayList<>();
                for (JsonElement playerElement : playersArray) {
                    JsonObject playerObject = playerElement.getAsJsonObject();
                    PlayerData playerData = new PlayerData();

                    if (playerObject.has("id")) {
                        playerData.id = playerObject.get("id").getAsInt();
                    }
                    if (playerObject.has("uuid")) {
                        playerData.uuid = UUID.fromString(playerObject.get("uuid").getAsString());
                    }
                    if (playerObject.has("username")) {
                        playerData.username = playerObject.get("username").getAsString();
                    }
                    if (playerObject.has("first_join_time")) {
                        playerData.firstJoinTime = playerObject.get("first_join_time").getAsString();
                    }
                    if (playerObject.has("last_login_time")) {
                        playerData.lastLoginTime = playerObject.get("last_login_time").getAsString();
                    }
                    if (playerObject.has("play_time")) {
                        String timeStr = playerObject.get("play_time").getAsString();
                        playerData.playTime = TimeUtil.TimePeriodConverter.toSeconds(timeStr);
                    }

                    // 解析 daily_server_paths
                    if (playerObject.has("daily_server_paths")) {
                        JsonObject pathsObject = playerObject.getAsJsonObject("daily_server_paths");
                        playerData.dailyServerPaths = new ConcurrentHashMap<>();
                        for (Map.Entry<String, JsonElement> pathEntry : pathsObject.entrySet()) {
                            // 路径是数组
                            JsonArray datePathsArray = pathEntry.getValue().getAsJsonArray();
                            List<ServerPathData> pathList = new ArrayList<>();
                            for (JsonElement pathElement : datePathsArray) {
                                JsonObject pathObject = pathElement.getAsJsonObject();
                                ServerPathData pathData = new ServerPathData();
                                if (pathObject.has("time")) {
                                    pathData.time = pathObject.get("time").getAsString();
                                }
                                if (pathObject.has("from")) {
                                    pathData.from = pathObject.get("from").getAsString();
                                }
                                if (pathObject.has("to")) {
                                    pathData.to = pathObject.get("to").getAsString();
                                }
                                pathList.add(pathData);
                            }
                            playerData.dailyServerPaths.put(pathEntry.getKey(), pathList);
                        }
                    }

                    rootData.playerData.add(playerData);
                }
            }

            return rootData;
        }
    }

    public static class RootData {
        public ServerInfoData serverInfo;
        public ServerTrackingData serverTracking;
        public List<PlayerData> playerData;

        public RootData() {
            this.serverInfo = new ServerInfoData();
            this.serverTracking = new ServerTrackingData();
            this.playerData = new ArrayList<>();
        }
    }

    public static class ServerInfoData {
        public String startupTime;
        public String lastReportGenerationTime;

        public ServerInfoData() {
            long currentTime = TimeUtil.SystemTime.getCurrentTimestamp();
            this.startupTime = TimeUtil.DateConverter.fromTimestamp(currentTime);
            this.lastReportGenerationTime = TimeUtil.DateConverter.fromTimestamp(currentTime);
        }
    }

    public static class ServerTrackingData {
        public int historicalPeakOnline;
        public Map<String, DailyPeakOnlineData> dailyPeakOnline;
        public Map<String, DailyNewPlayersData> dailyNewPlayers;

        public ServerTrackingData() {
            this.historicalPeakOnline = 0;
            this.dailyPeakOnline = new ConcurrentHashMap<>();
            this.dailyNewPlayers = new ConcurrentHashMap<>();
        }
    }

    public static class DailyPeakOnlineData {
        public int overall;
        public List<SubServerPeakData> subServer;

        public DailyPeakOnlineData() {
            this.overall = 0;
            this.subServer = new ArrayList<>();
        }
    }

    public static class SubServerPeakData {
        public String serverName;
        public int peakOnline;

        public SubServerPeakData() {
            this.serverName = "";
            this.peakOnline = 0;
        }
    }

    public static class DailyNewPlayersData {
        public int overall;
        public List<NewPlayerData> players;

        public DailyNewPlayersData() {
            this.overall = 0;
            this.players = new ArrayList<>();
        }
    }

    public static class NewPlayerData {
        public UUID uuid;
        public String time;

        public NewPlayerData() {
            this.uuid = UUID.randomUUID();
            this.time = "";
        }
    }

    public static class PlayerData {
        public int id;
        public UUID uuid;
        public String username;
        public String firstJoinTime;
        public String lastLoginTime;
        public long playTime;
        public Map<String, List<ServerPathData>> dailyServerPaths;

        public PlayerData() {
            this.id = 0;
            this.uuid = UUID.randomUUID();
            this.username = "";
            long currentTime = TimeUtil.SystemTime.getCurrentTimestamp();
            this.firstJoinTime = TimeUtil.DateTimeConverter.fromTimestamp(currentTime);
            this.lastLoginTime = TimeUtil.DateTimeConverter.fromTimestamp(currentTime);
            this.playTime = 0;
            this.dailyServerPaths = new ConcurrentHashMap<>();
        }
    }

    public static class ServerPathData {
        public String time;
        public String from;
        public String to;

        public ServerPathData() {
            this.time = "";
            this.from = "";
            this.to = "";
        }
    }

    public DataLoader(Logger logger, Path dataDirectory) {
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
        for (PlayerData player : rootData.playerData) {
            if (player.uuid.equals(uuid)) {
                return player;
            }
        }
        return null;
    }

    public void createPlayerData(UUID uuid, String playerName) {
        PlayerData newPlayer = new PlayerData();
        newPlayer.uuid = uuid;
        newPlayer.username = playerName;
        newPlayer.id = rootData.playerData.size() + 1;

        rootData.playerData.add(newPlayer);

        long currentTime = TimeUtil.SystemTime.getCurrentTimestamp();
        String currentDate = TimeUtil.DateConverter.fromTimestamp(currentTime);

        // 更新 daily_new_players
        DailyNewPlayersData dailyNewPlayers = rootData.serverTracking.dailyNewPlayers.computeIfAbsent(currentDate, k -> new DailyNewPlayersData());
        NewPlayerData newPlayerData = new NewPlayerData();
        newPlayerData.uuid = uuid;
        // 使用 TimeUtil 获取本地时间点
        newPlayerData.time = TimeUtil.TimePeriodConverter.fromTimestamp(currentTime);
        dailyNewPlayers.players.add(newPlayerData);
        dailyNewPlayers.overall = dailyNewPlayers.players.size();

        savePlayerData();
    }


    public void updatePlayerOnLogin(UUID uuid, String playerName) {
        PlayerData playerData = getPlayerData(uuid);
        if (playerData == null) {
            logger.warn("Attempted to update login for unknown player: {}. Creating new data.", uuid);
            createPlayerData(uuid, playerName);
            playerData = getPlayerData(uuid);
        }

        long currentTime = TimeUtil.SystemTime.getCurrentTimestamp();
        playerData.username = playerName;
        playerData.lastLoginTime = TimeUtil.DateTimeConverter.fromTimestamp(currentTime);

        savePlayerData();
    }

    public void updatePlayerOnQuit(UUID uuid, String playerName, String disconnectedFromServer, Duration sessionDuration) {
        PlayerData playerData = getPlayerData(uuid);
        if (playerData == null) {
            logger.warn("Attempted to update quit for unknown player: {}. Skipping.", uuid);
            return;
        }

        playerData.playTime += sessionDuration.getSeconds();

        savePlayerData();
    }

    public void updatePlayerServerLogin(UUID uuid, String serverName) {
        PlayerData playerData = getPlayerData(uuid);
        if (playerData == null) {
            logger.warn("Attempted to update server login for unknown player: {}. Please ensure player data is created.", uuid);
            return;
        }

        long currentTime = TimeUtil.SystemTime.getCurrentTimestamp();
        String currentDate = TimeUtil.DateConverter.fromTimestamp(currentTime);
        // 使用新的方法获取本地时间
        String timeStr = TimeUtil.TimePeriodConverter.fromTimestamp(currentTime);

        // 更新 daily_server_paths
        List<ServerPathData> serverPaths = playerData.dailyServerPaths.computeIfAbsent(currentDate, k -> new ArrayList<>());
        if (!serverPaths.isEmpty()) {
            ServerPathData lastPath = serverPaths.get(serverPaths.size() - 1);
            // 防止重复记录相同路径
            if (!lastPath.to.equals(serverName)) {
                ServerPathData newPath = new ServerPathData();
                newPath.time = timeStr;
                newPath.from = lastPath.to;
                newPath.to = serverName;
                serverPaths.add(newPath);
            }
        } else {
            ServerPathData newPath = new ServerPathData();
            newPath.time = timeStr;
            newPath.from = "unknown";
            newPath.to = serverName;
            serverPaths.add(newPath);
        }

        savePlayerData();
    }

    public void updateHistoricalPeakOnline(int currentOnlineCount) {
        // 更新当日峰值
        String currentDate = TimeUtil.DateConverter.fromTimestamp(TimeUtil.SystemTime.getCurrentTimestamp());
        DailyPeakOnlineData dailyPeak = rootData.serverTracking.dailyPeakOnline.computeIfAbsent(currentDate, k -> new DailyPeakOnlineData());

        if (currentOnlineCount > dailyPeak.overall) {
            dailyPeak.overall = currentOnlineCount;

            // 更新历史峰值
            if (currentOnlineCount > rootData.serverTracking.historicalPeakOnline) {
                rootData.serverTracking.historicalPeakOnline = currentOnlineCount;
            }

            savePlayerData();
        }
    }

    public void updateSubServerPeakOnline(String serverName, int currentOnlineCount) {
        String currentDate = TimeUtil.DateConverter.fromTimestamp(TimeUtil.SystemTime.getCurrentTimestamp());
        DailyPeakOnlineData dailyPeak = rootData.serverTracking.dailyPeakOnline.computeIfAbsent(currentDate, k -> new DailyPeakOnlineData());

        // 查找是否已存在该服务器记录
        SubServerPeakData subServerData = null;
        for (SubServerPeakData subServer : dailyPeak.subServer) {
            if (subServer.serverName.equals(serverName)) {
                subServerData = subServer;
                break;
            }
        }

        // 如果不存在，创建新记录
        if (subServerData == null) {
            subServerData = new SubServerPeakData();
            subServerData.serverName = serverName;
            dailyPeak.subServer.add(subServerData);
        }

        // 更新峰值
        if (currentOnlineCount > subServerData.peakOnline) {
            subServerData.peakOnline = currentOnlineCount;
            savePlayerData();
        }
    }
}
