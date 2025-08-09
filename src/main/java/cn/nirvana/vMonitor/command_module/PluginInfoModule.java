package cn.nirvana.vMonitor.command_module;

import cn.nirvana.vMonitor.loader.LanguageLoader;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.PluginDescription;
import com.velocitypowered.api.proxy.ProxyServer;

import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class PluginInfoModule {
    private final ProxyServer proxyServer;
    private final LanguageLoader languageLoader;
    private final MiniMessage miniMessage;

    public PluginInfoModule(ProxyServer proxyServer, LanguageLoader languageLoader, MiniMessage miniMessage) {
        this.proxyServer = proxyServer;
        this.languageLoader = languageLoader;
        this.miniMessage = miniMessage;
    }

    public void executePluginInfoAll(CommandSource source) {
        List<PluginContainer> plugins = proxyServer.getPluginManager().getPlugins().stream()
                .sorted(Comparator.comparing(p -> p.getDescription().getName().orElse(p.getDescription().getId())))
                .collect(Collectors.toList());

        if (plugins.isEmpty()) {
            source.sendMessage(miniMessage.deserialize(languageLoader.getMessage("commands.plugin.empty_list")));
            return;
        }

        StringBuilder pluginInfoList = new StringBuilder();

        // 获取插件信息格式字符串
        String pluginInfoFormat = languageLoader.getMessage("commands.plugin.info.format");

        // 获取 all_header
        String allPluginsHeader = languageLoader.getMessage("commands.plugin.info.all_header");

        for (PluginContainer plugin : plugins) {
            PluginDescription description = plugin.getDescription();

            String id = description.getId();
            String name = description.getName().orElse(id);
            String version = description.getVersion().orElse(languageLoader.getMessage("commands.plugin.info.no_version"));
            String url = description.getUrl().orElse(languageLoader.getMessage("commands.plugin.info.no_url"));
            String desc = description.getDescription().orElse(languageLoader.getMessage("commands.plugin.info.no_description"));
            String authors = String.join(", ", description.getAuthors());
            if (authors.isEmpty()) {
                authors = languageLoader.getMessage("commands.plugin.info.no_authors");
            }

            String pluginEntry = pluginInfoFormat
                    .replace("{id}", id)
                    .replace("{name}", name)
                    .replace("{version}", version)
                    .replace("{url}", url)
                    .replace("{description}", desc)
                    .replace("{authors}", authors);

            pluginInfoList.append(miniMessage.serialize(miniMessage.deserialize(pluginEntry))).append("\n");
        }

        String finalMessage = allPluginsHeader
                .replace("{plugin_list}", pluginInfoList.toString().trim());

        source.sendMessage(miniMessage.deserialize(finalMessage));
    }

    public void executePluginInfoSingle(CommandSource source, String pluginId) {
        // 解决 'ProxyServer' 中无法解析 'getPluginContainer' 的问题
        Optional<PluginContainer> plugin = proxyServer.getPluginManager().getPlugin(pluginId); // 使用 getPluginManager().getPlugin(id)
        if (plugin.isPresent()) {
            PluginDescription description = plugin.get().getDescription();
            String id = description.getId();
            String name = description.getName().orElse(id);
            String version = description.getVersion().orElse(languageLoader.getMessage("commands.plugin.info.no_version"));
            String url = description.getUrl().orElse(languageLoader.getMessage("commands.plugin.info.no_url"));
            String desc = description.getDescription().orElse(languageLoader.getMessage("commands.plugin.info.no_description"));
            String authors = String.join(", ", description.getAuthors());
            if (authors.isEmpty()) {
                authors = languageLoader.getMessage("commands.plugin.info.no_authors");
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