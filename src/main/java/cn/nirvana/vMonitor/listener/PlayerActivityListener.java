package cn.nirvana.vMonitor.listener;

import cn.nirvana.vMonitor.config.ConfigFileLoader;
import cn.nirvana.vMonitor.config.LanguageLoader;
import cn.nirvana.vMonitor.config.PlayerDataLoader;
import cn.nirvana.vMonitor.config.PlayerDataLoader.PlayerFirstJoinInfo;
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
    public void onPlayerLogin(LoginEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String playerName = player.getUsername();
        boolean isFirstJoin = !playerDataLoader.hasPlayerJoinedBefore(uuid);
        playerDataLoader.addPlayerFirstJoinInfo(uuid, playerName);
        proxyServer.getScheduler().buildTask(plugin, () -> {
            if (isFirstJoin) {
                String firstJoinMessage = languageLoader.getMessage("player_activity.first_join");
                if (firstJoinMessage != null && !firstJoinMessage.isEmpty()) {
                    Component component = miniMessage.deserialize(firstJoinMessage.replace("{player}", playerName));
                    proxyServer.sendMessage(component);
                }
            } else {
                String joinMessage = languageLoader.getMessage("player_activity.join");
                if (joinMessage != null && !joinMessage.isEmpty()) {
                    Component component = miniMessage.deserialize(joinMessage.replace("{player}", playerName));
                    proxyServer.sendMessage(component);
                }
            }
        }).delay(1, TimeUnit.SECONDS).schedule();
    }

    @Subscribe
    public void onPlayerDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        String leaveMessage = languageLoader.getMessage("player_activity.leave");
        if (leaveMessage != null && !leaveMessage.isEmpty()) {
            Component component = miniMessage.deserialize(leaveMessage.replace("{player}", player.getUsername()));
            proxyServer.sendMessage(component);
        }
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        Player player = event.getPlayer();
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
    }
}