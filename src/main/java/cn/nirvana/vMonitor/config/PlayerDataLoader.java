package cn.nirvana.vMonitor.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import org.slf4j.Logger;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import java.util.Date;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

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
            this.players = new ConcurrentHashMap<>();
        }
    }

    public static class ServerData {
        public String bootTime;
    }

    public static class PlayerData {
        public String firstJoinTime;
        public String lastLoginTime;
        public String lastQuitTime;

        public long totalPlayTime;

        public Map<String, DailyLoginData> dailyLogins;
        public Map<String, WeeklyLoginData> weeklyLogins;

        public AtomicInteger dailyLoginCount;
        public AtomicInteger weeklyLoginCount;
        public AtomicInteger totalLoginCount;

        public PlayerData() {
            this.dailyLogins = new ConcurrentHashMap<>();
            this.weeklyLogins = new ConcurrentHashMap<>();
            this.dailyLoginCount = new AtomicInteger(0);
            this.weeklyLoginCount = new AtomicInteger(0);
            this.totalLoginCount = new AtomicInteger(0);
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

    private final SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private final DateTimeFormatter bootTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public PlayerDataLoader(Logger logger, Path dataDirectory) {
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.rootData = new RootData();
    }
    public void loadPlayerData() {
        Path playerdataFile = dataDirectory.resolve(playerDataFileName);
        if (!Files.exists(playerdataFile)) {
            logger.info("playerdata.json not found, creating new one.");
            rootData = new RootData();
            savePlayerData();
            return;
        }
        try (FileReader reader = new FileReader(playerdataFile.toFile(), StandardCharsets.UTF_8)) {
            rootData = gson.fromJson(reader, TypeToken.get(RootData.class));
            if (rootData == null) {
                logger.warn("playerdata.json is empty or invalid, creating new one.");
                rootData = new RootData();
            } else {
                if (rootData.players == null) {
                    rootData.players = new ConcurrentHashMap<>();
                }
                rootData.players.forEach((uuid, playerData) -> {
                    if (playerData.dailyLogins == null) {
                        playerData.dailyLogins = new ConcurrentHashMap<>();
                    }
                    if (playerData.weeklyLogins == null) {
                        playerData.weeklyLogins = new ConcurrentHashMap<>();
                    }
                    if (playerData.dailyLoginCount == null) {
                        playerData.dailyLoginCount = new AtomicInteger(0);
                    }
                    if (playerData.weeklyLoginCount == null) {
                        playerData.weeklyLoginCount = new AtomicInteger(0);
                    }
                    if (playerData.totalLoginCount == null) {
                        playerData.totalLoginCount = new AtomicInteger(0);
                    }
                    if (playerData.firstJoinTime == null && playerData.lastLoginTime != null) {
                        playerData.firstJoinTime = playerData.lastLoginTime;
                    }
                    if (playerData.lastQuitTime == null && playerData.lastLoginTime != null) {
                        playerData.lastQuitTime = playerData.lastLoginTime;
                    }
                });
            }
        } catch (IOException e) {
            logger.error("Failed to load playerdata.json: " + e.getMessage());
            rootData = new RootData();
        }
    }

    public void savePlayerData() {
        Path playerdataFile = dataDirectory.resolve(playerDataFileName);
        try {
            Files.createDirectories(dataDirectory);
            try (FileWriter writer = new FileWriter(playerdataFile.toFile(), StandardCharsets.UTF_8)) {
                gson.toJson(rootData, writer);
            }
        } catch (IOException e) {
            logger.error("Failed to save playerdata.json: " + e.getMessage());
        }
    }

    public void onPlayerLogin(UUID uuid, String playerName) {
        PlayerData playerData = rootData.players.get(uuid);
        String currentTime = dateTimeFormat.format(new Date());
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String currentWeek = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-'W'ww"));
        if (playerData == null) {
            logger.info("First login detected for player: " + playerName + " (" + uuid + ")");
            playerData = new PlayerData();
            playerData.firstJoinTime = currentTime;
            playerData.lastQuitTime = currentTime;
            rootData.players.put(uuid, playerData);
        }
        playerData.lastLoginTime = currentTime;
        DailyLoginData dailyData = playerData.dailyLogins.computeIfAbsent(today, k -> new DailyLoginData());
        dailyData.loginCount++;
        playerData.dailyLoginCount.set(dailyData.loginCount);
        WeeklyLoginData weeklyData = playerData.weeklyLogins.computeIfAbsent(currentWeek, k -> new WeeklyLoginData());
        weeklyData.loginCount++;
        playerData.weeklyLoginCount.set(weeklyData.loginCount);
        playerData.totalLoginCount.incrementAndGet();
        savePlayerData();
    }

    public void onPlayerQuit(UUID uuid) {
        PlayerData playerData = rootData.players.get(uuid);
        if (playerData != null && playerData.lastLoginTime != null) {
            try {
                Date loginDate = dateTimeFormat.parse(playerData.lastLoginTime);
                long durationSeconds = (System.currentTimeMillis() - loginDate.getTime()) / 1000;
                if (durationSeconds < 0) {
                    durationSeconds = 0;
                }
                String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
                DailyLoginData dailyData = playerData.dailyLogins.computeIfAbsent(today, k -> new DailyLoginData());
                dailyData.playDurationSeconds += durationSeconds;
                String currentWeek = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-'W'ww"));
                WeeklyLoginData weeklyData = playerData.weeklyLogins.computeIfAbsent(currentWeek, k -> new WeeklyLoginData());
                weeklyData.totalPlayTimeSecondsInWeek += durationSeconds;
                playerData.totalPlayTime += durationSeconds;
                playerData.lastQuitTime = dateTimeFormat.format(new Date());
            } catch (java.text.ParseException e) {
                logger.error("Error parsing lastLoginTime for player " + uuid + ": " + e.getMessage());
            }
        }
        savePlayerData();
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

    public RootData getRootData() {
        return rootData;
    }
}