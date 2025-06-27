// File: src/main/java/cn/nirvana/vMonitor/VMonitor.java
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set; // 导入 Set

@Plugin(id = "v-monitor", name = "V-Monitor", version = "1.3.0", url = "https://github.com/MC-Nirvana/V-Monitor", description = "Monitor your Velocity Proxy player activity, server status, and generate daily/weekly reports.", authors = {"MC_Nirvana"})
public class VMonitor {
    private final ProxyServer proxyServer;
    private final Logger logger;
    private final Path dataDirectory;
    private LanguageFileLoader languageFileLoader;
    private ConfigFileLoader configFileLoader;
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

    private final DateTimeFormatter errorFileTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss"); // 用于错误文件重命名

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
        this.configFileLoader = new ConfigFileLoader(logger, dataDirectory);
        if (!loadAndCheckFile(configFileLoader, CONFIG_FILE_NAME, null)) { // null 表示没有子目录
            logger.error("Failed to load and validate config file. V-Monitor will not enable properly.");
            return;
        }

        // 3. 检查并加载语言文件 (依赖于 configFileLoader)
        this.languageFileLoader = new LanguageFileLoader(logger, dataDirectory, configFileLoader);
        // <<<<<<<<<<<<< 修正这里: 从 configFileLoader 获取语言键 >>>>>>>>>>>>>
        String defaultLanguageKeyFromConfig = configFileLoader.getLanguageKey();
        if (!loadAndCheckFile(languageFileLoader, defaultLanguageKeyFromConfig + ".yml", LANG_FOLDER_NAME)) { // <-- 使用获取到的语言键
            logger.error("Failed to load and validate language file. V-Monitor will not enable properly.");
            return;
        }

        // 4. 检查并加载玩家数据文件
        this.dataFileLoader = new DataFileLoader(logger, dataDirectory);
        if (!loadAndCheckFile(dataFileLoader, PLAYER_DATA_FILE_NAME, null)) {
            logger.error("Failed to load and validate player data file. V-Monitor will not enable properly.");
            return;
        }
        this.dataFileLoader.checkAndSetServerBootTime(); // 数据加载并验证后设置启动时间

