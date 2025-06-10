package cn.nirvana.vMonitor.listener;

import cn.nirvana.vMonitor.config.ConfigFileLoader;
import cn.nirvana.vMonitor.config.LanguageLoader;
import cn.nirvana.vMonitor.config.PlayerDataLoader;
import cn.nirvana.vMonitor.config.PlayerDataLoader.PlayerData;
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
    public void onLogin(LoginEvent event) {
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();
        String playerName = player.getUsername();
        if (!playerDataLoader.hasPlayerJoinedBefore(playerUuid)) {
            playerDataLoader.addPlayerFirstJoinInfo(playerUuid, playerName);
            String firstJoinMessage = languageLoader.getMessage("player_activity.first_join");
            if (firstJoinMessage != null && !firstJoinMessage.isEmpty()) {
                Component component = miniMessage.deserialize(firstJoinMessage.replace("{player}", playerName));
                proxyServer.sendMessage(component);
            }
        }
        playerDataLoader.updatePlayerLoginData(playerUuid, playerName);
        proxyServer.getScheduler().buildTask(plugin, () -> {
            String joinMessage = languageLoader.getMessage("player_activity.join");
            if (joinMessage != null && !joinMessage.isEmpty() && !joinMessage.startsWith("<red>Missing Language Key:")) {
                Component component = miniMessage.deserialize(joinMessage.replace("{player}", player.getUsername()));
                proxyServer.sendMessage(component);
            }
        }).delay(500, TimeUnit.MILLISECONDS).schedule();
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        playerDataLoader.updatePlayerLogoutData(player.getUniqueId());
        proxyServer.getScheduler().buildTask(plugin, () -> {
            String leaveMessage = languageLoader.getMessage("player_activity.leave");
            if (leaveMessage != null && !leaveMessage.isEmpty() && !leaveMessage.startsWith("<red>Missing Language Key:")) {
                Component component = miniMessage.deserialize(leaveMessage.replace("{player}", player.getUsername()));
                proxyServer.sendMessage(component);
            }
        }).delay(500, TimeUnit.MILLISECONDS).schedule();
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        proxyServer.getScheduler().buildTask(plugin, () -> {
            String fromServerRawName = event.getPreviousServer()
                    .map(serverConnection -> serverConnection.getServerInfo().getName())
                    .orElse(null);
            String toServerRawName = event.getServer().getServerInfo().getName();
            if (fromServerRawName != null) {
                String fromServerDisplayName = configFileLoader.getServerDisplayName(fromServerRawName);
                String toServerDisplayName = configFileLoader.getServerDisplayName(toServerRawName);
                String switchMessage = languageLoader.getMessage("player_activity.switch");
                if (switchMessage != null && !switchMessage.isEmpty()) {
                    Component component = miniMessage.deserialize(switchMessage
                            .replace("{player}", player.getUsername())
                            .replace("{from}", fromServerDisplayName)
                            .replace("{to}", toServerDisplayName));
                    proxyServer.sendMessage(component);
                }
            }
        }).delay(500, TimeUnit.MILLISECONDS).schedule();
    }
}