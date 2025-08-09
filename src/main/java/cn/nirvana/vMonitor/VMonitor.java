package cn.nirvana.vMonitor;

import cn.nirvana.vMonitor.command.*;
import cn.nirvana.vMonitor.command_module.*;
import cn.nirvana.vMonitor.exceptions.*;
import cn.nirvana.vMonitor.listener.*;
import cn.nirvana.vMonitor.loader.*;
import cn.nirvana.vMonitor.util.*;

import com.google.inject.Inject;

import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.plugin.PluginContainer;

import net.kyori.adventure.text.minimessage.MiniMessage;

import org.slf4j.Logger;

import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;

@Plugin(
        id = "v-monitor",
        name = "V-Monitor",
        version = BuildConstants.VERSION,
        url = "https://github.com/MC-Nirvana/V-Monitor",
        description = "Monitor your Velocity Proxy player activity and server statistics.",
        authors = {
                "MC-Nirvana"
        }
)
public class VMonitor {

    private final ProxyServer proxyServer;
    private final Logger logger;
    private final Path dataDirectory;
    private final PluginContainer pluginContainer; // 注入 PluginContainer

    private ConfigLoader configLoader;
    private LanguageLoader languageLoader;
    private DataLoader dataLoader;
    private FileUtil fileUtil; // 新增 FileUtil 实例

    private MiniMessage miniMessage;

