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
        this.language = new HashMap<>();
    }

    public void loadLanguage() {
        String configuredLang = configFileLoader.getLanguageKey();
        String langFileName = configuredLang + ".yml";
        Path langFilePath = dataDirectory.resolve(langFolderName).resolve(langFileName);
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(langFilePath.toFile()), StandardCharsets.UTF_8)) {
            Yaml yaml = new Yaml(new Constructor(new LoaderOptions()));
            this.language = yaml.load(reader);
            if (this.language == null) {
                throw new LanguageLoadException("Language file is empty or malformed: " + langFilePath.toAbsolutePath());
            }
            logger.info("Language file '{}' loaded.", langFileName);
        } catch (IOException e) {
            throw new LanguageLoadException("Failed to read language file: " + langFilePath.toAbsolutePath(), e);
        } catch (YAMLException e) {
            throw new LanguageLoadException("Failed to parse language file (YAML syntax error): " + langFilePath.toAbsolutePath(), e);
        } catch (Exception e) {
            throw new LanguageLoadException("An unexpected error occurred while loading language file: " + langFilePath.toAbsolutePath(), e);
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

    public static class LanguageLoadException extends RuntimeException {
        public LanguageLoadException(String message) {
            super(message);
        }

        public LanguageLoadException(String message, Throwable cause) {
            super(message, cause);
        }

        public LanguageLoadException(Throwable cause) {
            super(cause);
        }
    }
}