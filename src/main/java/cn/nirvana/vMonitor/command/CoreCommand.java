package cn.nirvana.vMonitor.command;

import cn.nirvana.vMonitor.loader.LanguageFileLoader;
import cn.nirvana.vMonitor.command_module.HelpModule;
import cn.nirvana.vMonitor.util.CommandUtil;

import com.mojang.brigadier.builder.LiteralArgumentBuilder; // 导入 LiteralArgumentBuilder

import com.velocitypowered.api.command.CommandSource;

import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public class CoreCommand {
    private final LanguageFileLoader languageFileLoader;
    private final MiniMessage miniMessage;
    private final CommandUtil commandUtil;
    private final HelpModule helpModule;

    public CoreCommand(LanguageFileLoader languageFileLoader, MiniMessage miniMessage,
                       CommandUtil commandUtil, HelpModule helpModule) {
        this.languageFileLoader = languageFileLoader;
        this.miniMessage = miniMessage;
        this.commandUtil = commandUtil;
        this.helpModule = helpModule;
        registerRootCommand();
    }

    private void registerRootCommand() {
        String rootLiteral = "vmonitor";
        Set<String> aliases = new HashSet<>(Arrays.asList("vm"));

        Consumer<LiteralArgumentBuilder<CommandSource>> rootConfigurer = root -> {
            root.executes(context -> {
                helpModule.executeHelp(context.getSource());
                return SINGLE_SUCCESS;
            });
        };

        commandUtil.initializeRootCommand(rootLiteral, aliases, rootConfigurer);
    }
}
