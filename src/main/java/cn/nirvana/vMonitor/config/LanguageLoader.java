package cn.nirvana.vMonitor.config;

import net.kyori.adventure.text.minimessage.MiniMessage;

import org.slf4j.Logger;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.LoaderOptions;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LanguageLoader {
    private final Logger logger;
    private final Path dataDirectory;
    private final ConfigFileLoader configFileLoader;
    private final String langFolderName = "lang";

    private Map<String, Object> language;

    public LanguageLoader(Logger logger, Path dataDirectory, ConfigFileLoader configFileLoader) {
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.configFileLoader = configFileLoader;
        this.language = new HashMap<>();
    }

    public void loadLanguage() {
        if (this.configFileLoader.getConfig() == null || this.configFileLoader.getConfig().isEmpty()) {
            this.language = new HashMap<>();
            return;
        }
        String defaultLang = this.configFileLoader.getString("language.default", "en_us");
        File langFile = new File(dataDirectory.toFile(), langFolderName + File.separator + defaultLang + ".yml");
        if (!langFile.exists()) {
            try {
                langFile.getParentFile().mkdirs();
                String resourceLangPath = "/lang/" + defaultLang + ".yml";
                InputStream defaultLangStream = getClass().getResourceAsStream(resourceLangPath);
                if (defaultLangStream == null) {
                    resourceLangPath = "/lang/en_us.yml";
                    defaultLangStream = getClass().getResourceAsStream(resourceLangPath);
                    if (defaultLangStream == null) {
                        this.language = new HashMap<>();
                        return;
                    }
                    Files.copy(defaultLangStream, langFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } else {
                    Files.copy(defaultLangStream, langFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                logger.error("Could not create language file {}: {}", langFile.getName(), e.getMessage(), e);
                this.language = new HashMap<>();
                return;
            }
        }
        LoaderOptions loaderOptions = new LoaderOptions();
        Yaml yaml = new Yaml(new Constructor(Map.class, loaderOptions));
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(langFile), StandardCharsets.UTF_8)) {
            Map<String, Object> loadedData = yaml.load(reader);
            if (loadedData == null) {
                this.language = new HashMap<>();
            } else {
                this.language = loadedData;
            }
        } catch (IOException e) {
            logger.error("Could not load language file '{}': {}", langFile.getName(), e.getMessage(), e);
            this.language = new HashMap<>();
        } catch (Exception e) {
            logger.error("Error parsing language file '{}': {}", langFile.getName(), e.getMessage(), e);
            this.language = new HashMap<>();
        }
    }

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

    public String getMessage(String key) {
        if (language == null) {
            return "<red>Internal Error: Language not loaded.</red>";
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

    public Map<String, Object> getLanguageMap() {
        return language;
    }
}