package cn.nirvana.vMonitor.module;

import cn.nirvana.vMonitor.loader.LanguageFileLoader;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.PluginDescription;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.plugin.PluginManager; // 导入 PluginManager

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.Component; // 确保导入 Component

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class PluginInfoModule {
    private final ProxyServer proxyServer;
    private final LanguageFileLoader languageFileLoader;
    private final MiniMessage miniMessage;

    public PluginInfoModule(ProxyServer proxyServer, LanguageFileLoader languageFileLoader, MiniMessage miniMessage) {
        this.proxyServer = proxyServer;
        this.languageFileLoader = languageFileLoader;
        this.miniMessage = miniMessage;
    }

    public void executePluginInfoAll(CommandSource source) {
        // 解决 'ProxyServer' 中无法解析 'getAllPlugins' 的问题
        List<PluginContainer> plugins = proxyServer.getPluginManager().getPlugins().stream() // 使用 getPluginManager().getPlugins()
                .sorted(Comparator.comparing(p -> p.getDescription().getName().orElse(p.getDescription().getId())))
                .collect(Collectors.toList());

        if (plugins.isEmpty()) {
            source.sendMessage(miniMessage.deserialize(languageFileLoader.getMessage("commands.plugin.no_plugins")));
            return;
        }

        StringBuilder pluginInfoList = new StringBuilder();
        String pluginInfoFormat = languageFileLoader.getMessage("commands.plugin.info.all_plugin_format"); // 获取单条插件信息的格式
        // 确保整体列表的格式也存在，例如 commands.plugin.info.all_format
        String allPluginsHeader = languageFileLoader.getMessage("commands.plugin.info.all_header");

        for (PluginContainer plugin : plugins) {
            PluginDescription description = plugin.getDescription(); // 'T' 中的 'getDescription' 应该不再有问题，因为 p 现在是 PluginContainer
            String id = description.getId();
            String name = description.getName().orElse(id);
            String version = description.getVersion().orElse(languageFileLoader.getMessage("global.unknown_version"));
            String entryLine = pluginInfoFormat
                    .replace("{plugin_name}", name)
                    .replace("{plugin_version}", version);
            pluginInfoList.append(miniMessage.serialize(miniMessage.deserialize(entryLine))).append("\n");
        }

        String finalMessage = allPluginsHeader
                .replace("{count}", String.valueOf(plugins.size()))
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