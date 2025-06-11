package cn.nirvana.vMonitor.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import org.slf4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;

import java.util.Date;
import java.util.Locale;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import java.text.SimpleDateFormat;

public class PlayerDataLoader {
    private final Logger logger;
    private final Path dataDirectory;
    private final String playerDataFileName = "playerdata.json";

    private RootData rootData;

    private final DateTimeFormatter bootTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final SimpleDateFormat dateFormatWithTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private final DateTimeFormatter weekFormatter = DateTimeFormatter.ofPattern("yyyy-'W'ww", Locale.ENGLISH);

    public static class RootData {
        public ServerData server;
        public Map<UUID, PlayerData> players;

        public RootData() {
            this.players = new ConcurrentHashMap<>();
        }
    }

    public static class ServerData {
        public String bootTime;
    }

    public static class PlayerData {
        public String playerName;
        public String firstJoinTime;
        public String lastLoginTime;
        public String lastQuitTime;
        public long totalPlayTime;
        public Map<String, DailyLoginData> dailyLogins;
        public Map<String, WeeklyLoginData> weeklyLogins;
        public int dailyLoginCount;
        public int weeklyLoginCount;
        public int totalLoginCount;

        public PlayerData() {
            this.dailyLogins = new ConcurrentHashMap<>();
            this.weeklyLogins = new ConcurrentHashMap<>();
            this.totalPlayTime = 0;
            this.dailyLoginCount = 0;
            this.weeklyLoginCount = 0;
            this.totalLoginCount = 0;
            this.lastQuitTime = "";
        }
    }

    public static class DailyLoginData {
        public long playDurationSeconds;
        public int loginCount;

        public DailyLoginData() {
            this.playDurationSeconds = 0;
            this.loginCount = 0;
        }
    }

    public static class WeeklyLoginData {
        public long totalPlayTimeSecondsInWeek;
        public int loginCount;

        public WeeklyLoginData() {
            this.totalPlayTimeSecondsInWeek = 0;
            this.loginCount = 0;
        }
    }

    public PlayerDataLoader(Logger logger, Path dataDirectory) {
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.rootData = new RootData();
        loadPlayerData();
        initializeServerBootTime();
    }

    public void loadPlayerData() {
        File dataFile = dataDirectory.resolve(playerDataFileName).toFile();
        if (dataFile.exists()) {
            try (FileReader reader = new FileReader(dataFile, StandardCharsets.UTF_8)) {
                Gson gson = new Gson();
                rootData = gson.fromJson(reader, new TypeToken<RootData>() {}.getType());
                if (rootData == null) {
                    rootData = new RootData();
                    logger.warn("Player data file was empty or malformed, initializing new data.");
                } else {
                    if (rootData.players == null) {
                        rootData.players = new ConcurrentHashMap<>();
                    }
                    rootData.players.values().forEach(playerData -> {
                        if (playerData.dailyLogins == null) playerData.dailyLogins = new ConcurrentHashMap<>();
                        if (playerData.weeklyLogins == null) playerData.weeklyLogins = new ConcurrentHashMap<>();
                        if (playerData.lastQuitTime == null) playerData.lastQuitTime = "";
                    });
                }
                logger.info("Player data loaded.");
            } catch (IOException e) {
                logger.error("Failed to load player data: " + e.getMessage());
                rootData = new RootData();
            }
        } else {
            logger.info("Player data file not found, creating new one.");
            rootData = new RootData();
            savePlayerData();
        }
    }

    public void savePlayerData() {
        File dataFile = dataDirectory.resolve(playerDataFileName).toFile();
        try {
            try (FileWriter writer = new FileWriter(dataFile, StandardCharsets.UTF_8)) {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                gson.toJson(rootData, writer);
                logger.info("Player data saved.");
            }
        } catch (IOException e) {
            logger.error("Failed to save player data: " + e.getMessage());
        }
    }

    private String getFormattedCurrentDate() {
        return LocalDate.now().format(bootTimeFormatter);
    }

    private String getFormattedCurrentWeek() {
        return LocalDate.now().format(weekFormatter);
    }

    public void updatePlayerJoinTime(UUID uuid, String playerName) {
        rootData.players.computeIfAbsent(uuid, k -> {
            PlayerData newPlayerData = new PlayerData();
            newPlayerData.playerName = playerName;
            String currentTime = dateFormatWithTime.format(new Date());
            newPlayerData.firstJoinTime = currentTime;
            newPlayerData.lastLoginTime = currentTime;
            newPlayerData.lastQuitTime = currentTime;
            newPlayerData.totalLoginCount = 1;
            newPlayerData.dailyLoginCount = 1;
            newPlayerData.weeklyLoginCount = 1;
            String currentDate = getFormattedCurrentDate();
            newPlayerData.dailyLogins.put(currentDate, new DailyLoginData());
            newPlayerData.dailyLogins.get(currentDate).loginCount = 1;
            String currentWeek = getFormattedCurrentWeek();
            newPlayerData.weeklyLogins.put(currentWeek, new WeeklyLoginData());
            newPlayerData.weeklyLogins.get(currentWeek).loginCount = 1;
            return newPlayerData;
        });
        rootData.players.get(uuid).playerName = playerName;
        savePlayerData();
    }

