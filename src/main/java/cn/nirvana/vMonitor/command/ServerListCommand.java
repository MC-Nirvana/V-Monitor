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
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;
import static com.mojang.brigadier.arguments.StringArgumentType.word;

public class ServerListCommand {
    private final ProxyServer proxyServer;
    private final ConfigFileLoader configFileLoader;
    private final LanguageLoader languageLoader;
    private final MiniMessage miniMessage;
    private final CommandRegistrar commandRegistrar;

    public ServerListCommand(ProxyServer proxyServer, ConfigFileLoader configFileLoader, LanguageLoader languageLoader,
                             MiniMessage miniMessage, CommandRegistrar commandRegistrar) {
        this.proxyServer = proxyServer;
        this.configFileLoader = configFileLoader;
        this.languageLoader = languageLoader;
        this.miniMessage = miniMessage;
        this.commandRegistrar = commandRegistrar;
        registerCommands();
    }

    private void registerCommands() {
        commandRegistrar.registerServerSubCommand(serverNode -> {
            LiteralCommandNode<CommandSource> listNode = LiteralArgumentBuilder.<CommandSource>literal("list")
                    .executes(context -> {
                        context.getSource().sendMessage(miniMessage.deserialize(languageLoader.getMessage("commands.server.usage.list")));
                        return SINGLE_SUCCESS;
                    })
                    .build();
            listNode.addChild(LiteralArgumentBuilder.<CommandSource>literal("all")
                    .executes(context -> {
                        executeListAll(context.getSource());
                        return SINGLE_SUCCESS;
                    })
                    .build()
            );
            listNode.addChild(RequiredArgumentBuilder.<CommandSource, String>argument("server", word())
                    .suggests(new ServerNameSuggestionProvider(proxyServer))
                    .executes(context -> {
                        executeListPlayersOnServer(context.getSource(), StringArgumentType.getString(context, "server"));
                        return SINGLE_SUCCESS;
                    })
                    .build()
            );
            serverNode.addChild(listNode);
        });
    }

    private void executeListAll(CommandSource source) {
        long totalOnlinePlayers = proxyServer.getAllServers().stream()
                .mapToLong(server -> server.getPlayersConnected().size())
                .sum();
        StringBuilder allPlayersListBuilder = new StringBuilder();
        proxyServer.getAllServers().forEach(registeredServer -> {
            String serverName = registeredServer.getServerInfo().getName();
            String serverDisplayName = configFileLoader.getServerDisplayName(serverName);
            Collection<Player> players = registeredServer.getPlayersConnected();
            String playersDisplay;
            if (players.isEmpty()) {
                playersDisplay = languageLoader.getMessage("commands.server.list.no_players_online_info");
            } else {
                playersDisplay = "<green>" + players.stream()
                        .map(Player::getUsername)
                        .collect(Collectors.joining(", ")) + "</green>";
            }
            String serverEntry = "<gold>" + serverDisplayName + "</gold>";

            allPlayersListBuilder.append(serverEntry).append(": ").append(playersDisplay).append("\n");
        });
        String allFormat = languageLoader.getMessage("commands.server.list.all_format")
                .replace("{online_players_count}", String.valueOf(totalOnlinePlayers))
                .replace("{all_players_list}", allPlayersListBuilder.toString().trim());
        source.sendMessage(miniMessage.deserialize(allFormat));
    }

    private void executeListPlayersOnServer(CommandSource source, String serverNameArg) {
        Optional<RegisteredServer> targetServer = proxyServer.getServer(serverNameArg);
        if (targetServer.isPresent()) {
            RegisteredServer registeredServer = targetServer.get();
            String serverDisplayName = configFileLoader.getServerDisplayName(serverNameArg);
            Collection<Player> players = registeredServer.getPlayersConnected();
            String specificPlayersListContent;
            if (players.isEmpty()) {
                specificPlayersListContent = languageLoader.getMessage("commands.server.list.no_players_online_info");
            } else {
                specificPlayersListContent = "<green>" + players.stream()
                        .map(Player::getUsername)
                        .collect(Collectors.joining(", ")) + "</green>";
            }
            String specificFormat = languageLoader.getMessage("commands.server.list.specific_format")
                    .replace("{server_display_name}", serverDisplayName)
                    .replace("{online_players_number}", String.valueOf(players.size()))
                    .replace("{specific_players_list}", specificPlayersListContent);
            source.sendMessage(miniMessage.deserialize(specificFormat));

        } else {
            source.sendMessage(miniMessage.deserialize(languageLoader.getMessage("commands.server.not_found").replace("{server}", serverNameArg)));
        }
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