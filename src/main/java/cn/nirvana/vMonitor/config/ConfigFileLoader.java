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
import java.nio.charset.StandardCharsets;

import java.util.HashMap;
import java.util.Map;
import java.util.List;

public class ConfigFileLoader {
    private final Logger logger;
    private final Path dataDirectory;
    private final String configFileName = "config.yml";

    private Map<String, Object> config;
    private Map<String, String> serverDisplayNames = new HashMap<>();

    public ConfigFileLoader(Logger logger, Path dataDirectory) {
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.config = new HashMap<>(); // 初始化，确保不为null
    }

    public Map<String, Object> getConfig() {
        return config;
    }

    public void loadConfig() {
        File configFile = new File(dataDirectory.toFile(), configFileName);
        try {
            if (!configFile.exists()) {
                configFile.getParentFile().mkdirs();
                String resourceConfigPath = "/" + configFileName;
                try (InputStream defaultConfigStream = getClass().getResourceAsStream(resourceConfigPath)) {
                    if (defaultConfigStream == null) {
                        logger.error("Could not find default config file '{}' in JAR resource path: {}", configFileName, resourceConfigPath);
                        this.config = new HashMap<>();
                        return;
                    }
                    Files.copy(defaultConfigStream, configFile.toPath());
                }
            }
            try (InputStreamReader reader = new InputStreamReader(new FileInputStream(configFile), StandardCharsets.UTF_8)) {
                LoaderOptions loaderOptions = new LoaderOptions();
                Yaml yaml = new Yaml(new Constructor(Map.class, loaderOptions));
                Map<String, Object> loadedConfig = yaml.load(reader);

                if (loadedConfig != null) {
                    this.config = loadedConfig;
                } else {
                    this.config = new HashMap<>();
                }
            }
            loadServerAliases();
        } catch (IOException e) {
            logger.error("Could not load config file {}: {}", configFileName, e.getMessage(), e);
            this.config = new HashMap<>();
        } catch (Exception e) {
            logger.error("Error parsing config file {}: {}", configFileName, e.getMessage(), e);
            this.config = new HashMap<>();
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

    public String getString(String key, String defaultValue) {
        if (config == null) {
            return defaultValue;
        }
        Object value = getNestedValue(key);
        if (value instanceof String) {
            return (String) value;
        }
        return defaultValue;
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