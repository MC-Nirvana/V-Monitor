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
        String defaultLang = this.configFileLoader.getString("language.default");
        String langFileName = defaultLang + ".yml";
        File langFile = new File(dataDirectory.toFile(), langFolderName + File.separator + langFileName);
        Path langFilePath = langFile.toPath();
        boolean langFileExists = Files.exists(langFilePath);
        if (!langFileExists) {
            logger.info("Language file '" + langFileName + "' not found, creating a new one from JAR resource.");
            if (!copyDefaultFile("/lang/" + langFileName, langFilePath)) {
                logger.error("Failed to create default language file '" + langFileName + "'. This likely indicates a missing resource in the plugin JAR.");
                this.language = new HashMap<>();
                return;
            }
            logger.info("Default language file '" + langFileName + "' created successfully.");
        }
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(langFile), StandardCharsets.UTF_8)) {
            LoaderOptions loaderOptions = new LoaderOptions();
            Yaml yaml = new Yaml(new Constructor(Map.class, loaderOptions));
            Map<String, Object> loadedData = yaml.load(reader);
            if (loadedData != null && !loadedData.isEmpty()) {
                this.language = loadedData;
                logger.info("Successfully loaded language file: " + langFileName);
            } else {
                logger.warn("Language file '" + langFileName + "' is empty or invalid. Restoring default.");
                throw new RuntimeException("Language file empty or invalid.");
            }
        } catch (Exception e) {
            logger.error("Error processing language file '" + langFileName + "': " + e.getMessage() + ". Renaming and restoring default.");
            renameAndCopyDefault(langFilePath, langFileName + ".err", "/lang/" + defaultLang + ".yml");
            try (InputStreamReader newReader = new InputStreamReader(new FileInputStream(langFile), StandardCharsets.UTF_8)) {
                LoaderOptions loaderOptions = new LoaderOptions();
                Yaml yaml = new Yaml(new Constructor(Map.class, loaderOptions));
                Map<String, Object> reloadedData = yaml.load(newReader);
                if (reloadedData != null && !reloadedData.isEmpty()) {
                    this.language = reloadedData;
                    logger.info("Successfully loaded the restored default language file.");
                } else {
                    logger.error("Failed to load the restored default language file. Plugin might have missing translations.");
                    this.language = new HashMap<>();
                }
            } catch (Exception ex) {
                logger.error("Critical: Failed to load language even after restoration attempt: " + ex.getMessage());
                this.language = new HashMap<>();
            }
        }
    }

    private boolean copyDefaultFile(String resourcePath, Path targetPath) {
        try {
            Files.createDirectories(targetPath.getParent());
            try (InputStream defaultStream = getClass().getResourceAsStream(resourcePath)) {
                if (defaultStream == null) {
                    logger.error("Could not find language resource '" + resourcePath + "' in JAR. This is a plugin packaging error. Please check your JAR file.");
                    return false;
                }
                Files.copy(defaultStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
                return true;
            }
        } catch (IOException e) {
            logger.error("Failed to copy language file from JAR '" + resourcePath + "' to '" + targetPath + "': " + e.getMessage());
            return false;
        }
    }

    private void renameAndCopyDefault(Path originalPath, String newSuffix, String resourcePath) {
        try {
            Path errorPath = originalPath.resolveSibling(originalPath.getFileName().toString() + newSuffix);
            Files.move(originalPath, errorPath, StandardCopyOption.REPLACE_EXISTING);
            logger.warn("Renamed corrupted language file to: " + errorPath.getFileName());
            if (!copyDefaultFile(resourcePath, originalPath)) {
                logger.error("Failed to copy a new default language file during restoration. This is critical!");
            }
        } catch (IOException e) {
            logger.error("Failed to rename or copy default language file for restoration: " + e.getMessage());
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