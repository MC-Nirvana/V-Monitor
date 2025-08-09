package cn.nirvana.vMonitor.command_module;

import cn.nirvana.vMonitor.BuildConstants;
import cn.nirvana.vMonitor.loader.LanguageLoader;

import com.velocitypowered.api.command.CommandSource;

import net.kyori.adventure.text.minimessage.MiniMessage;

public class VersionModule {
    private final LanguageLoader languageLoader;
    private final MiniMessage miniMessage;

    public VersionModule(LanguageLoader languageLoader, MiniMessage miniMessage) {
        this.languageLoader = languageLoader;
        this.miniMessage = miniMessage;
    }

    public void executeVersion(CommandSource source) {
        String versionMessage = languageLoader.getMessage("commands.version.format");
        versionMessage = versionMessage.replace("{plugin_version}", BuildConstants.VERSION);
        source.sendMessage(miniMessage.deserialize(versionMessage));
    }
}