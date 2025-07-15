package cn.nirvana.vMonitor;

import cn.nirvana.vMonitor.command.*;
import cn.nirvana.vMonitor.loader.*;
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

import java.util.Set;

@Plugin(
        id = "v-monitor",
        name = "V-Monitor",
        version = "1.3.0",
        url = "https://github.com/MC-Nirvana/V-Monitor",
        description = "Monitor your Velocity Proxy player activity, server status, and generate daily/weekly reports.",
        authors = {"MC_Nirvana"}
 )
public class VMonitor {
    private final ProxyServer proxyServer;
    private final Logger logger;
    private final Path dataDirectory;
    private LanguageFileLoader languageFileLoader;
    private ConfigFileLoader configFileLoader;
    private DataFileLoader dataFileLoader;
    private MiniMessage miniMessage;

    private static final Set<String> SUPPORTED_LANGUAGE_FILES = Set.of("zh_cn.yml", "zh_tw.yml", "en_us.yml");
    private static final String CONFIG_FILE_NAME = "config.yml";
    private static final String PLAYER_DATA_FILE_NAME = "data.json";
    private static final String LANG_FOLDER_NAME = "lang";

    private final DateTimeFormatter errorFileTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    @Inject
    public VMonitor(ProxyServer proxyServer, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxyServer = proxyServer;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        logger.info("V-Monitor is enabling...");
        if (!setupInitialPluginFiles()) {
            logger.error("Failed to setup initial plugin files. V-Monitor will not enable properly.");
            return;
        }
        this.miniMessage = MiniMessage.miniMessage();
        this.configFileLoader = new ConfigFileLoader(logger, dataDirectory); // 实例化 ConfigFileLoader
        if (!loadAndCheckFile(configFileLoader, CONFIG_FILE_NAME, null, "Configuration")) { // 添加类型参数
            logger.error("Failed to load and validate config file. V-Monitor will not enable properly.");
            return;
        }
        this.languageFileLoader = new LanguageFileLoader(logger, dataDirectory, configFileLoader);
        String defaultLanguageKeyFromConfig = configFileLoader.getLanguageKey();
        if (!loadAndCheckFile(languageFileLoader, defaultLanguageKeyFromConfig + ".yml", LANG_FOLDER_NAME, "Language")) { // 添加类型参数
            logger.error("Failed to load and validate language file. V-Monitor will not enable properly.");
            return;
        }
        this.dataFileLoader = new DataFileLoader(logger, dataDirectory);
        if (!loadAndCheckFile(dataFileLoader, PLAYER_DATA_FILE_NAME, null, "Data")) { // 添加类型参数
            logger.error("Failed to load and validate player data file. V-Monitor will not enable properly.");
            return;
        }
        if (dataFileLoader.getRootData().server.bootTime == null || dataFileLoader.getRootData().server.bootTime.isEmpty()) {
            dataFileLoader.getRootData().server.bootTime = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            dataFileLoader.savePlayerData();
            logger.info("Server boot time initialized: {}", dataFileLoader.getRootData().server.bootTime);
        } else {
            logger.info("Server boot time already set: {}", dataFileLoader.getRootData().server.bootTime);
        }
        this.proxyServer.getEventManager().register(this, new PlayerActivityListener(proxyServer, configFileLoader, languageFileLoader, dataFileLoader, miniMessage, this, logger));
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

    private boolean setupInitialPluginFiles() {
        boolean firstLoad = !Files.exists(dataDirectory) || !Files.exists(dataDirectory.resolve(CONFIG_FILE_NAME));
        if (firstLoad) {
            logger.info("First load detected, initializing plugin");
        }
        try {
            if (!Files.exists(dataDirectory)) {
                Files.createDirectories(dataDirectory);
                logger.info("Created plugin data directory!");
            }
        } catch (IOException e) {
            logger.error("Failed to create plugin data directory: {}", dataDirectory.toAbsolutePath(), e);
            return false;
        }
        Path configFilePath = dataDirectory.resolve(CONFIG_FILE_NAME);
        if (!copyDefaultResource(CONFIG_FILE_NAME, configFilePath, "Configuration file")) {
            return false;
        }
        Path dataFilePath = dataDirectory.resolve(PLAYER_DATA_FILE_NAME);
        if (!copyDefaultResource(PLAYER_DATA_FILE_NAME, dataFilePath, "Data file")) {
            return false;
        }
        Path langDirectory = dataDirectory.resolve(LANG_FOLDER_NAME);
        try {
            if (!Files.exists(langDirectory)) {
                Files.createDirectories(langDirectory);
                logger.info("Created language directory!");
            }
        } catch (IOException e) {
            logger.error("Failed to create plugin language directory: {}", langDirectory.toAbsolutePath(), e);
            return false;
        }
        for (String langFile : SUPPORTED_LANGUAGE_FILES) {
            Path targetLangFilePath = langDirectory.resolve(langFile);
            String resourcePathInJar = LANG_FOLDER_NAME + "/" + langFile;
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
            if (loader instanceof ConfigFileLoader) {
                ((ConfigFileLoader) loader).loadConfig();
            } else if (loader instanceof LanguageFileLoader) {
                ((LanguageFileLoader) loader).loadLanguage();
            } else if (loader instanceof DataFileLoader) {
                ((DataFileLoader) loader).loadPlayerData();
            }
            logger.info("{} file loaded!", fileType);
            return true;
        } catch (ConfigFileLoader.ConfigLoadException | LanguageFileLoader.LanguageLoadException | DataFileLoader.PlayerDataLoadException e) {
            logger.error("Failed to load and parse '{}' due to: {}. Attempting to repair...", filePath.getFileName(), e.getMessage());
            logger.error("Stack trace for load failure:", e);
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
                if (copyDefaultResource(resourcePathInJar, filePath, "Default " + fileType + " file")) {
                    logger.info("Successfully copied default '{}' for repair.", filePath.getFileName());
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

    private boolean copyDefaultResource(String resourcePathInJar, Path targetPath, String fileType) {
        if (!Files.exists(targetPath)) {
            logger.info("{} not found, trying to copy defaults from JAR...", fileType);
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePathInJar)) {
                if (in != null) {
                    Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
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
            return true;
        }
    }

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