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

    /**
     * 加载配置文件。
     * @param reader InputStreamReader 对象，用于读取配置文件内容。
     */
    public void loadConfig(InputStreamReader reader) {
        Yaml yaml = new Yaml(new Constructor(new LoaderOptions()));
        this.config = yaml.load(reader);
        loadServerDisplayNames();
    }


    /**
     * 重载配置文件。
     */
    public void reloadConfig() {
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(getConfigFilePath().toFile()), StandardCharsets.UTF_8)) {
            loadConfig(reader);
        } catch (IOException ignore) {
        }
    }

    /**
     * 获取配置文件路径。
     * @return 当前配置文件的完整路径。
     */
    private Path getConfigFilePath() {
        return dataDirectory.resolve(configFileName);
    }

    private void loadServerDisplayNames() {
        serverDisplayNames.clear(); // 清空旧数据
        Map<String, Object> aliasesSection = getTable("server-info.aliases");
        if (aliasesSection != null) {
            for (Map.Entry<String, Object> entry : aliasesSection.entrySet()) {
                String serverKey = entry.getKey();
                Object displayNameValue = entry.getValue();

                // 直接使用值作为显示名称，因为配置格式是 key: "value"
                if (displayNameValue instanceof String) {
                    String displayName = (String) displayNameValue;
                    if (!displayName.isEmpty()) {
                        serverDisplayNames.put(serverKey, displayName);
                    }
                }
            }
        }
        logger.debug("Loaded server display names: {}", serverDisplayNames);
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

    public String getServerDisplayName(String serverName) {
        return serverDisplayNames.getOrDefault(serverName, serverName);
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
        return getString("plugin-basic.language.default");
    }

    public String getServerName() {
        return getString("server-info.name");
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
