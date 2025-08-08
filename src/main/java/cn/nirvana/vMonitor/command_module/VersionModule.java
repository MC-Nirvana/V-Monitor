package cn.nirvana.vMonitor.command_module;

import cn.nirvana.vMonitor.BuildConstants;
import cn.nirvana.vMonitor.loader.LanguageFileLoader;
import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.text.minimessage.MiniMessage;

public class VersionModule {
    private final LanguageFileLoader languageFileLoader;
    private final MiniMessage miniMessage;

    public VersionModule(LanguageFileLoader languageFileLoader, MiniMessage miniMessage) {
        this.languageFileLoader = languageFileLoader;
        this.miniMessage = miniMessage;
    }

    public void executeVersion(CommandSource source) {
        String versionMessage = languageFileLoader.getMessage("commands.version.format");
        versionMessage = versionMessage.replace("{plugin_version}", BuildConstants.VERSION);
        source.sendMessage(miniMessage.deserialize(versionMessage));
    }
}