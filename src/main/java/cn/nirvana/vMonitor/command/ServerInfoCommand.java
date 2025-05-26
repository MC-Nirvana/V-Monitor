package cn.nirvana.vMonitor.command;

import cn.nirvana.vMonitor.config.ConfigFileLoader;
import cn.nirvana.vMonitor.config.LanguageLoader;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;

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
import java.util.stream.Collectors;

import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public class ServerInfoCommand {
    private final ProxyServer proxyServer;
    private final LanguageLoader languageLoader;
    private final MiniMessage miniMessage;
    private final ConfigFileLoader configFileLoader;
    private final CommandRegistrar commandRegistrar;

    public ServerInfoCommand(ProxyServer proxyServer, LanguageLoader languageLoader, MiniMessage miniMessage, ConfigFileLoader configFileLoader, CommandRegistrar commandRegistrar) {
        this.proxyServer = proxyServer;
        this.languageLoader = languageLoader;
        this.miniMessage = miniMessage;
        this.configFileLoader = configFileLoader;
        this.commandRegistrar = commandRegistrar;
        registerCommands();
    }

    private void registerCommands() {
        commandRegistrar.registerServerSubCommand(serverNode -> {
            LiteralCommandNode<CommandSource> infoNode = LiteralArgumentBuilder.<CommandSource>literal("info")
                    .executes(context -> {
                        context.getSource().sendMessage(miniMessage.deserialize(languageLoader.getMessage("commands.server.usage.info")));
                        return SINGLE_SUCCESS;
                    })
                    .build();
            infoNode.addChild(LiteralArgumentBuilder.<CommandSource>literal("all")
                    .executes(context -> {
                        executeInfoAll(context.getSource());
                        return SINGLE_SUCCESS;
                    })
                    .build()
            );
            infoNode.addChild(RequiredArgumentBuilder.<CommandSource, String>argument("server", word())
                    .suggests(new ServerNameSuggestionProvider(proxyServer))
                    .executes(context -> {
                        executeInfoSingleServer(context.getSource(), StringArgumentType.getString(context, "server"));
                        return SINGLE_SUCCESS;
                    })
                    .build()
            );
            serverNode.addChild(infoNode);
        });
    }

    private void executeInfoSingleServer(CommandSource source, String serverNameArg) {
        Optional<RegisteredServer> optionalServer = proxyServer.getServer(serverNameArg);
        if (optionalServer.isPresent()) {
            RegisteredServer registeredServer = optionalServer.get();
            String serverDisplayName = configFileLoader.getServerDisplayName(registeredServer.getServerInfo().getName());
            registeredServer.ping().whenComplete((pingResult, throwable) -> {
                if (throwable != null || pingResult == null) {
                    source.sendMessage(miniMessage.deserialize(languageLoader.getMessage("commands.server.unreachable").replace("{server}", serverDisplayName)));
                    return;
                }
                String motd = PlainTextComponentSerializer.plainText().serialize(pingResult.getDescriptionComponent());
                String version = pingResult.getVersion() != null ? pingResult.getVersion().getName() : languageLoader.getMessage("global.unknown_version");
                int onlinePlayers = 0;
                int maxPlayers = 0;
                Optional<ServerPing.Players> playersOptional = pingResult.getPlayers();
                if (playersOptional.isPresent()) {
                    ServerPing.Players players = playersOptional.get();
                    onlinePlayers = players.getOnline();
                    maxPlayers = players.getMax();
                }
                String infoMessage = languageLoader.getMessage("commands.server.info.specific_format") // specific_format 现在包含了 header
                        .replace("{server_name}", serverNameArg)
                        .replace("{server_display_name}", serverDisplayName)
                        .replace("{version}", version)
                        .replace("{online_players}", String.valueOf(onlinePlayers))
                        .replace("{max_players}", String.valueOf(maxPlayers))
                        .replace("{motd}", motd);
                source.sendMessage(miniMessage.deserialize(infoMessage));
            }).exceptionally(ex -> {
                source.sendMessage(miniMessage.deserialize(languageLoader.getMessage("commands.server.unreachable").replace("{server}", serverDisplayName)));
                return null;
            });
        } else {
            source.sendMessage(miniMessage.deserialize(languageLoader.getMessage("commands.server.not_found").replace("{server}", serverNameArg)));
        }
    }

    private void executeInfoAll(CommandSource source) {
        AtomicInteger totalOnlinePlayers = new AtomicInteger(0);
        AtomicInteger runningServersCount = new AtomicInteger(0);
        AtomicInteger offlineServersCount = new AtomicInteger(0);
        List<CompletableFuture<String>> serverStatusFutures = proxyServer.getAllServers().stream()
                .map(registeredServer -> {
                    String serverName = registeredServer.getServerInfo().getName();
                    String serverDisplayName = configFileLoader.getServerDisplayName(serverName);
                    return registeredServer.ping()
                            .thenApply(pingResult -> {
                                String statusMessage;
                                String onlinePlayersDisplay;
                                int playersOnline = 0;
                                if (pingResult != null) {
                                    runningServersCount.incrementAndGet();
                                    Optional<ServerPing.Players> playersOptional = pingResult.getPlayers();
                                    if (playersOptional.isPresent()) {
                                        playersOnline = playersOptional.get().getOnline();
                                        totalOnlinePlayers.addAndGet(playersOnline);
                                    }
                                    statusMessage = languageLoader.getMessage("commands.server.info.status_online");
                                    onlinePlayersDisplay = String.valueOf(playersOnline);
                                } else {
                                    offlineServersCount.incrementAndGet();
                                    statusMessage = languageLoader.getMessage("commands.server.info.status_offline");
                                    onlinePlayersDisplay = languageLoader.getMessage("commands.server.info.no_players_online_info");
                                }
                                return languageLoader.getMessage("commands.server.info.server_status_list_format")
                                        .replace("{server_name}", serverName)
                                        .replace("{server_display_name}", serverDisplayName)
                                        .replace("{status}", statusMessage)
                                        .replace("{online_players}", onlinePlayersDisplay);
                            })
                            .exceptionally(ex -> {
                                offlineServersCount.incrementAndGet();
                                return languageLoader.getMessage("commands.server.info.server_status_list_format")
                                        .replace("{server_name}", serverName)
                                        .replace("{server_display_name}", serverDisplayName)
                                        .replace("{status}", languageLoader.getMessage("commands.server.info.status_offline"))
                                        .replace("{online_players}", languageLoader.getMessage("commands.server.info.no_players_online_info"));
                            });
                })
                .collect(Collectors.toList());
        CompletableFuture.allOf(serverStatusFutures.toArray(new CompletableFuture[0]))
                .thenAccept(v -> {
                    String serverStatusList = serverStatusFutures.stream()
                            .map(CompletableFuture::join)
                            .collect(Collectors.joining("\n"));
                    String proxyVersion = proxyServer.getVersion().getName() + " " + proxyServer.getVersion().getVersion();
                    String allFormat = languageLoader.getMessage("commands.server.info.all_format")
                            .replace("{proxy_version}", proxyVersion)
                            .replace("{total_player}", String.valueOf(totalOnlinePlayers.get()))
                            .replace("{server_count}", String.valueOf(proxyServer.getAllServers().size()))
                            .replace("{running_servers}", String.valueOf(runningServersCount.get()))
                            .replace("{offline_servers}", String.valueOf(offlineServersCount.get()))
                            .replace("{server_status_list}", serverStatusList);
                    source.sendMessage(miniMessage.deserialize(allFormat));
                });
    }

    static class ServerNameSuggestionProvider implements SuggestionProvider<CommandSource> {
        private final ProxyServer proxyServer;

        public ServerNameSuggestionProvider(ProxyServer proxyServer) {
            this.proxyServer = proxyServer;
        }

        @Override
        public CompletableFuture<Suggestions> getSuggestions(CommandContext<CommandSource> context, SuggestionsBuilder builder) {
            String remaining = builder.getRemaining().toLowerCase();
            proxyServer.getAllServers().stream()
                    .map(server -> server.getServerInfo().getName())
                    .filter(name -> name.toLowerCase().startsWith(remaining))
                    .sorted()
                    .forEach(builder::suggest);
            return builder.buildFuture();
        }
    }
}