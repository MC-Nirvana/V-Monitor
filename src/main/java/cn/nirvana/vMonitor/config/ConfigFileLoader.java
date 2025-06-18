// File: src/main/java/cn/nirvana/vMonitor/config/ConfigFileLoader.java
package cn.nirvana.vMonitor.config;

import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set; // 移除 Set<String> ALLOWED_LANGUAGES 的定义

public class ConfigFileLoader {
    private final Logger logger;
    private final Path dataDirectory;
    private final String configFileName = "config.yml";
    // 移除这一行: private final Set<String> ALLOWED_LANGUAGES = Set.of("zh_cn", "zh_tw", "en_us");

    private Map<String, Object> config;
    private Map<String, String> serverDisplayNames = new HashMap<>();

    public ConfigFileLoader(Logger logger, Path dataDirectory) {
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    /**
     * 加载配置文件。如果文件不存在或解析失败，则抛出 ConfigFileLoader.ConfigLoadException。
     * @throws ConfigFileLoader.ConfigLoadException 如果配置文件加载或解析失败
     */
    public void loadConfig() {
        Path configFilePath = dataDirectory.resolve(configFileName);
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(configFilePath.toFile()), StandardCharsets.UTF_8)) {
            Yaml yaml = new Yaml(new Constructor(new LoaderOptions()));
            this.config = yaml.load(reader);
            if (this.config == null) {
                // 如果文件为空或内容无法解析为Map，Yaml.load可能返回null
                throw new ConfigLoadException("Config file is empty or malformed: " + configFilePath.toAbsolutePath());
            }
            logger.info("Config file '{}' loaded.", configFileName);
            loadServerDisplayNames(); // 配置文件加载成功后加载服务器别名
        } catch (IOException e) {
            // 文件不存在或I/O错误
            throw new ConfigLoadException("Failed to read config file: " + configFilePath.toAbsolutePath(), e);
        } catch (YAMLException e) {
            // YAML解析错误
            throw new ConfigLoadException("Failed to parse config file (YAML syntax error): " + configFilePath.toAbsolutePath(), e);
        } catch (Exception e) {
            // 捕获其他任何未预期的运行时异常
            throw new ConfigLoadException("An unexpected error occurred while loading config file: " + configFilePath.toAbsolutePath(), e);
        }
    }

    private void loadServerDisplayNames() {
        this.serverDisplayNames.clear(); // 清除旧的别名
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
        // 仅仅返回配置中的值，不进行有效性检查和回退
        return getString("language.default", "zh_cn"); // 默认值作为备用，但实际有效性由 LanguageLoader 处理
    }

    /**
     * 当配置文件加载或解析失败时抛出的异常。
     * 定义为 ConfigFileLoader 的静态嵌套类。
     */
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