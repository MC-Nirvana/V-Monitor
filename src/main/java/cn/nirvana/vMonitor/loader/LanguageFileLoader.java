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

    /**
     * 加载语言文件。如果文件不存在或解析失败，则抛出 LanguageFileLoader.LanguageLoadException。
     * 该方法直接使用从 ConfigFileLoader 获取的语言键来尝试加载。
     * 如果加载失败，将依赖 VMonitor 的文件修复机制。
     * @throws LanguageFileLoader.LanguageLoadException 如果语言文件加载或解析失败
     */
    public void loadLanguage() {
        // 从 configFileLoader 获取配置的语言键
        String configuredLang = configFileLoader.getLanguageKey();

        // 由于有文件修复机制，这里直接尝试加载配置的语言文件。
        // 不再进行有效性检查和回退逻辑。
        String langFileName = configuredLang + ".yml";
        Path langFilePath = dataDirectory.resolve(langFolderName).resolve(langFileName);

        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(langFilePath.toFile()), StandardCharsets.UTF_8)) {
            Yaml yaml = new Yaml(new Constructor(new LoaderOptions()));
            this.language = yaml.load(reader);
            if (this.language == null) {
                // 如果文件为空或内容无法解析为Map，Yaml.load可能返回null
                throw new LanguageLoadException("Language file is empty or malformed: " + langFilePath.toAbsolutePath());
            }
            logger.info("Language file '{}' loaded.", langFileName);
        } catch (IOException e) {
            // 文件不存在或I/O错误。VMonitor的 loadAndCheckFile 会捕获并处理。
            throw new LanguageLoadException("Failed to read language file: " + langFilePath.toAbsolutePath(), e);
        } catch (YAMLException e) {
            // YAML解析错误。VMonitor的 loadAndCheckFile 会捕获并处理。
            throw new LanguageLoadException("Failed to parse language file (YAML syntax error): " + langFilePath.toAbsolutePath(), e);
        } catch (Exception e) {
            // 捕获其他任何未预期的运行时异常。VMonitor的 loadAndCheckFile 会捕获并处理。
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

    /**
     * 当语言文件加载或解析失败时抛出的异常。
     * 定义为 LanguageFileLoader 的静态嵌套类。
     */
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