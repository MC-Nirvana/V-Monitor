package cn.nirvana.vMonitor.command_module;

import cn.nirvana.vMonitor.loader.DataLoader;
import cn.nirvana.vMonitor.loader.LanguageLoader;
import cn.nirvana.vMonitor.util.TimeUtil;

import com.velocitypowered.api.command.CommandSource;

import net.kyori.adventure.text.minimessage.MiniMessage;

public class PlayerInfoModule {
    private final DataLoader dataLoader;
    private final LanguageLoader languageLoader;
    private final MiniMessage miniMessage;

    public PlayerInfoModule(DataLoader dataLoader, LanguageLoader languageLoader, MiniMessage miniMessage) {
        this.dataLoader = dataLoader;
        this.languageLoader = languageLoader;
        this.miniMessage = miniMessage;
    }

    public void executePlayerInfo(CommandSource source, String playerName) {
        // 查找玩家数据
        DataLoader.PlayerData playerData = dataLoader.getPlayerDataByName(playerName);

        // 如果未找到玩家数据
        if (playerData == null) {
            String notFoundMessage = languageLoader.getMessage("commands.player.not_found")
                    .replace("{player}", playerName);
            source.sendMessage(miniMessage.deserialize(notFoundMessage));
            return;
        }

        // 获取语言文件中的玩家信息格式
        String playerInfoFormat = languageLoader.getMessage("commands.player.info.format");

        // 格式化玩家信息，只显示player_data中的基本信息
        String formattedMessage = playerInfoFormat
                .replace("{player_id}", String.valueOf(playerData.id))
                .replace("{uuid}", playerData.uuid.toString())
                .replace("{player_name}", playerData.username)
                .replace("{first_join_time}", TimeUtil.DateTimeConverter.fromTimestamp(
                        playerData.firstJoinTime.atZone(java.time.ZoneId.systemDefault()).toEpochSecond()))
                .replace("{last_login_time}", TimeUtil.DateTimeConverter.fromTimestamp(
                        playerData.lastLoginTime.atZone(java.time.ZoneId.systemDefault()).toEpochSecond()))
                .replace("{total_play_time}", TimeUtil.TimePeriodConverter.fromSeconds(playerData.playTime));

        source.sendMessage(miniMessage.deserialize(formattedMessage));
    }
}
