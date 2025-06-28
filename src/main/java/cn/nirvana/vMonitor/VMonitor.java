package cn.nirvana.vMonitor;

import cn.nirvana.vMonitor.command.*;
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
import java.time.format.DateTimeFormatter;

import java.util.Set;

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
        try {
            if (!Files.exists(dataDirectory)) {
                Files.createDirectories(dataDirectory);
                logger.info("Created data directory: {}", dataDirectory);
            }
        } catch (IOException e) {
            logger.error("Failed to create data directory: {}", dataDirectory, e);
            return;
        }
        if (!copyDefaultResource("config.yml", dataDirectory.resolve("config.yml"))) {
            logger.error("Failed to copy default config.yml. Plugin may not function correctly.");
        }
        try {
            configFileLoader = new ConfigFileLoader(logger, dataDirectory);
            configFileLoader.loadConfig();
        } catch (ConfigFileLoader.ConfigLoadException e) {
            logger.error("Failed to load config.yml: {}", e.getMessage(), e);
        }
        Path langDirectory = dataDirectory.resolve("lang");
        try {
            if (!Files.exists(langDirectory)) {
                Files.createDirectories(langDirectory);
                logger.info("Created language directory: {}", langDirectory);
            }
        } catch (IOException e) {
            logger.error("Failed to create language directory: {}", langDirectory, e);
        }
        String defaultLangFile = "lang/" + (configFileLoader != null ? configFileLoader.getLanguageKey() : "zh_cn") + ".yml";
        if (!copyDefaultResource(defaultLangFile, langDirectory.resolve((configFileLoader != null ? configFileLoader.getLanguageKey() : "zh_cn") + ".yml"))) {
            logger.error("Failed to copy default language file '{}'. Language messages may not load correctly.", defaultLangFile);
        }
        try {
            languageFileLoader = new LanguageFileLoader(logger, dataDirectory, configFileLoader);
            languageFileLoader.loadLanguage(); // 尝试加载语言文件
        } catch (LanguageFileLoader.LanguageLoadException e) {
            logger.error("Failed to load language file: {}", e.getMessage(), e);
        }
        try {
            dataFileLoader = new DataFileLoader(logger, dataDirectory);
            dataFileLoader.loadPlayerData();
        } catch (DataFileLoader.PlayerDataLoadException e) {
            logger.error("Failed to load player data: {}", e.getMessage(), e);
        }
        if (dataFileLoader != null && dataFileLoader.getRootData() != null && dataFileLoader.getRootData().server != null) {
            if (dataFileLoader.getRootData().server.bootTime == null || dataFileLoader.getRootData().server.bootTime.isEmpty()) {
                dataFileLoader.getRootData().server.bootTime = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                dataFileLoader.savePlayerData();
                logger.info("Server boot time initialized: {}", dataFileLoader.getRootData().server.bootTime);
            } else {
                logger.info("Server boot time already set: {}", dataFileLoader.getRootData().server.bootTime);
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
        proxyServer.getEventManager().register(this, new PlayerActivityListener(proxyServer, configFileLoader, languageFileLoader, dataFileLoader, miniMessage, this, logger));
        logger.info("All event listeners registered.");
        logger.info("V-Monitor plugin initialized successfully!");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (dataFileLoader != null) {
            dataFileLoader.savePlayerData();
            logger.info("Player data saved on shutdown.");
        }
        logger.info("V-Monitor plugin shut down.");
    }

    private boolean copyDefaultResource(String resourcePathInJar, Path targetPath) {
        if (!Files.exists(targetPath)) {
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
            logger.debug("File '{}' already exists. Skipping initial copy.", targetPath.getFileName());
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