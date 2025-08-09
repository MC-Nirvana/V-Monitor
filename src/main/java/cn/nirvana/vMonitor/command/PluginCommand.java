package cn.nirvana.vMonitor.command;

import cn.nirvana.vMonitor.loader.LanguageLoader;
import cn.nirvana.vMonitor.command_module.PluginInfoModule;
import cn.nirvana.vMonitor.command_module.PluginListModule;
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

public class PluginCommand {
    private final CommandUtil commandUtil;
    private final ProxyServer proxyServer;
    private final LanguageLoader languageLoader;
    private final MiniMessage miniMessage;
    private final PluginListModule pluginListModule;
    private final PluginInfoModule pluginInfoModule;
    private final HelpModule helpModule;

    public PluginCommand(CommandUtil commandUtil, ProxyServer proxyServer,
                         LanguageLoader languageLoader, MiniMessage miniMessage,
                         PluginListModule pluginListModule, PluginInfoModule pluginInfoModule,
                         HelpModule helpModule) {
        this.commandUtil = commandUtil;
        this.proxyServer = proxyServer;
        this.languageLoader = languageLoader;
        this.miniMessage = miniMessage;
        this.pluginListModule = pluginListModule;
        this.pluginInfoModule = pluginInfoModule;
        this.helpModule = helpModule;
        registerPluginCommand();
    }

    private void registerPluginCommand() {
        commandUtil.registerSubCommand(root -> {
            root.then(LiteralArgumentBuilder.<CommandSource>literal("plugin")
                    .requires(source -> source.hasPermission("vmonitor.admin"))
                    .executes(context -> {
                        helpModule.executePluginHelp(context.getSource());
                        return SINGLE_SUCCESS;
                    })
                    .then(LiteralArgumentBuilder.<CommandSource>literal("list")
                            .executes(context -> {
                                pluginListModule.executePluginList(context.getSource());
                                return SINGLE_SUCCESS;
                            })
                    )
                    .then(LiteralArgumentBuilder.<CommandSource>literal("info")
                            // 修改此处：不直接执行 executePluginInfoAll
                            .executes(context -> {
                                // 提示用法
                                String usage = languageLoader.getMessage("commands.plugin.usage.info");
                                context.getSource().sendMessage(miniMessage.deserialize(usage));
                                return SINGLE_SUCCESS;
                            })
                            .then(RequiredArgumentBuilder.<CommandSource, String>argument("plugin_id", word())
                                    .suggests(new PluginIdSuggestionProvider(proxyServer))
                                    .executes(context -> {
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
            );
        });
    }

    // 插件ID自动补全提供者（保持不变）
    class PluginIdSuggestionProvider implements SuggestionProvider<CommandSource> {
        private final ProxyServer proxyServer;

        public PluginIdSuggestionProvider(ProxyServer proxyServer) {
            this.proxyServer = proxyServer;
        }

        @Override
        public CompletableFuture<Suggestions> getSuggestions(CommandContext<CommandSource> context, SuggestionsBuilder builder) {
            String remaining = builder.getRemaining().toLowerCase();
            proxyServer.getPluginManager().getPlugins().stream()
                    .map(plugin -> plugin.getDescription().getId())
                    .filter(id -> id.toLowerCase().startsWith(remaining))
                    .sorted()
                    .forEach(id -> builder.suggest(id));
            builder.suggest("all");
            return builder.buildFuture();
        }
    }
}
