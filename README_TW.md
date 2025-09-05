- [简体中文版](./README.md)
- 繁體中文版
- [English version](./README_EN.md)

# V-Monitor - 玩家活動与伺服器状态监控 Velocity 外掛

[![GitHub release](https://img.shields.io/github/release/MC-Nirvana/V-Monitor.svg)](https://github.com/MC-Nirvana/V-Monitor/releases)
[![GitHub issues](https://img.shields.io/github/issues/MC-Nirvana/V-Monitor.svg)](https://github.com/MC-Nirvana/V-Monitor/issues)
[![GitHub license](https://img.shields.io/github/license/MC-Nirvana/V-Monitor.svg)](https://github.com/MC-Nirvana/V-Monitor/blob/main/LICENSE)

## 壹、简介
V-Monitor 是一个轻量级的 Velocity 代理端外掛（内部开发代号：Arona-01），专注于监控玩家的加入、离开、切换伺服器等活动状态，并提供方便的指令供玩家和管理员查询在线玩家列表以及后端伺服器的详细信息。

## 貳、主要特性
- **玩家活動监控**：实时追踪玩家登录、登出和伺服器切换事件
- **在线状态查询**：提供指令查看当前在线玩家列表和各伺服器分布情况
- **伺服器信息查询**：获取 Velocity 代理和各子伺服器的详细信息
- **外掛信息管理**：查询已加载外掛列表和详细信息
- **玩家数据查询**：查看特定玩家的活动历史和遊戲时长统计
- **多語言支持**：支持多种語言界面，可轻松扩展
- **灵活設定**：通过設定檔案自定义伺服器别名和語言设置
- **数据持久化**：使用 SQLite 或 MySQL 存储玩家活動数据
- **WebSocket 支持**：通过 WebSocket 实时推送玩家活動数据（待开发）

## 叁、安装指南
1.  从專案的 [Release 页面](https://github.com/MC-Nirvana/V-Monitor/releases/latest) 下载最新版本的外掛 JAR 檔案。
2.  将下载好的 JAR 檔案放入你的 Velocity 代理伺服器的 `plugins` 檔案夹中。
3.  启动 Velocity 代理伺服器。外掛将自动產生預設的設定檔案和語言檔案。
4.  根据需要编辑設定檔案和語言檔案。
5.  重载外掛設定（`/vm reload`）或重启伺服器使更改生效。
6.  享受 V-Monitor 带来的便捷功能！

## 肆、外掛用法 (指令)
外掛的主指令是 `/vmonitor`，别名为 `/vm`。

| 指令                            | 用法範例                                             | 权限节点         | 描述                             |
|---------------------------------|------------------------------------------------------|------------------|----------------------------------|
| `help`                          | `/vm help`                                           | `none`           | 获取外掛总帮助信息。             |
| `reload`                        | `/vm reload`                                         | `vmonitor.admin` | 重载外掛設定。                   |
| `version`                       | `/vm version`                                        | `vmonitor.admin` | 获取外掛版本信息。               |
| `server list [all或伺服器名称]` | `/vm server list all` 或 `/vm server list lobby`     | `none`           | 列出所有或指定伺服器上的玩家。   |
| `server info [all或伺服器名称]` | `/vm server info all` 或 `/vm server info lobby`     | `none`           | 获取所有或指定伺服器的详细信息。 |
| `plugin list`                   | `/vm plugin list`                                    | `vmonitor.admin` | 列出所有已加载外掛。             |
| `plugin info [all或外掛ID]`     | `/vm plugin info all` 或 `/vm plugin info V-Monitor` | `vmonitor.admin` | 获取所有或指定外掛的详细信息。   |
| `player info [玩家遊戲ID]`      | `/vm player info MC_Nirvana`                         | `vmonitor.admin` | 获取指定玩家的详细信息。         |
| `player switch [玩家遊戲ID]`    | `/vm player switch MC_Nirvana`                       | `vmonitor.admin` | 获取指定玩家的伺服器切换日志。   |

*預設情况下，拥有 OP 权限的玩家和主控台拥有所有权限节点。*

## 伍、設定檔案 (config.yml)
外掛启动后会在 `plugins/v-monitor/` 目錄下產生 `config.yml` 檔案。<br/>
以下是範例設定檔案的详细内容：
```yaml
# V-监视器設定檔案（V-Monitor Configuration）

# 外掛基本设置（Plugin basic settings）
plugin-basic:
  # 預設語言设置（Default language settings）
  language:
    # 預設語言（Default language）
    # 支持的語言：zh_cn, zh_tw, en_us（Support language: zh_cn, zh_tw, en_us）
    default: "zh_cn"
  # 数据存储设置 (Data storage settings)
  data-storage:
    # 資料庫类型（Database type）
    # 支持的类型：sqlite, mysql（Supported types: sqlite, mysql）
    type: "sqlite"
    # SQLite資料庫設定（SQLite database configuration）
    sqlite:
      # SQLite資料庫檔案路径（SQLite database file path）
      path: "data.db"
    # MySQL資料庫連線設定（MySQL connection configuration）
    mysql:
      # MySQL伺服器地址（MySQL server address）
      host: "localhost"
      # MySQL伺服器端口（MySQL server port）
      port: 3306
      # MySQL資料庫名称（MySQL database name）
      database: "v_monitor"
      # MySQL資料庫用户名（MySQL database username）
      username: "root"
      # MySQL資料庫密码（MySQL database password）
      password: "password"
      # MySQL自定义參數設定（MySQL custom parameter configuration）
      # 參數格式为：資料庫设置项: 值 （Parameter format: database setting item: value）
      # 範例（Example）：
      # parameters:
      #   useSSL: "false"
      parameters: []
    # HikariCP 資料庫連線池設定（HikariCP database connection pool configuration）
    hikari:
      # 最大連線数（Maximum number of connections）
      maximum-pool-size: 32
      # 最小空闲連線数（Minimum idle connections）
      minimum-idle: 16
      # 連線超时時間（Connection timeout）
      # 单位：毫秒（Unit: milliseconds）
      connection-timeout: 30000
      # 空闲連線存活時間（Idle connection lifetime）
      # 单位：毫秒（Unit: milliseconds）
      idle-timeout: 600000
      # 最大生命周期（Maximum lifetime）
      # 单位：毫秒（Unit: milliseconds）
      max-lifetime: 1800000

# 伺服器信息设置（Server info settings）
server-info:
  # 伺服器名称（Server name）
  name: "My Velocity Server"
  # 伺服器别名（Server aliases）
  # 參數格式为：实际伺服器名称: "别名"（Parameter format: actual server name: "alias"）
  # 支持为多个伺服器同时设置别名（Supports setting aliases for multiple servers at the same time）
  # 範例（Example）：
  # aliases:
  #   lobby: "hub"
  #   game: "minigames"
  aliases: []

# 報告设置 (Report settings)
report:
  # 是否启用報告功能（Enable report feature）
  # 如果启用，報告将在指定時間自动產生（If enabled, reports will be generated automatically at the specified time）
  enabled: true
  # 是否自动清理報告（Auto clean report）
  auto-clean-report: true
  # 输出目錄（Output directory）
  output-directory: "Reports"
  # 報告產生時間（Report generation time）
  # 格式为 HH:mm（Format is HH:mm）
  # 注意：時間应为24小時制（Note: Time should be in 24-hour format）
  # 範例：每天下午4点產生報告（Example: Generate report every day at 4 PM）
  # schedule-time: "16:00"
  schedule-time: "16:00"
```
你可以编辑此檔案来自定义外掛的行为和消息内容。預設設定檔案的完整内容请参考外掛首次运行產生的檔案。

## 陸、語言檔案 (lang/*.yml)
語言檔案位于 `plugins/v-monitor/lang/` 檔案夹下，采用 YAML 格式。
每个 `.yml` 檔案对应一种語言，其中包含了外掛所有输出到玩家或主控台的文本消息和格式模板。你可以自由编辑这些檔案，使用 MiniMessage 格式来自定义消息的颜色、样式和内容。
例如，`zh_cn.yml` 檔案包含了简体中文的所有消息文本。預設語言檔案的完整内容请参考外掛首次运行產生的檔案。

## 柒、数据存储 (data.db)
外掛会在 `plugins/v-monitor/` 目錄下產生名为 `data.db` 的 SQLite3 資料庫檔案。
此檔案用于存储連線过代理伺服器的玩家的相关信息，通过玩家的 **UUID** 进行唯一标识。**请勿手动编辑此檔案。**
可以通过修改設定檔案来使用MySQL資料庫。

## 捌、从源程式碼构建
如果你想从源程式碼构建外掛，你需要 Java Development Kit (JDK) 17+ 和 Gradle。

### 8-1：构建步骤
1. 複製倉庫：`git clone https://github.com/MC-Nirvana/V-Monitor.git`
2. 进入倉庫目錄：`cd V-Monitor`
3. 执行构建指令：`./gradlew build`
4. 在 `build/libs/` 目錄下找到產生的 JAR 檔案

### 8-2：开发环境设置
- 推荐使用 IntelliJ IDEA 进行开发
- 导入專案后，确保 Gradle 依赖能够正确下载

## 玖、贡献与支持
欢迎通过 GitHub Issues 提交 Bug 報告和功能建议。

### 9-1：贡献方式
- 提交程式碼改进和新功能实现
- 完善文档和翻译
- 報告 Bug 和安全问题
- 参与讨论和提供使用反馈

### 9-2：提交 Pull Request 的最佳实践
1. Fork 專案并创建功能分支
2. 编写清晰的提交信息
3. 确保程式碼符合專案编码规范
4. 添加相应的測試用例
5. 保持 Pull Request 聚焦于单一功能或修复

### 9-3：开发者资源
- 專案遵循标准的 Git 工作流
- 请在提交 Pull Request 前确保程式碼通过所有測試
- 保持程式碼风格一致，参考现有程式碼结构

## 拾、專案路线图
- [ ] 完善 WebSocket 实时数据推送功能
- [ ] 提供 Web 管理界面

## 拾壹、许可证
本專案采用 [GPL-3.0 license](LICENSE) 开源许可证。

## 拾貳、作者的话
这是我第一次开发适用于 Minecraft Java Edition 的服务端外掛。虽然專案在开发过程中使用了 AI 辅助编程，程式碼质量还有待提升，但我会持續优化改进。这个外掛最初是为我自己的 Minecraft Java Edition 伺服器而开发的，希望能对其他伺服器管理员也有帮助。

## 拾叁、支持与反馈
如果你喜欢这个專案，请考虑：
- 给專案点个 Star ⭐
- 在社交媒体上分享这个專案
- 参与專案讨论，提供宝贵意见

### 13-1：贊助支持
如果你希望支持本專案的持續开发和维护，可以通过以下方式贊助：

- [面包多](https://mbd.pub/o/MC_Nirvana) - 通过面包多贊助（适用于中國大陸用户）
- [PayPal](https://paypal.me/WHFNirvana) - 通过 PayPal 贊助（适用于國外用户）

您的贊助将用于：
- 维护專案基础设施
- 请作者去码头整点薯条:)