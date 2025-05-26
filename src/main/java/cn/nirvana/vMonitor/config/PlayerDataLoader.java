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

import java.util.HashMap;
import java.util.UUID;
import java.util.Map;
import java.util.Date;

import java.text.SimpleDateFormat;

public class PlayerDataLoader {
    private final Logger logger;
    private final Path dataDirectory;
    private final String playerDataFileName = "playerdata.json";
    private HashMap<UUID, PlayerFirstJoinInfo> playerData = new HashMap<>();

    public static class PlayerFirstJoinInfo {
        public String firstJoinTime;
        public String playerName;
        public PlayerFirstJoinInfo() {}
        public PlayerFirstJoinInfo(String firstJoinTime, String playerName) {
            this.firstJoinTime = firstJoinTime;
            this.playerName = playerName;
        }
    }

    public PlayerDataLoader(Logger logger, Path dataDirectory) {
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    public HashMap<UUID, PlayerFirstJoinInfo> getPlayerData() {
        return playerData;
    }

    public void loadPlayerData() {
        File playerDataFile = new File(dataDirectory.toFile(), playerDataFileName);
        Path playerFilePath = playerDataFile.toPath();
        boolean playerFileExists = Files.exists(playerFilePath);
        if (!playerFileExists) {
            logger.info("Player data file not found, creating a new one.");
            if (!createEmptyPlayerDataFile(playerFilePath)) {
                logger.error("Failed to create empty player data file. Plugin might not be able to save player data.");
                this.playerData = new HashMap<>();
            } else {
                logger.info("Empty player data file created successfully.");
                this.playerData = new HashMap<>();
            }
            return;
        }
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(playerDataFile), StandardCharsets.UTF_8)) {
            Gson gson = new Gson();
            java.lang.reflect.Type type = new TypeToken<HashMap<UUID, PlayerFirstJoinInfo>>() {}.getType();
            HashMap<UUID, PlayerFirstJoinInfo> loadedData = gson.fromJson(reader, type);
            if (loadedData != null) {
                this.playerData.clear();
                this.playerData.putAll(loadedData);
                logger.info("Successfully loaded player data file.");
            } else {
                logger.warn("Player data file is empty or invalid. Restoring default (empty data).");
                throw new RuntimeException("Player data file empty or invalid.");
            }
        } catch (Exception e) {
            logger.error("Error processing player data file '" + playerDataFileName + "': " + e.getMessage() + ". Renaming and restoring default.");
            renameAndCopyDefault(playerFilePath, playerDataFileName + ".err");
            try (InputStreamReader newReader = new InputStreamReader(new FileInputStream(playerDataFile), StandardCharsets.UTF_8)) {
                Gson gson = new Gson();
                java.lang.reflect.Type type = new TypeToken<HashMap<UUID, PlayerFirstJoinInfo>>() {}.getType();
                HashMap<UUID, PlayerFirstJoinInfo> reloadedData = gson.fromJson(newReader, type);
                if (reloadedData != null) {
                    this.playerData = reloadedData;
                    logger.info("Successfully loaded the restored default (empty) player data file.");
                } else {
                    logger.error("Failed to load the restored default (empty) player data file. Plugin might not be able to save player data.");
                    this.playerData = new HashMap<>();
                }
            } catch (Exception ex) {
                logger.error("Critical: Failed to load player data even after restoration attempt: " + ex.getMessage());
                this.playerData = new HashMap<>();
            }
        }
    }

    private boolean createEmptyPlayerDataFile(Path targetPath) {
        try {
            Files.createDirectories(targetPath.getParent());
            try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(targetPath.toFile()), StandardCharsets.UTF_8)) {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                gson.toJson(new HashMap<UUID, PlayerFirstJoinInfo>(), writer);
            }
            return true;
        } catch (IOException e) {
            logger.error("Failed to create empty player data file at '" + targetPath + "': " + e.getMessage());
            return false;
        }
    }

    private void renameAndCopyDefault(Path originalPath, String newSuffix) {
        try {
            Path errorPath = originalPath.resolveSibling(originalPath.getFileName().toString() + newSuffix);
            Files.move(originalPath, errorPath, StandardCopyOption.REPLACE_EXISTING);
            logger.warn("Renamed corrupted player data file to: " + errorPath.getFileName());
            createEmptyPlayerDataFile(originalPath);
        } catch (IOException e) {
            logger.error("Failed to rename or create default player data file for restoration: " + e.getMessage());
        }
    }

    public void savePlayerData() {
        File playerDataFile = new File(dataDirectory.toFile(), playerDataFileName);
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(playerDataFile), StandardCharsets.UTF_8)) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            if (this.playerData == null) {
                this.playerData = new HashMap<>();
            }
            gson.toJson(this.playerData, writer);
            logger.debug("Successfully saved player data file.");
        } catch (IOException e) {
            logger.error("Could not save player data file: " + e.getMessage());
        }
    }

    public void addPlayerFirstJoinInfo(UUID uuid, String playerName) {
        if (!playerData.containsKey(uuid)) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS");
            String formattedTime = sdf.format(new Date(System.currentTimeMillis()));
            playerData.put(uuid, new PlayerFirstJoinInfo(formattedTime, playerName));
            savePlayerData();
        } else {
            PlayerFirstJoinInfo existingInfo = playerData.get(uuid);
            if (!existingInfo.playerName.equals(playerName)) {
                existingInfo.playerName = playerName;
                savePlayerData();
            }
        }
    }

    public boolean hasPlayerJoinedBefore(UUID uuid) {
        return playerData.containsKey(uuid);
    }

    public PlayerFirstJoinInfo getPlayerFirstJoinInfo(UUID uuid) {
        return playerData.get(uuid);
    }
}