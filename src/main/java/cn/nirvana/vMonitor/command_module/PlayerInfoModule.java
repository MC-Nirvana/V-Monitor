package cn.nirvana.vMonitor.command_module;

import cn.nirvana.vMonitor.loader.DataFileLoader;
import cn.nirvana.vMonitor.loader.LanguageFileLoader;
import cn.nirvana.vMonitor.util.TimeUtil;
import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.text.minimessage.MiniMessage;
import java.util.Map;
import java.util.UUID;

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
        UUID playerUUID = null;
        DataFileLoader.PlayerData playerData = null;

        // 遍历所有玩家数据查找匹配的玩家名
        for (Map.Entry<UUID, DataFileLoader.PlayerData> entry : rootData.players.entrySet()) {
            if (entry.getValue().playerName.equalsIgnoreCase(playerName)) {
                playerUUID = entry.getKey();
                playerData = entry.getValue();
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
        String serverStatsHeader = languageFileLoader.getMessage("commands.player.info.server_stats_header");
        String serverStatLine = languageFileLoader.getMessage("commands.player.info.server_stat_line");
        String noServerStats = languageFileLoader.getMessage("commands.player.info.no_server_stats");

        // 构建服务器统计信息
        StringBuilder serverStats = new StringBuilder();
        if (playerData.loggedInServerLoginCounts.isEmpty()) {
            serverStats.append(noServerStats);
        } else {
            serverStats.append(serverStatsHeader).append("\n");
            for (Map.Entry<String, Integer> entry : playerData.loggedInServerLoginCounts.entrySet()) {
                serverStats.append(serverStatLine
                                .replace("{server}", entry.getKey())
                                .replace("{count}", String.valueOf(entry.getValue())))
                        .append("\n");
            }
        }

        // 格式化玩家信息
        String formattedMessage = playerInfoFormat
                .replace("{player_name}", playerData.playerName)
                .replace("{uuid}", playerUUID.toString())
                .replace("{first_join_time}", playerData.firstJoinTime)
                .replace("{last_login_time}", playerData.lastLoginTime)
                .replace("{last_quit_time}",
                        (playerData.lastQuitTime != null && !playerData.lastQuitTime.isEmpty()) ?
                                playerData.lastQuitTime : languageFileLoader.getMessage("commands.player.info.never_quit"))
                .replace("{total_login_count}", String.valueOf(playerData.totalLoginCount))
                .replace("{total_play_time}", TimeUtil.TimePeriodConverter.fromSeconds(playerData.totalPlayTime))
                .replace("{server_stats}", serverStats.toString().trim());

        source.sendMessage(miniMessage.deserialize(formattedMessage));
    }
}
