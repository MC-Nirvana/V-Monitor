package cn.nirvana.vMonitor.util;

import cn.nirvana.vMonitor.exceptions.FileException;

import org.slf4j.Logger;

import org.yaml.snakeyaml.error.YAMLException;

import com.google.gson.JsonSyntaxException;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import java.util.function.Consumer;

public class FileUtil {

    private final Logger logger;
    private final Path dataDirectory;

    public FileUtil(Logger logger, Path dataDirectory) {
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    /**
     * 检查文件是否存在，如果不存在则尝试从JAR中复制默认文件。
     *
     * @param targetFilePath    目标文件路径
     * @param resourcePathInJar JAR包内的资源路径
     * @param fileType          文件类型描述（用于日志）
     * @return 如果文件存在或成功复制，返回true；否则返回false。
     */
    public boolean ensureFileExists(Path targetFilePath, String resourcePathInJar, String fileType) {
        if (!Files.exists(targetFilePath)) {
            logger.info("Default {} ('{}') not found, trying to copy from JAR...", fileType, targetFilePath.getFileName());
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePathInJar)) {
                if (in != null) {
                    Files.copy(in, targetFilePath, StandardCopyOption.REPLACE_EXISTING);
                    logger.info("Default {} copied to '{}'.", fileType, targetFilePath.toAbsolutePath());
                    return true;
                } else {
                    logger.error("Default resource '{}' NOT FOUND in plugin JAR. This is unexpected. Please check plugin JAR integrity.", resourcePathInJar);
                    return false;
                }
            } catch (IOException e) {
                logger.error("Failed to copy default resource '{}' to '{}': {}", resourcePathInJar, targetFilePath.toAbsolutePath(), e.getMessage());
                return false;
            }
        } else {
            logger.debug("{} '{}' already exists. Skipping initial copy.", fileType, targetFilePath.getFileName());
            return true;
        }
    }

    /**
     * 校验文件是否存在且可读。
     *
     * @param filePath 文件路径
     * @param fileType 文件类型（用于日志）
     * @throws FileException 如果文件不存在或不可读
     */
    public void verifyFile(Path filePath, String fileType) throws FileException {
        if (!Files.exists(filePath)) {
            String message = fileType + " file not found: " + filePath.getFileName();
            logger.error(message);
            throw new FileException(message);
        }

        if (!Files.isReadable(filePath)) {
            String message = fileType + " file is not readable: " + filePath.getFileName();
            logger.error(message);
            throw new FileException(message);
        }

        logger.debug("{} file verified: {}", fileType, filePath.getFileName());
    }

    /**
     * 泛型方法，用于安全地加载配置文件。
     *
     * @param <T>                 Loader的类型 (例如 ConfigLoader, LanguageLoader)
     * @param targetFilePath      要加载的文件路径
     * @param fileType            文件类型描述（用于日志信息）
     * @param loaderInstance      Loader的实例（例如 configFileLoader, languageFileLoader）
     * @param loadFunction        Loader中实际执行加载逻辑的方法引用
     * @throws FileException 如果在文件操作或加载过程中发生任何异常
     */
    public <T> void loadFile(Path targetFilePath, String fileType, T loaderInstance, Consumer<InputStreamReader> loadFunction) throws FileException {
        logger.info("Attempting to load {} file from: {}", fileType, targetFilePath.toAbsolutePath());

        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(targetFilePath.toFile()), StandardCharsets.UTF_8)) {
            loadFunction.accept(reader);
            logger.info("{} file '{}' loaded successfully.", fileType, targetFilePath.getFileName());
        } catch (IOException e) {
            throw new FileException("Failed to read " + fileType + " file '" + targetFilePath.getFileName() + "': " + e.getMessage(), e);
        } catch (YAMLException e) {
            throw new FileException("Invalid YAML syntax in " + fileType + " file '" + targetFilePath.getFileName() + "': " + e.getMessage(), e);
        } catch (Exception e) {
            throw new FileException("An unexpected error occurred while loading " + fileType + " file '" + targetFilePath.getFileName() + "': " + e.getMessage(), e);
        }
    }

    /**
     * 释放资源文件。
     *
     * @return 如果成功释放，返回 true；否则返回 false。
     */

    // 释放配置文件
    public boolean releaseConfigFile() {
        Path configPath = dataDirectory.resolve("config.yml");
        return ensureFileExists(configPath, "config.yml", "config");
    }

    // 释放语言文件
    public boolean releaseLanguageFiles() {
        boolean success = true;
        String[] languages = {"zh_cn.yml", "zh_tw.yml", "en_us.yml"};

        for (String langFile : languages) {
            Path langPath = dataDirectory.resolve("lang").resolve(langFile);
            boolean result = ensureFileExists(langPath, "lang/" + langFile, "language file");
            success = success && result;
        }

        return success;
    }

    /**
     * 尝试修复损坏的文件：重命名旧文件 + 释放默认文件。
     *
     * @param filePath 文件路径
     * @param resourcePathInJar JAR 包内的资源路径
     * @param fileType 文件类型（用于日志）
     * @return 修复成功返回 true
     * @throws FileException 如果修复失败
     */
    public boolean repairFile(Path filePath, String resourcePathInJar, String fileType) throws FileException {
        logger.warn("Repairing {} file: {}", fileType, filePath.getFileName());

        try {
            if (Files.exists(filePath)) {
                // 生成带时间戳的备份文件名
                String backupFileName = filePath.getFileName() + "." +
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-ddTHH-mm-ssXXX")) + ".error";
                Path backupPath = filePath.resolveSibling(backupFileName);

                // 重命名损坏文件
                Files.move(filePath, backupPath, StandardCopyOption.REPLACE_EXISTING);
                logger.info("Old {} file backed up to '{}'", fileType, backupPath.getFileName());
            }

            // 从 JAR 中复制默认文件
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePathInJar)) {
                if (in == null) {
                    logger.error("Default resource '{}' NOT FOUND in plugin JAR.", resourcePathInJar);
                    throw new FileException("Failed to repair " + fileType + " file: default resource not found");
                }
                Files.copy(in, filePath, StandardCopyOption.REPLACE_EXISTING);
                logger.info("{} file repaired and replaced: '{}'", fileType, filePath.getFileName());
                return true;
            }
        } catch (IOException e) {
            logger.error("Failed to repair {} file '{}': {}", fileType, filePath.getFileName(), e.getMessage());
            throw new FileException("Failed to repair " + fileType + " file '" + filePath.getFileName() + "': " + e.getMessage(), e);
        }
    }
}
