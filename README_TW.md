- [中文版](./README.md)
- [English version](./README_EN.md)

# V-Monitor - 玩家活動與伺服器狀態監控 Velocity 插件

## 一、簡介
V-Monitor 是一個輕量級的 Velocity 代理端插件（內部開發代號：Arona-01），專注於監控玩家的加入、離開、切換伺服器等活動狀態，並提供方便的命令供玩家和管理員查詢在線玩家列表以及後端伺服器的詳細資訊。

## 二、主要特性
- **玩家活動提醒:** 在玩家加入、離開、切換伺服器時發送可自定義的訊息（包含首次加入區分）。
- **在線玩家列表查詢:** 提供命令查看代理總在線人數和各後端伺服器的在線玩家列表。
- **伺服器資訊查詢:** 提供命令查詢 Velocity 代理自身概覽和指定後端伺服器的詳細資訊。
- **插件資訊查詢:** 提供命令查詢插件清單和指定插件的詳細資訊。
- **高度自定義:** 所有面向玩家的訊息和命令輸出都支援通過語言文件自定義格式。
- **多語言支援:** 通過獨立的語言文件實現多語言功能。
- **伺服器別名:** 支援為後端伺服器設置別名。
- **數據持久化:** 使用 UUID 記錄玩家的首次加入資訊。

## 三、安裝指南
1.  從項目的 [Release 頁面](https://github.com/MC-Nirvana/V-Monitor/releases/latest) 下載最新版本的插件 JAR 文件。
2.  將下載好的 JAR 文件放入你的 Velocity 代理伺服器的 `plugins` 文件夾中。
3.  啟動 Velocity 代理伺服器。插件將自動生成默認的配置文件和語言文件。
4.  根據需要編輯配置文件和語言文件。
5.  重載插件配置（`/vm reload`）或重啟伺服器使更改生效。
6.  享受 V-Monitor 帶來的便捷功能！

## 四、插件用法 (命令)
插件的主命令是 `/vmonitor`，別名為 `/vm`。

| 命令                            | 用法示例                                             | 權限節點           | 描述                             |
|---------------------------------|------------------------------------------------------|--------------------|----------------------------------|
| `help`                          | `/vm help`                                           | `none`             | 獲取插件總幫助資訊。             |
| `reload`                        | `/vm reload`                                         | `vmonitor.reload`  | 重載插件配置。                   |
| `version`                       | `/vm version`                                        | `vmonitor.version` | 獲取插件版本資訊。               |
| `server list [all或伺服器名稱]` | `/vm server list all` 或 `/vm server list lobby`     | `none`             | 列出所有或指定伺服器上的玩家。   |
| `server info [all或伺服器名稱]` | `/vm server info all` 或 `/vm server info lobby`     | `none`             | 獲取所有或指定伺服器的詳細資訊。 |
| `plugin list`                   | `/vm plugin list`                                    | `vmonitor.plugin`  | 列出所有已加載插件。             |
| `plugin info [all或插件ID]`     | `/vm plugin info all` 或 `/vm plugin info V-Monitor` | `vmonitor.plugin`  | 獲取所有或指定插件的詳細資訊。   |

*默認情況下，擁有 OP 權限的玩家和控制台擁有所有權限節點。*

## 五、配置文件 (config.yml)
插件啟動後會在 `plugins/v-monitor/` 目錄下生成 `config.yml` 文件。

- `language.default`: 設置插件使用的語言代碼 (例如 `en_us`, `zh_cn`, `zh_tw`)。
- `server-aliases`: 在此部分為你的後端伺服器設置顯示別名，格式如 `實際伺服器名稱: "想要顯示的別名"`。

你可以編輯此文件來自定義插件的行為和訊息內容。默認配置文件的完整內容請參考插件首次運行生成的文件。

## 六、語言文件 (lang/*.yml)
語言文件位於 `plugins/v-monitor/lang/` 文件夾下，採用 YAML 格式。
每個 `.yml` 文件對應一種語言，其中包含了插件所有輸出到玩家或控制台的文本訊息和格式模板。你可以自由編輯這些文件，使用 MiniMessage 格式來自定義訊息的顏色、樣式和內容。
例如，`zh_tw.yml` 文件包含了繁體中文的所有訊息文本。默認語言文件的完整內容請參考插件首次運行生成的文件。

## 七、數據存儲 (playerdata.json)
插件會在 `plugins/v-monitor/` 目錄下生成 `playerdata.json` 文件。
此文件用於存儲連接過代理伺服器的玩家的首次加入資訊，通過玩家的 **UUID** 進行唯一識別。**請勿手動編輯此文件。**

## 八、從源碼構建
如果你想從源碼構建插件，你需要 Java Development Kit (JDK) 17+ 和 Maven/Gradle。請參考項目倉庫中的構建說明。

## 九、貢獻與支援
歡迎通過 GitHub Issues 提交 Bug 報告和功能建議。