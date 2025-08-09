package cn.nirvana.vMonitor.listener;

import cn.nirvana.vMonitor.loader.ConfigLoader;
import cn.nirvana.vMonitor.loader.DataLoader;
import cn.nirvana.vMonitor.loader.LanguageLoader;
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
    private final ConfigLoader configLoader;
    private final LanguageLoader languageLoader;
    private final DataLoader dataLoader;
    private final MiniMessage miniMessage;
    private final VMonitor plugin;
    private final Logger logger;
    private final Map<UUID, LocalDateTime> playerLoginTimes; // 存储玩家登录时间
    private final Map<UUID, String> playerCurrentServers; //存储玩家当前所在的服务器名称

    public PlayerActivityListener(ProxyServer proxyServer, ConfigLoader configLoader,
                                  LanguageLoader languageLoader, DataLoader dataLoader,
                                  MiniMessage miniMessage, VMonitor plugin, Logger logger) {
        this.proxyServer = proxyServer;
        this.configLoader = configLoader;
        this.languageLoader = languageLoader;
        this.dataLoader = dataLoader;
        this.miniMessage = miniMessage;
        this.plugin = plugin;
        this.logger = logger;
        this.playerLoginTimes = new ConcurrentHashMap<>();
        this.playerCurrentServers = new ConcurrentHashMap<>(); // 初始化新的 Map
    }

    /**
     * 当玩家登录时发送登录消息。
     *
     * @param event 登录事件
     */
    @Subscribe
    public void onPlayerLogin(LoginEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String playerName = player.getUsername();

        // 检查是否为首次登录
        boolean isFirstLogin = dataLoader.getPlayerData(uuid) == null;

        // 根据是否首次登录发送不同消息
        String messageKey = isFirstLogin ? "player_activity.first_join" : "player_activity.join";

        // 添加500ms延迟后发送消息
        plugin.getProxyServer().getScheduler().buildTask(plugin, () -> {
            sendPlayerActivityMessage(playerName, messageKey, null, null);
        }).delay(500, TimeUnit.MILLISECONDS).schedule();
    }

    /**
     * 当玩家连接到服务器时更新数据。
     *
     * @param event 服务器连接事件
     */
    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String playerName = player.getUsername();
        String serverName = event.getServer().getServerInfo().getName(); // 获取目标服务器名称

        // 记录登录时间
        playerLoginTimes.put(uuid, LocalDateTime.now());

        // 记录玩家当前连接的服务器
        playerCurrentServers.put(uuid, serverName);

        // 更新玩家数据
        dataLoader.updatePlayerOnLogin(uuid, playerName);

        // 更新历史峰值在线人数
        int currentOnlineCount = proxyServer.getPlayerCount();
        dataLoader.updateHistoricalPeakOnline(currentOnlineCount);

        // 更新子服务器峰值在线人数
        int serverOnlineCount = event.getServer().getPlayersConnected().size() + 1; // +1是因为玩家即将连接
        dataLoader.updateSubServerPeakOnline(serverName, serverOnlineCount);

        // 处理玩家首次连接的情况
        Optional<RegisteredServer> previousServer = event.getPreviousServer();
        if (!previousServer.isPresent()) {
            // 如果没有前一个服务器，说明是首次连接，需要记录路径
            dataLoader.updatePlayerServerLogin(uuid, serverName);
        }
    }



    /**
     * 当玩家断开连接时发送离开消息通知。
     *
     * @param event 断开连接事件
     */
    @Subscribe
    public void onPlayerQuit(DisconnectEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String playerName = player.getUsername();

        // 使用配置的显示名称来发送消息，并添加500ms延迟
        String disconnectedServerName = playerCurrentServers.get(uuid);
        String displayServerName = disconnectedServerName != null ? configLoader.getServerDisplayName(disconnectedServerName) : null;

        plugin.getProxyServer().getScheduler().buildTask(plugin, () -> {
            sendPlayerActivityMessage(playerName, "player_activity.quit", displayServerName, null);
        }).delay(500, TimeUnit.MILLISECONDS).schedule();
    }

    /**
     * 当玩家完全断开连接时更新玩家数据。
     *
     * @param event 断开连接事件
     */
    @Subscribe
    public void onServerDisconnected(DisconnectEvent event) {
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

        dataLoader.updatePlayerOnQuit(uuid, playerName, disconnectedServerName, sessionDuration);

        // 更新历史峰值在线人数（玩家退出后）
        int currentOnlineCount = proxyServer.getPlayerCount();
        dataLoader.updateHistoricalPeakOnline(currentOnlineCount);
    }

    /**
     * 当玩家切换服务器时发送切换消息。
     *
     * @param event 服务器连接事件
     */
    @Subscribe
    public void onPlayerSwitch(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String playerName = player.getUsername();
        String serverName = event.getServer().getServerInfo().getName(); // 获取目标服务器名称

        Optional<RegisteredServer> previousServer = event.getPreviousServer();
        if (previousServer.isPresent()) {
            // 只有在玩家切换服务器时才发送切换消息
            // 添加500ms延迟后发送消息
            plugin.getProxyServer().getScheduler().buildTask(plugin, () -> {
                sendPlayerActivityMessage(playerName, "player_activity.switch",
                        configLoader.getServerDisplayName(previousServer.get().getServerInfo().getName()),
                        configLoader.getServerDisplayName(serverName));
            }).delay(500, TimeUnit.MILLISECONDS).schedule();
        }
    }


    /**
     * 当玩家切换服务器连接完成时更新数据。
     *
     * @param event 服务器连接事件
     */
    @Subscribe
    public void onServerSwitchConnected(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String serverName = event.getServer().getServerInfo().getName(); // 获取目标服务器名称

        Optional<RegisteredServer> previousServer = event.getPreviousServer();
        if (previousServer.isPresent()) {
            // 只有在玩家切换服务器时才更新服务器登录数据
            dataLoader.updatePlayerServerLogin(uuid, serverName);

            // 更新历史峰值在线人数
            int currentOnlineCount = proxyServer.getPlayerCount();
            dataLoader.updateHistoricalPeakOnline(currentOnlineCount);

            // 更新子服务器峰值在线人数
            int serverOnlineCount = event.getServer().getPlayersConnected().size() + 1; // +1是因为玩家即将连接
            dataLoader.updateSubServerPeakOnline(serverName, serverOnlineCount);
        }
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
            String message = languageLoader.getMessage(messageKey);
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
        }).schedule();
    }
}
