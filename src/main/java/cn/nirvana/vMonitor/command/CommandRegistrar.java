package cn.nirvana.vMonitor.command;

import cn.nirvana.vMonitor.config.LanguageLoader;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;

import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.function.Consumer;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public class CommandRegistrar {
    private final CommandManager commandManager;
    private final ProxyServer proxyServer;
    private final LanguageLoader languageLoader;
    private final MiniMessage miniMessage;

    private LiteralCommandNode<CommandSource> vmonitorRootNode;
    private LiteralCommandNode<CommandSource> serverSubCommandNode;
    private LiteralCommandNode<CommandSource> pluginSubCommandNode;
    private HelpCommand helpCommand;
    private ReloadCommand reloadCommand;

    public CommandRegistrar(CommandManager commandManager, ProxyServer proxyServer, LanguageLoader languageLoader, MiniMessage miniMessage) {
        this.commandManager = commandManager;
        this.proxyServer = proxyServer;
        this.languageLoader = languageLoader;
        this.miniMessage = miniMessage;
    }
    public void setHelpCommand(HelpCommand helpCommand) {
        this.helpCommand = helpCommand;
    }

    public void setReloadCommand(ReloadCommand reloadCommand) {
        this.reloadCommand = reloadCommand;
    }

    public void registerCommands() {
        vmonitorRootNode = LiteralArgumentBuilder.<CommandSource>literal("vmonitor")
                .executes(context -> {
                    Component message = miniMessage.deserialize(languageLoader.getMessage("commands.version.format"));
                    context.getSource().sendMessage(message);
                    return SINGLE_SUCCESS;
                }).build();
        vmonitorRootNode.addChild(LiteralArgumentBuilder.<CommandSource>literal("help")
                .executes(context -> {
                    if (helpCommand != null) {
                        helpCommand.execute(context.getSource());
                    } else {
                        context.getSource().sendMessage(miniMessage.deserialize("<red>Help command not initialized. Internal error.</red>"));
                    }
                    return SINGLE_SUCCESS;
                }).build()
        );
        vmonitorRootNode.addChild(LiteralArgumentBuilder.<CommandSource>literal("reload")
                .requires(source -> source.hasPermission("vmonitor.reload"))
                .executes(context -> {
                    if (reloadCommand != null) {
                        reloadCommand.execute(context.getSource());
                    } else {
                        context.getSource().sendMessage(miniMessage.deserialize("<red>Reload command not initialized. Internal error.</red>"));
                    }
                    return SINGLE_SUCCESS;
                }).build()
        );
        this.serverSubCommandNode = LiteralArgumentBuilder.<CommandSource>literal("server")
                .executes(context -> {
                    context.getSource().sendMessage(miniMessage.deserialize(languageLoader.getMessage("commands.help.server_format")));
                    return SINGLE_SUCCESS;
                }).build();
        vmonitorRootNode.addChild(serverSubCommandNode);
        this.pluginSubCommandNode = LiteralArgumentBuilder.<CommandSource>literal("plugin")
                .requires(source -> source.hasPermission("vmonitor.plugin"))
                .executes(context -> {
                    context.getSource().sendMessage(miniMessage.deserialize(languageLoader.getMessage("commands.help.plugin_format")));
                    return SINGLE_SUCCESS;
                }).build();
        vmonitorRootNode.addChild(pluginSubCommandNode);
        CommandMeta commandMeta = commandManager.metaBuilder("vmonitor")
                .aliases("vm")
                .build();
        commandManager.register(commandMeta, new BrigadierCommand(vmonitorRootNode));
    }

    public void registerServerSubCommand(Consumer<LiteralCommandNode<CommandSource>> subCommandBuilder) {
        if (serverSubCommandNode != null) {
            subCommandBuilder.accept(serverSubCommandNode);
        }
    }

    public void registerPluginSubCommand(Consumer<LiteralCommandNode<CommandSource>> subCommandBuilder) {
        if (pluginSubCommandNode != null) {
            subCommandBuilder.accept(pluginSubCommandNode);
        }
    }
}
