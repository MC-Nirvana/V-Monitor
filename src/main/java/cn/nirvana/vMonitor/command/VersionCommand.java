package cn.nirvana.vMonitor.command;

import cn.nirvana.vMonitor.command_module.VersionModule;
import cn.nirvana.vMonitor.util.CommandUtil;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import com.velocitypowered.api.command.CommandSource;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public class VersionCommand {
    private final CommandUtil commandUtil;
    private final VersionModule versionModule;

    public VersionCommand(CommandUtil commandUtil, VersionModule versionModule) {
        this.commandUtil = commandUtil;
        this.versionModule = versionModule;
        registerVersionCommand();
    }

    private void registerVersionCommand() {
        commandUtil.registerSubCommand(root -> {
            root.then(LiteralArgumentBuilder.<CommandSource>literal("version")
                    .requires(source -> source.hasPermission("vmonitor.admin"))
                    .executes(context -> {
                        versionModule.executeVersion(context.getSource());
                        return SINGLE_SUCCESS;
                    })
            );
        });
    }
}
