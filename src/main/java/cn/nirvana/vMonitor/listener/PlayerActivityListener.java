package cn.nirvana.vMonitor.listener;

import cn.nirvana.vMonitor.config.ConfigFileLoader; // 导入 ConfigFileLoader
import cn.nirvana.vMonitor.config.LanguageLoader;
import cn.nirvana.vMonitor.config.PlayerDataLoader;
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

import org.slf4j.Logger; // 导入 Logger

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.Optional;

public class PlayerActivityListener {
    private final ProxyServer proxyServer;
    private final ConfigFileLoader configFileLoader; // 新增注入 ConfigFileLoader
    private final LanguageLoader languageLoader;
    private final PlayerDataLoader playerDataLoader;
    private final MiniMessage miniMessage;
    private final VMonitor plugin;
    private final Logger logger; // 新增注入 Logger

    public PlayerActivityListener(ProxyServer proxyServer, ConfigFileLoader configFileLoader, LanguageLoader languageLoader, PlayerDataLoader playerDataLoader, MiniMessage miniMessage, VMonitor plugin, Logger logger) {
        this.proxyServer = proxyServer;
        this.configFileLoader = configFileLoader; // 初始化 ConfigFileLoader
        this.languageLoader = languageLoader;
        this.playerDataLoader = playerDataLoader;
        this.miniMessage = miniMessage;
        this.plugin = plugin;
        this.logger = logger;

        proxyServer.getScheduler().buildTask(plugin, () -> {
            for (Player player : proxyServer.getAllPlayers()) {
                playerDataLoader.updatePlayerPlayTime(player.getUniqueId());
            }
        }).repeat(1, TimeUnit.MINUTES).schedule();
    }

    @Subscribe
    public void onLogin(LoginEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String playerName = player.getUsername();
        // String ipAddress = player.getRemoteAddress().getAddress().getHostAddress(); // 移除 ipAddress 获取
        boolean hasJoinedBefore = playerDataLoader.getPlayerName(uuid) != null;
        playerDataLoader.updatePlayerOnLogin(uuid, playerName); // 移除 ipAddress 参数
        if (!hasJoinedBefore) {
            sendPlayerActivityMessage("player_activity.first_join", playerName, null, null, true); // 新增 enableCheck 参数
        } else {
            sendPlayerActivityMessage("player_activity.join", playerName, null, null, true); // 新增 enableCheck 参数
        }
    }


    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String playerName = player.getUsername();
        Optional<RegisteredServer> previousServer = event.getPreviousServer();
        RegisteredServer currentServer = event.getServer();
        // playerDataLoader.setPlayerCurrentServer(uuid, currentServer.getServerInfo().getName()); // 移除此行，因为 PlayerDataLoader 中没有此方法
        if (previousServer.isPresent()) {
            String fromServerName = previousServer.get().getServerInfo().getName();
            String toServerName = currentServer.getServerInfo().getName();
            sendPlayerActivityMessage("player_activity.switch", playerName, fromServerName, toServerName, true); // 新增 enableCheck 参数
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String playerName = player.getUsername();
        playerDataLoader.updatePlayerOnLogout(uuid, playerName);
        sendPlayerActivityMessage("player_activity.quit", playerName, null, null, true); // 新增 enableCheck 参数
    }

    private void sendPlayerActivityMessage(String messageKey, String playerName, String fromServer, String toServer, boolean enableCheck) {
        proxyServer.getScheduler().buildTask(plugin, () -> {
            // 根据 messageKey 检查配置文件中的对应设置
            if (enableCheck) { // 只有当 enableCheck 为 true 时才进行配置检查
                if ("player_activity.join".equals(messageKey) && !configFileLoader.getBoolean("player_activity.enable_login_message", true)) {
                    return; // 如果禁用登录消息，则不发送
                }
                if ("player_activity.first_join".equals(messageKey) && !configFileLoader.getBoolean("player_activity.enable_first_join_message", true)) {
                    return; // 如果禁用首次加入消息，则不发送
                }
                if ("player_activity.quit".equals(messageKey) && !configFileLoader.getBoolean("player_activity.enable_quit_message", true)) {
                    return; // 如果禁用退出消息，则不发送
                }
                if ("player_activity.switch".equals(messageKey) && !configFileLoader.getBoolean("player_activity.enable_switch_message", true)) {
                    return; // 如果禁用切换消息，则不发送
                }
            }


            String message = languageLoader.getMessage(messageKey);
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