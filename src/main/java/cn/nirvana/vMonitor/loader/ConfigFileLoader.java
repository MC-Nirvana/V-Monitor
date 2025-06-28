package cn.nirvana.vMonitor.loader;

import org.slf4j.Logger;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigFileLoader {
    private final Logger logger;
    private final Path dataDirectory;
    private final String configFileName = "config.yml";

    private Map<String, Object> config;
    private Map<String, String> serverDisplayNames = new HashMap<>();

    public ConfigFileLoader(Logger logger, Path dataDirectory) {
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    public void loadConfig() {
        Path configFilePath = dataDirectory.resolve(configFileName);
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(configFilePath.toFile()), StandardCharsets.UTF_8)) {
            Yaml yaml = new Yaml(new Constructor(new LoaderOptions()));
            this.config = yaml.load(reader);
            if (this.config == null) {
                throw new ConfigLoadException("Config file is empty or malformed: " + configFilePath.toAbsolutePath());
            }
            logger.info("Config file '{}' loaded.", configFileName);
            loadServerDisplayNames();
        } catch (IOException e) {
            throw new ConfigLoadException("Failed to read config file: " + configFilePath.toAbsolutePath(), e);
        } catch (YAMLException e) {
            throw new ConfigLoadException("Failed to parse config file (YAML syntax error): " + configFilePath.toAbsolutePath(), e);
        } catch (Exception e) {
            throw new ConfigLoadException("An unexpected error occurred while loading config file: " + configFilePath.toAbsolutePath(), e);
        }
    }

    private void loadServerDisplayNames() {
        this.serverDisplayNames.clear();
        @SuppressWarnings("unchecked")
        List<Map<String, String>> aliasesList = (List<Map<String, String>>) getNestedValue("server-aliases");
        if (aliasesList != null) {
            for (Map<String, String> aliasEntry : aliasesList) {
                aliasEntry.forEach((actualName, displayName) -> {
                    if (actualName != null && !actualName.isEmpty() && displayName != null && !displayName.isEmpty()) {
                        serverDisplayNames.put(actualName, displayName);
                    }
                });
            }
            logger.debug("Loaded {} server display names.", serverDisplayNames.size());
        }
    }

    public String getServerDisplayName(String actualName) {
        return serverDisplayNames.getOrDefault(actualName, actualName);
    }

    @SuppressWarnings("unchecked")
    private Object getNestedValue(String key) {
        String[] parts = key.split("\\.");
        Object current = this.config;
        for (String part : parts) {
            if (current instanceof Map) {
                current = ((Map<String, Object>) current).get(part);
                if (current == null) {
                    return null;
                }
            } else {
                return null;
            }
        }
        return current;
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

    public String getLanguageKey() {
        return getString("language.default");
    }

    public static class ConfigLoadException extends RuntimeException {
        public ConfigLoadException(String message) {
            super(message);
        }

        public ConfigLoadException(String message, Throwable cause) {
            super(message, cause);
        }

        public ConfigLoadException(Throwable cause) {
            super(cause);
        }
    }
}