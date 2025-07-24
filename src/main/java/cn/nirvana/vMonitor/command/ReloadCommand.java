package cn.nirvana.vMonitor.command;

import cn.nirvana.vMonitor.command_module.ReloadModule;
import cn.nirvana.vMonitor.util.CommandUtil;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

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
        commandUtil.registerSubCommand(root -> {
            root.then(LiteralArgumentBuilder.<CommandSource>literal("reload")
                    .requires(source -> source.hasPermission("vmonitor.reload"))
                    .executes(context -> {
                        reloadModule.executeReload(context.getSource());
                        return SINGLE_SUCCESS;
                    })
            );
        });
    }
}
