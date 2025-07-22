package cn.nirvana.vMonitor.loader;

import org.slf4j.Logger;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.LoaderOptions;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

import java.util.List;
import java.util.Map;

public class LanguageFileLoader {
    private final Logger logger;
    private final Path dataDirectory;
    private final ConfigFileLoader configFileLoader;
    private final String langFolderName = "lang";

    private Map<String, Object> language;

    public LanguageFileLoader(Logger logger, Path dataDirectory, ConfigFileLoader configFileLoader) {
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.configFileLoader = configFileLoader;
    }

    /**
     * 使用已打开的 reader 加载语言文件。
     * @param reader 已打开的 InputStreamReader
     */
    public void loadLanguage(InputStreamReader reader) {
        Yaml yaml = new Yaml(new Constructor(new LoaderOptions()));
        this.language = yaml.load(reader);
    }

    /**
     * 重载语言文件。
     */
    public void reloadLanguage() {
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(getLanguageFilePath().toFile()), StandardCharsets.UTF_8)) {
            loadLanguage(reader);
        } catch (IOException ignore) {
        }
    }

    /**
     * 获取语言文件路径。
     */
    private Path getLanguageFilePath() {
        String configuredLang = configFileLoader.getLanguageKey();
        String langFileName = configuredLang + ".yml";
        return dataDirectory.resolve(langFolderName).resolve(langFileName);
    }

    /**
     * 获取嵌套键值。
     */
    private Object getNestedValue(String key) {
        String[] parts = key.split("\\.");
        Object current = this.language;
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

    /**
     * 获取语言键对应的消息。
     */
    public String getMessage(String key) {
        if (language == null || language.isEmpty()) {
            return "<red>Internal Error: Language not loaded or empty for key: " + key + "</red>";
        }
        Object value = getNestedValue(key);
        if (value == null) {
            return "<red>Missing Language Key: " + key + "</red>";
        }
        if (value instanceof String) {
            return (String) value;
        }
        if (value instanceof List) {
            return "<red>Language key '" + key + "' is a list, expected a string.</red>";
        }
        return value.toString();
    }
}
