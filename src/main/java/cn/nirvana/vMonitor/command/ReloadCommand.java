package cn.nirvana.vMonitor.command;

import cn.nirvana.vMonitor.loader.ConfigFileLoader;
import cn.nirvana.vMonitor.loader.LanguageFileLoader;

import com.velocitypowered.api.command.CommandSource;

import net.kyori.adventure.text.minimessage.MiniMessage;

public class ReloadCommand {
    private final ConfigFileLoader configFileLoader;
    private final LanguageFileLoader languageFileLoader;
    private final MiniMessage miniMessage;

    public ReloadCommand(ConfigFileLoader configFileLoader, LanguageFileLoader languageFileLoader, MiniMessage miniMessage) {
        this.configFileLoader = configFileLoader;
        this.languageFileLoader = languageFileLoader;
        this.miniMessage = miniMessage;
    }

    public void execute(CommandSource source) {
        if (configFileLoader != null) {
            configFileLoader.loadConfig();
        } else {
            source.sendMessage(miniMessage.deserialize("<red>Configuration loader not available for reload.</red>"));
        }
        languageFileLoader.loadLanguage();
        source.sendMessage(miniMessage.deserialize(languageFileLoader.getMessage("global.reload_success")));
    }
}