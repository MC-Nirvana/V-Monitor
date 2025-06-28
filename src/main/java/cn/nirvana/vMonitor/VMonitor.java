package cn.nirvana.vMonitor;

import cn.nirvana.vMonitor.command.*;

import cn.nirvana.vMonitor.loader.ConfigFileLoader;
import cn.nirvana.vMonitor.loader.DataFileLoader;
import cn.nirvana.vMonitor.loader.LanguageFileLoader;

import cn.nirvana.vMonitor.listener.PlayerActivityListener;

import cn.nirvana.vMonitor.util.TimeUtil;

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

@Plugin(id = "v-monitor", name = "V-Monitor", version = "1.3.0",
        description = "A powerful monitoring plugin for Velocity proxy servers.",
        authors = {"MC_Nirvana"})
public class VMonitor {

    private final ProxyServer proxyServer;
    private final Logger logger;
    private final Path dataDirectory;
    private final MiniMessage miniMessage;

    private ConfigFileLoader configFileLoader;
    private LanguageFileLoader languageFileLoader;
    private DataFileLoader dataFileLoader;
    private CommandRegistrar commandRegistrar;

    @Inject
    public VMonitor(ProxyServer proxyServer, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxyServer = proxyServer;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.miniMessage = MiniMessage.miniMessage();
        logger.info("V-Monitor plugin initializing...");
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        boolean anyFileOrDirCreated = false;
        try {
            if (!Files.exists(dataDirectory)) {
                Files.createDirectories(dataDirectory);
                logger.info("Created plugin data directory!");
                anyFileOrDirCreated = true;
            } else {
                logger.debug("Plugin data directory already exists: {}", dataDirectory);
            }
        } catch (IOException e) {
            logger.error("Failed to create data directory: {}", dataDirectory, e);
            return;
        }
        boolean configCopied = copyDefaultResource("config.yml", dataDirectory.resolve("config.yml"), "Configuration");
        if (configCopied) {
            anyFileOrDirCreated = true;
        }
        try {
            configFileLoader = new ConfigFileLoader(logger, dataDirectory);
            configFileLoader.loadConfig();
            logger.info("Configuration file loaded!");
        } catch (ConfigFileLoader.ConfigLoadException e) {
            logger.error("Failed to load config.yml: {}", e.getMessage(), e);
        }
        Path langDirectory = dataDirectory.resolve("lang");
        try {
            if (!Files.exists(langDirectory)) {
                Files.createDirectories(langDirectory);
                logger.info("Created language directory!");
                anyFileOrDirCreated = true;
            } else {
                logger.debug("Language directory already exists: {}", langDirectory);
            }
        } catch (IOException e) {
            logger.error("Failed to create language directory: {}", langDirectory, e);
        }
        String languageKey = (configFileLoader != null && configFileLoader.getLanguageKey() != null) ? configFileLoader.getLanguageKey() : "zh_cn";
        boolean langCopied = copyDefaultResource("lang/" + languageKey + ".yml", langDirectory.resolve(languageKey + ".yml"), "Language");
        if (langCopied) {
            anyFileOrDirCreated = true;
        }
        try {
            languageFileLoader = new LanguageFileLoader(logger, dataDirectory, configFileLoader);
            languageFileLoader.loadLanguage();
            logger.info("Language file loaded!");
        } catch (LanguageFileLoader.LanguageLoadException e) {
            logger.error("Failed to load language file: {}", e.getMessage(), e);
        }
        try {
            dataFileLoader = new DataFileLoader(logger, dataDirectory);
            dataFileLoader.loadPlayerData();
            if (!Files.exists(dataDirectory.resolve("data.json"))) {
                anyFileOrDirCreated = true;
            }
            logger.info("Data file loaded!");
        } catch (DataFileLoader.PlayerDataLoadException e) {
            logger.error("Failed to load player data: {}", e.getMessage(), e);
        }
        if (anyFileOrDirCreated) {
            logger.info("First load detected, initializing plugin");
        }
        logger.info("The plugin has been initialized and is loading.");
        if (dataFileLoader != null && dataFileLoader.getRootData() != null && dataFileLoader.getRootData().server != null) {
            if (dataFileLoader.getRootData().server.bootTime == null || dataFileLoader.getRootData().server.bootTime.isEmpty()) {
                dataFileLoader.getRootData().server.bootTime = TimeUtil.getCurrentDateString();
                dataFileLoader.savePlayerData();
                logger.info("Server boot time initialized: {}", dataFileLoader.getRootData().server.bootTime);
            }
        } else {
            logger.warn("DataFileLoader or its root data is not properly initialized. Skipping server boot time setup.");
        }
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
        logger.info("All commands registered.");
        proxyServer.getEventManager().register(this, new PlayerActivityListener(proxyServer, configFileLoader, languageFileLoader, dataFileLoader, miniMessage, this, logger));
        logger.info("All event listeners registered.");
        logger.info("V-Monitor enabled!");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (dataFileLoader != null) {
            dataFileLoader.savePlayerData();
            logger.info("Player data saved on shutdown.");
        }
        logger.info("V-Monitor plugin shut down.");
    }

    private boolean copyDefaultResource(String resourcePathInJar, Path targetPath, String fileDescription) {
        if (!Files.exists(targetPath)) {
            logger.info("{} file not found, trying to copy defaults from JAR...", fileDescription);
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePathInJar)) {
                if (in != null) {
                    Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
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
            return false;
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