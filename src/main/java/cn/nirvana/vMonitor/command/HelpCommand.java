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
        commandUtil.registerSubCommand(rootNode -> {
            LiteralCommandNode<CommandSource> helpNode = LiteralArgumentBuilder.<CommandSource>literal("help")
                    .executes(context -> {
                        helpModule.executeHelp(context.getSource()); // 委托给 HelpModule 处理执行逻辑
                        return SINGLE_SUCCESS;
                    })
                    .build();
            rootNode.addChild(helpNode);
        });
    }
}