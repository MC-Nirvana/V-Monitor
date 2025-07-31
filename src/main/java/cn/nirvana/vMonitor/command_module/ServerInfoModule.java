package cn.nirvana.vMonitor.command_module;

import cn.nirvana.vMonitor.VMonitor;
import cn.nirvana.vMonitor.loader.ConfigFileLoader;
import cn.nirvana.vMonitor.loader.LanguageFileLoader;
import cn.nirvana.vMonitor.util.TimeUtil;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerPing;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public class ServerInfoModule {
    private final ProxyServer proxyServer;
    private final LanguageFileLoader languageFileLoader;
    private final MiniMessage miniMessage;
    private final ConfigFileLoader configFileLoader;
    private final VMonitor plugin;

    public ServerInfoModule(ProxyServer proxyServer, LanguageFileLoader languageFileLoader, MiniMessage miniMessage, ConfigFileLoader configFileLoader, VMonitor plugin) {
        this.proxyServer = proxyServer;
        this.languageFileLoader = languageFileLoader;
        this.miniMessage = miniMessage;
        this.configFileLoader = configFileLoader;
        this.plugin = plugin;
    }

    public void executeInfoSingleServer(CommandSource source, String serverNameArg) {
        Optional<RegisteredServer> serverOptional = proxyServer.getServer(serverNameArg);
        if (serverOptional.isPresent()) {
            RegisteredServer server = serverOptional.get();
            String serverDisplayName = configFileLoader.getServerDisplayName(server.getServerInfo().getName());

            server.ping()
                    .thenAccept(ping -> {
                        String onlinePlayers = String.valueOf(ping.getPlayers().map(ServerPing.Players::getOnline).orElse(0));
                        String maxPlayers = String.valueOf(ping.getPlayers().map(ServerPing.Players::getMax).orElse(0));
                        String status = languageFileLoader.getMessage("commands.server.info.status_online");
                        String motd = PlainTextComponentSerializer.plainText().serialize(ping.getDescriptionComponent());


                        String infoMessage = languageFileLoader.getMessage("commands.server.info.specific_format")
                                .replace("{server_name}", serverNameArg)
                                .replace("{server_display_name}", serverDisplayName)
                                .replace("{version}", Optional.ofNullable(ping.getVersion())
                                        .map(ServerPing.Version::getName)
                                        .orElse(languageFileLoader.getMessage("commands.server.info.no_version")))
                                .replace("{status}", status)
                                .replace("{online_players}", onlinePlayers)
                                .replace("{motd}", motd);
                        source.sendMessage(miniMessage.deserialize(infoMessage));
                    })
                    .exceptionally(throwable -> {
                        String infoMessage = languageFileLoader.getMessage("commands.server.info.specific_format")
                                .replace("{server_name}", serverNameArg)
                                .replace("{server_display_name}", serverDisplayName)
                                .replace("{version}", languageFileLoader.getMessage("commands.server.info.no_version"))
                                .replace("{status}", languageFileLoader.getMessage("commands.server.info.status_offline"))
                                .replace("{online_players}", languageFileLoader.getMessage("commands.server.info.no_players"))
                                .replace("{motd}", languageFileLoader.getMessage("commands.server.info.no_motd"));
                        source.sendMessage(miniMessage.deserialize(infoMessage));
                        return null;
                    });
        } else {
            source.sendMessage(miniMessage.deserialize(languageFileLoader.getMessage("commands.server.info.not_found").replace("{server}", serverNameArg)));
        }
    }

    public void executeInfoAll(CommandSource source) {
        List<RegisteredServer> servers = proxyServer.getAllServers().stream().toList();

        if (servers.isEmpty()) {
            source.sendMessage(miniMessage.deserialize(languageFileLoader.getMessage("commands.server.info.no_servers")));
            return;
        }

        StringBuilder serverStatusList = new StringBuilder();
        AtomicInteger totalOnlinePlayers = new AtomicInteger(0);
        AtomicInteger runningServersCount = new AtomicInteger(0);
        AtomicInteger offlineServersCount = new AtomicInteger(0);

        String serverStatusFormat = languageFileLoader.getMessage("commands.server.info.server_status_list_format");

        // 解决：未检查的赋值警告
        @SuppressWarnings("unchecked")
        CompletableFuture<Void>[] futures = servers.stream()
                .map(server -> server.ping()
                        .thenAccept(ping -> {
                            String serverDisplayName = configFileLoader.getServerDisplayName(server.getServerInfo().getName());
                            totalOnlinePlayers.addAndGet(ping.getPlayers().map(ServerPing.Players::getOnline).orElse(0));
                            runningServersCount.incrementAndGet();

                            String onlinePlayers = String.valueOf(ping.getPlayers().map(ServerPing.Players::getOnline).orElse(0));
                            String maxPlayers = String.valueOf(ping.getPlayers().map(ServerPing.Players::getMax).orElse(0));
                            String status = languageFileLoader.getMessage("commands.server.info.status_online");

                            String currentServerLine = serverStatusFormat
                                    .replace("{server_name}", server.getServerInfo().getName())
                                    .replace("{server_display_name}", serverDisplayName)
                                    .replace("{status}", status)
                                    .replace("{online_players}", onlinePlayers);

                            serverStatusList.append(miniMessage.serialize(miniMessage.deserialize(currentServerLine))).append("\n");
                        })
                        .exceptionally(throwable -> {
                            String serverDisplayName = configFileLoader.getServerDisplayName(server.getServerInfo().getName());
                            offlineServersCount.incrementAndGet();
                            String currentServerLine = serverStatusFormat
                                    .replace("{server_name}", server.getServerInfo().getName())
                                    .replace("{server_display_name}", serverDisplayName)
                                    .replace("{status}", languageFileLoader.getMessage("commands.server.info.status_offline"))
                                    .replace("{online_players}", languageFileLoader.getMessage("commands.server.info.no_players"));
                            serverStatusList.append(miniMessage.serialize(miniMessage.deserialize(currentServerLine))).append("\n");
                            return null;
                        })
                ).toArray(CompletableFuture[]::new);

        CompletableFuture.allOf(futures)
                .thenRun(() -> {
                    String proxyVersion = proxyServer.getVersion().getVersion();

                    // 获取开服时间和运行时间
                    String serverStartTime = "Unknown";
                    String serverUptime = languageFileLoader.getMessage("global.unknown_info");

                    try {
                        // 从DataFileLoader获取开服时间
                        String bootTime = plugin.getPlayerDataLoader().getRootData().serverInfo.startupTime;
                        if (bootTime != null && !bootTime.isEmpty()) {
                            serverStartTime = bootTime;

                            // 使用TimeUtil计算运行时间
                            long uptimeDays = TimeUtil.UptimeCalculator.calculateUptimeDays(bootTime);
                            if (uptimeDays <= 0) {
                                serverUptime = languageFileLoader.getMessage("commands.server.info.uptime_same_day");
                            } else {
                                serverUptime = languageFileLoader.getMessage("commands.server.info.uptime_days")
                                        .replace("{days}", String.valueOf(uptimeDays));
                            }
                        }
                    } catch (Exception e) {
                        // 如果出现任何异常，使用默认值
                        serverStartTime = languageFileLoader.getMessage("global.unknown_info");
                        serverUptime = languageFileLoader.getMessage("global.unknown_info");
                    }

                    String allFormat = languageFileLoader.getMessage("commands.server.info.all_format")
                            .replace("{proxy_version}", proxyVersion)
                            .replace("{total_player}", String.valueOf(totalOnlinePlayers.get()))
                            .replace("{server_count}", String.valueOf(servers.size()))
                            .replace("{online_servers}", String.valueOf(runningServersCount.get()))
                            .replace("{offline_servers}", String.valueOf(offlineServersCount.get()))
                            .replace("{server_status_list}", serverStatusList.toString().trim())
                            .replace("{server_start_time}", serverStartTime)
                            .replace("{server_uptime}", serverUptime);
                    source.sendMessage(miniMessage.deserialize(allFormat));
                });
    }
}
