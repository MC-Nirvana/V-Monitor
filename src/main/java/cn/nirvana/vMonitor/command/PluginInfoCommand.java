package cn.nirvana.vMonitor.command;

import cn.nirvana.vMonitor.config.LanguageLoader;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;

import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.PluginDescription;
import com.velocitypowered.api.proxy.ProxyServer;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.event.ClickEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.Collection;

import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public class PluginInfoCommand {
    private final ProxyServer proxyServer;
    private final LanguageLoader languageLoader;
    private final MiniMessage miniMessage;
    private final CommandRegistrar commandRegistrar;

    public PluginInfoCommand(ProxyServer proxyServer, LanguageLoader languageLoader,
                             MiniMessage miniMessage, CommandRegistrar commandRegistrar) {
        this.proxyServer = proxyServer;
        this.languageLoader = languageLoader;
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
                .requires(source -> source.hasPermission("vmonitor.plugin.info"))
                .executes(context -> {
                    context.getSource().sendMessage(miniMessage.deserialize(languageLoader.getMessage("commands.plugin.usage.info")));
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
            String pluginInfoFormat = languageLoader.getMessage("commands.plugin.info.format");
            for (PluginContainer plugin : plugins) {
                PluginDescription description = plugin.getDescription();
                String id = description.getId();
                String name = description.getName().orElse(id);
                String version = description.getVersion().orElse(languageLoader.getMessage("global.unknown_version"));
                String url = description.getUrl().orElse(languageLoader.getMessage("commands.plugin.no_url"));
                String desc = description.getDescription().orElse(languageLoader.getMessage("commands.plugin.no_description"));
                String authors = String.join(", ", description.getAuthors());
                if (authors.isEmpty()) {
                    authors = languageLoader.getMessage("commands.plugin.no_authors");
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
                String version = description.getVersion().orElse(languageLoader.getMessage("global.unknown_version"));
                String url = description.getUrl().orElse(languageLoader.getMessage("commands.plugin.no_url"));
                String desc = description.getDescription().orElse(languageLoader.getMessage("commands.plugin.no_description"));
                String authors = String.join(", ", description.getAuthors());
                if (authors.isEmpty()) {
                    authors = languageLoader.getMessage("commands.plugin.no_authors");
                }
                String infoMessage = languageLoader.getMessage("commands.plugin.info.format")
                        .replace("{id}", id)
                        .replace("{name}", name)
                        .replace("{version}", version)
                        .replace("{url}", url)
                        .replace("{description}", desc)
                        .replace("{authors}", authors);
                source.sendMessage(miniMessage.deserialize(infoMessage));
            } else {
                source.sendMessage(miniMessage.deserialize(languageLoader.getMessage("commands.plugin.not_found").replace("{plugin}", pluginId)));
            }
        }
    }
}