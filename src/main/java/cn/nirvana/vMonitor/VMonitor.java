package cn.nirvana.vMonitor;

import cn.nirvana.vMonitor.command.*;
import cn.nirvana.vMonitor.command.ServerInfoCommand;
import cn.nirvana.vMonitor.loader.ConfigFileLoader;
import cn.nirvana.vMonitor.loader.DataFileLoader;
import cn.nirvana.vMonitor.loader.LanguageFileLoader;
import cn.nirvana.vMonitor.listener.PlayerActivityListener;

import com.google.inject.Inject;

import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;

import net.kyori.adventure.text.minimessage.MiniMessage;

import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set; // 导入 Set

@Plugin(id = "v-monitor", name = "V-Monitor", version = "1.3.0", url = "https://github.com/MC-Nirvana/V-Monitor", description = "Monitor your Velocity Proxy player activity, server status, and generate daily/weekly reports.", authors = {"MC_Nirvana"})
public class VMonitor {
    private final ProxyServer proxyServer;
    private final Logger logger;
    private final Path dataDirectory;
    private LanguageFileLoader languageFileLoader;
    private ConfigFileLoader configFileLoader; // 确保 ConfigFileLoader 实例在这里声明
    private DataFileLoader dataFileLoader;
    private MiniMessage miniMessage;

    // 硬编码支持的语言文件列表，确保所有语言文件都被复制
    private static final Set<String> SUPPORTED_LANGUAGE_FILES = Set.of(
            "zh_cn.yml",
            "zh_tw.yml",
            "en_us.yml"
    );
    private static final String CONFIG_FILE_NAME = "config.yml";
    private static final String PLAYER_DATA_FILE_NAME = "data.json";
    private static final String LANG_FOLDER_NAME = "lang";

    private final DateTimeFormatter errorFileTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"); // 用于错误文件重命名

    @Inject
    public VMonitor(ProxyServer proxyServer, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxyServer = proxyServer;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        logger.info("V-Monitor is enabling...");

        // 1. 设置插件所需的文件和目录 (确保文件存在)
        if (!setupInitialPluginFiles()) {
            logger.error("Failed to setup initial plugin files. V-Monitor will not enable properly.");
            return;
        }

        // 初始化 MiniMessage
        this.miniMessage = MiniMessage.miniMessage();

        // 2. 检查并加载配置文件
        this.configFileLoader = new ConfigFileLoader(logger, dataDirectory); // 实例化 ConfigFileLoader
        if (!loadAndCheckFile(configFileLoader, CONFIG_FILE_NAME, null, "Configuration")) { // 添加类型参数
            logger.error("Failed to load and validate config file. V-Monitor will not enable properly.");
            return;
        }

        // 3. 检查并加载语言文件 (依赖于 configFileLoader)
        this.languageFileLoader = new LanguageFileLoader(logger, dataDirectory, configFileLoader);
        String defaultLanguageKeyFromConfig = configFileLoader.getLanguageKey();
        if (!loadAndCheckFile(languageFileLoader, defaultLanguageKeyFromConfig + ".yml", LANG_FOLDER_NAME, "Language")) { // 添加类型参数
            logger.error("Failed to load and validate language file. V-Monitor will not enable properly.");
            return;
        }

        // 4. 检查并加载玩家数据文件
        this.dataFileLoader = new DataFileLoader(logger, dataDirectory);
        if (!loadAndCheckFile(dataFileLoader, PLAYER_DATA_FILE_NAME, null, "Data")) { // 添加类型参数
            logger.error("Failed to load and validate player data file. V-Monitor will not enable properly.");
            return;
        }
        if (dataFileLoader.getRootData().server.bootTime == null || dataFileLoader.getRootData().server.bootTime.isEmpty()) {
            dataFileLoader.getRootData().server.bootTime = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            dataFileLoader.savePlayerData(); // 保存更新后的启动时间
            logger.info("Server boot time initialized: {}", dataFileLoader.getRootData().server.bootTime);
        } else {
            logger.info("Server boot time already set: {}", dataFileLoader.getRootData().server.bootTime);
        }

        // 注册事件监听器 - 修正此处，添加 configFileLoader
        this.proxyServer.getEventManager().register(this, new PlayerActivityListener(proxyServer, configFileLoader, languageFileLoader, dataFileLoader, miniMessage, this, logger));

        // 注册命令
        CommandRegistrar commandRegistrar = new CommandRegistrar(proxyServer.getCommandManager(), proxyServer, languageFileLoader, miniMessage);
        HelpCommand helpCommandInstance = new HelpCommand(languageFileLoader, miniMessage);
        ReloadCommand reloadCommandInstance = new ReloadCommand(configFileLoader, languageFileLoader, miniMessage);
        commandRegistrar.setHelpCommand(helpCommandInstance);
        commandRegistrar.setReloadCommand(reloadCommandInstance);
        commandRegistrar.registerCommands();
        new ServerListCommand(proxyServer, configFileLoader, languageFileLoader, miniMessage, commandRegistrar);
        new ServerInfoCommand(proxyServer, languageFileLoader, miniMessage, configFileLoader, commandRegistrar);
        new PluginListCommand(proxyServer, languageFileLoader, miniMessage, commandRegistrar);
        new PluginInfoCommand(proxyServer, languageFileLoader, miniMessage, commandRegistrar);

        logger.info("V-Monitor enabled!");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        logger.info("V-Monitor is disabling...");
        logger.info("Saving player data...");
        dataFileLoader.savePlayerData();
        logger.info("Saved player data!");
        logger.info("V-Monitor disabled!");
    }

