package cn.nirvana.vMonitor.module;

import cn.nirvana.vMonitor.loader.ConfigFileLoader;
import cn.nirvana.vMonitor.loader.LanguageFileLoader;

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

    public ServerInfoModule(ProxyServer proxyServer, LanguageFileLoader languageFileLoader, MiniMessage miniMessage, ConfigFileLoader configFileLoader) {
        this.proxyServer = proxyServer;
        this.languageFileLoader = languageFileLoader;
        this.miniMessage = miniMessage;
        this.configFileLoader = configFileLoader;
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
                        String status = languageFileLoader.getMessage("global.online");
                        String motd = PlainTextComponentSerializer.plainText().serialize(ping.getDescriptionComponent());


                        String infoMessage = languageFileLoader.getMessage("commands.server.info.single_format")
                                .replace("{server_display_name}", serverDisplayName)
                                .replace("{online_players}", onlinePlayers)
                                .replace("{max_players}", maxPlayers)
                                .replace("{status}", status)
                                .replace("{motd}", motd);
                        source.sendMessage(miniMessage.deserialize(infoMessage));
                    })
                    .exceptionally(throwable -> {
                        String infoMessage = languageFileLoader.getMessage("commands.server.info.single_format")
                                .replace("{server_display_name}", serverDisplayName)
                                .replace("{online_players}", "0")
                                .replace("{max_players}", "0")
                                .replace("{status}", languageFileLoader.getMessage("global.offline"))
                                .replace("{motd}", languageFileLoader.getMessage("global.server_offline"));
                        source.sendMessage(miniMessage.deserialize(infoMessage));
                        return null;
                    });
        } else {
            source.sendMessage(miniMessage.deserialize(languageFileLoader.getMessage("commands.server.not_found").replace("{server}", serverNameArg)));
        }
    }

    public void executeInfoAll(CommandSource source) {
        List<RegisteredServer> servers = proxyServer.getAllServers().stream().toList();

        if (servers.isEmpty()) {
            source.sendMessage(miniMessage.deserialize(languageFileLoader.getMessage("commands.server.no_servers")));
            return;
        }

        StringBuilder serverStatusList = new StringBuilder();
        AtomicInteger totalOnlinePlayers = new AtomicInteger(0);
        AtomicInteger runningServersCount = new AtomicInteger(0);
        AtomicInteger offlineServersCount = new AtomicInteger(0);

        String serverStatusFormat = languageFileLoader.getMessage("commands.server.info.status_line_format");

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
                            String status = languageFileLoader.getMessage("global.online");

                            String currentServerLine = serverStatusFormat
                                    .replace("{server_display_name}", serverDisplayName)
                                    .replace("{online_players}", onlinePlayers)
                                    .replace("{max_players}", maxPlayers)
                                    .replace("{status}", status);

                            serverStatusList.append(miniMessage.serialize(miniMessage.deserialize(currentServerLine))).append("\n");
                        })
                        .exceptionally(throwable -> {
                            String serverDisplayName = configFileLoader.getServerDisplayName(server.getServerInfo().getName());
                            offlineServersCount.incrementAndGet();
                            String currentServerLine = serverStatusFormat
                                    .replace("{server_display_name}", serverDisplayName)
                                    .replace("{online_players}", "0")
                                    .replace("{max_players}", "0")
                                    .replace("{status}", languageFileLoader.getMessage("global.offline"));
                            serverStatusList.append(miniMessage.serialize(miniMessage.deserialize(currentServerLine))).append("\n");
                            return null;
                        })
                ).toArray(CompletableFuture[]::new);

        CompletableFuture.allOf(futures)
                .thenRun(() -> {
                    String proxyVersion = proxyServer.getVersion().getName();
                    String allFormat = languageFileLoader.getMessage("commands.server.info.all_format")
                            .replace("{proxy_version}", proxyVersion)
                            .replace("{total_player}", String.valueOf(totalOnlinePlayers.get()))
                            .replace("{server_count}", String.valueOf(servers.size()))
                            .replace("{running_servers}", String.valueOf(runningServersCount.get()))
                            .replace("{offline_servers}", String.valueOf(offlineServersCount.get()))
                            .replace("{server_status_list}", serverStatusList.toString().trim());
                    source.sendMessage(miniMessage.deserialize(allFormat));
                });
    }
}