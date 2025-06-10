package cn.nirvana.vMonitor.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import org.slf4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.InputStream;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;

import java.util.HashMap;
import java.util.UUID;
import java.util.Map;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

import java.text.SimpleDateFormat;

public class PlayerDataLoader {
    private final Logger logger;
    private final Path dataDirectory;
    private final String playerDataFileName = "playerdata.json";
    private RootData rootData;

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
        public ServerData() {
            this.bootTime = null;
        }
    }

    public static class PlayerData {
        public String playerName;
        public String firstJoinTime;
        public String lastLoginTime;
        public String lastLogoutTime;
        public Map<String, DailyLoginData> dailyLogins;
        public Map<String, WeeklyLoginData> weeklyLogins;

        public long totalPlayTime;

        public PlayerData() {
            this.dailyLogins = new ConcurrentHashMap<>();
            this.weeklyLogins = new ConcurrentHashMap<>();
            this.totalPlayTime = 0;
        }

        public PlayerData(String playerName, String firstJoinTime) {
            this();
            this.playerName = playerName;
            this.firstJoinTime = firstJoinTime;
        }
    }

    public static class DailyLoginData {
        public int loginCount;
        public long playDurationSeconds;

        public DailyLoginData() {
            this.loginCount = 0;
            this.playDurationSeconds = 0;
        }
    }

    public static class WeeklyLoginData {
        public int loginDaysInWeek;
        public long totalPlayTimeSecondsInWeek;

        public WeeklyLoginData() {
            this.loginDaysInWeek = 0;
            this.totalPlayTimeSecondsInWeek = 0;
        }
    }

    public PlayerDataLoader(Logger logger, Path dataDirectory) {
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.rootData = new RootData();
    }

    public void loadPlayerData() {
        File playerDataFile = dataDirectory.resolve(playerDataFileName).toFile();
        if (!playerDataFile.exists()) {
            this.rootData = new RootData();
            savePlayerData();
            logger.info("Player data file does not exist, created a new one.");
            return;
        }

        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(playerDataFile), StandardCharsets.UTF_8)) {
            Gson gson = new Gson();
            TypeToken<RootData> typeToken = new TypeToken<RootData>() {};
            this.rootData = gson.fromJson(reader, typeToken.getType());
            if (this.rootData == null) {
                this.rootData = new RootData();
            }
            if (this.rootData.server == null) {
                this.rootData.server = new ServerData();
            }
            if (this.rootData.players == null) {
                this.rootData.players = new ConcurrentHashMap<>();
            }
            this.rootData.players.values().forEach(playerData -> {
                if (playerData.dailyLogins == null) playerData.dailyLogins = new ConcurrentHashMap<>();
                if (playerData.weeklyLogins == null) playerData.weeklyLogins = new ConcurrentHashMap<>();
            });
            logger.info("Player data loaded successfully.");
        } catch (IOException e) {
            logger.error("Could not load player data file: " + e.getMessage());
            this.rootData = new RootData();
        }
    }

    public void savePlayerData() {
        File playerDataFile = dataDirectory.resolve(playerDataFileName).toFile();
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(playerDataFile), StandardCharsets.UTF_8)) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            gson.toJson(this.rootData, writer);
            logger.debug("Successfully saved player data file.");
        } catch (IOException e) {
            logger.error("Could not save player data file: " + e.getMessage());
        }
    }

    public void addPlayerFirstJoinInfo(UUID uuid, String playerName) {
        if (!rootData.players.containsKey(uuid)) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS");
            String formattedTime = sdf.format(new Date(System.currentTimeMillis()));
            PlayerData newPlayerData = new PlayerData(playerName, formattedTime);
            newPlayerData.lastLoginTime = formattedTime;
            rootData.players.put(uuid, newPlayerData);
            savePlayerData();
        } else {
            PlayerData existingInfo = rootData.players.get(uuid);
            if (!existingInfo.playerName.equals(playerName)) {
                existingInfo.playerName = playerName;
                savePlayerData();
            }
        }
    }

    public boolean hasPlayerJoinedBefore(UUID uuid) {
        return rootData.players.containsKey(uuid);
    }

    public PlayerData getPlayerData(UUID uuid) {
        return rootData.players.get(uuid);
    }

    public void updatePlayerLoginData(UUID uuid, String playerName) {
        PlayerData playerData = rootData.players.computeIfAbsent(uuid, k -> {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS");
            String formattedTime = sdf.format(new Date(System.currentTimeMillis()));
            return new PlayerData(playerName, formattedTime);
        });
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS");
        String currentFormattedTime = sdf.format(new Date(System.currentTimeMillis()));
        playerData.lastLoginTime = currentFormattedTime;
        playerData.playerName = playerName;
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        DailyLoginData dailyData = playerData.dailyLogins.computeIfAbsent(today, k -> new DailyLoginData());
        dailyData.loginCount++;
        String currentWeek = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-'W'ww"));
        WeeklyLoginData weeklyData = playerData.weeklyLogins.computeIfAbsent(currentWeek, k -> new WeeklyLoginData());
        if (dailyData.loginCount == 1) {
            weeklyData.loginDaysInWeek++;
        }
        savePlayerData();
    }

    public void updatePlayerLogoutData(UUID uuid) {
        PlayerData playerData = rootData.players.get(uuid);
        if (playerData != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS");
            String currentFormattedTime = sdf.format(new Date(System.currentTimeMillis()));
            playerData.lastLogoutTime = currentFormattedTime;
            if (playerData.lastLoginTime != null && !playerData.lastLoginTime.isEmpty()) {
                try {
                    Date loginDate = sdf.parse(playerData.lastLoginTime);
                    long durationSeconds = (System.currentTimeMillis() - loginDate.getTime()) / 1000;
                    String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
                    DailyLoginData dailyData = playerData.dailyLogins.computeIfAbsent(today, k -> new DailyLoginData());
                    dailyData.playDurationSeconds += durationSeconds;
                    String currentWeek = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-'W'ww"));
                    WeeklyLoginData weeklyData = playerData.weeklyLogins.computeIfAbsent(currentWeek, k -> new WeeklyLoginData());
                    weeklyData.totalPlayTimeSecondsInWeek += durationSeconds;
                    playerData.totalPlayTime += durationSeconds;
                } catch (java.text.ParseException e) {
                    logger.error("Error parsing lastLoginTime for player " + uuid + ": " + e.getMessage());
                }
            }
            savePlayerData();
        }
    }

    public String getServerBootTime() {
        if (rootData != null && rootData.server != null) {
            return rootData.server.bootTime;
        }
        return null;
    }

    public void setServerBootTime(String bootTime) {
        if (rootData != null) {
            if (rootData.server == null) {
                rootData.server = new ServerData();
            }
            rootData.server.bootTime = bootTime;
            savePlayerData();
        }
    }
}