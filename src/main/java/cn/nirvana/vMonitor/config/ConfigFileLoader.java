// File: src/main/java/cn/nirvana/vMonitor/config/ConfigFileLoader.java
package cn.nirvana.vMonitor.config;

import org.slf4j.Logger;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.LoaderOptions;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.FileWriter; // 新增导入

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;

public class ConfigFileLoader {
    private final Logger logger;
    private final Path dataDirectory;
    private final String configFileName = "config.yml";
    private final Set<String> ALLOWED_LANGUAGES = Set.of("zh_cn", "zh_tw", "en_us");

    private Map<String, Object> config;
    private Map<String, String> serverDisplayNames = new HashMap<>();

    public ConfigFileLoader(Logger logger, Path dataDirectory) {
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.config = new HashMap<>();
    }

    public Map<String, Object> getConfig() {
        return config;
    }

    public void loadConfig() {
        File configFile = dataDirectory.resolve(configFileName).toFile();
        if (!configFile.exists()) {
            logger.info("Config file not found, creating default one.");
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(configFileName)) {
                if (in != null) {
                    // Files.createDirectories(dataDirectory); // <--- 移除此行
                    Files.copy(in, configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    logger.info("Default config file created.");
                } else {
                    logger.error("Default config file not found in plugin resources.");
                }
            } catch (IOException e) {
                logger.error("Failed to create default config file: " + e.getMessage());
            }
        }
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(configFile), StandardCharsets.UTF_8)) {
            Yaml yaml = new Yaml(new Constructor(Map.class, new LoaderOptions()));
            config = yaml.load(reader);
            if (config == null) {
                config = new HashMap<>();
                logger.warn("Config file is empty or malformed, initializing empty config.");
            }
            loadServerDisplayNames(); // Load server display names after config is loaded
            logger.info("Config loaded.");
        } catch (IOException e) {
            logger.error("Failed to load config file: " + e.getMessage());
        }
        // Check config version and restore if needed (if you have versioning logic)
        // For simplicity, this example doesn't include versioning logic for config.
    }

    public void saveConfig() {
        File configFile = dataDirectory.resolve(configFileName).toFile();
        try {
            // Files.createDirectories(dataDirectory); // <--- 移除此行
            try (FileWriter writer = new FileWriter(configFile, StandardCharsets.UTF_8)) {
                Yaml yaml = new Yaml();
                yaml.dump(config, writer);
                logger.info("Config saved.");
            }
        } catch (IOException e) {
            logger.error("Failed to save config file: " + e.getMessage());
        }
    }

    private void loadServerDisplayNames() {
        serverDisplayNames.clear(); // Clear previous names
        Map<String, Object> serversSection = getTable("server_display_names");
        if (serversSection != null) {
            serversSection.forEach((key, value) -> {
                if (value instanceof String) {
                    serverDisplayNames.put(key.toLowerCase(), (String) value);
                    logger.debug("Loaded server display name: " + key + " -> " + value);
                }
            });
        }
    }

    public String getServerDisplayName(String serverRawName) {
        return serverDisplayNames.getOrDefault(serverRawName.toLowerCase(), serverRawName);
    }

    private Map<String, Object> getDefaultConfig() {
        // This method should provide your default config map
        // For brevity, I'm providing a minimal example. You should fill this based on your actual config.yml
        Map<String, Object> defaultConfig = new HashMap<>();
        defaultConfig.put("language", Map.of("default", "en_us"));
        defaultConfig.put("player_activity", Map.of(
                "enable_login_message", true,
                "enable_quit_message", true,
                "enable_switch_message", true
        ));
        defaultConfig.put("server_display_names", new HashMap<>());
        // Add other default configurations here
        return defaultConfig;
    }

    public void restoreDefaultConfig() {
        File currentConfigFile = dataDirectory.resolve(configFileName).toFile();
        File backupConfigFile = dataDirectory.resolve(configFileName + ".bak").toFile();

        try {
            if (currentConfigFile.exists()) {
                Files.copy(currentConfigFile.toPath(), backupConfigFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                logger.info("Backed up current config to " + backupConfigFile.getName());
            }
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(configFileName)) {
                if (in != null) {
                    Files.copy(in, currentConfigFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    logger.info("Restored default config file.");
                } else {
                    logger.error("Default config file not found in plugin resources for restoration.");
                }
            }
            loadConfig(); // Reload the newly restored config
        } catch (IOException e) {
            logger.error("Failed to restore default config file: " + e.getMessage());
        }
    }

    public String getString(String key) {
        if (config == null) {
            return null;
        }
        Object value = getNestedValue(key);
        if (value instanceof String) {
            return (String) value;
        }
        return null;
    }

    public String getString(String key, String defaultValue) {
        String value = getString(key);
        return value != null ? value : defaultValue;
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        if (config == null) {
            return defaultValue;
        }
        Object value = getNestedValue(key);
        if (value instanceof Boolean) {
            return (boolean) value;
        }
        return defaultValue;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getTable(String key) {
        if (config == null) {
            return null;
        }
        Object value = getNestedValue(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return null;
    }

    private Object getNestedValue(String key) {
        String[] parts = key.split("\\.");
        Object current = this.config;
        for (String part : parts) {
            if (current instanceof Map) {
                current = ((Map<?, ?>) current).get(part);
                if (current == null) {
                    return null;
                }
            } else {
                return null;
            }
        }
        return current;
    }
}