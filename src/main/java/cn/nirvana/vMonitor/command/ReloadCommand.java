package cn.nirvana.vMonitor.command;

import cn.nirvana.vMonitor.module.ReloadModule;
import cn.nirvana.vMonitor.util.CommandUtil;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;

import com.velocitypowered.api.command.CommandSource;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public class ReloadCommand {
    private final CommandUtil commandUtil;
    private final ReloadModule reloadModule;

    public ReloadCommand(CommandUtil commandUtil, ReloadModule reloadModule) {
        this.commandUtil = commandUtil;
        this.reloadModule = reloadModule;
        registerReloadCommand();
    }

    private void registerReloadCommand() {
        commandUtil.registerSubCommand(rootNode -> {
            LiteralCommandNode<CommandSource> reloadNode = LiteralArgumentBuilder.<CommandSource>literal("reload")
                    .requires(source -> source.hasPermission("vmonitor.reload"))
                    .executes(context -> {
                        reloadModule.executeReload(context.getSource()); // 委托给 ReloadModule 处理执行逻辑
                        return SINGLE_SUCCESS;
                    })
                    .build();
            rootNode.addChild(reloadNode);
        });
    }
}