    /**
     * 设置插件初始化所需的文件和目录。
     * 确保数据目录、语言目录和必要文件都存在，如果不存在则从 JAR 包复制默认文件。
     * @return 如果所有文件和目录都设置成功则返回 true，否则返回 false。
     */
    private boolean setupInitialPluginFiles() {
        boolean firstLoad = !Files.exists(dataDirectory) || !Files.exists(dataDirectory.resolve(CONFIG_FILE_NAME));

        if (firstLoad) {
            logger.info("First load detected, initializing plugin");
        }

        // Create data directory if it doesn't exist
        try {
            if (!Files.exists(dataDirectory)) {
                Files.createDirectories(dataDirectory);
                logger.info("Created plugin data directory!"); // 简化日志
            }
        } catch (IOException e) {
            logger.error("Failed to create plugin data directory: {}", dataDirectory.toAbsolutePath(), e);
            return false;
        }

        // Copy default config.yml
        Path configFilePath = dataDirectory.resolve(CONFIG_FILE_NAME);
        // 修改 copyDefaultResource 的行为，使其输出更符合期望的日志
        if (!copyDefaultResource(CONFIG_FILE_NAME, configFilePath, "Configuration file")) {
            return false;
        }

        // Copy default data.json
        Path dataFilePath = dataDirectory.resolve(PLAYER_DATA_FILE_NAME);
        if (!copyDefaultResource(PLAYER_DATA_FILE_NAME, dataFilePath, "Data file")) {
            return false;
        }

        // Ensure the lang directory exists
        Path langDirectory = dataDirectory.resolve(LANG_FOLDER_NAME);
        try {
            if (!Files.exists(langDirectory)) {
                Files.createDirectories(langDirectory);
                logger.info("Created language directory!"); // 简化日志
            }
        } catch (IOException e) {
            logger.error("Failed to create plugin language directory: {}", langDirectory.toAbsolutePath(), e);
            return false;
        }

        // Copy all supported language files
        for (String langFile : SUPPORTED_LANGUAGE_FILES) {
            Path targetLangFilePath = langDirectory.resolve(langFile);
            String resourcePathInJar = LANG_FOLDER_NAME + "/" + langFile;
            // 为每个语言文件使用更通用的日志
            if (!copyDefaultResource(resourcePathInJar, targetLangFilePath, "Language file")) {
                logger.error("Failed to copy language file: {}", langFile);
                return false;
            }
        }

        if (firstLoad) {
            logger.info("The plugin has been initialized and is loading.");
        }

        return true;
    }

