package cn.nirvana.vMonitor.listener;

import cn.nirvana.vMonitor.config.ConfigFileLoader;
import cn.nirvana.vMonitor.config.LanguageLoader;
import cn.nirvana.vMonitor.config.PlayerDataLoader;
import cn.nirvana.vMonitor.config.PlayerDataLoader.PlayerFirstJoinInfo;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;

import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.UUID;

public class PlayerActivityListener {
    private final ProxyServer proxyServer;
    private final ConfigFileLoader configFileLoader;
    private final LanguageLoader languageLoader;
    private final PlayerDataLoader playerDataLoader;
    private final MiniMessage miniMessage;

    public PlayerActivityListener(ProxyServer proxyServer, ConfigFileLoader configFileLoader, LanguageLoader languageLoader, PlayerDataLoader playerDataLoader, MiniMessage miniMessage) {
        this.proxyServer = proxyServer;
        this.configFileLoader = configFileLoader;
        this.languageLoader = languageLoader;
        this.playerDataLoader = playerDataLoader;
        this.miniMessage = miniMessage;
    }

    @Subscribe
    public void onPlayerLogin(LoginEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String playerName = player.getUsername();
        boolean isFirstJoin = !playerDataLoader.hasPlayerJoinedBefore(uuid);
        playerDataLoader.addPlayerFirstJoinInfo(uuid, playerName);

        if (isFirstJoin) {
            String firstJoinMessage = languageLoader.getMessage("player_activity.first_join");
            if (firstJoinMessage != null && !firstJoinMessage.isEmpty()) {
                proxyServer.sendMessage(miniMessage.deserialize(firstJoinMessage.replace("{player}", playerName)));
            }
            String joinMessage = languageLoader.getMessage("player_activity.join");
            if (joinMessage != null && !joinMessage.isEmpty()) {
                proxyServer.sendMessage(miniMessage.deserialize(joinMessage.replace("{player}", playerName)));
            }
        } else {
            String joinMessage = languageLoader.getMessage("player_activity.join");
            if (joinMessage != null && !joinMessage.isEmpty()) {
                proxyServer.sendMessage(miniMessage.deserialize(joinMessage.replace("{player}", playerName)));
            }
        }
    }

    @Subscribe
    public void onPlayerDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        String leaveMessage = languageLoader.getMessage("player_activity.leave");
        if (leaveMessage != null && !leaveMessage.isEmpty()) {
            proxyServer.sendMessage(miniMessage.deserialize(leaveMessage.replace("{player}", player.getUsername())));
        }
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        String fromServerRawName = event.getPreviousServer()
                .map(serverConnection -> serverConnection.getServerInfo().getName())
                .orElse(null);
        String toServerRawName = event.getServer().getServerInfo().getName();
        String fromServerDisplayName = (fromServerRawName != null) ?
                configFileLoader.getServerDisplayName(fromServerRawName) :
                languageLoader.getMessage("global.unknown_server");
        String toServerDisplayName = configFileLoader.getServerDisplayName(toServerRawName);
        String switchMessage = languageLoader.getMessage("player_activity.switch");
        if (switchMessage != null && !switchMessage.isEmpty()) {
            proxyServer.sendMessage(miniMessage.deserialize(switchMessage
                    .replace("{player}", player.getUsername())
                    .replace("{from}", fromServerDisplayName)
                    .replace("{to}", toServerDisplayName)));
        }
    }
}