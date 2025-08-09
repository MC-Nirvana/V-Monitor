package cn.nirvana.vMonitor.command_module;

import cn.nirvana.vMonitor.VMonitor;
import cn.nirvana.vMonitor.loader.ConfigLoader;
import cn.nirvana.vMonitor.loader.LanguageLoader;
import cn.nirvana.vMonitor.util.TimeUtil;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerPing;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public class ServerInfoModule {
    private final ProxyServer proxyServer;
    private final LanguageLoader languageLoader;
    private final MiniMessage miniMessage;
    private final ConfigLoader configLoader;
    private final VMonitor plugin;

    public ServerInfoModule(ProxyServer proxyServer, LanguageLoader languageLoader, MiniMessage miniMessage, ConfigLoader configLoader, VMonitor plugin) {
        this.proxyServer = proxyServer;
        this.languageLoader = languageLoader;
        this.miniMessage = miniMessage;
        this.configLoader = configLoader;
        this.plugin = plugin;
    }

    public void executeInfoSingleServer(CommandSource source, String serverNameArg) {
        Optional<RegisteredServer> serverOptional = proxyServer.getServer(serverNameArg);
        if (serverOptional.isPresent()) {
            RegisteredServer server = serverOptional.get();
            String serverDisplayName = configLoader.getServerDisplayName(server.getServerInfo().getName());

            server.ping()
                    .thenAccept(ping -> {
                        String onlinePlayers = String.valueOf(ping.getPlayers().map(ServerPing.Players::getOnline).orElse(0));
                        String maxPlayers = String.valueOf(ping.getPlayers().map(ServerPing.Players::getMax).orElse(0));
                        String status = languageLoader.getMessage("commands.server.info.status_online");
                        String motd = PlainTextComponentSerializer.plainText().serialize(ping.getDescriptionComponent());


                        String infoMessage = languageLoader.getMessage("commands.server.info.specific_format")
                                .replace("{server_name}", serverNameArg)
                                .replace("{server_display_name}", serverDisplayName)
                                .replace("{version}", Optional.ofNullable(ping.getVersion())
                                        .map(ServerPing.Version::getName)
                                        .orElse(languageLoader.getMessage("commands.server.info.no_version")))
                                .replace("{status}", status)
                                .replace("{online_players}", onlinePlayers)
                                .replace("{motd}", motd);
                        source.sendMessage(miniMessage.deserialize(infoMessage));
                    })
                    .exceptionally(throwable -> {
                        String infoMessage = languageLoader.getMessage("commands.server.info.specific_format")
                                .replace("{server_name}", serverNameArg)
                                .replace("{server_display_name}", serverDisplayName)
                                .replace("{version}", languageLoader.getMessage("commands.server.info.no_version"))
                                .replace("{status}", languageLoader.getMessage("commands.server.info.status_offline"))
                                .replace("{online_players}", languageLoader.getMessage("commands.server.info.no_players"))
                                .replace("{motd}", languageLoader.getMessage("commands.server.info.no_motd"));
                        source.sendMessage(miniMessage.deserialize(infoMessage));
                        return null;
                    });
        } else {
            source.sendMessage(miniMessage.deserialize(languageLoader.getMessage("commands.server.info.not_found").replace("{server}", serverNameArg)));
        }
    }

    public void executeInfoAll(CommandSource source) {
        List<RegisteredServer> servers = proxyServer.getAllServers().stream().toList();

        if (servers.isEmpty()) {
            source.sendMessage(miniMessage.deserialize(languageLoader.getMessage("commands.server.info.no_servers")));
            return;
        }

        StringBuilder serverStatusList = new StringBuilder();
        AtomicInteger totalOnlinePlayers = new AtomicInteger(0);
        AtomicInteger runningServersCount = new AtomicInteger(0);
        AtomicInteger offlineServersCount = new AtomicInteger(0);

        String serverStatusFormat = languageLoader.getMessage("commands.server.info.server_status_list_format");

        // 解决：未检查的赋值警告
        @SuppressWarnings("unchecked")
        CompletableFuture<Void>[] futures = servers.stream()
                .map(server -> server.ping()
                        .thenAccept(ping -> {
                            String serverDisplayName = configLoader.getServerDisplayName(server.getServerInfo().getName());
                            totalOnlinePlayers.addAndGet(ping.getPlayers().map(ServerPing.Players::getOnline).orElse(0));
                            runningServersCount.incrementAndGet();

                            String onlinePlayers = String.valueOf(ping.getPlayers().map(ServerPing.Players::getOnline).orElse(0));
                            String maxPlayers = String.valueOf(ping.getPlayers().map(ServerPing.Players::getMax).orElse(0));
                            String status = languageLoader.getMessage("commands.server.info.status_online");

                            String currentServerLine = serverStatusFormat
                                    .replace("{server_name}", server.getServerInfo().getName())
                                    .replace("{server_display_name}", serverDisplayName)
                                    .replace("{status}", status)
                                    .replace("{online_players}", onlinePlayers);

                            serverStatusList.append(miniMessage.serialize(miniMessage.deserialize(currentServerLine))).append("\n");
                        })
                        .exceptionally(throwable -> {
                            String serverDisplayName = configLoader.getServerDisplayName(server.getServerInfo().getName());
                            offlineServersCount.incrementAndGet();
                            String currentServerLine = serverStatusFormat
                                    .replace("{server_name}", server.getServerInfo().getName())
                                    .replace("{server_display_name}", serverDisplayName)
                                    .replace("{status}", languageLoader.getMessage("commands.server.info.status_offline"))
                                    .replace("{online_players}", languageLoader.getMessage("commands.server.info.no_players"));
                            serverStatusList.append(miniMessage.serialize(miniMessage.deserialize(currentServerLine))).append("\n");
                            return null;
                        })
                ).toArray(CompletableFuture[]::new);

        CompletableFuture.allOf(futures)
                .thenRun(() -> {
                    String proxyVersion = proxyServer.getVersion().getVersion();

                    // 获取开服时间和运行时间
                    String serverStartTime = "Unknown";
                    String serverUptime = languageLoader.getMessage("global.unknown_info");

                    try {
                        // 从DataFileLoader获取开服时间
                        String bootTime = plugin.getPlayerDataLoader().getRootData().serverInfo.startupTime;
                        if (bootTime != null && !bootTime.isEmpty()) {
                            serverStartTime = bootTime;

                            // 直接使用 TimeUtil.DateConverter 来处理日期字符串
                            try {
                                long bootTimestamp = TimeUtil.DateConverter.toTimestamp(bootTime);
                                long currentTimestamp = TimeUtil.SystemTime.getCurrentTimestamp();

                                // 如果当前时间早于开服时间，则返回0
                                if (currentTimestamp >= bootTimestamp) {
                                    LocalDateTime bootDateTime = LocalDateTime.ofInstant(
                                            Instant.ofEpochSecond(bootTimestamp),
                                            ZoneId.systemDefault()
                                    );
                                    LocalDateTime currentDateTime = LocalDateTime.ofInstant(
                                            Instant.ofEpochSecond(currentTimestamp),
                                            ZoneId.systemDefault()
                                    );

                                    long uptimeDays = java.time.temporal.ChronoUnit.DAYS.between(bootDateTime, currentDateTime);

                                    if (uptimeDays <= 0) {
                                        serverUptime = languageLoader.getMessage("commands.server.info.uptime_same_day");
                                    } else {
                                        serverUptime = languageLoader.getMessage("commands.server.info.uptime_days")
                                                .replace("{days}", String.valueOf(uptimeDays));
                                    }
                                } else {
                                    serverUptime = languageLoader.getMessage("commands.server.info.uptime_same_day");
                                }
                            } catch (Exception e) {
                                // 如果计算运行时间出现异常，使用默认值
                                serverUptime = languageLoader.getMessage("global.unknown_info");
                            }
                        }
                    } catch (Exception e) {
                        // 如果出现任何异常，使用默认值
                        serverStartTime = languageLoader.getMessage("global.unknown_info");
                        serverUptime = languageLoader.getMessage("global.unknown_info");
                    }

                    // 从配置文件获取服务器名称
                    String serverName = configLoader.getServerName();

                    String allFormat = languageLoader.getMessage("commands.server.info.all_format")
                            .replace("{proxy_version}", proxyVersion)
                            .replace("{total_player}", String.valueOf(totalOnlinePlayers.get()))
                            .replace("{server_count}", String.valueOf(servers.size()))
                            .replace("{online_servers}", String.valueOf(runningServersCount.get()))
                            .replace("{offline_servers}", String.valueOf(offlineServersCount.get()))
                            .replace("{server_status_list}", serverStatusList.toString().trim())
                            .replace("{server_start_time}", serverStartTime)
                            .replace("{server_uptime}", serverUptime)
                            .replace("{server_name}", serverName); // 添加服务器名称替换
                    source.sendMessage(miniMessage.deserialize(allFormat));
                });

    }
}