    /**
     * 辅助方法：处理文件加载和完整性检查。
     * 如果文件加载失败（抛出自定义异常），则尝试修复：重命名损坏文件并复制默认文件。
     *
     * @param loader 要调用的 Loader 实例 (ConfigFileLoader, LanguageFileLoader, DataFileLoader)
     * @param fileName 文件的名称 (例如 "config.yml", "zh_cn.yml", "data.json")
     * @param subDirectoryName 如果文件在子目录中 (例如 "lang")，则提供子目录名，否则为 null
     * @param fileType 用于日志记录的文件类型描述 (例如 "Configuration", "Language", "Data")
     * @return 如果文件成功加载并验证（或成功修复并加载）则返回 true，否则返回 false。
     */
    private boolean loadAndCheckFile(Object loader, String fileName, String subDirectoryName, String fileType) {
        Path filePath;
        String resourcePathInJar;

        if (subDirectoryName != null && !subDirectoryName.isEmpty()) {
            filePath = dataDirectory.resolve(subDirectoryName).resolve(fileName);
            resourcePathInJar = subDirectoryName + "/" + fileName;
        } else {
            filePath = dataDirectory.resolve(fileName);
            resourcePathInJar = fileName;
        }
        try {
            // 尝试加载文件
            if (loader instanceof ConfigFileLoader) {
                ((ConfigFileLoader) loader).loadConfig();
            } else if (loader instanceof LanguageFileLoader) {
                ((LanguageFileLoader) loader).loadLanguage();
            } else if (loader instanceof DataFileLoader) {
                ((DataFileLoader) loader).loadPlayerData();
            }
            logger.info("{} file loaded!", fileType); // 简化加载成功日志
            return true; // 文件加载成功
        } catch (ConfigFileLoader.ConfigLoadException | LanguageFileLoader.LanguageLoadException | DataFileLoader.PlayerDataLoadException e) {
            // 更新此处引用
            logger.error("Failed to load and parse '{}' due to: {}. Attempting to repair...", filePath.getFileName(), e.getMessage());
            logger.error("Stack trace for load failure:", e);
            // 文件损坏，执行修复逻辑：重命名旧文件并复制默认文件
            String timestamp = LocalDateTime.now().format(errorFileTimeFormatter);
            String originalFileName = filePath.getFileName().toString();
            String fileExtension = "";
            String baseFileName = originalFileName;
            int dotIndex = originalFileName.lastIndexOf('.');
            if (dotIndex > 0 && dotIndex < originalFileName.length() - 1) {
                fileExtension = originalFileName.substring(dotIndex);
                baseFileName = originalFileName.substring(0, dotIndex);
            }
            Path corruptedFilePath = filePath.getParent().resolve(baseFileName + "-" + timestamp + fileExtension + ".err");
            try {
                if (Files.exists(filePath)) {
                    Files.move(filePath, corruptedFilePath, StandardCopyOption.REPLACE_EXISTING);
                    logger.warn("Corrupted file moved to: {}", corruptedFilePath.toAbsolutePath());
                } else {
                    logger.warn("Original file '{}' was expected to exist for repair but was not found. Copying new default.", filePath.getFileName());
                }
                // 复制默认资源
                // 修改 copyDefaultResource 的日志行为，使其输出更符合期望的日志
                if (copyDefaultResource(resourcePathInJar, filePath, "Default " + fileType + " file")) { // 尝试复制默认文件
                    logger.info("Successfully copied default '{}' for repair.", filePath.getFileName());
                    // 再次尝试加载修复后的文件
                    try {
                        if (loader instanceof ConfigFileLoader) {
                            ((ConfigFileLoader) loader).loadConfig();
                        } else if (loader instanceof LanguageFileLoader) {
                            ((LanguageFileLoader) loader).loadLanguage();
                        } else if (loader instanceof DataFileLoader) {
                            ((DataFileLoader) loader).loadPlayerData();
                        }
                        logger.info("Successfully loaded repaired file: {}", filePath.getFileName());
                        return true;
                    } catch (ConfigFileLoader.ConfigLoadException | LanguageFileLoader.LanguageLoadException | DataFileLoader.PlayerDataLoadException reloadedE) {
                        logger.error("Failed to load repaired file '{}' after copying default: {}", filePath.getFileName(), reloadedE.getMessage());
                        return false;
                    }
                } else {
                    logger.error("Failed to copy default '{}' for repair. Plugin will not enable properly for this file.", filePath.getFileName());
                    return false;
                }
            } catch (IOException moveE) {
                logger.error("Failed to move corrupted file '{}': {}", filePath.getFileName(), moveE.getMessage());
                return false;
            }
        }
    }

    /**
     * 辅助方法：从 JAR 复制默认资源到目标路径。
     * 根据文件是否存在来调整日志输出。
     *
     * @param resourcePathInJar JAR 包内的资源路径 (例如 "config.yml" 或 "lang/zh_cn.yml")
     * @param targetPath 目标文件系统路径
     * @param fileType 用于日志记录的文件类型描述 (例如 "Configuration file", "Language file")
     * @return 成功复制或文件已存在则返回 true，否则返回 false。
     */
    private boolean copyDefaultResource(String resourcePathInJar, Path targetPath, String fileType) {
        if (!Files.exists(targetPath)) {
            logger.info("{} not found, trying to copy defaults from JAR...", fileType); // 更改这里的日志
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePathInJar)) {
                if (in != null) {
                    Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    // 复制成功后不再额外输出INFO，因为前面的“trying to copy”已经包含了信息
                    return true;
                } else {
                    logger.error("Default resource '{}' NOT FOUND in plugin JAR. This is unexpected. Please check plugin JAR integrity.", resourcePathInJar);
                    return false;
                }
            } catch (IOException e) {
                logger.error("Failed to copy default resource '{}' to '{}': {}", resourcePathInJar, targetPath.toAbsolutePath(), e.getMessage());
                return false;
            }
        } else {
            logger.debug("{} '{}' already exists. Skipping initial copy.", fileType, targetPath.getFileName()); // 改为 debug 级别或不输出
            return true; // 文件已存在，视为成功
        }
    }

    // ====================================================================
    // 以下是原有的Getter方法，保持不变
    // ====================================================================
    public ConfigFileLoader getConfigFileLoader() {
        return configFileLoader;
    }

    public LanguageFileLoader getLanguageLoader() {
        return languageFileLoader;
    }

    public DataFileLoader getPlayerDataLoader() {
        return dataFileLoader;
    }

    public Logger getLogger() {
        return logger;
    }

    public ProxyServer getProxyServer() {
        return proxyServer;
    }

    public MiniMessage getMiniMessage() {
        return miniMessage;
    }
}