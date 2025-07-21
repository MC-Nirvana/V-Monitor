// ServerCommand.java (更新后的代码)

package cn.nirvana.vMonitor.command;

import cn.nirvana.vMonitor.loader.ConfigFileLoader;
import cn.nirvana.vMonitor.loader.LanguageFileLoader;
import cn.nirvana.vMonitor.module.ServerInfoModule;
import cn.nirvana.vMonitor.module.ServerListModule;
import cn.nirvana.vMonitor.util.CommandUtil;
import cn.nirvana.vMonitor.module.HelpModule; // 导入 HelpModule

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer; // 确保导入

import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public class ServerCommand {
    private final CommandUtil commandUtil;
    private final ProxyServer proxyServer;
    private final LanguageFileLoader languageFileLoader;
    private final MiniMessage miniMessage;
    private final ServerListModule serverListModule;
    private final ServerInfoModule serverInfoModule;
    private final ConfigFileLoader configFileLoader; // 需要ConfigFileLoader来获取服务器显示名称
    private final HelpModule helpModule; // 注入 HelpModule

    public ServerCommand(CommandUtil commandUtil, ProxyServer proxyServer,
                         LanguageFileLoader languageFileLoader, MiniMessage miniMessage,
                         ServerListModule serverListModule, ServerInfoModule serverInfoModule,
                         ConfigFileLoader configFileLoader, HelpModule helpModule) { // 接收 HelpModule 实例
        this.commandUtil = commandUtil;
        this.proxyServer = proxyServer;
        this.languageFileLoader = languageFileLoader;
        this.miniMessage = miniMessage;
        this.serverListModule = serverListModule;
        this.serverInfoModule = serverInfoModule;
        this.configFileLoader = configFileLoader;
        this.helpModule = helpModule; // 赋值
        registerServerCommand();
    }

    private void registerServerCommand() {
        commandUtil.registerSubCommand(rootNode -> {
            LiteralCommandNode<CommandSource> serverNode = LiteralArgumentBuilder.<CommandSource>literal("server")
                    // .requires(source -> source.hasPermission("vmonitor.server")) // 如果需要，可以取消注释
                    .executes(context -> {
                        // 当只输入 /vmonitor server 时，显示 server 命令的帮助信息
                        helpModule.executeServerHelp(context.getSource()); // 调用 HelpModule 的方法
                        return SINGLE_SUCCESS;
                    })
                    .then(LiteralArgumentBuilder.<CommandSource>literal("list")
                            .requires(source -> source.hasPermission("vmonitor.server.list"))
                            .executes(context -> {
                                serverListModule.executeListAll(context.getSource());
                                return SINGLE_SUCCESS;
                            })
                            .then(RequiredArgumentBuilder.<CommandSource, String>argument("server_name", word())
                                    .suggests(new ServerNameSuggestionProvider(proxyServer))
                                    .executes(context -> {
                                        // 当输入 /vmonitor server list <server_name> 时
                                        String serverName = context.getArgument("server_name", String.class);
                                        serverListModule.executeListPlayersOnServer(context.getSource(), serverName);
                                        return SINGLE_SUCCESS;
                                    })
                            )
                    )
                    .then(LiteralArgumentBuilder.<CommandSource>literal("info")
                            .requires(source -> source.hasPermission("vmonitor.server.info"))
                            .executes(context -> {
                                // 当只输入 /vmonitor server info 时，显示所有服务器信息
                                serverInfoModule.executeInfoAll(context.getSource());
                                return SINGLE_SUCCESS;
                            })
                            .then(RequiredArgumentBuilder.<CommandSource, String>argument("server_name", word())
                                    .suggests(new ServerNameSuggestionProvider(proxyServer))
                                    .executes(context -> {
                                        // 当输入 /vmonitor server info <server_name> 时
                                        String serverName = context.getArgument("server_name", String.class);
                                        if ("all".equalsIgnoreCase(serverName)) {
                                            serverInfoModule.executeInfoAll(context.getSource());
                                        } else {
                                            serverInfoModule.executeInfoSingleServer(context.getSource(), serverName);
                                        }
                                        return SINGLE_SUCCESS;
                                    })
                            )
                    )
                    .build();
            rootNode.addChild(serverNode);
        });
    }

    // 服务器名称自动补全提供者 (保持不变)
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
                    .forEach(name -> builder.suggest(name)); // 明确调用 suggest(String)
            builder.suggest("all"); // 确保 "all" 选项也可用
            return builder.buildFuture();
        }
    }
}