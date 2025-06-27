package cn.nirvana.vMonitor.command;

import cn.nirvana.vMonitor.loader.LanguageFileLoader;

import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;

import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.PluginDescription;
import com.velocitypowered.api.proxy.ProxyServer;

import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public class PluginInfoCommand {
    private final ProxyServer proxyServer;
    private final LanguageFileLoader languageFileLoader;
    private final MiniMessage miniMessage;
    private final CommandRegistrar commandRegistrar;

    public PluginInfoCommand(ProxyServer proxyServer, LanguageFileLoader languageFileLoader,
                             MiniMessage miniMessage, CommandRegistrar commandRegistrar) {
        this.proxyServer = proxyServer;
        this.languageFileLoader = languageFileLoader;
        this.miniMessage = miniMessage;
        this.commandRegistrar = commandRegistrar;
        registerPluginInfoCommand();
    }

    private void registerPluginInfoCommand() {
        SuggestionProvider<CommandSource> pluginIdSuggestionProvider = (context, builder) -> {
            String partialPluginId = builder.getRemaining();
            List<String> suggestions = proxyServer.getPluginManager().getPlugins().stream()
                    .map(plugin -> plugin.getDescription().getId())
                    .filter(id -> id.toLowerCase().startsWith(partialPluginId.toLowerCase()))
                    .collect(Collectors.toList());
            if ("all".startsWith(partialPluginId.toLowerCase())) {
                suggestions.add("all");
            }
            return CompletableFuture.completedFuture(suggestions)
                    .thenApply(list -> {
                        list.forEach(builder::suggest);
                        return builder.build();
                    });
        };
        RequiredArgumentBuilder<CommandSource, String> pluginArgument = BrigadierCommand.requiredArgumentBuilder("plugin", word())
                .suggests(pluginIdSuggestionProvider)
                .executes(context -> {
                    String pluginId = context.getArgument("plugin", String.class);
                    execute(context.getSource(), pluginId);
                    return SINGLE_SUCCESS;
                });
        LiteralCommandNode<CommandSource> infoNode = BrigadierCommand.literalArgumentBuilder("info")
                .requires(source -> source.hasPermission("vmonitor.plugin"))
                .executes(context -> {
                    context.getSource().sendMessage(miniMessage.deserialize(languageFileLoader.getMessage("commands.plugin.usage.info")));
                    return SINGLE_SUCCESS;
                })
                .then(pluginArgument)
                .build();
        commandRegistrar.registerPluginSubCommand(pluginNode -> {
            pluginNode.addChild(infoNode);
        });
    }

    public void execute(CommandSource source, String pluginId) {
        if ("all".equalsIgnoreCase(pluginId)) {
            List<PluginContainer> plugins = new ArrayList<>(proxyServer.getPluginManager().getPlugins());
            plugins.sort(Comparator.comparing(p -> p.getDescription().getName().orElse(p.getDescription().getId())));
            String pluginInfoFormat = languageFileLoader.getMessage("commands.plugin.info.format");
            for (PluginContainer plugin : plugins) {
                PluginDescription description = plugin.getDescription();
                String id = description.getId();
                String name = description.getName().orElse(id);
                String version = description.getVersion().orElse(languageFileLoader.getMessage("global.unknown_version"));
                String url = description.getUrl().orElse(languageFileLoader.getMessage("commands.plugin.no_url"));
                String desc = description.getDescription().orElse(languageFileLoader.getMessage("commands.plugin.no_description"));
                String authors = String.join(", ", description.getAuthors());
                if (authors.isEmpty()) {
                    authors = languageFileLoader.getMessage("commands.plugin.no_authors");
                }
                String filledInfo = pluginInfoFormat
                        .replace("{id}", id)
                        .replace("{name}", name)
                        .replace("{version}", version)
                        .replace("{url}", url)
                        .replace("{description}", desc)
                        .replace("{authors}", authors);
                source.sendMessage(miniMessage.deserialize(filledInfo));
            }
        } else {
            Optional<PluginContainer> plugin = proxyServer.getPluginManager().getPlugin(pluginId);
            if (plugin.isPresent()) {
                PluginDescription description = plugin.get().getDescription();
                String id = description.getId();
                String name = description.getName().orElse(id);
                String version = description.getVersion().orElse(languageFileLoader.getMessage("global.unknown_version"));
                String url = description.getUrl().orElse(languageFileLoader.getMessage("commands.plugin.no_url"));
                String desc = description.getDescription().orElse(languageFileLoader.getMessage("commands.plugin.no_description"));
                String authors = String.join(", ", description.getAuthors());
                if (authors.isEmpty()) {
                    authors = languageFileLoader.getMessage("commands.plugin.no_authors");
                }
                String infoMessage = languageFileLoader.getMessage("commands.plugin.info.format")
                        .replace("{id}", id)
                        .replace("{name}", name)
                        .replace("{version}", version)
                        .replace("{url}", url)
                        .replace("{description}", desc)
                        .replace("{authors}", authors);
                source.sendMessage(miniMessage.deserialize(infoMessage));
            } else {
                source.sendMessage(miniMessage.deserialize(languageFileLoader.getMessage("commands.plugin.not_found").replace("{plugin}", pluginId)));
            }
        }
    }
}