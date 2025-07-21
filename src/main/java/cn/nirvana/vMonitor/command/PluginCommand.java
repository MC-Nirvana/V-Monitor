package cn.nirvana.vMonitor.command;

import cn.nirvana.vMonitor.loader.LanguageFileLoader;
import cn.nirvana.vMonitor.module.PluginInfoModule;
import cn.nirvana.vMonitor.module.PluginListModule;
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
import com.velocitypowered.api.plugin.PluginContainer; // 导入 PluginContainer
import com.velocitypowered.api.proxy.ProxyServer;

import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors; // 确保导入这个

import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public class PluginCommand {
    private final CommandUtil commandUtil;
    private final ProxyServer proxyServer;
    private final LanguageFileLoader languageFileLoader;
    private final MiniMessage miniMessage;
    private final PluginListModule pluginListModule;
    private final PluginInfoModule pluginInfoModule;
    private final HelpModule helpModule; // 注入 HelpModule

    public PluginCommand(CommandUtil commandUtil, ProxyServer proxyServer,
                         LanguageFileLoader languageFileLoader, MiniMessage miniMessage,
                         PluginListModule pluginListModule, PluginInfoModule pluginInfoModule,
                         HelpModule helpModule) { // 接收 HelpModule 实例
        this.commandUtil = commandUtil;
        this.proxyServer = proxyServer;
        this.languageFileLoader = languageFileLoader;
        this.miniMessage = miniMessage;
        this.pluginListModule = pluginListModule;
        this.pluginInfoModule = pluginInfoModule;
        this.helpModule = helpModule; // 赋值
        registerPluginCommand();
    }

    private void registerPluginCommand() {
        commandUtil.registerSubCommand(rootNode -> {
            LiteralCommandNode<CommandSource> pluginNode = LiteralArgumentBuilder.<CommandSource>literal("plugin")
                    .requires(source -> source.hasPermission("vmonitor.plugin")) // 如果需要，可以取消注释
                    .executes(context -> {
                        // 当只输入 /vmonitor plugin 时，显示 plugin 命令的帮助信息
                        helpModule.executePluginHelp(context.getSource()); // 调用 HelpModule 的方法
                        return SINGLE_SUCCESS;
                    })
                    .then(LiteralArgumentBuilder.<CommandSource>literal("list")
                            .requires(source -> source.hasPermission("vmonitor.plugin"))
                            .executes(context -> {
                                pluginListModule.executePluginList(context.getSource());
                                return SINGLE_SUCCESS;
                            })
                    )
                    .then(LiteralArgumentBuilder.<CommandSource>literal("info")
                            .requires(source -> source.hasPermission("vmonitor.plugin"))
                            .executes(context -> {
                                // 当只输入 /vmonitor plugin info 时，显示所有插件信息
                                pluginInfoModule.executePluginInfoAll(context.getSource());
                                return SINGLE_SUCCESS;
                            })
                            .then(RequiredArgumentBuilder.<CommandSource, String>argument("plugin_id", word())
                                    .suggests(new PluginIdSuggestionProvider(proxyServer)) // 使用改进的 SuggestionProvider
                                    .executes(context -> {
                                        // 当输入 /vmonitor plugin info <plugin_id> 时
                                        String pluginId = context.getArgument("plugin_id", String.class);
                                        if ("all".equalsIgnoreCase(pluginId)) {
                                            pluginInfoModule.executePluginInfoAll(context.getSource());
                                        } else {
                                            pluginInfoModule.executePluginInfoSingle(context.getSource(), pluginId);
                                        }
                                        return SINGLE_SUCCESS;
                                    })
                            )
                    )
                    .build();
            rootNode.addChild(pluginNode);
        });
    }

    // 插件ID自动补全提供者
    static class PluginIdSuggestionProvider implements SuggestionProvider<CommandSource> {
        private final ProxyServer proxyServer;

        public PluginIdSuggestionProvider(ProxyServer proxyServer) {
            this.proxyServer = proxyServer;
        }

        @Override
        public CompletableFuture<Suggestions> getSuggestions(CommandContext<CommandSource> context, SuggestionsBuilder builder) {
            String remaining = builder.getRemaining().toLowerCase();
            // 解决无法解析 ProxyServer.getAllPlugins()
            proxyServer.getPluginManager().getPlugins().stream() // 使用 getPluginManager().getPlugins()
                    .map(plugin -> plugin.getDescription().getId()) // getDescription() 在 PluginContainer 上是正确的
                    .filter(id -> id.toLowerCase().startsWith(remaining)) // toLowerCase() 在 String 上是正确的
                    .sorted()
                    .forEach(id -> builder.suggest(id)); // 明确调用 suggest(String)
            builder.suggest("all"); // 确保 "all" 选项也可用
            return builder.buildFuture();
        }
    }
}