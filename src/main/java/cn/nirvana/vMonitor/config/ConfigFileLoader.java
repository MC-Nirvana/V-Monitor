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
        File configFile = new File(dataDirectory.toFile(), configFileName);
        Path configPath = configFile.toPath();
        boolean configFileExists = Files.exists(configPath);
        boolean configLoadAttemptSuccessful = false;
        if (!configFileExists) {
            logger.info("Configuration file not found, creating a new one.");
            if (!copyDefaultFile("/" + configFileName, configPath)) {
                logger.error("Failed to create default configuration file. Plugin might not function correctly.");
                this.config = new HashMap<>();
                return;
            }
            logger.info("Default configuration file created successfully.");
        }
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(configFile), StandardCharsets.UTF_8)) {
            LoaderOptions loaderOptions = new LoaderOptions();
            Yaml yaml = new Yaml(new Constructor(Map.class, loaderOptions));
            Map<String, Object> loadedConfig = yaml.load(reader);
            if (loadedConfig != null && !loadedConfig.isEmpty()) {
                this.config = loadedConfig;
                String defaultLanguage = getString("language.default");
                if (defaultLanguage == null || !ALLOWED_LANGUAGES.contains(defaultLanguage)) {
                    logger.error("Invalid or missing 'language.default' value in config.yml: '" + defaultLanguage + "'. Must be one of " + ALLOWED_LANGUAGES + ". Restoring default config.");
                    throw new RuntimeException("Invalid language setting in config.");
                }
                logger.info("Successfully loaded configuration file.");
                configLoadAttemptSuccessful = true;
            } else {
                logger.warn("Configuration file is empty or invalid. Restoring default config.");
                throw new RuntimeException("Config file empty or invalid.");
            }
        } catch (Exception e) {
            logger.error("Error processing config file '" + configFileName + "': " + e.getMessage() + ". Renaming and restoring default.");
            renameAndCopyDefault(configPath, configFileName + ".err", "/" + configFileName);
            try (InputStreamReader newReader = new InputStreamReader(new FileInputStream(configFile), StandardCharsets.UTF_8)) {
                LoaderOptions loaderOptions = new LoaderOptions();
                Yaml yaml = new Yaml(new Constructor(Map.class, loaderOptions));
                Map<String, Object> reloadedConfig = yaml.load(newReader);
                if (reloadedConfig != null && !reloadedConfig.isEmpty()) {
                    this.config = reloadedConfig;
                    logger.info("Successfully loaded the restored default configuration file.");
                    configLoadAttemptSuccessful = true;
                } else {
                    logger.error("Failed to load the restored default configuration file. Plugin might not function correctly.");
                    this.config = new HashMap<>();
                }
            } catch (Exception ex) {
                logger.error("Critical: Failed to load configuration even after restoration attempt: " + ex.getMessage());
                this.config = new HashMap<>();
            }
        }
        if (configLoadAttemptSuccessful) {
            loadServerAliases();
        } else {
            this.config = new HashMap<>();
        }
    }

    private boolean copyDefaultFile(String resourcePath, Path targetPath) {
        try {
            Files.createDirectories(targetPath.getParent());
            try (InputStream defaultStream = getClass().getResourceAsStream(resourcePath)) {
                if (defaultStream == null) {
                    logger.error("Could not find default resource '" + resourcePath + "' in JAR.");
                    return false;
                }
                Files.copy(defaultStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
                return true;
            }
        } catch (IOException e) {
            logger.error("Failed to copy default file '" + resourcePath + "' to '" + targetPath + "': " + e.getMessage());
            return false;
        }
    }

    private void renameAndCopyDefault(Path originalPath, String newSuffix, String resourcePath) {
        try {
            Path errorPath = originalPath.resolveSibling(originalPath.getFileName().toString() + newSuffix);
            Files.move(originalPath, errorPath, StandardCopyOption.REPLACE_EXISTING);
            logger.warn("Renamed corrupted file to: " + errorPath.getFileName());
            copyDefaultFile(resourcePath, originalPath);
        } catch (IOException e) {
            logger.error("Failed to rename or copy default file for restoration: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void loadServerAliases() {
        this.serverDisplayNames.clear();
        if (this.config == null || this.config.isEmpty()) {
            return;
        }
        Object aliasesSectionObj = this.config.get("server-aliases");
        if (aliasesSectionObj instanceof List) {
            try {
                List<Object> aliasesList = (List<Object>) aliasesSectionObj;
                for (Object item : aliasesList) {
                    if (item instanceof Map) {
                        Map<String, Object> aliasEntry = (Map<String, Object>) item;
                        if (aliasEntry.size() == 1) {
                            Map.Entry<String, Object> entry = aliasEntry.entrySet().iterator().next();
                            String actualName = entry.getKey();
                            Object displayAliasObj = entry.getValue();
                            if (displayAliasObj instanceof String) {
                                String displayAlias = (String) displayAliasObj;
                                this.serverDisplayNames.put(actualName, displayAlias);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("Could not load server display names from config due to an error.", e);
                this.serverDisplayNames = new HashMap<>();
            }
        } else if (aliasesSectionObj != null) {
            this.serverDisplayNames = new HashMap<>();
        } else {
            this.serverDisplayNames = new HashMap<>();
        }
    }

    public String getServerDisplayName(String actualServerName) {
        return serverDisplayNames.getOrDefault(actualServerName, actualServerName);
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