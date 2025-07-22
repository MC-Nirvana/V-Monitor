package cn.nirvana.vMonitor.util;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;

import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.plugin.PluginContainer;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class CommandUtil {

    private final CommandManager commandManager;
    private final Logger logger;
    private final PluginContainer pluginContainer;

    private LiteralArgumentBuilder<CommandSource> rootBuilder;
    private String rootLiteral;
    private Set<String> rootAliases;

    private final List<Consumer<LiteralArgumentBuilder<CommandSource>>> subCommandBuilders = new ArrayList<>();

    public CommandUtil(CommandManager commandManager, Logger logger, PluginContainer pluginContainer) {
        this.commandManager = commandManager;
        this.logger = logger;
        this.pluginContainer = pluginContainer;
    }

    /**
     * 初始化根命令构建器（不注册）
     */
    public void initializeRootCommand(String rootLiteral, Set<String> rootAliases,
                                      Consumer<LiteralArgumentBuilder<CommandSource>> rootCommandConfigurer) {
        this.rootLiteral = rootLiteral;
        this.rootAliases = rootAliases;

        this.rootBuilder = LiteralArgumentBuilder.<CommandSource>literal(rootLiteral);
        rootCommandConfigurer.accept(rootBuilder);
    }

    /**
     * 注册子命令构建器（延迟构建）
     */
    public void registerSubCommand(Consumer<LiteralArgumentBuilder<CommandSource>> subCommandBuilder) {
        if (rootBuilder == null) {
            throw new IllegalStateException("Root command not initialized before registering sub-commands");
        }
        subCommandBuilders.add(subCommandBuilder);
    }

    /**
     * 构建完整命令树并注册到 Velocity
     */
    public void registerAllCommands() {
        if (rootBuilder == null) {
            throw new IllegalStateException("Root command not initialized before registration");
        }

        // 构建子命令
        for (Consumer<LiteralArgumentBuilder<CommandSource>> subCommandBuilder : subCommandBuilders) {
            subCommandBuilder.accept(rootBuilder);
        }

        LiteralCommandNode<CommandSource> commandTree = rootBuilder.build();

        // 构建命令元数据
        CommandMeta.Builder metaBuilder = commandManager.metaBuilder(rootLiteral);
        if (rootAliases != null && !rootAliases.isEmpty()) {
            metaBuilder.aliases(rootAliases.toArray(new String[0]));
        }
        metaBuilder.plugin(pluginContainer);

        CommandMeta meta = metaBuilder.build();

        // 注册命令
        commandManager.register(meta, new BrigadierCommand(commandTree));
        logger.info("Root command '{}' and all sub-commands registered.", rootLiteral);
    }
}
