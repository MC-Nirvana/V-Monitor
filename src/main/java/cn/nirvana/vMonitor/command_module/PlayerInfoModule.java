package cn.nirvana.vMonitor.command_module;

import cn.nirvana.vMonitor.loader.DataFileLoader;
import cn.nirvana.vMonitor.loader.LanguageFileLoader;
import cn.nirvana.vMonitor.util.TimeUtil;
import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.text.minimessage.MiniMessage;
import java.util.List;

public class PlayerInfoModule {
    private final DataFileLoader dataFileLoader;
    private final LanguageFileLoader languageFileLoader;
    private final MiniMessage miniMessage;

    public PlayerInfoModule(DataFileLoader dataFileLoader, LanguageFileLoader languageFileLoader, MiniMessage miniMessage) {
        this.dataFileLoader = dataFileLoader;
        this.languageFileLoader = languageFileLoader;
        this.miniMessage = miniMessage;
    }

    public void executePlayerInfo(CommandSource source, String playerName) {
        // 查找玩家数据
        DataFileLoader.RootData rootData = dataFileLoader.getRootData();
        DataFileLoader.PlayerData playerData = null;

        // 遍历所有玩家数据查找匹配的玩家名
        for (DataFileLoader.PlayerData player : rootData.playerData) {
            if (player.username.equalsIgnoreCase(playerName)) {
                playerData = player;
                break;
            }
        }

        // 如果未找到玩家数据
        if (playerData == null) {
            String notFoundMessage = languageFileLoader.getMessage("commands.player.not_found")
                    .replace("{player}", playerName);
            source.sendMessage(miniMessage.deserialize(notFoundMessage));
            return;
        }

        // 获取语言文件中的玩家信息格式
        String playerInfoFormat = languageFileLoader.getMessage("commands.player.info.format");

        // 格式化玩家信息，只显示player_data中的基本信息
        String formattedMessage = playerInfoFormat
                .replace("{player_id}", String.valueOf(playerData.id))
                .replace("{uuid}", playerData.uuid.toString())
                .replace("{player_name}", playerData.username)
                .replace("{first_join_time}", playerData.firstJoinTime)
                .replace("{last_login_time}", playerData.lastLoginTime)
                .replace("{total_play_time}", TimeUtil.TimePeriodConverter.fromSeconds(playerData.playTime));

        source.sendMessage(miniMessage.deserialize(formattedMessage));
    }
}
