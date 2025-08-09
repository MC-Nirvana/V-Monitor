package cn.nirvana.vMonitor.command_module;

import cn.nirvana.vMonitor.loader.LanguageLoader;

import com.velocitypowered.api.command.CommandSource;

import net.kyori.adventure.text.minimessage.MiniMessage;

public class HelpModule {
    private final LanguageLoader languageLoader;
    private final MiniMessage miniMessage;

    public HelpModule(LanguageLoader languageLoader, MiniMessage miniMessage) {
        this.languageLoader = languageLoader;
        this.miniMessage = miniMessage;
    }

    /**
     * 显示主命令的帮助信息。
     * @param source 命令发送者
     */
    public void executeHelp(CommandSource source) {
        String helpMessage = languageLoader.getMessage("commands.help.all_format");
        if (helpMessage != null && !helpMessage.isEmpty() && !helpMessage.startsWith("<red>Missing Language Key:")) {
            source.sendMessage(miniMessage.deserialize(helpMessage));
        } else {
            source.sendMessage(miniMessage.deserialize("<red>No help message configured or key 'commands.help.all_format' is missing in the language file.</red>"));
        }
    }

    /**
     * 显示plugin命令的帮助信息。
     * @param source 命令发送者
     */
    public void executePluginHelp(CommandSource source) {
        String helpMessage = languageLoader.getMessage("commands.help.plugin_format");
        if (helpMessage != null && !helpMessage.isEmpty() && !helpMessage.startsWith("<red>Missing Language Key:")) {
            source.sendMessage(miniMessage.deserialize(helpMessage));
        } else {
            source.sendMessage(miniMessage.deserialize("<red>No plugin help message configured or key 'commands.help.plugin_format' is missing in the language file.</red>"));
        }
    }

    /**
     * 显示server命令的帮助信息。
     * @param source 命令发送者
     */
    public void executeServerHelp(CommandSource source) {
        String helpMessage = languageLoader.getMessage("commands.help.server_format");
         if (helpMessage != null && !helpMessage.isEmpty() && !helpMessage.startsWith("<red>Missing Language Key:")) {
             source.sendMessage(miniMessage.deserialize(helpMessage));
         } else {
             source.sendMessage(miniMessage.deserialize("<red>No server help message configured or key 'commands.help.server_format' is missing in the language file.</red>"));
         }
    }

    /**
     * 显示player命令的帮助信息。
     * @param source 命令发送者
     */
    public void executePlayerHelp(CommandSource source) {
        String helpMessage = languageLoader.getMessage("commands.help.player_format");
        if (helpMessage != null && !helpMessage.isEmpty() && !helpMessage.startsWith("<red>Missing Language Key:")) {
            source.sendMessage(miniMessage.deserialize(helpMessage));
        } else {
            source.sendMessage(miniMessage.deserialize("<red>No player help message configured or key 'commands.help.player_format' is missing in the language file.</red>"));
        }
    }
}