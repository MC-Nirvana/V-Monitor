package cn.nirvana.vMonitor.loader;

import cn.nirvana.vMonitor.util.DatabaseUtil;
import cn.nirvana.vMonitor.util.TimeUtil;

import org.slf4j.Logger;

import java.sql.*;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class DataLoader {
    private final Logger logger;
    private final DatabaseUtil databaseUtil;

    public DataLoader(Logger logger, DatabaseUtil databaseUtil) {
        this.logger = logger;
        this.databaseUtil = databaseUtil;
    }

    /**
     * 初始化数据库中的基础数据
     */
    public void initializeData() {
        try {
            // 初始化 server_info 表
            initializeServerInfo();
        } catch (SQLException e) {
            logger.error("Failed to initialize data: {}", e.getMessage());
        }
    }

    /**
     * 初始化服务器信息
     *
     * @throws SQLException SQL执行异常
     */
    private void initializeServerInfo() throws SQLException {
        try (Connection connection = databaseUtil.getConnection()) {
            // 检查记录是否存在
            boolean hasRecord = false;
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT COUNT(*) FROM server_info")) {

                ResultSet rs = statement.executeQuery();
                rs.next();
                hasRecord = rs.getInt(1) > 0;
            }

            // 如果没有记录，则插入初始记录
            if (!hasRecord) {
                String insertSQL;
                if (databaseUtil.getDatabaseType() == DatabaseUtil.DatabaseType.SQLITE) {
                    insertSQL = "INSERT INTO server_info (startup_time, last_report_generation_time) VALUES (?, NULL)";
                } else {
                    // MySQL可以使用IGNORE来避免重复插入
                    insertSQL = "INSERT IGNORE INTO server_info (startup_time, last_report_generation_time) VALUES (?, NULL)";
                }

                try (PreparedStatement insertStatement = connection.prepareStatement(insertSQL)) {
                    long currentTime = TimeUtil.SystemTime.getCurrentTimestamp();
                    // 使用DateConverter处理日期格式 (yyyy-mm-dd)
                    String dateStr = TimeUtil.DateConverter.fromTimestamp(currentTime);
                    insertStatement.setString(1, dateStr);
                    // 不需要设置第二个参数，SQL中已经写了NULL
                    insertStatement.executeUpdate();
                }
            }
        }
    }

    /**
     * 更新最后报表生成时间
     */
    public void updateLastReportGenerationTime() {
        try (Connection connection = databaseUtil.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE server_info SET last_report_generation_time = ?")) {

            long currentTime = TimeUtil.SystemTime.getCurrentTimestamp();
            String dateStr = TimeUtil.DateConverter.fromTimestamp(currentTime);
            statement.setString(1, dateStr);
            statement.executeUpdate();

            logger.info("Updated last report generation time to: {}", dateStr);
        } catch (SQLException e) {
            logger.error("Failed to update last report generation time: {}", e.getMessage());
        }
    }

    /**
     * 获取最后报表生成时间
     *
     * @return 最后报表生成时间，如果未生成过报表则返回null
     */
    public String getLastReportGenerationTime() {
        try (Connection connection = databaseUtil.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT last_report_generation_time FROM server_info LIMIT 1")) {

            ResultSet rs = statement.executeQuery();
            if (rs.next()) {
                return rs.getString("last_report_generation_time");
            }
        } catch (SQLException e) {
            logger.error("Failed to get last report generation time: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 获取服务器信息
     *
     * @return 服务器信息数据
     */
    public ServerInfoData getServerInfo() {
        ServerInfoData serverInfo = new ServerInfoData();
        try (Connection connection = databaseUtil.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT startup_time, last_report_generation_time FROM server_info LIMIT 1")) {

            ResultSet rs = statement.executeQuery();
            if (rs.next()) {
                // 使用 TimeUtil 转换时间
                String startupTimeStr = rs.getString("startup_time");
                String lastReportTimeStr = rs.getString("last_report_generation_time");

                serverInfo.startupTime = startupTimeStr;
                serverInfo.lastReportGenerationTime = lastReportTimeStr;
            }
        } catch (SQLException e) {
            logger.error("Failed to get server info: {}", e.getMessage());
        }
        return serverInfo;
    }

    // 在 ServerInfoData 类中添加构造函数
    public static class ServerInfoData {
        public String startupTime;
        public String lastReportGenerationTime;

        public ServerInfoData() {
            // 初始化为空字符串而不是使用当前时间
            this.startupTime = "";
            this.lastReportGenerationTime = "";
        }
    }

    /**
     * 根据UUID获取玩家数据
     *
     * @param uuid 玩家UUID
     * @return 玩家数据，如果不存在则返回null
     */
    public PlayerData getPlayerData(UUID uuid) {
        try (Connection connection = databaseUtil.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id, uuid, username, first_join_time, last_login_time, play_time FROM player_data WHERE uuid = ?")) {

            statement.setString(1, uuid.toString());
            ResultSet rs = statement.executeQuery();

            if (rs.next()) {
                PlayerData playerData = new PlayerData();
                playerData.id = rs.getInt("id");
                playerData.uuid = UUID.fromString(rs.getString("uuid"));
                playerData.username = rs.getString("username");

                // 使用 TimeUtil 转换时间
                String firstJoinTimeStr = rs.getString("first_join_time");
                String lastLoginTimeStr = rs.getString("last_login_time");
                long firstJoinTimestamp = TimeUtil.DateTimeConverter.toTimestamp(firstJoinTimeStr);
                long lastLoginTimestamp = TimeUtil.DateTimeConverter.toTimestamp(lastLoginTimeStr);
                playerData.firstJoinTime = LocalDateTime.ofInstant(java.time.Instant.ofEpochSecond(firstJoinTimestamp), ZoneId.systemDefault());
                playerData.lastLoginTime = LocalDateTime.ofInstant(java.time.Instant.ofEpochSecond(lastLoginTimestamp), ZoneId.systemDefault());

                // 处理 play_time (HH:mm:ss 格式)
                String playTimeStr = rs.getString("play_time");
                playerData.playTime = TimeUtil.TimePeriodConverter.toSeconds(playTimeStr);

                // 加载玩家的服务器路径数据
                loadPlayerServerPaths(playerData, connection);

                return playerData;
            }
        } catch (SQLException e) {
            logger.error("Failed to get player data for UUID {}: {}", uuid, e.getMessage());
        }
        return null;
    }

    /**
     * 根据玩家名称获取玩家数据
     *
     * @param playerName 玩家名称
     * @return 玩家数据，如果不存在则返回null
     */
    public PlayerData getPlayerDataByName(String playerName) {
        try (Connection connection = databaseUtil.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT uuid FROM player_data WHERE username = ?")) {

            statement.setString(1, playerName);
            ResultSet rs = statement.executeQuery();

            if (rs.next()) {
                String uuidStr = rs.getString("uuid");
                return getPlayerData(UUID.fromString(uuidStr));
            }
        } catch (SQLException e) {
            logger.error("Failed to get player data by name '{}': {}", playerName, e.getMessage());
        }
        return null;
    }

    /**
     * 获取所有玩家名称列表
     *
     * @return 玩家名称列表
     */
    public List<String> getAllPlayerNames() {
        List<String> playerNames = new ArrayList<>();
        try (Connection connection = databaseUtil.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT username FROM player_data")) {

            ResultSet rs = statement.executeQuery();
            while (rs.next()) {
                playerNames.add(rs.getString("username"));
            }
        } catch (SQLException e) {
            logger.error("Failed to get all player names: {}", e.getMessage());
        }
        return playerNames;
    }

    /**
     * 加载玩家的服务器路径数据
     *
     * @param playerData 玩家数据对象
     * @param connection 数据库连接
     * @throws SQLException SQL执行异常
     */
    private void loadPlayerServerPaths(PlayerData playerData, Connection connection) throws SQLException {
        playerData.dailyServerPaths = new ConcurrentHashMap<>();

        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT time, from_server, to_server FROM player_daily_server_paths WHERE uuid = ? ORDER BY time")) {

            statement.setString(1, playerData.uuid.toString());
            ResultSet rs = statement.executeQuery();

            while (rs.next()) {
                ServerPathData pathData = new ServerPathData();

                // 使用 TimeUtil 转换时间
                String timeStr = rs.getString("time");
                long timestamp = TimeUtil.DateTimeConverter.toTimestamp(timeStr);
                pathData.time = LocalDateTime.ofInstant(java.time.Instant.ofEpochSecond(timestamp), ZoneId.systemDefault());

                pathData.from = rs.getString("from_server");
                pathData.to = rs.getString("to_server");

                // 按日期分组存储路径数据
                LocalDate date = pathData.time.toLocalDate();
                String dateStr = date.toString();

                playerData.dailyServerPaths.computeIfAbsent(dateStr, k -> new ArrayList<>()).add(pathData);
            }
        }
    }

    /**
     * 创建玩家数据
     *
     * @param uuid 玩家UUID
     * @param playerName 玩家名称
     */
    public void createPlayerData(UUID uuid, String playerName) {
        try (Connection connection = databaseUtil.getConnection()) {
            connection.setAutoCommit(false);

            try {
                // 插入玩家数据
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO player_data (uuid, username, first_join_time, last_login_time, play_time) VALUES (?, ?, ?, ?, ?)")) {

                    long currentTime = TimeUtil.SystemTime.getCurrentTimestamp();
                    String dateTimeStr = TimeUtil.DateTimeConverter.fromTimestamp(currentTime);
                    String playTimeStr = TimeUtil.TimePeriodConverter.fromSeconds(0); // 初始化为00:00:00

                    statement.setString(1, uuid.toString());
                    statement.setString(2, playerName);
                    statement.setString(3, dateTimeStr);
                    statement.setString(4, dateTimeStr);
                    statement.setString(5, playTimeStr);

                    statement.executeUpdate();
                }

                // 记录新玩家信息（传递玩家名）
                recordNewPlayer(uuid, playerName, connection);

                connection.commit();
                logger.info("Created new player data for {}: {}", playerName, uuid);
            } catch (SQLException e) {
                connection.rollback();
                logger.error("Failed to create player data for {}: {}", playerName, e.getMessage());
                throw e;
            }
        } catch (SQLException e) {
            logger.error("Failed to create player data for {}: {}", playerName, e.getMessage());
        }
    }


    /**
     * 记录新玩家信息
     *
     * @param uuid 玩家UUID
     * @param playerName 玩家名称
     * @param connection 数据库连接
     * @throws SQLException SQL执行异常
     */
    private void recordNewPlayer(UUID uuid, String playerName, Connection connection) throws SQLException {
        // 更新 daily_new_players 表
        int overall = 1;
        long currentTime = TimeUtil.SystemTime.getCurrentTimestamp();
        String dateStr = TimeUtil.DateConverter.fromTimestamp(currentTime);

        try (PreparedStatement selectStatement = connection.prepareStatement(
                "SELECT overall FROM daily_new_players WHERE time = ?")) {

            selectStatement.setString(1, dateStr);
            ResultSet rs = selectStatement.executeQuery();

            if (rs.next()) {
                overall = rs.getInt("overall") + 1;
                try (PreparedStatement updateStatement = connection.prepareStatement(
                        "UPDATE daily_new_players SET overall = ? WHERE time = ?")) {

                    updateStatement.setInt(1, overall);
                    updateStatement.setString(2, dateStr);
                    updateStatement.executeUpdate();
                }
            } else {
                try (PreparedStatement insertStatement = connection.prepareStatement(
                        "INSERT INTO daily_new_players (time, overall) VALUES (?, ?)")) {

                    insertStatement.setString(1, dateStr);
                    insertStatement.setInt(2, overall);
                    insertStatement.executeUpdate();
                }
            }
        }

        // 插入 daily_new_players_info 记录
        String insertSQL;
        if (databaseUtil.getDatabaseType() == DatabaseUtil.DatabaseType.SQLITE) {
            insertSQL = "INSERT OR IGNORE INTO daily_new_players_info (time, uuid, original_username) VALUES (?, ?, ?)";
        } else {
            insertSQL = "INSERT IGNORE INTO daily_new_players_info (time, uuid, original_username) VALUES (?, ?, ?)";
        }

        try (PreparedStatement statement = connection.prepareStatement(insertSQL)) {
            String dateTimeStr = TimeUtil.DateTimeConverter.fromTimestamp(currentTime);
            statement.setString(1, dateTimeStr);
            statement.setString(2, uuid.toString());
            statement.setString(3, playerName);
            statement.executeUpdate();
        }
    }

    /**
     * 增量更新玩家游戏时间（线程安全）
     *
     * @param uuid 玩家UUID
     * @param sessionDuration 会话时长
     */
    public void incrementPlayerPlayTime(UUID uuid, Duration sessionDuration) {
        ReentrantLock lock = getPlayerLock(uuid);
        lock.lock();
        try {
            updatePlayerPlayTimeInternal(uuid, sessionDuration);
        } finally {
            lock.unlock();
        }
    }

    // 为每个玩家维护一个锁
    private final Map<UUID, ReentrantLock> playerUpdateLocks = new ConcurrentHashMap<>();

    private ReentrantLock getPlayerLock(UUID uuid) {
        return playerUpdateLocks.computeIfAbsent(uuid, k -> new ReentrantLock());
    }

    /**
     * 内部方法：更新玩家游戏时间
     *
     * @param uuid 玩家UUID
     * @param sessionDuration 会话时长
     */
    private void updatePlayerPlayTimeInternal(UUID uuid, Duration sessionDuration) {
        try (Connection connection = databaseUtil.getConnection()) {
            // 获取当前游戏时间
            PlayerData playerData = getPlayerData(uuid);
            if (playerData != null) {
                // 计算新的游戏时间
                long newPlayTimeSeconds = playerData.playTime + sessionDuration.getSeconds();
                String newPlayTimeStr = TimeUtil.TimePeriodConverter.fromSeconds(newPlayTimeSeconds);

                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE player_data SET play_time = ? WHERE uuid = ?")) {

                    statement.setString(1, newPlayTimeStr);
                    statement.setString(2, uuid.toString());

                    statement.executeUpdate();
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to update player play time for UUID {}: {}", uuid, e.getMessage());
        }
    }

    /**
     * 玩家登录时更新数据（线程安全）
     *
     * @param uuid 玩家UUID
     * @param playerName 玩家名称
     */
    public void updatePlayerOnLogin(UUID uuid, String playerName) {
        ReentrantLock lock = getPlayerLock(uuid);
        lock.lock();
        try {
            updatePlayerOnLoginInternal(uuid, playerName);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 内部方法：玩家登录时更新数据
     */
    private void updatePlayerOnLoginInternal(UUID uuid, String playerName) {
        try (Connection connection = databaseUtil.getConnection()) {
            PlayerData playerData = getPlayerData(uuid);

            if (playerData == null) {
                logger.info("Creating new player data for {} ({})", playerName, uuid);
                createPlayerData(uuid, playerName);
            } else {
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE player_data SET username = ?, last_login_time = ? WHERE uuid = ?")) {

                    long currentTime = TimeUtil.SystemTime.getCurrentTimestamp();
                    String dateTimeStr = TimeUtil.DateTimeConverter.fromTimestamp(currentTime);

                    statement.setString(1, playerName);
                    statement.setString(2, dateTimeStr);
                    statement.setString(3, uuid.toString());

                    int updatedRows = statement.executeUpdate();
                    if (updatedRows > 0) {
                        logger.debug("Updated login time for player {}: {}", playerName, uuid);
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to update player login for {}: {}", playerName, e.getMessage());
        }
    }


    /**
     * 玩家退出时更新数据（线程安全）
     *
     * @param uuid 玩家UUID
     * @param playerName 玩家名称
     * @param disconnectedFromServer 断开连接的服务器
     * @param sessionDuration 会话时长
     */
    public void updatePlayerOnQuit(UUID uuid, String playerName, String disconnectedFromServer, Duration sessionDuration) {
        ReentrantLock lock = getPlayerLock(uuid);
        lock.lock();
        try {
            updatePlayerOnQuitInternal(uuid, playerName, disconnectedFromServer, sessionDuration);
        } finally {
            lock.unlock();
            // 玩家退出时移除锁
            playerUpdateLocks.remove(uuid);
        }
    }

    /**
     * 内部方法：玩家退出时更新数据
     */
    private void updatePlayerOnQuitInternal(UUID uuid, String playerName, String disconnectedFromServer, Duration sessionDuration) {
        try (Connection connection = databaseUtil.getConnection()) {
            PlayerData playerData = getPlayerData(uuid);

            if (playerData == null) {
                logger.warn("Attempted to update quit for unknown player: {}. Skipping.", uuid);
                return;
            }

            // 计算新的游戏时间
            long newPlayTimeSeconds = playerData.playTime + sessionDuration.getSeconds();
            String newPlayTimeStr = TimeUtil.TimePeriodConverter.fromSeconds(newPlayTimeSeconds);

            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE player_data SET play_time = ? WHERE uuid = ?")) {

                statement.setString(1, newPlayTimeStr);
                statement.setString(2, uuid.toString());

                statement.executeUpdate();
            }
        } catch (SQLException e) {
            logger.error("Failed to update player quit for {}: {}", playerName, e.getMessage());
        }
    }

    /**
     * 玩家登录服务器时更新数据（线程安全）
     *
     * @param uuid 玩家UUID
     * @param fromServer 来源服务器名称
     * @param toServer 目标服务器名称
     */
    public void updatePlayerServerLogin(UUID uuid, String fromServer, String toServer) {
        ReentrantLock lock = getPlayerLock(uuid);
        lock.lock();
        try {
            updatePlayerServerLoginInternal(uuid, fromServer, toServer);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 内部方法：玩家登录服务器时更新数据
     */
    private void updatePlayerServerLoginInternal(UUID uuid, String fromServer, String toServer) {
        try (Connection connection = databaseUtil.getConnection()) {
            PlayerData playerData = getPlayerData(uuid);

            if (playerData == null) {
                logger.warn("Attempted to update server login for unknown player: {}. Please ensure player data is created.", uuid);
                return;
            }

            String insertSQL;
            if (databaseUtil.getDatabaseType() == DatabaseUtil.DatabaseType.SQLITE) {
                insertSQL = "INSERT OR IGNORE INTO player_daily_server_paths (time, uuid, from_server, to_server) VALUES (?, ?, ?, ?)";
            } else {
                insertSQL = "INSERT IGNORE INTO player_daily_server_paths (time, uuid, from_server, to_server) VALUES (?, ?, ?, ?)";
            }

            try (PreparedStatement statement = connection.prepareStatement(insertSQL)) {
                long currentTime = TimeUtil.SystemTime.getCurrentTimestamp();
                String dateTimeStr = TimeUtil.DateTimeConverter.fromTimestamp(currentTime);

                statement.setString(1, dateTimeStr);
                statement.setString(2, uuid.toString());
                statement.setString(3, fromServer);  // 直接使用传入的来源服务器名
                statement.setString(4, toServer);    // 直接使用传入的目标服务器名

                statement.executeUpdate();
            }
        } catch (SQLException e) {
            logger.error("Failed to update player server login from {} to {}: {}", fromServer, toServer, e.getMessage());
        }
    }


    /**
     * 获取玩家最后登录的服务器
     *
     * @param connection 数据库连接
     * @param uuid 玩家UUID
     * @return 最后登录的服务器名称
     * @throws SQLException SQL执行异常
     */
    private String getLastServerForPlayer(Connection connection, UUID uuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT to_server FROM player_daily_server_paths WHERE uuid = ? ORDER BY time DESC LIMIT 1")) {

            statement.setString(1, uuid.toString());
            ResultSet rs = statement.executeQuery();

            if (rs.next()) {
                return rs.getString("to_server");
            }

            return null;
        }
    }

    /**
     * 更新历史峰值在线人数
     *
     * @param currentOnlineCount 当前在线人数
     */
    public void updateHistoricalPeakOnline(int currentOnlineCount) {
        try (Connection connection = databaseUtil.getConnection()) {
            connection.setAutoCommit(false);

            try {
                long currentTime = TimeUtil.SystemTime.getCurrentTimestamp();
                String dateStr = TimeUtil.DateConverter.fromTimestamp(currentTime);

                // 更新当日峰值
                updateDailyPeakOnline(connection, dateStr, currentOnlineCount);

                // 更新历史峰值
                updateHistoricalPeak(connection, currentOnlineCount);

                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            logger.error("Failed to update historical peak online: {}", e.getMessage());
        }
    }

    /**
     * 更新当日峰值在线人数
     *
     * @param connection 数据库连接
     * @param dateStr 日期字符串 (yyyy-mm-dd)
     * @param currentOnlineCount 当前在线人数
     * @throws SQLException SQL执行异常
     */
    private void updateDailyPeakOnline(Connection connection, String dateStr, int currentOnlineCount) throws SQLException {
        try (PreparedStatement selectStatement = connection.prepareStatement(
                "SELECT overall FROM daily_peak_online WHERE time = ?")) {

            selectStatement.setString(1, dateStr);
            ResultSet rs = selectStatement.executeQuery();

            if (rs.next()) {
                int existingPeak = rs.getInt("overall");
                if (currentOnlineCount > existingPeak) {
                    try (PreparedStatement updateStatement = connection.prepareStatement(
                            "UPDATE daily_peak_online SET overall = ? WHERE time = ?")) {

                        updateStatement.setInt(1, currentOnlineCount);
                        updateStatement.setString(2, dateStr);
                        updateStatement.executeUpdate();
                    }
                }
            } else {
                try (PreparedStatement insertStatement = connection.prepareStatement(
                        "INSERT INTO daily_peak_online (time, overall) VALUES (?, ?)")) {

                    insertStatement.setString(1, dateStr);
                    insertStatement.setInt(2, currentOnlineCount);
                    insertStatement.executeUpdate();
                }
            }
        }
    }

    /**
     * 更新历史峰值在线人数
     *
     * @param connection 数据库连接
     * @param currentOnlineCount 当前在线人数
     * @throws SQLException SQL执行异常
     */
    private void updateHistoricalPeak(Connection connection, int currentOnlineCount) throws SQLException {
        try (PreparedStatement selectStatement = connection.prepareStatement(
                "SELECT historical_peak_online FROM server_tracking LIMIT 1");
             PreparedStatement updateStatement = connection.prepareStatement(
                     "UPDATE server_tracking SET historical_peak_online = ?")) {

            ResultSet rs = selectStatement.executeQuery();

            if (rs.next()) {
                int existingPeak = rs.getInt("historical_peak_online");
                if (currentOnlineCount > existingPeak) {
                    updateStatement.setInt(1, currentOnlineCount);
                    updateStatement.executeUpdate();
                }
            } else {
                // 如果没有记录，则插入新记录
                try (PreparedStatement insertStatement = connection.prepareStatement(
                        "INSERT INTO server_tracking (historical_peak_online) VALUES (?)")) {

                    insertStatement.setInt(1, currentOnlineCount);
                    insertStatement.executeUpdate();
                }
            }
        }
    }

    /**
     * 更新子服务器峰值在线人数
     *
     * @param serverName 服务器名称
     * @param currentOnlineCount 当前在线人数
     */
    public void updateSubServerPeakOnline(String serverName, int currentOnlineCount) {
        try (Connection connection = databaseUtil.getConnection()) {
            long currentTime = TimeUtil.SystemTime.getCurrentTimestamp();
            String dateStr = TimeUtil.DateConverter.fromTimestamp(currentTime);

            try (PreparedStatement selectStatement = connection.prepareStatement(
                    "SELECT peak_online FROM sub_server_peak_online WHERE time = ? AND server_name = ?")) {

                selectStatement.setString(1, dateStr);
                selectStatement.setString(2, serverName);

                ResultSet rs = selectStatement.executeQuery();

                if (rs.next()) {
                    int existingPeak = rs.getInt("peak_online");
                    if (currentOnlineCount > existingPeak) {
                        try (PreparedStatement updateStatement = connection.prepareStatement(
                                "UPDATE sub_server_peak_online SET peak_online = ? WHERE time = ? AND server_name = ?")) {

                            updateStatement.setInt(1, currentOnlineCount);
                            updateStatement.setString(2, dateStr);
                            updateStatement.setString(3, serverName);
                            updateStatement.executeUpdate();
                        }
                    }
                } else {
                    try (PreparedStatement insertStatement = connection.prepareStatement(
                            "INSERT INTO sub_server_peak_online (time, server_name, peak_online) VALUES (?, ?, ?)")) {

                        insertStatement.setString(1, dateStr);
                        insertStatement.setString(2, serverName);
                        insertStatement.setInt(3, currentOnlineCount);
                        insertStatement.executeUpdate();
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to update sub server peak online for {}: {}", serverName, e.getMessage());
        }
    }

    /**
     * 获取指定日期范围内的每日峰值在线人数
     *
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 日期到在线人数的映射
     */
    public Map<String, Integer> getDailyPeakOnlinePlayers(LocalDate startDate, LocalDate endDate) {
        Map<String, Integer> result = new LinkedHashMap<>();
        try (Connection connection = databaseUtil.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT time, overall FROM daily_peak_online WHERE time BETWEEN ? AND ? ORDER BY time")) {

            statement.setString(1, startDate.toString());
            statement.setString(2, endDate.toString());

            ResultSet rs = statement.executeQuery();
            while (rs.next()) {
                result.put(rs.getString("time"), rs.getInt("overall"));
            }
        } catch (SQLException e) {
            logger.error("Failed to get daily peak online players: {}", e.getMessage());
        }
        return result;
    }

    /**
     * 获取服务器总玩家数量
     *
     * @return 玩家总数
     */
    public int getTotalPlayerCount() {
        try (Connection connection = databaseUtil.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM player_data")) {

            ResultSet rs = statement.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.error("Failed to get total player count: {}", e.getMessage());
        }
        return 0;
    }

    /**
     * 获取指定日期范围内的新玩家数量
     *
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 新玩家总数
     */
    public int getNewPlayerCount(LocalDate startDate, LocalDate endDate) {
        try (Connection connection = databaseUtil.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT SUM(overall) FROM daily_new_players WHERE time BETWEEN ? AND ?")) {

            statement.setString(1, startDate.toString());
            statement.setString(2, endDate.toString());

            ResultSet rs = statement.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.error("Failed to get new player count: {}", e.getMessage());
        }
        return 0;
    }

    /**
     * 获取核心玩家数量（登录天数超过指定阈值的玩家）
     *
     * @param daysThreshold 天数阈值
     * @return 核心玩家数量
     */
    public int getCorePlayerCount(int daysThreshold) {
        try (Connection connection = databaseUtil.getConnection()) {
            // 这里需要根据实际业务逻辑实现
            // 简化实现：假设我们统计登录次数超过阈值的玩家
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT COUNT(*) FROM player_data WHERE play_time > ?")) {

                // 假设阈值为15天，转换为秒数
                int secondsThreshold = daysThreshold * 24 * 60 * 60;
                statement.setString(1, TimeUtil.TimePeriodConverter.fromSeconds(secondsThreshold));

                ResultSet rs = statement.executeQuery();
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to get core player count: {}", e.getMessage());
        }
        return 0;
    }

    /**
     * 获取流失风险玩家数量（超过指定天数未登录的玩家）
     *
     * @param daysThreshold 天数阈值
     * @return 流失风险玩家数量
     */
    public int getAtRiskPlayerCount(int daysThreshold) {
        try (Connection connection = databaseUtil.getConnection()) {
            LocalDate cutoffDate = LocalDate.now().minusDays(daysThreshold);
            String cutoffDateTime = cutoffDate.atStartOfDay().toString();

            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT COUNT(*) FROM player_data WHERE last_login_time < ?")) {

                statement.setString(1, cutoffDateTime);

                ResultSet rs = statement.executeQuery();
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to get at-risk player count: {}", e.getMessage());
        }
        return 0;
    }

    /**
     * 获取平均DAU（日活跃用户数）
     *
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 平均DAU
     */
    public double getAverageDAU(LocalDate startDate, LocalDate endDate) {
        try (Connection connection = databaseUtil.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT AVG(overall) FROM daily_peak_online WHERE time BETWEEN ? AND ?")) {

            statement.setString(1, startDate.toString());
            statement.setString(2, endDate.toString());

            ResultSet rs = statement.executeQuery();
            if (rs.next()) {
                return rs.getDouble(1);
            }
        } catch (SQLException e) {
            logger.error("Failed to get average DAU: {}", e.getMessage());
        }
        return 0.0;
    }

    /**
     * 获取服务器历史峰值在线人数
     *
     * @return 历史峰值在线人数
     */
    public int getHistoricalPeakOnline() {
        try (Connection connection = databaseUtil.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT historical_peak_online FROM server_tracking LIMIT 1")) {

            ResultSet rs = statement.executeQuery();
            if (rs.next()) {
                return rs.getInt("historical_peak_online");
            }
        } catch (SQLException e) {
            logger.error("Failed to get historical peak online: {}", e.getMessage());
        }
        return 0;
    }

    /**
     * 获取玩家上线时间段分布（按小时）
     *
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 小时到玩家数量的映射
     */
    public Map<Integer, Integer> getHourlyPlayerDistribution(LocalDate startDate, LocalDate endDate) {
        Map<Integer, Integer> hourlyData = new LinkedHashMap<>();

        // 初始化24小时数据
        for (int i = 0; i < 24; i++) {
            hourlyData.put(i, 0);
        }

        try (Connection connection = databaseUtil.getConnection()) {
            String sql;
            if (databaseUtil.getDatabaseType() == DatabaseUtil.DatabaseType.SQLITE) {
                sql = "SELECT strftime('%H', time) as hour, COUNT(*) as count FROM player_daily_server_paths " +
                        "WHERE date(time) BETWEEN ? AND ? GROUP BY hour";
            } else {
                sql = "SELECT HOUR(time) as hour, COUNT(*) as count FROM player_daily_server_paths " +
                        "WHERE DATE(time) BETWEEN ? AND ? GROUP BY hour";
            }

            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, startDate.toString());
                statement.setString(2, endDate.toString());

                ResultSet rs = statement.executeQuery();
                while (rs.next()) {
                    int hour = Integer.parseInt(rs.getString("hour"));
                    int count = rs.getInt("count");
                    hourlyData.put(hour, count);
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to get hourly player distribution: {}", e.getMessage());
        }
        return hourlyData;
    }

    /**
     * 获取玩家登录星期分布
     *
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 星期到玩家数量的映射（1=周一，7=周日）
     */
    public Map<Integer, Integer> getWeeklyPlayerDistribution(LocalDate startDate, LocalDate endDate) {
        Map<Integer, Integer> weeklyData = new LinkedHashMap<>();

        // 初始化7天数据
        for (int i = 1; i <= 7; i++) {
            weeklyData.put(i, 0);
        }

        try (Connection connection = databaseUtil.getConnection()) {
            String sql;
            if (databaseUtil.getDatabaseType() == DatabaseUtil.DatabaseType.SQLITE) {
                sql = "SELECT strftime('%w', time) as day, COUNT(*) as count FROM player_daily_server_paths " +
                        "WHERE date(time) BETWEEN ? AND ? GROUP BY day";
            } else {
                sql = "SELECT DAYOFWEEK(time) as day, COUNT(*) as count FROM player_daily_server_paths " +
                        "WHERE DATE(time) BETWEEN ? AND ? GROUP BY day";
            }

            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, startDate.toString());
                statement.setString(2, endDate.toString());

                ResultSet rs = statement.executeQuery();
                while (rs.next()) {
                    int day = Integer.parseInt(rs.getString("day"));
                    // 转换为1-7（周一到周日）
                    int adjustedDay = day == 0 ? 7 : (day == 1 ? 7 : day - 1);
                    if (databaseUtil.getDatabaseType() == DatabaseUtil.DatabaseType.MYSQL) {
                        adjustedDay = day == 1 ? 7 : day - 1;
                    }
                    int count = rs.getInt("count");
                    weeklyData.put(adjustedDay, count);
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to get weekly player distribution: {}", e.getMessage());
        }
        return weeklyData;
    }

    /**
     * 获取服务器分布数据
     *
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 服务器名称到玩家数量的映射
     */
    public Map<String, Integer> getServerDistribution(LocalDate startDate, LocalDate endDate) {
        Map<String, Integer> serverData = new LinkedHashMap<>();

        try (Connection connection = databaseUtil.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT to_server, COUNT(DISTINCT uuid) as player_count FROM player_daily_server_paths " +
                             "WHERE date(time) BETWEEN ? AND ? GROUP BY to_server ORDER BY player_count DESC")) {

            statement.setString(1, startDate.toString());
            statement.setString(2, endDate.toString());

            ResultSet rs = statement.executeQuery();
            while (rs.next()) {
                String server = rs.getString("to_server");
                int count = rs.getInt("player_count");
                serverData.put(server, count);
            }
        } catch (SQLException e) {
            logger.error("Failed to get server distribution: {}", e.getMessage());
        }
        return serverData;
    }

    /**
     * 获取最长在线时间的玩家TOP列表
     *
     * @param limit 返回记录数
     * @return 玩家列表
     */
    public List<TopPlayerByPlayTime> getTopPlayersByPlayTime(int limit) {
        List<TopPlayerByPlayTime> topPlayers = new ArrayList<>();

        try (Connection connection = databaseUtil.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT username, play_time FROM player_data ORDER BY play_time DESC LIMIT ?")) {

            statement.setInt(1, limit);

            ResultSet rs = statement.executeQuery();
            while (rs.next()) {
                TopPlayerByPlayTime player = new TopPlayerByPlayTime();
                player.username = rs.getString("username");
                player.playTime = rs.getString("play_time");
                topPlayers.add(player);
            }
        } catch (SQLException e) {
            logger.error("Failed to get top players by play time: {}", e.getMessage());
        }
        return topPlayers;
    }

    /**
     * 获取每日玩家数量最多的几天
     *
     * @param limit 返回记录数
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 日期到玩家数量的映射
     */
    public Map<String, Integer> getTopPlayerDays(int limit, LocalDate startDate, LocalDate endDate) {
        Map<String, Integer> topDays = new LinkedHashMap<>();

        try (Connection connection = databaseUtil.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT time, overall FROM daily_peak_online WHERE time BETWEEN ? AND ? " +
                             "ORDER BY overall DESC LIMIT ?")) {

            statement.setString(1, startDate.toString());
            statement.setString(2, endDate.toString());
            statement.setInt(3, limit);

            ResultSet rs = statement.executeQuery();
            while (rs.next()) {
                topDays.put(rs.getString("time"), rs.getInt("overall"));
            }
        } catch (SQLException e) {
            logger.error("Failed to get top player days: {}", e.getMessage());
        }
        return topDays;
    }

    /**
     * 获取最受欢迎的服务器列表
     *
     * @param limit 返回记录数
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 服务器信息列表
     */
    public List<PopularServer> getPopularServers(int limit, LocalDate startDate, LocalDate endDate) {
        List<PopularServer> popularServers = new ArrayList<>();

        try (Connection connection = databaseUtil.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT to_server, COUNT(DISTINCT uuid) as player_count, " +
                             "AVG(CAST(SUBSTR(play_time, 1, 2) AS INTEGER) * 3600 + " +
                             "CAST(SUBSTR(play_time, 4, 2) AS INTEGER) * 60 + " +
                             "CAST(SUBSTR(play_time, 7, 2) AS INTEGER)) as avg_seconds " +
                             "FROM player_daily_server_paths psp " +
                             "JOIN player_data pd ON psp.uuid = pd.uuid " +
                             "WHERE date(psp.time) BETWEEN ? AND ? " +
                             "GROUP BY to_server " +
                             "ORDER BY player_count DESC LIMIT ?")) {

            statement.setString(1, startDate.toString());
            statement.setString(2, endDate.toString());
            statement.setInt(3, limit);

            ResultSet rs = statement.executeQuery();
            while (rs.next()) {
                PopularServer server = new PopularServer();
                server.serverName = rs.getString("to_server");
                server.playerCount = rs.getInt("player_count");
                // 将秒数转换为分钟
                server.avgPlayTimeMinutes = rs.getInt("avg_seconds") / 60;
                popularServers.add(server);
            }
        } catch (SQLException e) {
            logger.error("Failed to get popular servers: {}", e.getMessage());
        }
        return popularServers;
    }

    // 数据类定义
    public static class PlayerData {
        public int id;
        public UUID uuid;
        public String username;
        public LocalDateTime firstJoinTime;
        public LocalDateTime lastLoginTime;
        public long playTime; // 以秒为单位存储
        public Map<String, List<ServerPathData>> dailyServerPaths;

        public PlayerData() {
            this.uuid = UUID.randomUUID();
            this.username = "";
            LocalDateTime now = LocalDateTime.now();
            this.firstJoinTime = now;
            this.lastLoginTime = now;
            this.playTime = 0;
            this.dailyServerPaths = new ConcurrentHashMap<>();
        }
    }

    public static class ServerPathData {
        public LocalDateTime time;
        public String from;
        public String to;

        public ServerPathData() {
            this.time = LocalDateTime.now();
            this.from = "";
            this.to = "";
        }
    }

    public static class TopPlayerByPlayTime {
        public String username;
        public String playTime;
    }

    public static class PopularServer {
        public String serverName;
        public int playerCount;
        public int avgPlayTimeMinutes;
    }
}
