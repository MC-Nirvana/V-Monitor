package cn.nirvana.vMonitor.listener;

import cn.nirvana.vMonitor.loader.ConfigFileLoader;
import cn.nirvana.vMonitor.loader.LanguageFileLoader;
import cn.nirvana.vMonitor.loader.DataFileLoader;
import cn.nirvana.vMonitor.VMonitor;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.Component;

import org.slf4j.Logger;

import java.time.Duration;
import java.time.LocalDateTime;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.Optional;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerActivityListener {
    private final ProxyServer proxyServer;
    private final ConfigFileLoader configFileLoader;
    private final LanguageFileLoader languageFileLoader;
    private final DataFileLoader dataFileLoader;
    private final MiniMessage miniMessage;
    private final VMonitor plugin;
    private final Logger logger;
    private final Map<UUID, LocalDateTime> playerLoginTimes;

    public PlayerActivityListener(ProxyServer proxyServer, ConfigFileLoader configFileLoader,
                                  LanguageFileLoader languageFileLoader, DataFileLoader dataFileLoader,
                                  MiniMessage miniMessage, VMonitor plugin, Logger logger) {
        this.proxyServer = proxyServer;
        this.configFileLoader = configFileLoader;
        this.languageFileLoader = languageFileLoader;
        this.dataFileLoader = dataFileLoader;
        this.miniMessage = miniMessage;
        this.plugin = plugin;
        this.logger = logger;
        this.playerLoginTimes = new ConcurrentHashMap<>();
    }

    @Subscribe
    public void onLogin(LoginEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String playerName = player.getUsername();
        playerLoginTimes.put(uuid, LocalDateTime.now());
        dataFileLoader.updatePlayerOnLogin(uuid, playerName);
        if (dataFileLoader.getPlayerData(uuid) != null && dataFileLoader.getPlayerData(uuid).totalLoginCount == 1) {
            sendPlayerActivityMessage(playerName, "player_activity.first_join", null, null);
        }
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String playerName = player.getUsername();
        RegisteredServer previousServer = event.getPreviousServer().orElse(null);
        RegisteredServer currentServer = event.getServer();
        dataFileLoader.updatePlayerServerLogin(uuid, currentServer.getServerInfo().getName());
        if (previousServer != null) {
            String fromServerName = configFileLoader.getServerDisplayName(previousServer.getServerInfo().getName());
            String toServerName = configFileLoader.getServerDisplayName(currentServer.getServerInfo().getName());
            sendPlayerActivityMessage(playerName, "player_activity.switch", fromServerName, toServerName);
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String playerName = player.getUsername();
        LocalDateTime loginTime = playerLoginTimes.remove(uuid);
        Duration sessionDuration = Duration.ZERO;
        if (loginTime != null) {
            sessionDuration = Duration.between(loginTime, LocalDateTime.now());
        }
        Optional<RegisteredServer> currentServer = player.getCurrentServer().map(serverConnection -> serverConnection.getServer());
        RegisteredServer disconnectedFromServer = currentServer.orElse(null);
        dataFileLoader.updatePlayerOnQuit(uuid, playerName, disconnectedFromServer, sessionDuration);
        String serverName = disconnectedFromServer != null ? configFileLoader.getServerDisplayName(disconnectedFromServer.getServerInfo().getName()) : null;
        sendPlayerActivityMessage(playerName, "player_activity.quit", serverName, null);
    }

    private void sendPlayerActivityMessage(String playerName, String messageKey, String fromServer, String toServer) {
        plugin.getProxyServer().getScheduler().buildTask(plugin, () -> {
            String message = languageFileLoader.getMessage(messageKey);
            if (message != null && !message.isEmpty() && !message.startsWith("<red>Missing Language Key:")) {
                String formattedMessage = message.replace("{player}", playerName);
                if (fromServer != null) {
                    formattedMessage = formattedMessage.replace("{from}", fromServer);
                }
                if (toServer != null) {
                    formattedMessage = formattedMessage.replace("{to}", toServer);
                }
                Component component = miniMessage.deserialize(formattedMessage);
                proxyServer.sendMessage(component);
            } else {
                logger.warn("Language key '{}' is missing or malformed for player activity message. " +  "Check your language files. Message won't be sent.", messageKey);
            }
        }).delay(500, TimeUnit.MILLISECONDS).schedule();
    }
}