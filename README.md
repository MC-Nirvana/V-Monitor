- 简体中文版
- [繁體中文版](./README_TW.md)
- [English version](./README_EN.md)

<p align="center">
  <img src="v_monitor-logo.png" alt="V-Monitor Logo" width="128" height="128">
</p>

# V-Monitor - 玩家活动与服务器状态监控 Velocity 插件

[![GitHub release](https://img.shields.io/github/release/MC-Nirvana/V-Monitor.svg)](https://github.com/MC-Nirvana/V-Monitor/releases)
[![GitHub issues](https://img.shields.io/github/issues/MC-Nirvana/V-Monitor.svg)](https://github.com/MC-Nirvana/V-Monitor/issues)
[![GitHub license](https://img.shields.io/github/license/MC-Nirvana/V-Monitor.svg)](https://github.com/MC-Nirvana/V-Monitor/blob/main/LICENSE)

## 一、简介
V-Monitor 是一个轻量级的 Velocity 代理端插件（内部开发代号：Arona-01），专注于监控玩家的加入、离开、切换服务器等活动状态，并提供方便的命令供玩家和管理员查询在线玩家列表以及后端服务器的详细信息。

## 二、主要特性
- **玩家活动监控**：实时追踪玩家登录、登出和服务器切换事件
- **在线状态查询**：提供命令查看当前在线玩家列表和各服务器分布情况
- **服务器信息查询**：获取 Velocity 代理和各子服务器的详细信息
- **插件信息管理**：查询已加载插件列表和详细信息
- **玩家数据查询**：查看特定玩家的活动历史和游戏时长统计
- **多语言支持**：支持多种语言界面，可轻松扩展
- **灵活配置**：通过配置文件自定义服务器别名和语言设置
- **数据持久化**：使用 SQLite 或 MySQL 存储玩家活动数据
- **WebSocket 支持**：通过 WebSocket 实时推送玩家活动数据（待开发）

## 三、安装指南
1.  从项目的 [Release 页面](https://github.com/MC-Nirvana/V-Monitor/releases/latest) 下载最新版本的插件 JAR 文件。
2.  将下载好的 JAR 文件放入你的 Velocity 代理服务器的 `plugins` 文件夹中。
3.  启动 Velocity 代理服务器。插件将自动生成默认的配置文件和语言文件。
4.  根据需要编辑配置文件和语言文件。
5.  重载插件配置（`/vm reload`）或重启服务器使更改生效。
6.  享受 V-Monitor 带来的便捷功能！

## 四、插件用法 (命令)
插件的主命令是 `/vmonitor`，别名为 `/vm`。

| 命令                            | 用法示例                                             | 权限节点         | 描述                             |
|---------------------------------|------------------------------------------------------|------------------|----------------------------------|
| `help`                          | `/vm help`                                           | `none`           | 获取插件总帮助信息。             |
| `reload`                        | `/vm reload`                                         | `vmonitor.admin` | 重载插件配置。                   |
| `version`                       | `/vm version`                                        | `vmonitor.admin` | 获取插件版本信息。               |
| `server list [all或服务器名称]` | `/vm server list all` 或 `/vm server list lobby`     | `none`           | 列出所有或指定服务器上的玩家。   |
| `server info [all或服务器名称]` | `/vm server info all` 或 `/vm server info lobby`     | `none`           | 获取所有或指定服务器的详细信息。 |
| `plugin list`                   | `/vm plugin list`                                    | `vmonitor.admin` | 列出所有已加载插件。             |
| `plugin info [all或插件ID]`     | `/vm plugin info all` 或 `/vm plugin info V-Monitor` | `vmonitor.admin` | 获取所有或指定插件的详细信息。   |
| `player info [玩家游戏ID]`      | `/vm player info MC_Nirvana`                         | `vmonitor.admin` | 获取指定玩家的详细信息。         |
| `player switch [玩家游戏ID]`    | `/vm player switch MC_Nirvana`                       | `vmonitor.admin` | 获取指定玩家的服务器切换日志。   |

*默认情况下，拥有 OP 权限的玩家和控制台拥有所有权限节点。*

## 五、配置文件 (config.yml)
插件启动后会在 `plugins/v-monitor/` 目录下生成 `config.yml` 文件。<br/>
以下是示例配置文件的详细内容：
```yaml
# V-监视器配置文件（V-Monitor Configuration）

# 插件基本设置（Plugin basic settings）
plugin-basic:
  # 默认语言设置（Default language settings）
  language:
    # 默认语言（Default language）
    # 支持的语言：zh_cn, zh_tw, en_us（Support language: zh_cn, zh_tw, en_us）
    default: "zh_cn"
  # 数据存储设置 (Data storage settings)
  data-storage:
    # 数据库类型（Database type）
    # 支持的类型：sqlite, mysql（Supported types: sqlite, mysql）
    type: "sqlite"
    # SQLite数据库配置（SQLite database configuration）
    sqlite:
      # SQLite数据库文件路径（SQLite database file path）
      path: "data.db"
    # MySQL数据库连接配置（MySQL connection configuration）
    mysql:
      # MySQL服务器地址（MySQL server address）
      host: "localhost"
      # MySQL服务器端口（MySQL server port）
      port: 3306
      # MySQL数据库名称（MySQL database name）
      database: "v_monitor"
      # MySQL数据库用户名（MySQL database username）
      username: "root"
      # MySQL数据库密码（MySQL database password）
      password: "password"
      # MySQL自定义参数配置（MySQL custom parameter configuration）
      # 参数格式为：数据库设置项: 值 （Parameter format: database setting item: value）
      # 示例（Example）：
      # parameters:
      #   useSSL: "false"
      parameters: []
    # HikariCP 数据库连接池配置（HikariCP database connection pool configuration）
    hikari:
      # 最大连接数（Maximum number of connections）
      maximum-pool-size: 32
      # 最小空闲连接数（Minimum idle connections）
      minimum-idle: 16
      # 连接超时时间（Connection timeout）
      # 单位：毫秒（Unit: milliseconds）
      connection-timeout: 30000
      # 空闲连接存活时间（Idle connection lifetime）
      # 单位：毫秒（Unit: milliseconds）
      idle-timeout: 600000
      # 最大生命周期（Maximum lifetime）
      # 单位：毫秒（Unit: milliseconds）
      max-lifetime: 1800000

# 服务器信息设置（Server info settings）
server-info:
  # 服务器名称（Server name）
  name: "My Velocity Server"
  # 服务器别名（Server aliases）
  # 参数格式为：实际服务器名称: "别名"（Parameter format: actual server name: "alias"）
  # 支持为多个服务器同时设置别名（Supports setting aliases for multiple servers at the same time）
  # 示例（Example）：
  # aliases:
  #   lobby: "hub"
  #   game: "minigames"
  aliases: []

# 报告设置 (Report settings)
report:
  # 是否启用报告功能（Enable report feature）
  # 如果启用，报告将在指定时间自动生成（If enabled, reports will be generated automatically at the specified time）
  enabled: true
  # 是否自动清理报告（Auto clean report）
  auto-clean-report: true
  # 输出目录（Output directory）
  output-directory: "Reports"
  # 报告生成时间（Report generation time）
  # 格式为 HH:mm（Format is HH:mm）
  # 注意：时间应为24小时制（Note: Time should be in 24-hour format）
  # 示例：每天下午4点生成报告（Example: Generate report every day at 4 PM）
  # schedule-time: "16:00"
  schedule-time: "16:00"
```
你可以编辑此文件来自定义插件的行为和消息内容。默认配置文件的完整内容请参考插件首次运行生成的文件。

## 六、语言文件 (lang/*.yml)
语言文件位于 `plugins/v-monitor/lang/` 文件夹下，采用 YAML 格式。
每个 `.yml` 文件对应一种语言，其中包含了插件所有输出到玩家或控制台的文本消息和格式模板。你可以自由编辑这些文件，使用 MiniMessage 格式来自定义消息的颜色、样式和内容。
例如，`zh_cn.yml` 文件包含了简体中文的所有消息文本。默认语言文件的完整内容请参考插件首次运行生成的文件。

## 七、数据存储 (data.db)
插件会在 `plugins/v-monitor/` 目录下生成名为 `data.db` 的 SQLite3 数据库文件。
此文件用于存储连接过代理服务器的玩家的相关信息，通过玩家的 **UUID** 进行唯一标识。**请勿手动编辑此文件。**
可以通过修改配置文件来使用MySQL数据库。

## 八、从源代码构建
如果你想从源代码构建插件，你需要 Java Development Kit (JDK) 17+ 和 Gradle。

### 8-1：构建步骤
1. 克隆仓库：`git clone https://github.com/MC-Nirvana/V-Monitor.git`
2. 进入仓库目录：`cd V-Monitor`
3. 执行构建命令：`./gradlew build`
4. 在 `build/libs/` 目录下找到生成的 JAR 文件

### 8-2：开发环境设置
- 推荐使用 IntelliJ IDEA 进行开发
- 导入项目后，确保 Gradle 依赖能够正确下载

## 九、贡献与支持
欢迎通过 GitHub Issues 提交 Bug 报告和功能建议。

### 9-1：贡献方式
- 提交代码改进和新功能实现
- 完善文档和翻译
- 报告 Bug 和安全问题
- 参与讨论和提供使用反馈

### 9-2：提交 Pull Request 的最佳实践
1. Fork 项目并创建功能分支
2. 编写清晰的提交信息
3. 确保代码符合项目编码规范
4. 添加相应的测试用例
5. 保持 Pull Request 聚焦于单一功能或修复

### 9-3：开发者资源
- 项目遵循标准的 Git 工作流
- 请在提交 Pull Request 前确保代码通过所有测试
- 保持代码风格一致，参考现有代码结构

## 十、项目路线图
- [ ] 完善 WebSocket 实时数据推送功能
- [ ] 提供 Web 管理界面

## 十一、许可证
本项目采用 [GPL-3.0 license](LICENSE) 开源许可证。

## 十二、作者的话
这是我第一次开发适用于 Minecraft Java Edition 的服务端插件。虽然项目在开发过程中使用了 AI 辅助编程，代码质量还有待提升，但我会持续优化改进。这个插件最初是为我自己的 Minecraft Java Edition 服务器而开发的，希望能对其他服务器管理员也有帮助。

## 十三、支持与反馈
如果你喜欢这个项目，请考虑：
- 给项目点个 Star ⭐
- 在社交媒体上分享这个项目
- 参与项目讨论，提供宝贵意见

### 13-1：赞助支持
如果你希望支持本项目的持续开发和维护，可以通过以下方式赞助：

- [面包多](https://mbd.pub/o/MC_Nirvana) - 通过面包多赞助（适用于国内用户）
- [PayPal](https://paypal.me/WHFNirvana) - 通过 PayPal 赞助（适用于海外用户）

您的赞助将用于：
- 维护项目基础设施
- 请作者去码头整点薯条:)