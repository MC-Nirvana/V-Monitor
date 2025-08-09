- [中文版](./README.md)
- [繁體中文版](./README_TW.md)

# V-Monitor - Player Activity & Server Status Monitor for Velocity

[![GitHub release](https://img.shields.io/github/release/MC-Nirvana/V-Monitor.svg)](https://github.com/MC-Nirvana/V-Monitor/releases)
[![GitHub issues](https://img.shields.io/github/issues/MC-Nirvana/V-Monitor.svg)](https://github.com/MC-Nirvana/V-Monitor/issues)
[![GitHub license](https://img.shields.io/github/license/MC-Nirvana/V-Monitor.svg)](https://github.com/MC-Nirvana/V-Monitor/blob/main/LICENSE)

## I. Introduction
V-Monitor is a lightweight Velocity proxy plugin (internal codename: Arona-01) designed to monitor player join, leave, and server-switch activities. It provides convenient commands for both players and administrators to query the online player list and detailed backend server information.

## II. Key Features
- **Player Activity Monitoring**: Real-time tracking of player login, logout, and server switching events
- **Online Status Query**: Commands to view the list of online players and their server distribution
- **Server Information Query**: Retrieve detailed information about the Velocity proxy and all sub-servers
- **Plugin Information Management**: List loaded plugins and view detailed information
- **Player Data Query**: View specific player's activity history and total playtime statistics
- **Multi-language Support**: Support for multiple languages with easy extension
- **Flexible Configuration**: Customize server aliases and language settings via configuration files
- **Data Persistence**: Store player activity data using SQLite or MySQL
- **WebSocket Support**: Real-time player activity push via WebSocket (planned)

## III. Installation Guide
1. Download the latest plugin JAR from the [Release Page](https://github.com/MC-Nirvana/V-Monitor/releases/latest).
2. Place the downloaded JAR file into your Velocity proxy server's `plugins` folder.
3. Start the Velocity proxy server. The plugin will automatically generate default configuration and language files.
4. Edit the configuration and language files as needed.
5. Reload the plugin configuration (`/vm reload`) or restart the server for changes to take effect.
6. Enjoy the convenience brought by V-Monitor!

## IV. Plugin Usage (Commands)
The main command is `/vmonitor`, with alias `/vm`.

| Command                           | Example Usage                                        | Permission Node  | Description                              |
|-----------------------------------|------------------------------------------------------|------------------|------------------------------------------|
| `help`                            | `/vm help`                                           | `none`           | Get general help information for plugin. |
| `reload`                          | `/vm reload`                                         | `vmonitor.admin` | Reload the plugin configuration.         |
| `version`                         | `/vm version`                                        | `vmonitor.admin` | Get the plugin version information.      |
| `server list [all or serverName]` | `/vm server list all` or `/vm server list lobby`     | `none`           | List players on all or a specific server.|
| `server info [all or serverName]` | `/vm server info all` or `/vm server info lobby`     | `none`           | Get detailed info for all or one server. |
| `plugin list`                     | `/vm plugin list`                                    | `vmonitor.admin` | List all loaded plugins.                 |
| `plugin info [all or pluginID]`   | `/vm plugin info all` or `/vm plugin info V-Monitor` | `vmonitor.admin` | Get details for all or one plugin.       |
| `player info [playerID]`          | `/vm player info MC_Nirvana`                         | `vmonitor.admin` | Get details for a specific player.       |
| `player switch [playerID]`        | `/vm player switch MC_Nirvana`                       | `vmonitor.admin` | Get server switch logs for a player.     |

*By default, players with OP permissions and the console have all permission nodes.*

## V. Configuration File (config.yml)
When the plugin starts, it will generate a `config.yml` file in the `plugins/v-monitor/` directory.  
Below is an example configuration file with details:
```yaml
# V-Monitor Configuration

# Plugin basic settings
plugin-basic:
  # Default language settings
  language:
    # Default language
    # Support language: zh_cn, zh_tw, en_us
    default: "zh_cn"
  # Data storage settings
  data-storage:
    # Database type
    # Supported types: sqlite, mysql
    type: "sqlite"
    # SQLite database configuration
    sqlite:
      # SQLite database file path
      path: "data.db"
    # MySQL connection configuration
    mysql:
      # MySQL server address
      host: "localhost"
      # MySQL server port
      port: 3306
      # MySQL database name
      database: "v_monitor"
      # MySQL database username
      username: "root"
      # MySQL database password
      password: "password"
      # MySQL custom parameter configuration
      # Parameter format: database setting item: value
      # Example：
      # parameters:
      #   useSSL: "false"
      parameters: []
    # HikariCP database connection pool configuration
    hikari:
      # Maximum number of connections
      maximum-pool-size: 32
      # Minimum idle connections
      minimum-idle: 16
      # Connection timeout
      # Unit: milliseconds
      connection-timeout: 30000
      # Idle connection lifetime
      # Unit: milliseconds
      idle-timeout: 600000
      # Maximum lifetime
      # Unit: milliseconds
      max-lifetime: 1800000

# Server info settings
server-info:
  # Server name
  name: "My Velocity Server"
  # Server aliases
  # Parameter format: actual server name: "alias"
  # Supports setting aliases for multiple servers at the same time
  # Example：
  # aliases:
  #   lobby: "hub"
  #   game: "minigames"
  aliases: []

# Report settings
report:
  # Enable report feature
  # If enabled, reports will be generated automatically at the specified time
  enabled: true
  # Auto clean report
  auto-clean-report: true
  # Output directory
  output-directory: "Reports"
  # Report generation time
  # Format is HH:mm
  # Note: Time should be in 24-hour format
  # Example: Generate report every day at 4 PM
  # schedule-time: "16:00"
  schedule-time: "16:00"
```
You can edit this file to customize the plugin’s behavior and message content. For the complete default configuration, refer to the file generated when the plugin runs for the first time.

## VI. Language Files (lang/*.yml)
Language files are stored in `plugins/v-monitor/lang/` in YAML format.  
Each `.yml` file corresponds to a language and contains all text messages and format templates output by the plugin. You can freely edit these files and use MiniMessage format to customize colors, styles, and content.

## VII. Data Storage (data.db)
The plugin generates an SQLite3 database file named `data.db` in the `plugins/v-monitor/` directory.  
This file stores information about players who have connected to the proxy server, uniquely identified by their **UUID**. **Do not manually edit this file.**  
You can switch to MySQL by editing the configuration file.

## VIII. Building from Source
To build the plugin from source, you need JDK 17+ and Gradle.

### 8.1 Build Steps
1. Clone the repository: `git clone https://github.com/MC-Nirvana/V-Monitor.git`
2. Enter the repository directory: `cd V-Monitor`
3. Run the build command: `./gradlew build`
4. Find the generated JAR file in the `build/libs/` directory

### 8.2 Development Environment Setup
- IntelliJ IDEA is recommended for development
- Ensure Gradle dependencies are downloaded after importing the project

## IX. Contribution & Support
You are welcome to submit bug reports and feature suggestions via GitHub Issues.

### 9.1 How to Contribute
- Submit code improvements and new features
- Improve documentation and translations
- Report bugs and security issues
- Participate in discussions and provide feedback

### 9.2 Pull Request Best Practices
1. Fork the project and create a feature branch
2. Write clear commit messages
3. Ensure code meets project coding standards
4. Add appropriate test cases
5. Keep each Pull Request focused on a single feature or fix

### 9.3 Developer Resources
- The project follows the standard Git workflow
- Ensure your code passes all tests before submitting a Pull Request
- Maintain consistent coding style, following the existing code structure

## X. Project Roadmap
- [ ] Complete WebSocket real-time data push feature
- [ ] Provide a web-based admin interface

## XI. License
This project is licensed under the [GPL-3.0 license](LICENSE).

## XII. Author’s Note
This is my first time developing a server-side plugin for Minecraft Java Edition. Although AI-assisted coding was used during development and code quality still has room for improvement, I will continue to optimize it. Originally, this plugin was developed for my own Minecraft Java Edition server, but I hope it can be helpful for other server administrators as well.

## XIII. Support & Feedback
If you like this project, please consider:
- Giving it a Star ⭐
- Sharing it on social media
- Participating in discussions and providing valuable feedback

### 13.1 Sponsorship
If you would like to support the ongoing development and maintenance of this project, you can sponsor it via:

- [Mianbaoduo](https://mbd.pub/o/MC_Nirvana) - For users in mainland China
- [PayPal](https://paypal.me/WHFNirvana) - For overseas users

Your sponsorship will be used for:
- Maintaining project infrastructure
- Taking the author out for fries at the pier :)