    @Inject
    public VMonitor(ProxyServer proxyServer, Logger logger, @DataDirectory Path dataDirectory, PluginContainer pluginContainer) {
        this.proxyServer = proxyServer;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.pluginContainer = pluginContainer; // 注入 PluginContainer
        this.miniMessage = MiniMessage.miniMessage();
        this.fileUtil = new FileUtil(logger, dataDirectory); // 初始化 FileUtil
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        logger.info("V-Monitor plugin is starting...");

        // 初始化目录路径
        Path langDirectory = dataDirectory.resolve("lang");

        // 初始化判定逻辑
        boolean dataDirExists = Files.exists(dataDirectory);
        boolean langDirExists = Files.exists(langDirectory);

        // 执行初始化判定逻辑
        if (!dataDirExists && !langDirExists) {
            // 情况 1：数据目录和 lang 子目录都不存在 → 第一次加载
            try {
                Files.createDirectories(langDirectory);
                logger.info("Data directory and lang subdirectory created.");
                fileUtil.releaseConfigFile();
                fileUtil.releaseLanguageFiles();
                fileUtil.releaseDataFile();
            } catch (IOException e) {
                logger.error("Failed to create directories or release default files: {}", e.getMessage());
                throw new RuntimeException("Critical error during plugin initialization.", e);
            }

        } else if (dataDirExists && !langDirExists) {
            // 情况 2：数据目录存在，但 lang 子目录不存在 → lang 子目录丢失
            try {
                Files.createDirectories(langDirectory);
                logger.info("Lang subdirectory created.");
                fileUtil.releaseLanguageFiles();
            } catch (IOException e) {
                logger.error("Failed to create lang directory: {}", e.getMessage());
                throw new RuntimeException("Critical error during lang directory creation.", e);
            }

        } else if (dataDirExists && langDirExists) {
            // 情况 3：数据目录和 lang 子目录都存在 → 正常加载
            logger.info("Data directory and lang subdirectory already exist. Proceeding with normal initialization.");
        }

        // 初始化 loader
        this.configLoader = new ConfigLoader(logger, dataDirectory);
        this.languageLoader = new LanguageLoader(logger, dataDirectory, configLoader);
        this.dataLoader = new DataLoader(logger, dataDirectory);

        // 文件路径（延迟构造 langPath）
        Path configPath = dataDirectory.resolve("config.yml");
        Path dataPath = dataDirectory.resolve("data.json");

        // 1. 校验并加载 config.yml
        try {
            fileUtil.verifyFile(configPath, "config");
            fileUtil.loadFile(configPath, "Config", configLoader, configLoader::loadConfig);
        } catch (FileException e) {
            logger.error("Config file verification or loading failed: {}", e.getMessage());
            try {
                logger.warn("Repairing config.yml...");
                fileUtil.repairFile(configPath, "config.yml", "config");
                fileUtil.verifyFile(configPath, "config");
                fileUtil.loadFile(configPath, "Config", configLoader, configLoader::loadConfig);
            } catch (FileException repairException) {
                logger.error("Failed to repair config file: {}", repairException.getMessage());
                throw new RuntimeException("Critical config error. Plugin cannot start.", repairException);
            }
        }

        // 2. 构造 langPath 并加载语言文件
        String langKey = configLoader.getLanguageKey();
        if (langKey == null || langKey.isEmpty()) {
            logger.error("Language key is missing or empty. This should not happen in production.");
            throw new RuntimeException("Critical config error: 'language.default' is missing or empty.");
        }

        Path langPath = langDirectory.resolve(langKey + ".yml");

        try {
            fileUtil.verifyFile(langPath, "language");
            fileUtil.loadFile(langPath, "Language", languageLoader, languageLoader::loadLanguage);
        } catch (FileException e) {
            logger.error("Language file verification or loading failed: {}", e.getMessage());
            try {
                logger.warn("Repairing language file...");
                fileUtil.repairFile(langPath, "lang/" + langKey + ".yml", "language");
                fileUtil.verifyFile(langPath, "language");
                fileUtil.loadFile(langPath, "Language", languageLoader, languageLoader::loadLanguage);
            } catch (FileException repairException) {
                logger.error("Failed to repair language file: {}", repairException.getMessage());
                throw new RuntimeException("Critical language file error. Plugin cannot start.", repairException);
            }
        }

        // 3. 校验并加载 data.json
        try {
            fileUtil.verifyFile(dataPath, "data");
            fileUtil.loadFile(dataPath, "Data", dataLoader, dataLoader::loadData);
        } catch (FileException e) {
            logger.error("Data file verification or loading failed: {}", e.getMessage());
            try {
                logger.warn("Repairing data.json...");
                fileUtil.repairFile(dataPath, "data.json", "data");
                fileUtil.verifyFile(dataPath, "data");
                fileUtil.loadFile(dataPath, "Data", dataLoader, dataLoader::loadData);
            } catch (FileException repairException) {
                logger.error("Failed to repair data file: {}", repairException.getMessage());
                throw new RuntimeException("Critical data file error. Plugin cannot start.", repairException);
            }
        }

        // 注册事件监听器
        proxyServer.getEventManager().register(this, new PlayerActivityListener(proxyServer, configLoader, languageLoader, dataLoader, miniMessage, this, logger));

        // 初始化并注册命令
        CommandUtil commandUtil = new CommandUtil(proxyServer.getCommandManager(), logger, pluginContainer);

        // 初始化命令模块
        HelpModule helpModule = new HelpModule(languageLoader, miniMessage);
        PlayerInfoModule playerInfoModule = new PlayerInfoModule(dataLoader, languageLoader, miniMessage);
        PlayerSwitchModule playerSwitchModule = new PlayerSwitchModule(dataLoader, languageLoader, miniMessage);
        PluginListModule pluginListModule = new PluginListModule(proxyServer, languageLoader, miniMessage);
        PluginInfoModule pluginInfoModule = new PluginInfoModule(proxyServer, languageLoader, miniMessage);
        ServerListModule serverListModule = new ServerListModule(proxyServer, configLoader, languageLoader, miniMessage);
        ServerInfoModule serverInfoModule = new ServerInfoModule(proxyServer, languageLoader, miniMessage, configLoader, this);
        ReloadModule reloadModule = new ReloadModule(configLoader, languageLoader, miniMessage);

        // 注册命令
        new CoreCommand(languageLoader, miniMessage, commandUtil, helpModule);
        new HelpCommand(commandUtil, helpModule);
        new PlayerCommand(commandUtil, languageLoader, miniMessage, playerInfoModule, helpModule, dataLoader, playerSwitchModule);
        new PluginCommand(commandUtil, proxyServer, languageLoader, miniMessage, pluginListModule, pluginInfoModule, helpModule);
        new ServerCommand(commandUtil, proxyServer, languageLoader, miniMessage, serverListModule, serverInfoModule, configLoader, helpModule, this);
        new ReloadCommand(commandUtil, reloadModule);
        new VersionCommand(commandUtil, new VersionModule(languageLoader, miniMessage));
        commandUtil.registerAllCommands();

        // 保存数据文件
        dataLoader.savePlayerData();

        logger.info("V-Monitor plugin enabled!");
    }


    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        // 在关服时执行数据保存操作，确保所有玩家数据和统计信息都已持久化
        logger.info("V-Monitor plugin is shutting down. Saving player data...");
        if (dataLoader != null) {
            dataLoader.savePlayerData();
            logger.info("Player data saved successfully.");
        } else {
            logger.warn("DataLoader was not initialized. Player data may not have been saved.");
        }
        logger.info("V-Monitor plugin disabled.");
    }

    // 提供一些公共访问器
    public ConfigLoader getConfigFileLoader() {
        return configLoader;
    }

    public LanguageLoader getLanguageLoader() {
        return languageLoader;
    }

    public DataLoader getPlayerDataLoader() {
        return dataLoader;
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