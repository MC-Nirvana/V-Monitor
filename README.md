- [English version](./README_EN.md)
- [繁體中文版](./README_TW.md)

# V-Monitor - 玩家活动与服务器状态监控 Velocity 插件

## 一、简介
V-Monitor 是一个轻量级的 Velocity 代理端插件（内部开发代号：Arona-01），专注于监控玩家的加入、离开、切换服务器等活动状态，并提供方便的命令供玩家和管理员查询在线玩家列表以及后端服务器的详细信息。

## 二、主要特性
- **玩家活动提醒:** 在玩家加入、离开、切换服务器时发送可自定义的消息（包含首次加入区分）。
- **在线玩家列表查询:** 提供命令查看代理总在线人数和各后端服务器的在线玩家列表。
- **服务器信息查询:** 提供命令查询 Velocity 代理自身概览和指定后端服务器的详细信息。
- **插件信息查询:** 提供命令查询插件列表和指定插件的详细信息。
- **高度自定义:** 所有面向玩家的消息和命令输出都支持通过语言文件自定义格式。
- **多语言支持:** 通过独立的语言文件实现多语言功能。
- **服务器别名:** 支持为后端服务器设置别名。
- **数据持久化:** 使用 UUID 记录玩家的首次加入信息。

## 三、安装指南
1.  从项目的 [Release 页面](https://github.com/MC-Nirvana/V-Monitor/releases/latest) 下载最新版本的插件 JAR 文件。
2.  将下载好的 JAR 文件放入你的 Velocity 代理服务器的 `plugins` 文件夹中。
3.  启动 Velocity 代理服务器。插件将自动生成默认的配置文件和语言文件。
4.  根据需要编辑配置文件和语言文件。
5.  重载插件配置（`/vm reload`）或重启服务器使更改生效。
6.  享受 V-Monitor 带来的便捷功能！

## 四、插件用法 (命令)
插件的主命令是 `/vmonitor`，别名为 `/vm`。

| 命令                            | 用法示例                                             | 权限节点          | 描述                             |
|---------------------------------|------------------------------------------------------|-------------------|----------------------------------|
| `help`                          | `/vm help`                                           | `none`            | 获取插件总帮助信息。             |
| `reload`                        | `/vm reload`                                         | `vmonitor.reload` | 重载插件配置。                   |
| `server list [all或服务器名称]` | `/vm server list all` 或 `/vm server list lobby`     | `none`            | 列出所有或指定服务器上的玩家。   |
| `server info [all或服务器名称]` | `/vm server info all` 或 `/vm server info lobby`     | `none`            | 获取所有或指定服务器的详细信息。 |
| `plugin list`                   | `/vm plugin list`                                    | `vmonitor.plugin` | 列出所有已加载插件。             |
| `plugin info [all或插件ID]`     | `/vm plugin info all` 或 `/vm plugin info V-Monitor` | `vmonitor.plugin` | 获取所有或指定插件的详细信息。   |

*默认情况下，拥有 OP 权限的玩家和控制台拥有所有权限节点。*

## 五、配置文件 (config.yml)
插件启动后会在 `plugins/v-monitor/` 目录下生成 `config.yml` 文件。

- `language.default`: 设置插件使用的语言代码 (例如 `en_us`, `zh_cn`, `zh_tw`)。
- `server-aliases`: 在此部分为你的后端服务器设置显示别名，格式如 `实际服务器名称: "想要显示的别名"`。

你可以编辑此文件来自定义插件的行为和消息内容。默认配置文件的完整内容请参考插件首次运行生成的文件。

## 六、语言文件 (lang/*.yml)
语言文件位于 `plugins/v-monitor/lang/` 文件夹下，采用 YAML 格式。
每个 `.yml` 文件对应一种语言，其中包含了插件所有输出到玩家或控制台的文本消息和格式模板。你可以自由编辑这些文件，使用 MiniMessage 格式来自定义消息的颜色、样式和内容。
例如，`zh_cn.yml` 文件包含了简体中文的所有消息文本。默认语言文件的完整内容请参考插件首次运行生成的文件。

## 七、数据存储 (playerdata.json)
插件会在 `plugins/v-monitor/` 目录下生成 `playerdata.json` 文件。
此文件用于存储连接过代理服务器的玩家的首次加入信息，通过玩家的 **UUID** 进行唯一标识。**请勿手动编辑此文件。**

## 八、从源代码构建
如果你想从源代码构建插件，你需要 Java Development Kit (JDK) 17+ 和 Maven/Gradle。请参考项目仓库中的构建说明。

## 九、贡献与支持
欢迎通过 GitHub Issues 提交 Bug 报告和功能建议。