package cn.nirvana.vMonitor;

import cn.nirvana.vMonitor.command.*;
import cn.nirvana.vMonitor.loader.*;
import cn.nirvana.vMonitor.listener.PlayerActivityListener;
import cn.nirvana.vMonitor.module.*; // 导入所有新的模块类
import cn.nirvana.vMonitor.util.CommandUtil; // 导入 CommandUtil

import com.google.inject.Inject;

import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.plugin.PluginContainer; // 导入 PluginContainer

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
        description = "Monitor your Velocity Proxy player activity and server statistics.",
        authors = {"MC_Nirvana"}
)
public class VMonitor {

    private final ProxyServer proxyServer;
    private final Logger logger;
    private final Path dataDirectory;
    private final PluginContainer pluginContainer;
    private ConfigFileLoader configFileLoader;
    private LanguageFileLoader languageFileLoader;
    private DataFileLoader dataFileLoader;
    private MiniMessage miniMessage;

    @Inject
    public VMonitor(ProxyServer proxyServer, Logger logger, @DataDirectory Path dataDirectory, PluginContainer pluginContainer) {
        this.proxyServer = proxyServer;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.pluginContainer = pluginContainer;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        logger.info("V-Monitor is initializing...");

        if (!Files.exists(dataDirectory)) {
            try {
                Files.createDirectories(dataDirectory);
                logger.info("Created data directory: {}", dataDirectory.toAbsolutePath());
            } catch (IOException e) {
                logger.error("Failed to create data directory: {}", dataDirectory.toAbsolutePath(), e);
                return;
            }
        }

        boolean configCopied = copyDefaultResource("config.yml", "config.yml", "config file");
        boolean langFolderCopied = copyDefaultLanguageFiles("lang", "lang folder");

        if (!configCopied || !langFolderCopied) {
            logger.error("Failed to copy default configuration or language files. Plugin may not function correctly.");
        }

        this.miniMessage = MiniMessage.miniMessage();
        this.configFileLoader = new ConfigFileLoader(logger, dataDirectory);
        configFileLoader.loadConfig();
        this.languageFileLoader = new LanguageFileLoader(logger, dataDirectory, configFileLoader);
        languageFileLoader.loadLanguage();

        this.dataFileLoader = new DataFileLoader(logger, dataDirectory);

        try {
            dataFileLoader.loadPlayerData();
        } catch (DataFileLoader.PlayerDataLoadException e) {
            logger.error("Failed to load player data: {}", e.getMessage());
        }


        CommandUtil commandUtil = new CommandUtil(proxyServer.getCommandManager(), logger, pluginContainer);

        HelpModule helpModule = new HelpModule(languageFileLoader, miniMessage);
        ServerListModule serverListModule = new ServerListModule(proxyServer, configFileLoader, languageFileLoader, miniMessage);
        ServerInfoModule serverInfoModule = new ServerInfoModule(proxyServer, languageFileLoader, miniMessage, configFileLoader);
        PluginListModule pluginListModule = new PluginListModule(proxyServer, languageFileLoader, miniMessage);
        PluginInfoModule pluginInfoModule = new PluginInfoModule(proxyServer, languageFileLoader, miniMessage);
        ReloadModule reloadModule = new ReloadModule(configFileLoader, languageFileLoader, miniMessage);

        new CoreCommand(languageFileLoader, miniMessage, commandUtil, helpModule);
        new HelpCommand(commandUtil, helpModule);
        new ReloadCommand(commandUtil, reloadModule);
        new ServerCommand(commandUtil, proxyServer, languageFileLoader, miniMessage, serverListModule, serverInfoModule, configFileLoader, helpModule);
        new PluginCommand(commandUtil, proxyServer, languageFileLoader, miniMessage, pluginListModule, pluginInfoModule, helpModule);

        commandUtil.registerAllCommands();

        proxyServer.getEventManager().register(this, new PlayerActivityListener(proxyServer, configFileLoader, languageFileLoader, dataFileLoader, miniMessage, this, logger));

        logger.info("V-Monitor initialization complete.");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        logger.info("V-Monitor is shutting down...");
        if (dataFileLoader != null) {
            dataFileLoader.savePlayerData();
        }
        logger.info("V-Monitor shutdown complete.");
    }

    private boolean copyDefaultLanguageFiles(String resourcePathInJar, String fileType) {
        Path targetFolder = dataDirectory.resolve(resourcePathInJar);
        if (!Files.exists(targetFolder)) {
            try {
                Files.createDirectories(targetFolder);
                logger.info("Created language directory: {}", targetFolder.toAbsolutePath());
            } catch (IOException e) {
                logger.error("Failed to create language directory '{}': {}", targetFolder.toAbsolutePath(), e.getMessage());
                return false;
            }
        }

        String[] langFiles = {"en-US.yml", "zh-CN.yml"};

        boolean allCopied = true;
        for (String langFile : langFiles) {
            String resourcePath = resourcePathInJar + "/" + langFile;
            Path targetPath = targetFolder.resolve(langFile);
            // 修正：将 copyResource 改为 copyDefaultResource
            if (!copyDefaultResource(resourcePath, langFile, "language file")) {
                allCopied = false;
            }
        }
        return allCopied;
    }

    private boolean copyDefaultResource(String resourcePathInJar, String fileName, String fileType) {
        Path targetPath = dataDirectory.resolve(fileName);
        if (!Files.exists(targetPath)) {
            logger.info("Default {} ('{}') not found, trying to copy from JAR...", fileType, targetPath.getFileName());
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
            logger.debug("{} '{}' already exists. Skipping initial copy.", fileType, targetPath.getFileName());
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