package cn.nirvana.vMonitor.command;

import cn.nirvana.vMonitor.loader.LanguageFileLoader;

import com.mojang.brigadier.tree.LiteralCommandNode;

import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.PluginDescription;
import com.velocitypowered.api.proxy.ProxyServer;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public class PluginListCommand {
    private final ProxyServer proxyServer;
    private final LanguageFileLoader languageFileLoader;
    private final MiniMessage miniMessage;
    private final CommandRegistrar commandRegistrar;

    public PluginListCommand(ProxyServer proxyServer, LanguageFileLoader languageFileLoader,
                             MiniMessage miniMessage, CommandRegistrar commandRegistrar) {
        this.proxyServer = proxyServer;
        this.languageFileLoader = languageFileLoader;
        this.miniMessage = miniMessage;
        this.commandRegistrar = commandRegistrar;
        registerPluginListCommand();
    }

    private void registerPluginListCommand() {
        LiteralCommandNode<CommandSource> listNode = BrigadierCommand.literalArgumentBuilder("list")
                .requires(source -> source.hasPermission("vmonitor.plugin"))
                .executes(context -> {
                    execute(context.getSource());
                    return SINGLE_SUCCESS;
                })
                .build();
        commandRegistrar.registerPluginSubCommand(pluginNode -> {
            pluginNode.addChild(listNode);
        });
    }

    public void execute(CommandSource source) {
        List<PluginContainer> plugins = new ArrayList<>(proxyServer.getPluginManager().getPlugins());
        plugins.sort(Comparator.comparing(p -> p.getDescription().getName().orElse(p.getDescription().getId())));
        String pluginListFormat = languageFileLoader.getMessage("commands.plugin.list.format");
        String pluginLineFormat = languageFileLoader.getMessage("commands.plugin.list.plugin_line");
        String pluginListHoverFormat = languageFileLoader.getMessage("commands.plugin.list.hover_format");
        StringBuilder pluginEntries = new StringBuilder();
        for (PluginContainer plugin : plugins) {
            PluginDescription description = plugin.getDescription();
            String id = description.getId();
            String name = description.getName().orElse(id);
            String version = description.getVersion().orElse(languageFileLoader.getMessage("global.unknown_version"));
            String url = description.getUrl().orElse(languageFileLoader.getMessage("commands.plugin.no_url"));
            String descriptionText = description.getDescription().orElse(languageFileLoader.getMessage("commands.plugin.no_description"));
            String authors = String.join(", ", description.getAuthors());
            if (authors.isEmpty()) {
                authors = languageFileLoader.getMessage("commands.plugin.no_authors");
            }
            String entryLine = pluginLineFormat
                    .replace("{plugin_name}", name)
                    .replace("{plugin_version}", version);
            String filledHoverText = pluginListHoverFormat
                    .replace("{id}", id)
                    .replace("{name}", name)
                    .replace("{version}", version)
                    .replace("{url}", url)
                    .replace("{description}", descriptionText)
                    .replace("{authors}", authors);
            Component finalEntryComponent = miniMessage.deserialize(entryLine)
                    .hoverEvent(HoverEvent.showText(miniMessage.deserialize(filledHoverText)))
                    .clickEvent(ClickEvent.runCommand("/vmonitor plugin info " + id));
            pluginEntries.append(miniMessage.serialize(finalEntryComponent)).append("\n");
        }
        String finalMessage = pluginListFormat
                .replace("{count}", String.valueOf(plugins.size()))
                .replace("{plugin_list}", pluginEntries.toString().trim());
        source.sendMessage(miniMessage.deserialize(finalMessage));
    }
}