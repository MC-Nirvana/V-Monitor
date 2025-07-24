package cn.nirvana.vMonitor.command_module;

import cn.nirvana.vMonitor.loader.ConfigFileLoader;
import cn.nirvana.vMonitor.loader.LanguageFileLoader;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Collectors;

public class ServerListModule {
    private final ProxyServer proxyServer;
    private final ConfigFileLoader configFileLoader;
    private final LanguageFileLoader languageFileLoader;
    private final MiniMessage miniMessage;

    public ServerListModule(ProxyServer proxyServer, ConfigFileLoader configFileLoader, LanguageFileLoader languageFileLoader, MiniMessage miniMessage) {
        this.proxyServer = proxyServer;
        this.configFileLoader = configFileLoader;
        this.languageFileLoader = languageFileLoader;
        this.miniMessage = miniMessage;
    }

    public void executeListAll(CommandSource source) {
        Collection<RegisteredServer> servers = proxyServer.getAllServers();
        if (servers.isEmpty()) {
            source.sendMessage(miniMessage.deserialize(languageFileLoader.getMessage("commands.server.list.no_servers")));
            return;
        }

        StringBuilder serverListContent = new StringBuilder();
        int totalPlayers = 0;
        String lineFormat = languageFileLoader.getMessage("commands.server.list.all_line_format");

        for (RegisteredServer server : servers) {
            String serverDisplayName = configFileLoader.getServerDisplayName(server.getServerInfo().getName());
            Collection<Player> players = server.getPlayersConnected();
            totalPlayers += players.size();

            String playersListContent;
            if (players.isEmpty()) {
                playersListContent = languageFileLoader.getMessage("commands.server.list.no_players");
            } else {
                playersListContent = players.stream()
                        .sorted(Comparator.comparing(Player::getUsername))
                        .map(Player::getUsername)
                        .collect(Collectors.joining(", "));
            }

            serverListContent.append(lineFormat
                    .replace("{server_display_name}", serverDisplayName)
                    .replace("{online_players}", String.valueOf(players.size()))
                    .replace("{players_list}", playersListContent)
            ).append("\n");
        }

        String allFormat = languageFileLoader.getMessage("commands.server.list.all_format")
                .replace("{online_players_count}", String.valueOf(totalPlayers))
                .replace("{all_players_list}", serverListContent.toString().trim());

        source.sendMessage(miniMessage.deserialize(allFormat));
    }

    public void executeListPlayersOnServer(CommandSource source, String serverNameArg) {
        Optional<RegisteredServer> serverOptional = proxyServer.getServer(serverNameArg);
        if (serverOptional.isPresent()) {
            RegisteredServer server = serverOptional.get();
            String serverDisplayName = configFileLoader.getServerDisplayName(server.getServerInfo().getName());
            Collection<Player> players = server.getPlayersConnected();

            String specificPlayersListContent;
            if (players.isEmpty()) {
                specificPlayersListContent = "<red>" + languageFileLoader.getMessage("commands.server.list.no_players") + "</red>";
            } else {
                specificPlayersListContent = "<green>" + players.stream()
                        .sorted(Comparator.comparing(Player::getUsername))
                        .map(Player::getUsername)
                        .collect(Collectors.joining(", ")) + "</green>";
            }
            String specificFormat = languageFileLoader.getMessage("commands.server.list.specific_format")
                    .replace("{server_display_name}", serverDisplayName)
                    .replace("{online_players_number}", String.valueOf(players.size()))
                    .replace("{specific_players_list}", specificPlayersListContent);
            source.sendMessage(miniMessage.deserialize(specificFormat));

        } else {
            source.sendMessage(miniMessage.deserialize(languageFileLoader.getMessage("commands.server.not_found").replace("{server}", serverNameArg)));
        }
    }
}