package cn.nirvana.vMonitor.command;

import cn.nirvana.vMonitor.loader.LanguageFileLoader;
import cn.nirvana.vMonitor.module.HelpModule;
import cn.nirvana.vMonitor.util.CommandUtil;

import com.mojang.brigadier.builder.LiteralArgumentBuilder; // 导入 LiteralArgumentBuilder
import com.mojang.brigadier.tree.LiteralCommandNode;

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

        // 定义根命令的执行逻辑，现在 Consumer 接收 LiteralArgumentBuilder
        Consumer<LiteralArgumentBuilder<CommandSource>> rootCommandConfigurer = (rootBuilder) -> {
            // 在 builder 上调用 executes
            rootBuilder.executes(context -> {
                helpModule.executeHelp(context.getSource());
                return SINGLE_SUCCESS;
            });
        };

        // 将配置器传递给 CommandUtil
        commandUtil.initializeRootCommand(rootLiteral, aliases, rootCommandConfigurer);
    }
}