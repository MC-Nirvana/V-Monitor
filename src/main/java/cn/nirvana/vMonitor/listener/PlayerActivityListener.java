package cn.nirvana.vMonitor.listener;

import cn.nirvana.vMonitor.config.ConfigFileLoader;
import cn.nirvana.vMonitor.config.LanguageLoader;
import cn.nirvana.vMonitor.config.PlayerDataLoader;
import cn.nirvana.vMonitor.VMonitor;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class PlayerActivityListener {
    private final ProxyServer proxyServer;
    private final ConfigFileLoader configFileLoader;
    private final LanguageLoader languageLoader;
    private final PlayerDataLoader playerDataLoader;
    private final MiniMessage miniMessage;
    private final VMonitor plugin;

    public PlayerActivityListener(ProxyServer proxyServer, ConfigFileLoader configFileLoader, LanguageLoader languageLoader, PlayerDataLoader playerDataLoader, MiniMessage miniMessage, VMonitor plugin) {
        this.proxyServer = proxyServer;
        this.configFileLoader = configFileLoader;
        this.languageLoader = languageLoader;
        this.playerDataLoader = playerDataLoader;
        this.miniMessage = miniMessage;
        this.plugin = plugin;
    }

    @Subscribe
    public void onPlayerConnect(LoginEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        String playerName = event.getPlayer().getUsername();
        boolean isFirstJoin = !playerDataLoader.hasPlayerJoinedBefore(uuid);
        if (isFirstJoin) {
            playerDataLoader.updatePlayerJoinTime(uuid, playerName);
        } else {
            playerDataLoader.updatePlayerLastLoginTime(uuid);
        }
        onPlayerLogin(playerName, isFirstJoin);
    }

    @Subscribe
    public void onPlayerDisconnect(DisconnectEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        String playerName = event.getPlayer().getUsername();
        playerDataLoader.updatePlayerLastQuitTime(uuid);
        onPlayerLogout(playerName);
    }

    @Subscribe
    public void onPlayerSwitch(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        proxyServer.getScheduler().buildTask(plugin, () -> {
            String fromServerRawName = event.getPreviousServer()
                    .map(serverConnection -> serverConnection.getServerInfo().getName())
                    .orElse(null);
            String toServerRawName = event.getServer().getServerInfo().getName();
            if (fromServerRawName != null) {
                if (configFileLoader.getBoolean("player_activity.enable_switch_message", true)) {
                    String fromServerDisplayName = configFileLoader.getServerDisplayName(fromServerRawName);
                    String toServerDisplayName = configFileLoader.getServerDisplayName(toServerRawName);
                    String switchMessage = languageLoader.getMessage("player_activity.switch");
                    if (switchMessage != null && !switchMessage.isEmpty() && !switchMessage.startsWith("<red>Missing Language Key:")) {
                        Component component = miniMessage.deserialize(switchMessage
                                .replace("{player}", player.getUsername())
                                .replace("{from}", fromServerDisplayName)
                                .replace("{to}", toServerDisplayName));
                        proxyServer.sendMessage(component);
                    } else {
                        proxyServer.sendMessage(miniMessage.deserialize("<red>Server switch message configuration error or missing language key.</red>"));
                    }
                }
            }
        }).delay(500, TimeUnit.MILLISECONDS).schedule();
    }

    private void onPlayerLogin(String playerName, boolean isFirstJoin) {
        proxyServer.getScheduler().buildTask(plugin, () -> {
            String messageKey = isFirstJoin ? "player_activity.first_join" : "player_activity.join";
            String message = languageLoader.getMessage(messageKey);
            if ("player_activity.join".equals(messageKey) && !configFileLoader.getBoolean("player_activity.enable_login_message", true)) {
                return;
            }
            if (message != null && !message.isEmpty() && !message.startsWith("<red>Missing Language Key:")) {
                Component component = miniMessage.deserialize(message.replace("{player}", playerName));
                proxyServer.sendMessage(component);
            } else {
                proxyServer.sendMessage(miniMessage.deserialize("<red>Login message configuration error or missing language key for " + messageKey + ".</red>"));
            }
        }).delay(500, TimeUnit.MILLISECONDS).schedule();
    }

    private void onPlayerLogout(String playerName) {
        proxyServer.getScheduler().buildTask(plugin, () -> {
            String quitMessage = languageLoader.getMessage("player_activity.quit");
            if (quitMessage != null && !quitMessage.isEmpty() && !quitMessage.startsWith("<red>Missing Language Key:")) {
                Component component = miniMessage.deserialize(quitMessage.replace("{player}", playerName));
                proxyServer.sendMessage(component);
            } else {
                proxyServer.sendMessage(miniMessage.deserialize("<red>Quit message configuration error or missing language key.</red>"));
            }
        }).delay(500, TimeUnit.MILLISECONDS).schedule();
    }
}