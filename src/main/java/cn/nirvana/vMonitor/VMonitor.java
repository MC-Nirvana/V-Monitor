// File: src/main/java/cn/nirvana/vMonitor/VMonitor.java
package cn.nirvana.vMonitor;

import cn.nirvana.vMonitor.command.*;
import cn.nirvana.vMonitor.command.ServerInfoCommand;
import cn.nirvana.vMonitor.config.ConfigFileLoader;
import cn.nirvana.vMonitor.config.LanguageLoader;
import cn.nirvana.vMonitor.config.PlayerDataLoader;
import cn.nirvana.vMonitor.listener.PlayerActivityListener;

import com.google.inject.Inject;

import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;

import net.kyori.adventure.text.minimessage.MiniMessage;

import org.slf4j.Logger;

import java.io.IOException; // 新增导入
import java.nio.file.Files; // 新增导入
import java.nio.file.Path;

@Plugin(id = "v-monitor", name = "V-Monitor", version = "1.2.1", url = "https://github.com/MC-Nirvana/V-Monitor", description = "Monitor the player's activity status", authors = {"MC-Nirvana"})
public final class VMonitor {
    private final ProxyServer proxyServer;
    private final Logger logger;
    private final Path dataDirectory;
    private ConfigFileLoader configFileLoader;
    private LanguageLoader languageLoader;
    private PlayerDataLoader playerDataLoader;
    private MiniMessage miniMessage;

    @Inject
    public VMonitor(ProxyServer proxyServer, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxyServer = proxyServer;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        logger.info("V-Monitor is enabling...");

        // --- 集中创建目录的逻辑 ---
        try {
            // 创建主数据目录
            if (!Files.exists(dataDirectory)) {
                Files.createDirectories(dataDirectory);
                logger.info("Created data directory: " + dataDirectory);
            }

            // 创建语言文件目录 (位于 dataDirectory/lang)
            Path langDirectory = dataDirectory.resolve("lang");
            if (!Files.exists(langDirectory)) {
                Files.createDirectories(langDirectory);
                logger.info("Created language directory: " + langDirectory);
            }
        } catch (IOException e) {
            logger.error("Failed to create plugin directories: " + e.getMessage());
            // 如果目录创建失败，插件可能无法正常工作，可以选择在此处抛出异常或停止初始化
            return; // 停止初始化
        }
        // --- 目录创建逻辑结束 ---


        // 初始化 MiniMessage
        this.miniMessage = MiniMessage.miniMessage();

        // 加载配置文件
        this.configFileLoader = new ConfigFileLoader(logger, dataDirectory);
        configFileLoader.loadConfig();

        // 加载语言文件
        this.languageLoader = new LanguageLoader(logger, dataDirectory, configFileLoader);
        languageLoader.loadLanguage();

        // 加载玩家数据
        this.playerDataLoader = new PlayerDataLoader(logger, dataDirectory);

        // 注册玩家活动监听器
        proxyServer.getEventManager().register(this, new PlayerActivityListener(proxyServer, configFileLoader, languageLoader, playerDataLoader, miniMessage, this));

        // 初始化并注册各种命令
        CommandManager commandManager = proxyServer.getCommandManager();
        CommandRegistrar commandRegistrar = new CommandRegistrar(commandManager, proxyServer, languageLoader, miniMessage);

        HelpCommand helpCommandInstance = new HelpCommand(languageLoader, miniMessage);
        ReloadCommand reloadCommandInstance = new ReloadCommand(configFileLoader, languageLoader, miniMessage);
        commandRegistrar.setHelpCommand(helpCommandInstance);
        commandRegistrar.setReloadCommand(reloadCommandInstance);
        commandRegistrar.registerCommands();
        new ServerListCommand(proxyServer, configFileLoader, languageLoader, miniMessage, commandRegistrar);
        new ServerInfoCommand(proxyServer, languageLoader, miniMessage, configFileLoader, commandRegistrar);
        new PluginListCommand(proxyServer, languageLoader, miniMessage, commandRegistrar);
        new PluginInfoCommand(proxyServer, languageLoader, miniMessage, commandRegistrar);

        logger.info("V-Monitor enabled!");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        logger.info("V-Monitor is disabling...");
        logger.info("Saving player data...");
        playerDataLoader.savePlayerData();
        logger.info("Saved player data!");
        logger.info("V-Monitor disabled!");
    }

    public ConfigFileLoader getConfigFileLoader() {
        return configFileLoader;
    }

    public LanguageLoader getLanguageLoader() {
        return languageLoader;
    }

    public PlayerDataLoader getPlayerDataLoader() {
        return playerDataLoader;
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