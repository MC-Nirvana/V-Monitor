package cn.nirvana.vMonitor.command_module;

import cn.nirvana.vMonitor.loader.LanguageFileLoader;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.PluginDescription; // 确保导入
import com.velocitypowered.api.proxy.ProxyServer;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class PluginListModule {
    private final ProxyServer proxyServer;
    private final LanguageFileLoader languageFileLoader;
    private final MiniMessage miniMessage;

    public PluginListModule(ProxyServer proxyServer, LanguageFileLoader languageFileLoader, MiniMessage miniMessage) {
        this.proxyServer = proxyServer;
        this.languageFileLoader = languageFileLoader;
        this.miniMessage = miniMessage;
    }

    public void executePluginList(CommandSource source) {
        // 解决 'ProxyServer' 中无法解析 'getAllPlugins' 的问题
        List<PluginContainer> plugins = proxyServer.getPluginManager().getPlugins().stream() // 使用 getPluginManager().getPlugins()
                .sorted(Comparator.comparing(p -> p.getDescription().getName().orElse(p.getDescription().getId()))) // 'T' 中的 getDescription 应该不再有问题
                .collect(Collectors.toList());

        if (plugins.isEmpty()) {
            source.sendMessage(miniMessage.deserialize(languageFileLoader.getMessage("commands.plugin.empty_list")));
            return;
        }

        StringBuilder pluginEntries = new StringBuilder();
        // 获取语言文件中的格式字符串
        String pluginListFormat = languageFileLoader.getMessage("commands.plugin.list.format");
        String pluginLineFormat = languageFileLoader.getMessage("commands.plugin.list.plugin_line");
        String pluginListHoverFormat = languageFileLoader.getMessage("commands.plugin.list.hover_format");

        for (PluginContainer plugin : plugins) {
            PluginDescription description = plugin.getDescription();
            String id = description.getId();
            String name = description.getName().orElse(id);
            String version = description.getVersion().orElse(languageFileLoader.getMessage("commands.plugin.list.no_veesion"));
            String url = description.getUrl().orElse(languageFileLoader.getMessage("commands.plugin.list.no_url"));
            String descriptionText = description.getDescription().orElse(languageFileLoader.getMessage("commands.plugin.list.no_description"));
            String authors = String.join(", ", description.getAuthors());
            if (authors.isEmpty()) {
                authors = languageFileLoader.getMessage("commands.plugin.list.no_authors");
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