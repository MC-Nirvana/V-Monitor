package cn.nirvana.vMonitor.command;

import cn.nirvana.vMonitor.loader.LanguageFileLoader;

import com.velocitypowered.api.command.CommandSource;

import net.kyori.adventure.text.minimessage.MiniMessage;

public class HelpCommand {
    private final LanguageFileLoader languageFileLoader;
    private final MiniMessage miniMessage;

    public HelpCommand(LanguageFileLoader languageFileLoader, MiniMessage miniMessage) {
        this.languageFileLoader = languageFileLoader;
        this.miniMessage = miniMessage;
    }

    public void execute(CommandSource source) {
        String helpMessage = languageFileLoader.getMessage("commands.help.all_format");
        if (helpMessage != null && !helpMessage.isEmpty() && !helpMessage.startsWith("<red>Missing Language Key:")) {
            source.sendMessage(miniMessage.deserialize(helpMessage));
        } else {
            source.sendMessage(miniMessage.deserialize("<red>No help message configured or key 'help-format' is missing in the language file.</red>"));
        }
    }
}