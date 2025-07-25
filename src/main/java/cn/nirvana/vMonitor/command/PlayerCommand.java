package cn.nirvana.vMonitor.command;

import cn.nirvana.vMonitor.loader.LanguageFileLoader;
import cn.nirvana.vMonitor.command_module.HelpModule;
import cn.nirvana.vMonitor.command_module.PlayerInfoModule;
import cn.nirvana.vMonitor.util.CommandUtil;
import cn.nirvana.vMonitor.loader.DataFileLoader;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.concurrent.CompletableFuture;
import java.util.Map;
import java.util.UUID;

import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public class PlayerCommand {
    private final CommandUtil commandUtil;
    private final LanguageFileLoader languageFileLoader;
    private final MiniMessage miniMessage;
    private final PlayerInfoModule playerInfoModule;
    private final HelpModule helpModule;
    private final DataFileLoader dataFileLoader;

    public PlayerCommand(CommandUtil commandUtil, LanguageFileLoader languageFileLoader,
                         MiniMessage miniMessage, PlayerInfoModule playerInfoModule,
                         HelpModule helpModule, DataFileLoader dataFileLoader) {
        this.commandUtil = commandUtil;
        this.languageFileLoader = languageFileLoader;
        this.miniMessage = miniMessage;
        this.playerInfoModule = playerInfoModule;
        this.helpModule = helpModule;
        this.dataFileLoader = dataFileLoader;
        registerPlayerCommand();
    }

    private void registerPlayerCommand() {
        commandUtil.registerSubCommand(root -> {
            root.then(LiteralArgumentBuilder.<CommandSource>literal("player")
                    .executes(context -> {
                        helpModule.executePlayerHelp(context.getSource());
                        return SINGLE_SUCCESS;
                    })
                    .then(LiteralArgumentBuilder.<CommandSource>literal("info")
                            .executes(context -> {
                                String usage = languageFileLoader.getMessage("commands.player.usage.info");
                                context.getSource().sendMessage(miniMessage.deserialize(usage));
                                return SINGLE_SUCCESS;
                            })
                            .then(RequiredArgumentBuilder.<CommandSource, String>argument("player", word())
                                    .suggests(new PlayerNameSuggestionProvider(dataFileLoader))
                                    .executes(context -> {
                                        String playerName = context.getArgument("player", String.class);
                                        playerInfoModule.executePlayerInfo(context.getSource(), playerName);
                                        return SINGLE_SUCCESS;
                                    })
                            )
                    )
            );
        });
    }

    // 玩家名称自动补全提供者
    static class PlayerNameSuggestionProvider implements SuggestionProvider<CommandSource> {
        private final DataFileLoader dataFileLoader;

        public PlayerNameSuggestionProvider(DataFileLoader dataFileLoader) {
            this.dataFileLoader = dataFileLoader;
        }

        @Override
        public CompletableFuture<Suggestions> getSuggestions(CommandContext<CommandSource> context, SuggestionsBuilder builder) {
            String remaining = builder.getRemaining().toLowerCase();

            // 从数据文件中获取所有玩家名称并进行过滤
            DataFileLoader.RootData rootData = dataFileLoader.getRootData();
            for (Map.Entry<UUID, DataFileLoader.PlayerData> entry : rootData.players.entrySet()) {
                String playerName = entry.getValue().playerName;
                if (playerName.toLowerCase().startsWith(remaining)) {
                    builder.suggest(playerName);
                }
            }

            return builder.buildFuture();
        }
    }
}
