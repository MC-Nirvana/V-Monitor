package cn.nirvana.vMonitor.command;

import cn.nirvana.vMonitor.module.HelpModule;
import cn.nirvana.vMonitor.util.CommandUtil;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;

import com.velocitypowered.api.command.CommandSource;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public class HelpCommand {
    private final CommandUtil commandUtil;
    private final HelpModule helpModule;

    public HelpCommand(CommandUtil commandUtil, HelpModule helpModule) {
        this.commandUtil = commandUtil;
        this.helpModule = helpModule;
        registerHelpCommand();
    }

    private void registerHelpCommand() {
        commandUtil.registerSubCommand(root -> {
            root.then(LiteralArgumentBuilder.<CommandSource>literal("help")
                    .executes(context -> {
                        helpModule.executeHelp(context.getSource());
                        return SINGLE_SUCCESS;
                    })
            );
        });
    }
}
