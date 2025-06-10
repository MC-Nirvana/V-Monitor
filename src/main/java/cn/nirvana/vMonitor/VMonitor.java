package cn.nirvana.vMonitor;

import cn.nirvana.vMonitor.command.*;
import cn.nirvana.vMonitor.command.ServerInfoCommand;
import cn.nirvana.vMonitor.config.ConfigFileLoader;
import cn.nirvana.vMonitor.config.LanguageLoader;
import cn.nirvana.vMonitor.config.PlayerDataLoader;
import cn.nirvana.vMonitor.listener.PlayerActivityListener;

import com.google.inject.Inject;

import com.velocitypowered.api.command.CommandManager; // <-- Ensure this is imported
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;

import net.kyori.adventure.text.minimessage.MiniMessage;

import org.slf4j.Logger;

import java.nio.file.Path;

@Plugin(id = "v-monitor", name = "V-Monitor", version = "1.2.1", url = "https://github.com/MC-Nirvana/V-Monitor", description = "Monitor the player's activity status", authors = {"MC-Nirvana"})
public final class VMonitor {
    private final ProxyServer proxyServer;
    private final Logger logger;
    private final Path dataDirectory; // Plugin data directory
    private ConfigFileLoader configFileLoader;
    private LanguageLoader languageLoader;
    private PlayerDataLoader playerDataLoader;
    private MiniMessage miniMessage;
    private CommandRegistrar commandRegistrar;

    @Inject
    public VMonitor(ProxyServer proxyServer, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxyServer = proxyServer;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        logger.info("V-Monitor is enabling...");
        this.configFileLoader = new ConfigFileLoader(logger, dataDirectory);
        this.configFileLoader.loadConfig();
        this.languageLoader = new LanguageLoader(logger, dataDirectory, configFileLoader);
        this.languageLoader.loadLanguage();
        this.miniMessage = MiniMessage.miniMessage();
        this.playerDataLoader = new PlayerDataLoader(logger, dataDirectory);
        this.playerDataLoader.loadPlayerData();
        this.playerDataLoader.initializeServerBootTime();
        proxyServer.getEventManager().register(this, new PlayerActivityListener(proxyServer, configFileLoader, languageLoader, playerDataLoader, miniMessage, this));
        CommandManager commandManager = proxyServer.getCommandManager();
        commandRegistrar = new CommandRegistrar(commandManager, proxyServer, languageLoader, miniMessage);
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