        // 注册事件监听器
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
     * 辅助方法：处理文件加载和完整性检查。
     * 如果文件加载失败（抛出自定义异常），则尝试修复：重命名损坏文件并复制默认文件。
     *
     * @param loader 要调用的 Loader 实例 (ConfigFileLoader, LanguageFileLoader, DataFileLoader)
     * @param fileName 文件的名称 (例如 "config.yml", "zh_cn.yml", "data.json")
     * @param subDirectoryName 如果文件在子目录中 (例如 "lang")，则提供子目录名，否则为 null
     * @return 如果文件成功加载并验证（或成功修复并加载）则返回 true，否则返回 false。
     */
    private boolean loadAndCheckFile(Object loader, String fileName, String subDirectoryName) {
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
            return true; // 文件加载成功
        } catch (ConfigFileLoader.ConfigLoadException | LanguageFileLoader.LanguageLoadException | DataFileLoader.PlayerDataLoadException e) { // 更新此处引用
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

                // 复制新的默认文件
                if (copyResource(resourcePathInJar, filePath)) {
                    logger.info("New default '{}' has been copied due to corruption.", filePath.getFileName());
                    // 重新尝试加载新的默认文件
                    try {
                        if (loader instanceof ConfigFileLoader) {
                            ((ConfigFileLoader) loader).loadConfig();
                        } else if (loader instanceof LanguageFileLoader) {
                            ((LanguageFileLoader) loader).loadLanguage();
                        } else if (loader instanceof DataFileLoader) {
                            ((DataFileLoader) loader).loadPlayerData();
                        }
                        logger.info("Successfully loaded repaired '{}'.", filePath.getFileName());
                        return true;
                    } catch (ConfigFileLoader.ConfigLoadException | LanguageFileLoader.LanguageLoadException | DataFileLoader.PlayerDataLoadException reLoadE) { // 更新此处引用
                        logger.error("Failed to load newly copied default '{}'. This indicates a problem with the default resource or loader logic: {}", filePath.getFileName(), reLoadE.getMessage());
                        logger.error("Stack trace for re-load failure:", reLoadE);
                        return false; // 即使复制了默认文件，新默认文件也无法加载，插件无法正常工作
                    }
                } else {
                    logger.error("Failed to copy default resource '{}' after detecting corruption. Plugin cannot proceed.", resourcePathInJar);
                    return false; // 无法复制默认文件，插件无法正常工作
                }
            } catch (IOException moveE) {
                logger.error("Failed to rename corrupted file '{}' or copy new default: {}", filePath.getFileName(), moveE.getMessage());
                logger.error("Stack trace for rename/copy failure:", moveE);
                return false; // 无法修复，插件无法正常工作
            }
        } catch (Exception e) {
            // 捕获 loadAndCheckFile 中可能发生的任何其他意外异常
            logger.error("An unexpected error occurred during file check and load for '{}': {}", filePath.getFileName(), e.getMessage());
            logger.error("Stack trace for unexpected error:", e);
            return false;
        }
    }

    /**
     * 设置插件所需的文件和目录。
     * 包括创建数据目录、语言文件目录，并从JAR中复制默认的配置文件、语言文件和玩家数据文件。
     * 此方法仅确保文件存在，不负责内容验证。内容验证由 loadAndCheckFile 负责。
     *
     * @return 如果所有文件和目录都设置成功则返回 true，否则返回 false。
     */
    private boolean setupInitialPluginFiles() {
        // 1. 创建插件数据目录 (plugins/v-monitor)
        try {
            if (!Files.exists(dataDirectory)) {
                Files.createDirectories(dataDirectory);
                logger.info("Created plugin data directory: {}", dataDirectory.toAbsolutePath());
            } else {
                logger.info("Plugin data directory already exists: {}", dataDirectory.toAbsolutePath());
            }
        } catch (IOException e) {
            logger.error("Failed to create plugin data directory {}: {}", dataDirectory.toAbsolutePath(), e.getMessage());
            return false;
        }

        // 2. 创建语言文件子目录 (plugins/v-monitor/lang)
        Path langDirectory = dataDirectory.resolve(LANG_FOLDER_NAME);
        try {
            if (!Files.exists(langDirectory)) {
                Files.createDirectories(langDirectory);
                logger.info("Created language directory: {}", langDirectory.toAbsolutePath());
            } else {
                logger.info("Language directory already exists: {}", langDirectory.toAbsolutePath());
            }
        } catch (IOException e) {
            logger.error("Failed to create language directory {}: {}", langDirectory.toAbsolutePath(), e.getMessage());
            return false;
        }

        // 3. 复制配置文件 (config.yml) - 仅当不存在时复制
        if (!copyResource(CONFIG_FILE_NAME, dataDirectory.resolve(CONFIG_FILE_NAME))) {
            logger.error("Failed to ensure default config file exists: {}", CONFIG_FILE_NAME);
            return false;
        }

        // 4. 复制所有语言文件 - 仅当不存在时复制
        for (String langFileName : SUPPORTED_LANGUAGE_FILES) {
            Path targetLangFile = langDirectory.resolve(langFileName);
            String resourcePathInJar = LANG_FOLDER_NAME + "/" + langFileName;
            if (!copyResource(resourcePathInJar, targetLangFile)) {
                logger.error("Failed to ensure default language file exists: {}", langFileName);
                // 语言文件非核心，即使失败也可能允许插件继续，但这里我们选择严格一点
                return false;
            }
        }

        // 5. 复制玩家数据文件 (data.json) - 仅当不存在时复制
        if (!copyResource(PLAYER_DATA_FILE_NAME, dataDirectory.resolve(PLAYER_DATA_FILE_NAME))) {
            logger.error("Failed to ensure default player data file exists: {}", PLAYER_DATA_FILE_NAME);
            return false;
        }

        logger.info("Initial plugin files and directories existence ensured.");
        return true;
    }

    /**
     * 辅助方法：从JAR中复制资源到目标路径。
     * 如果目标文件不存在，则复制；如果存在，则不进行操作。
     *
     * @param resourcePathInJar JAR内资源的路径（例如 "config.yml" 或 "lang/zh_cn.yml"）
     * @param targetPath 目标文件系统路径
     * @return 复制成功或文件已存在则返回 true，否则返回 false。
     */
    private boolean copyResource(String resourcePathInJar, Path targetPath) {
        if (!Files.exists(targetPath)) {
            logger.info("File '{}' not found, attempting to copy default from JAR...", targetPath.getFileName());
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePathInJar)) {
                if (in != null) {
                    Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING); // 使用 REPLACE_EXISTING 确保覆盖，即使目标存在但为空
                    logger.info("Default '{}' copied successfully.", targetPath.getFileName());
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
            logger.debug("File '{}' already exists. Skipping initial copy.", targetPath.getFileName());
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