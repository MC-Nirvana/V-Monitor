- [中文版](./README.md)
- [繁體中文版](./README_TW.md)

# V-Monitor - Player Activity and Server Status Monitoring Velocity Plugin

## I. Introduction
V-Monitor is a lightweight Velocity proxy plugin (Internal development code: Arona-01) focused on monitoring player activity such as joining, leaving, and switching servers, and providing convenient commands for players and administrators to query online player lists and detailed backend server information.

## II. Key Features
- **Player Activity Notifications:** Send customizable messages when players join, leave, or switch servers (including first join distinction).
- **Online Player List Query:** Provides commands to view the total online players on the proxy and an overview of online player lists on each backend server.
- **Server Information Query:** Provides commands to query the overall proxy information and detailed information for specified backend servers.
- **Plugin Information Query:** Provides commands to query the list of plugins and details of the specified plugin.
- **Highly Customizable:** All messages and command outputs displayed to players or the console can be fully customized through language files.
- **Multi-language Support:** Implements multi-language functionality through separate language files.
- **Server Aliases:** Supports configuring aliases for backend servers.
- **Data Persistence:** Uses UUIDs to record player's first join information.

## III. Installation Guide
1.  Download the latest version of the plugin JAR file from the project's [Release page](https://github.com/MC-Nirvana/V-Monitor/releases/latest).
2.  Place the downloaded JAR file into the `plugins` folder of your Velocity proxy server.
3.  Start the Velocity proxy server. The plugin will automatically generate the default configuration file and language files.
4.  Edit the configuration file and language files as needed.
5.  Reload the plugin configuration (`/vm reload`) or restart the server for changes to take effect.
6.  Enjoy the convenient features provided by V-Monitor!

## IV. Plugin Usage (Commands)
The plugin's main command is `/vmonitor`, with the alias `/vm`.

| Command                            | Usage Example                                        | Permission Node    | Description                                            |
|------------------------------------|------------------------------------------------------|--------------------|--------------------------------------------------------|
| `help`                             | `/vm help`                                           | `none`             | Get general plugin help information.                   |
| `reload`                           | `/vm reload`                                         | `vmonitor.reload`  | Reload configuration and language files.               |
| `version`                          | `/vm version`                                        | `vmonitor.version` | Get plugin version information.                        |
| `server list [all or server_name]` | `/vm server list all` or `/vm server list lobby`     | `none`             | List players on all or specified servers.              |
| `server info [all or server_name]` | `/vm server info all` or `/vm server info lobby`     | `none`             | Get detailed information for all or specified servers. |
| `plugin list`                      | `/vm plugin list`                                    | `vmonitor.plugin`  | List all loaded plugins.                               |
| `plugin info [all or plugin_id]`   | `/vm plugin info all` or `/vm plugin info V-Monitor` | `vmonitor.plugin`  | Get detailed information for all or specified plugins. |

*By default, players with OP permission and the console have all permission nodes.*

## V. Configuration File (config.yml)
After the plugin starts, a `config.yml` file will be generated in the `plugins/v-monitor/` directory.

- `language.default`: Sets the language code used by the plugin (e.g., `en_us`, `zh_cn`, `zh_tw`).
- `server-aliases`: In this section, set display aliases for your backend servers, format like `actual server name: "desired display alias"`.

You can edit this file to customize the plugin's behavior and message content. Please refer to the file generated upon first plugin run for the full default configuration.

## VI. Language Files (lang/*.yml)
Language files are located in the `plugins/v-monitor/lang/` folder, in YAML format.
Each `.yml` file corresponds to one language and contains all text messages and format templates output by the plugin to players or the console. You are free to edit these files and use MiniMessage format to customize message colors, styles, and content.
For example, `en_us.yml` contains all messages in English. Please refer to the files generated upon first plugin run for the full default language files.

## VII. Data Storage (playerdata.json)
The plugin will generate a `playerdata.json` file in the `plugins/v-monitor/` directory.
This file is used to store the first join information of players who have connected to the proxy, uniquely identified by the player's **UUID**. **Do not manually edit this file.**

## VIII. Building from Source
If you wish to build the plugin from source, you will need Java Development Kit (JDK) 17+ and Maven/Gradle. Please refer to the building instructions in the project repository.

## IX. Contribution and Support
Contributions and support requests are welcome via GitHub Issues.