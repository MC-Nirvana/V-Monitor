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
    private final Map<UUID, LocalDateTime> playerLoginTimes; // 存储玩家登录时间
    private final Map<UUID, String> playerCurrentServers; // 新增：存储玩家当前所在的服务器名称

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
        this.playerCurrentServers = new ConcurrentHashMap<>(); // 初始化新的 Map
    }

    /**
     * 当玩家登录时记录时间并更新数据。
     *
     * @param event 登录事件
     */
    @Subscribe
    public void onPlayerLogin(LoginEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String playerName = player.getUsername();

        playerLoginTimes.put(uuid, LocalDateTime.now()); // 记录登录时间

        dataFileLoader.updatePlayerOnLogin(uuid, playerName);
        sendPlayerActivityMessage(playerName, "player_activity.login", null, null);
    }

    /**
     * 当玩家连接到服务器时更新数据（例如，记录服务器登录次数）。
     * 同时，记录玩家当前所在的服务器。
     *
     * @param event 服务器连接事件
     */
    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String playerName = player.getUsername();
        String serverName = event.getServer().getServerInfo().getName(); // 获取目标服务器名称

        // 记录玩家当前连接的服务器
        playerCurrentServers.put(uuid, serverName);

        dataFileLoader.updatePlayerServerLogin(uuid, serverName);

        Optional<RegisteredServer> previousServer = event.getPreviousServer();
        if (previousServer.isPresent()) {
            sendPlayerActivityMessage(playerName, "player_activity.switch",
                    configFileLoader.getServerDisplayName(previousServer.get().getServerInfo().getName()),
                    configFileLoader.getServerDisplayName(serverName));
        }
    }

    /**
     * 当玩家断开连接时，计算在线时长并更新数据。
     *
     * @param event 断开连接事件
     */
    @Subscribe
    public void onPlayerQuit(DisconnectEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String playerName = player.getUsername();
        LocalDateTime loginTime = playerLoginTimes.remove(uuid); // 获取并移除登录时间

        // 获取玩家最后所在的服务器名称，并从 Map 中移除
        String disconnectedServerName = playerCurrentServers.remove(uuid);

        Duration sessionDuration = Duration.ZERO;
        if (loginTime != null) {
            sessionDuration = Duration.between(loginTime, LocalDateTime.now());
        }

        dataFileLoader.updatePlayerOnQuit(uuid, playerName, disconnectedServerName, sessionDuration);

        // 使用配置的显示名称来发送消息
        String displayServerName = disconnectedServerName != null ? configFileLoader.getServerDisplayName(disconnectedServerName) : null;
        sendPlayerActivityMessage(playerName, "player_activity.quit", displayServerName, null);
    }

    /**
     * 发送玩家活动消息到代理服。
     *
     * @param playerName 玩家名称
     * @param messageKey 语言文件中的消息键
     * @param fromServer 玩家来自的服务器显示名称 (可选)
     * @param toServer   玩家前往的服务器显示名称 (可选)
     */
    private void sendPlayerActivityMessage(String playerName, String messageKey, String fromServer, String toServer) {
        // 调度任务以避免在事件线程中执行耗时操作，同时允许Velocity处理其他事件。
        // 此处的lambda表达式包含多条语句和局部变量引用，不适合直接替换为方法引用。
        plugin.getProxyServer().getScheduler().buildTask(plugin, () -> {
            String message = languageFileLoader.getMessage(messageKey);
            // 检查消息是否存在且未缺失
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
                logger.warn("Language key '{}' is missing or malformed for player activity message. " +
                        "Check your language files. Message won't be sent.", messageKey);
            }
        }).delay(500, TimeUnit.MILLISECONDS).schedule(); // 延迟发送以避免立即发送可能导致的聊天刷屏
    }
}