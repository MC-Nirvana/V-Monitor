package cn.nirvana.vMonitor.command;

import cn.nirvana.vMonitor.VMonitor;
import cn.nirvana.vMonitor.loader.ConfigFileLoader;
import cn.nirvana.vMonitor.loader.LanguageFileLoader;
import cn.nirvana.vMonitor.command_module.ServerInfoModule;
import cn.nirvana.vMonitor.command_module.ServerListModule;
import cn.nirvana.vMonitor.util.CommandUtil;
import cn.nirvana.vMonitor.command_module.HelpModule;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;

import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.concurrent.CompletableFuture;

import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public class ServerCommand {
    private final CommandUtil commandUtil;
    private final ProxyServer proxyServer;
    private final LanguageFileLoader languageFileLoader;
    private final MiniMessage miniMessage;
    private final ServerListModule serverListModule;
    private final ServerInfoModule serverInfoModule;
    private final ConfigFileLoader configFileLoader;
    private final HelpModule helpModule;

    public ServerCommand(CommandUtil commandUtil, ProxyServer proxyServer,
                         LanguageFileLoader languageFileLoader, MiniMessage miniMessage,
                         ServerListModule serverListModule, ServerInfoModule serverInfoModule,
                         ConfigFileLoader configFileLoader, HelpModule helpModule,
                         VMonitor plugin) { // 添加 VMonitor 参数
        this.commandUtil = commandUtil;
        this.proxyServer = proxyServer;
        this.languageFileLoader = languageFileLoader;
        this.miniMessage = miniMessage;
        this.serverListModule = serverListModule;
        this.serverInfoModule = serverInfoModule;
        this.configFileLoader = configFileLoader;
        this.helpModule = helpModule;
        registerServerCommand();
    }

    private void registerServerCommand() {
        commandUtil.registerSubCommand(root -> {
            root.then(LiteralArgumentBuilder.<CommandSource>literal("server")
                    .executes(context -> {
                        helpModule.executeServerHelp(context.getSource());
                        return SINGLE_SUCCESS;
                    })
                    .then(LiteralArgumentBuilder.<CommandSource>literal("list")
                            // 修改此处：不直接执行 executeListAll
                            .executes(context -> {
                                // 提示用法
                                String usage = languageFileLoader.getMessage("commands.server.usage.list");
                                context.getSource().sendMessage(miniMessage.deserialize(usage));
                                return SINGLE_SUCCESS;
                            })
                            .then(RequiredArgumentBuilder.<CommandSource, String>argument("server_name", word())
                                    .suggests(new ServerNameSuggestionProvider(proxyServer))
                                    .executes(context -> {
                                        String name = context.getArgument("server_name", String.class);
                                        if ("all".equalsIgnoreCase(name)) {
                                            serverListModule.executeListAll(context.getSource());
                                        } else {
                                            serverListModule.executeListPlayersOnServer(context.getSource(), name);
                                        }
                                        return SINGLE_SUCCESS;
                                    })
                            )
                    )
                    .then(LiteralArgumentBuilder.<CommandSource>literal("info")
                            .executes(context -> {
                                String usage = languageFileLoader.getMessage("commands.server.usage.info");
                                context.getSource().sendMessage(miniMessage.deserialize(usage));
                                return SINGLE_SUCCESS;
                            })
                            .then(RequiredArgumentBuilder.<CommandSource, String>argument("server_name", word())
                                    .suggests(new ServerNameSuggestionProvider(proxyServer))
                                    .executes(context -> {
                                        String name = context.getArgument("server_name", String.class);
                                        if ("all".equalsIgnoreCase(name)) {
                                            serverInfoModule.executeInfoAll(context.getSource());
                                        } else {
                                            serverInfoModule.executeInfoSingleServer(context.getSource(), name);
                                        }
                                        return SINGLE_SUCCESS;
                                    })
                            )
                    )
            );
        });
    }

    // 保持 ServerNameSuggestionProvider 类不变
    class ServerNameSuggestionProvider implements SuggestionProvider<CommandSource> {
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
                    .forEach(name -> builder.suggest(name));
            builder.suggest("all");
            return builder.buildFuture();
        }
    }
}
