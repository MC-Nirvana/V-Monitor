package cn.nirvana.vMonitor.util;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.plugin.PluginContainer;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public class CommandUtil {

    private final CommandManager commandManager;
    private final Logger logger;
    private final PluginContainer pluginContainer;

    // 用于存储顶级命令节点，以便其他命令可以添加到其下
    private LiteralCommandNode<CommandSource> rootCommandNode;

    public CommandUtil(CommandManager commandManager, Logger logger, PluginContainer pluginContainer) {
        this.commandManager = commandManager;
        this.logger = logger;
        this.pluginContainer = pluginContainer;
    }

    /**
     * 初始化根命令节点。所有后续子命令都将通过此根节点构建。
     * @param rootLiteral 根命令的字面量名称（例如 "vmonitor"）
     * @param rootAliases 根命令的别名
     * @param rootCommandConfigurer 用于配置根命令构建器的消费者函数，包括添加执行逻辑。
     */
    public void initializeRootCommand(String rootLiteral, Set<String> rootAliases,
                                      Consumer<LiteralArgumentBuilder<CommandSource>> rootCommandConfigurer) {
        LiteralArgumentBuilder<CommandSource> rootBuilder = LiteralArgumentBuilder.<CommandSource>literal(rootLiteral);

        // 应用传入的配置器，它会添加执行逻辑或任何其他根命令特性
        rootCommandConfigurer.accept(rootBuilder);

        rootCommandNode = rootBuilder.build(); // 构建并保存根节点

        CommandMeta.Builder metaBuilder = commandManager.metaBuilder(rootLiteral);
        if (rootAliases != null && !rootAliases.isEmpty()) {
            metaBuilder.aliases(rootAliases.toArray(new String[0]));
        }
        metaBuilder.plugin(pluginContainer); // 关联插件容器

        commandManager.register(metaBuilder.build(), new BrigadierCommand(rootCommandNode));
        logger.info("Root command '{}' and its aliases registered.", rootLiteral);
    }

    /**
     * 提供一个接口，用于在根命令节点下注册子命令。
     * @param subCommandBuilder 一个Consumer，接收一个LiteralCommandNode作为参数，用于构建和添加子命令。
     */
    public void registerSubCommand(Consumer<LiteralCommandNode<CommandSource>> subCommandBuilder) {
        if (rootCommandNode == null) {
            logger.error("Root command node is not initialized. Cannot register sub-command.");
            return;
        }
        subCommandBuilder.accept(rootCommandNode);
    }

    /**
     * 注册所有的Brigadier命令到Velocity的CommandManager中。
     * 这个方法应该在所有命令构建完成后调用一次。
     * （在当前设计中，由于根命令直接注册，此方法更多是占位符）
     */
    public void registerAllCommands() {
        // 由于我们将命令构建成一个统一的树，所以只需要注册根节点。
        // 子命令已经通过 registerSubCommand 添加到了根节点下。
        logger.debug("All commands (including sub-commands) have been registered via the root command.");
    }
}