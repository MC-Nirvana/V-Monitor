package cn.nirvana.vMonitor.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.UUID;
import java.util.Map;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;

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
        if (!playerDataFile.exists()) {
            try {
                playerDataFile.getParentFile().mkdirs();
                if (playerDataFile.createNewFile()) {
                    savePlayerData();
                    logger.info("Player data file created: {}", playerDataFile.getAbsolutePath());
                }
            } catch (IOException e) {
                logger.error("Could not create player data file ", e);
            }
            if (this.playerData == null) {
                this.playerData = new HashMap<>();
            }
            return;
        }
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(playerDataFile), StandardCharsets.UTF_8)) {
            Gson gson = new Gson();
            java.lang.reflect.Type type = new TypeToken<HashMap<UUID, PlayerFirstJoinInfo>>() {
            }.getType();
            HashMap<UUID, PlayerFirstJoinInfo> loadedData = gson.fromJson(reader, type);
            if (loadedData != null) {
                this.playerData.clear();
                this.playerData.putAll(loadedData);
            } else {
                this.playerData = new HashMap<>();
                logger.warn("Player data file is empty or invalid: {}", playerDataFile.getName());
            }
            logger.info("Successfully loaded player data file");
        } catch (IOException e) {
            logger.error("Could not load player data file ", e);
            if (this.playerData == null) {
                this.playerData = new HashMap<>();
            }
        } catch (Exception e) {
            logger.error("Error parsing player data file ", e);
            if (this.playerData == null) {
                this.playerData = new HashMap<>();
            }
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
            logger.debug("Successfully saved player data file");
        } catch (IOException e) {
            logger.error("Could not save player data file ", e);
        }
    }

    public void addPlayerFirstJoinInfo(UUID uuid, String playerName) {
        if (!playerData.containsKey(uuid)) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
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