    public void updatePlayerLastLoginTime(UUID uuid) {
        PlayerData playerData = rootData.players.get(uuid);
        if (playerData != null) {
            playerData.lastLoginTime = dateFormatWithTime.format(new Date());
            playerData.totalLoginCount++;
            String currentDate = getFormattedCurrentDate();
            if (!playerData.dailyLogins.containsKey(currentDate)) {
                playerData.dailyLoginCount = 0;
                playerData.dailyLogins.clear();
            }
            playerData.dailyLoginCount++;
            playerData.dailyLogins.computeIfAbsent(currentDate, k -> new DailyLoginData()).loginCount++;
            String currentWeek = getFormattedCurrentWeek();
            if (!playerData.weeklyLogins.containsKey(currentWeek)) {
                playerData.weeklyLoginCount = 0;
                playerData.weeklyLogins.clear();
            }
            playerData.weeklyLoginCount++;
            playerData.weeklyLogins.computeIfAbsent(currentWeek, k -> new WeeklyLoginData()).loginCount++;
            savePlayerData();
        }
    }

    public void updatePlayerLastQuitTime(UUID uuid) {
        PlayerData playerData = rootData.players.get(uuid);
        if (playerData != null) {
            long loginTimeMillis = 0;
            long quitTimeMillis = System.currentTimeMillis();
            try {
                if (playerData.lastLoginTime != null && !playerData.lastLoginTime.isEmpty()) {
                    loginTimeMillis = dateFormatWithTime.parse(playerData.lastLoginTime).getTime();
                } else {
                    loginTimeMillis = quitTimeMillis;
                }
            } catch (java.text.ParseException e) {
                logger.error("Error parsing lastLoginTime for player " + uuid + ": " + e.getMessage());
                loginTimeMillis = quitTimeMillis;
            }
            playerData.lastQuitTime = dateFormatWithTime.format(new Date());
            long playDurationSeconds = (quitTimeMillis - loginTimeMillis) / 1000;
            if (playDurationSeconds < 0) playDurationSeconds = 0;
            playerData.totalPlayTime += playDurationSeconds;
            String currentDate = getFormattedCurrentDate();
            DailyLoginData dailyData = playerData.dailyLogins.computeIfAbsent(currentDate, k -> new DailyLoginData());
            dailyData.playDurationSeconds += playDurationSeconds;
            String currentWeek = getFormattedCurrentWeek();
            WeeklyLoginData weeklyData = playerData.weeklyLogins.computeIfAbsent(currentWeek, k -> new WeeklyLoginData());
            weeklyData.totalPlayTimeSecondsInWeek += playDurationSeconds;
            savePlayerData();
        }
    }

    public boolean hasPlayerJoinedBefore(UUID uuid) {
        return rootData.players.containsKey(uuid);
    }

    public String getServerBootTime() {
        if (rootData != null && rootData.server != null) {
            return rootData.server.bootTime;
        }
        return null;
    }

    private void setServerBootTime(String bootTime) {
        if (rootData != null) {
            if (rootData.server == null) {
                rootData.server = new ServerData();
            }
            rootData.server.bootTime = bootTime;
        }
    }

    public void initializeServerBootTime() {
        if (rootData.server == null) {
            rootData.server = new ServerData();
        }
        String currentBootTime = getServerBootTime();
        if (currentBootTime == null || currentBootTime.isEmpty()) {
            String formattedBootTime = LocalDate.now().format(bootTimeFormatter);
            setServerBootTime(formattedBootTime);
            logger.info("Server boot time recorded: " + formattedBootTime);
        } else {
            logger.info("Server last boot time was: " + currentBootTime);
        }
        savePlayerData();
    }

    public String getPlayerName(UUID uuid) {
        PlayerData data = rootData.players.get(uuid);
        return data != null ? data.playerName : null;
    }

    public long getPlayerTotalPlayTime(UUID uuid) {
        PlayerData data = rootData.players.get(uuid);
        return data != null ? data.totalPlayTime : 0;
    }

    public int getPlayerDailyLoginCount(UUID uuid) {
        PlayerData data = rootData.players.get(uuid);
        return data != null ? data.dailyLoginCount : 0;
    }

    public int getPlayerWeeklyLoginCount(UUID uuid) {
        PlayerData data = rootData.players.get(uuid);
        return data != null ? data.weeklyLoginCount : 0;
    }

    public int getPlayerTotalLoginCount(UUID uuid) {
        PlayerData data = rootData.players.get(uuid);
        return data != null ? data.totalLoginCount : 0;
    }

    public DailyLoginData getPlayerDailyLoginData(UUID uuid, String date) {
        PlayerData data = rootData.players.get(uuid);
        return data != null ? data.dailyLogins.get(date) : null;
    }

    public WeeklyLoginData getPlayerWeeklyLoginData(UUID uuid, String week) {
        PlayerData data = rootData.players.get(uuid);
        return data != null ? data.weeklyLogins.get(week) : null;
    }

    public Map<UUID, PlayerData> getAllPlayersData() {
        return rootData.players;
    }
}