package cn.nirvana.vMonitor.command_module;

import cn.nirvana.vMonitor.loader.DataLoader;
import cn.nirvana.vMonitor.loader.LanguageLoader;
import cn.nirvana.vMonitor.util.TimeUtil;

import com.velocitypowered.api.command.CommandSource;

import net.kyori.adventure.text.minimessage.MiniMessage;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public class PlayerSwitchModule {
    private final DataLoader dataLoader;
    private final LanguageLoader languageLoader;
    private final MiniMessage miniMessage;

    public PlayerSwitchModule(DataLoader dataLoader, LanguageLoader languageLoader, MiniMessage miniMessage) {
        this.dataLoader = dataLoader;
        this.languageLoader = languageLoader;
        this.miniMessage = miniMessage;
    }

    public void executePlayerSwitch(CommandSource source, String playerName) {
        // 查找玩家数据
        DataLoader.PlayerData playerData = dataLoader.getPlayerDataByName(playerName);

        // 如果未找到玩家数据
        if (playerData == null) {
            String notFoundMessage = languageLoader.getMessage("commands.player.not_found")
                    .replace("{player}", playerName);
            source.sendMessage(miniMessage.deserialize(notFoundMessage));
            return;
        }

        // 获取语言文件中的玩家切换日志格式
        String switchLogHeader = languageLoader.getMessage("commands.player.switch.header");
        String switchLogEntryFormat = languageLoader.getMessage("commands.player.switch.entry_format");
        String noSwitchLogsMessage = languageLoader.getMessage("commands.player.switch.no_logs");
        String switchLogFormat = languageLoader.getMessage("commands.player.switch.format");

        // 构建切换日志内容
        StringBuilder switchLogBuilder = new StringBuilder();
        boolean hasLogs = false;

        // 遍历玩家的所有服务器路径日志
        for (Map.Entry<String, List<DataLoader.ServerPathData>> entry : playerData.dailyServerPaths.entrySet()) {
            List<DataLoader.ServerPathData> paths = entry.getValue();

            if (!paths.isEmpty()) {
                hasLogs = true;
                for (DataLoader.ServerPathData path : paths) {
                    // 将时间转换为 ISO8601 标准格式
                    String iso8601Time = TimeUtil.DateTimeConverter.fromTimestamp(
                            path.time.atZone(ZoneId.systemDefault()).toEpochSecond());

                    // 只替换新的 ISO8601 格式占位符
                    String entryMessage = switchLogEntryFormat
                            .replace("{iso8601_time}", iso8601Time)
                            .replace("{from}", path.from)
                            .replace("{to}", path.to);

                    switchLogBuilder.append(entryMessage).append("\n");
                }
            }
        }

        // 如果没有日志记录
        if (!hasLogs) {
            switchLogBuilder.append(noSwitchLogsMessage);
        }

        // 使用 format 模板格式化最终输出
        String finalMessage = switchLogFormat
                .replace("{player_name}", playerData.username)
                .replace("{switch_log}", switchLogBuilder.toString().trim());

        source.sendMessage(miniMessage.deserialize(finalMessage));
    }
}
