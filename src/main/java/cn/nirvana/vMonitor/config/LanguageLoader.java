// File: src/main/java/cn/nirvana/vMonitor/config/LanguageLoader.java
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
import java.io.FileWriter; // 新增导入

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
    private final String langFolderName = "lang"; // 语言文件子目录名

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
        Path langFilePath = dataDirectory.resolve(langFolderName).resolve(langFileName); // 构建完整的语言文件路径

        File langFile = langFilePath.toFile();

        // 检查并复制默认语言文件
        if (!langFile.exists()) {
            logger.info("Language file '" + langFileName + "' not found, creating default one.");
            try (InputStream in = getClass().getClassLoader().getResourceAsStream("lang/" + langFileName)) {
                if (in != null) {
                    // Files.createDirectories(dataDirectory.resolve(langFolderName)); // <--- 移除此行 (已在VMonitor中创建)
                    Files.copy(in, langFilePath, StandardCopyOption.REPLACE_EXISTING);
                    logger.info("Default language file '" + langFileName + "' created.");
                } else {
                    logger.error("Default language file '" + langFileName + "' not found in plugin resources. Falling back to en_us.");
                    // If specific language file not found, try to load en_us
                    if (!defaultLang.equals("en_us")) {
                        loadEnglishAsFallback();
                        return; // Try to load English, then return.
                    } else {
                        logger.error("English language file not found in resources. Cannot load any language.");
                        return;
                    }
                }
            } catch (IOException e) {
                logger.error("Failed to create default language file '" + langFileName + "': " + e.getMessage());
            }
        }

        // 加载语言文件
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(langFile), StandardCharsets.UTF_8)) {
            Yaml yaml = new Yaml(new Constructor(Map.class, new LoaderOptions()));
            language = yaml.load(reader);
            if (language == null) {
                language = new HashMap<>();
                logger.warn("Language file '" + langFileName + "' is empty or malformed, initializing empty language map.");
            }
            logger.info("Language file '" + langFileName + "' loaded.");
        } catch (IOException e) {
            logger.error("Failed to load language file '" + langFileName + "': " + e.getMessage());
            loadEnglishAsFallback(); // Fallback to English on load error
        }
    }

    private void loadEnglishAsFallback() {
        String englishFileName = "en_us.yml";
        Path englishLangFilePath = dataDirectory.resolve(langFolderName).resolve(englishFileName);
        File englishLangFile = englishLangFilePath.toFile();

        if (!englishLangFile.exists()) {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream("lang/" + englishFileName)) {
                if (in != null) {
                    Files.copy(in, englishLangFilePath, StandardCopyOption.REPLACE_EXISTING);
                    logger.warn("English language file not found, copied default 'en_us.yml'.");
                } else {
                    logger.error("Default 'en_us.yml' not found in plugin resources. Cannot provide any language.");
                }
            } catch (IOException e) {
                logger.error("Failed to create default 'en_us.yml': " + e.getMessage());
            }
        }

        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(englishLangFile), StandardCharsets.UTF_8)) {
            Yaml yaml = new Yaml(new Constructor(Map.class, new LoaderOptions()));
            language = yaml.load(reader);
            if (language == null) {
                language = new HashMap<>();
            }
            logger.info("Loaded fallback language: 'en_us.yml'.");
        } catch (IOException e) {
            logger.error("Failed to load fallback 'en_us.yml': " + e.getMessage());
            language = new HashMap<>(); // Ensure it's not null on double failure
        }
    }

    public void restoreDefaultLanguageFile(String langCode) {
        String langFileName = langCode + ".yml";
        Path originalPath = dataDirectory.resolve(langFolderName).resolve(langFileName);
        Path backupPath = dataDirectory.resolve(langFolderName).resolve(langFileName + ".bak");
        String resourcePath = "lang/" + langFileName;

        try {
            if (Files.exists(originalPath)) {
                Files.copy(originalPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
                logger.info("Backed up language file " + langFileName + " to " + langFileName + ".bak");
            }
            // Files.createDirectories(dataDirectory.resolve(langFolderName)); // <--- 移除此行 (已在VMonitor中创建)
            if (!copyDefaultFile(resourcePath, originalPath)) {
                logger.error("Failed to copy a new default language file during restoration. This is critical!");
            }
        } catch (IOException e) {
            logger.error("Failed to rename or copy default language file for restoration: " + e.getMessage());
        }
    }

    private boolean copyDefaultFile(String resourcePath, Path targetPath) {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (in != null) {
                Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
                return true;
            } else {
                logger.error("Resource not found: " + resourcePath);
                return false;
            }
        } catch (IOException e) {
            logger.error("Failed to copy resource " + resourcePath + " to " + targetPath + ": " + e.getMessage());
            return false;
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

    public Map<String, Object> getLanguage() {
        return language;
    }
}