package cn.nirvana.vMonitor.command;

import cn.nirvana.vMonitor.config.ConfigFileLoader;
import cn.nirvana.vMonitor.config.LanguageLoader;

import com.velocitypowered.api.command.CommandSource;

import net.kyori.adventure.text.minimessage.MiniMessage;

public class ReloadCommand {
    private final ConfigFileLoader configFileLoader;
    private final LanguageLoader languageLoader;
    private final MiniMessage miniMessage;

    public ReloadCommand(ConfigFileLoader configFileLoader, LanguageLoader languageLoader, MiniMessage miniMessage) {
        this.configFileLoader = configFileLoader;
        this.languageLoader = languageLoader;
        this.miniMessage = miniMessage;
    }

    public void execute(CommandSource source) {
        if (configFileLoader != null) {
            configFileLoader.loadConfig();
        } else {
            source.sendMessage(miniMessage.deserialize("<red>Configuration loader not available for reload.</red>"));
        }
        languageLoader.loadLanguage();
        source.sendMessage(miniMessage.deserialize(languageLoader.getMessage("global.reload_success")));
    }
}