package cn.nirvana.vMonitor.util;

import cn.nirvana.vMonitor.loader.ConfigLoader;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

/**
 * 数据库工具类，用于管理数据库连接和基本操作
 */
public class DatabaseUtil {
    private final Logger logger;
    private final ConfigLoader configLoader;
    private final Path dataDirectory;
    private HikariDataSource dataSource;
    private DatabaseType databaseType;

    public DatabaseUtil(Logger logger, ConfigLoader configLoader, Path dataDirectory) {
        this.logger = logger;
        this.configLoader = configLoader;
        this.dataDirectory = dataDirectory;
    }

    /**
     * 初始化数据库连接池和表结构
     *
     * @throws SQLException 数据库连接异常
     */
    public void initialize() throws SQLException {
        String type = configLoader.getDatabaseType();
        databaseType = DatabaseType.fromString(type);

        HikariConfig hikariConfig = new HikariConfig();

        // 设置连接池基本配置
        hikariConfig.setMaximumPoolSize(configLoader.getHikariMaximumPoolSize());
        hikariConfig.setMinimumIdle(configLoader.getHikariMinimumIdle());
        hikariConfig.setConnectionTimeout(configLoader.getHikariConnectionTimeout());
        hikariConfig.setIdleTimeout(configLoader.getHikariIdleTimeout());
        hikariConfig.setMaxLifetime(configLoader.getHikariMaxLifetime());

        switch (databaseType) {
            case SQLITE:
                initializeSQLite(hikariConfig);
                break;
            case MYSQL:
                initializeMySQL(hikariConfig);
                break;
            default:
                throw new SQLException("Unsupported database type: " + type);
        }

        dataSource = new HikariDataSource(hikariConfig);
        initializeTables();
        logger.info("Database connection pool initialized successfully with {} connections",
                dataSource.getMaximumPoolSize());
    }

    /**
     * 初始化 SQLite 数据库配置
     *
     * @param hikariConfig HikariCP 配置对象
     */
    private void initializeSQLite(HikariConfig hikariConfig) {
        String path = configLoader.getSQLitePath();
        String absolutePath;

        Path dbPath = Paths.get(path);

        if (dbPath.isAbsolute()) {
            absolutePath = path;
        } else {
            Path resolvedPath = dataDirectory.resolve(path);
            absolutePath = resolvedPath.toString();
        }

        hikariConfig.setJdbcUrl("jdbc:sqlite:" + absolutePath);
        hikariConfig.setDriverClassName("org.sqlite.JDBC");

        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        hikariConfig.addDataSourceProperty("journalMode", "WAL");
        hikariConfig.addDataSourceProperty("synchronous", "NORMAL");

        logger.info("SQLite database configured with path: {}", absolutePath);
    }

    /**
     * 初始化 MySQL 数据库配置
     *
     * @param hikariConfig HikariCP 配置对象
     */
    private void initializeMySQL(HikariConfig hikariConfig) {
        String host = configLoader.getMySQLHost();
        int port = configLoader.getMySQLPort();
        String database = configLoader.getMySQLDatabase();
        String username = configLoader.getMySQLUsername();
        String password = configLoader.getMySQLPassword();

        StringBuilder jdbcUrl = new StringBuilder();
        jdbcUrl.append("jdbc:mysql://")
                .append(host)
                .append(":")
                .append(port)
                .append("/")
                .append(database);

        Map<String, String> parameters = configLoader.getMySQLParameters();
        if (!parameters.isEmpty()) {
            jdbcUrl.append("?");
            boolean first = true;
            for (Map.Entry<String, String> entry : parameters.entrySet()) {
                if (!first) {
                    jdbcUrl.append("&");
                }
                jdbcUrl.append(entry.getKey())
                        .append("=")
                        .append(entry.getValue());
                first = false;
            }
        }

        hikariConfig.setJdbcUrl(jdbcUrl.toString());
        hikariConfig.setUsername(username);
        hikariConfig.setPassword(password);
        hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");

        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");
        hikariConfig.addDataSourceProperty("useLocalSessionState", "true");
        hikariConfig.addDataSourceProperty("rewriteBatchedStatements", "true");
        hikariConfig.addDataSourceProperty("cacheResultSetMetadata", "true");
        hikariConfig.addDataSourceProperty("cacheServerConfiguration", "true");
        hikariConfig.addDataSourceProperty("elideSetAutoCommits", "true");
        hikariConfig.addDataSourceProperty("maintainTimeStats", "false");

        logger.info("MySQL database configured with URL: {}", jdbcUrl);
    }

    /**
     * 初始化数据库表结构
     *
     * @throws SQLException SQL执行异常
     */
    private void initializeTables() throws SQLException {
        try (Connection connection = getConnection()) {
            switch (databaseType) {
                case SQLITE:
                    initializeSQLiteTables(connection);
                    break;
                case MYSQL:
                    initializeMySQLTables(connection);
                    break;
                default:
                    throw new SQLException("Unsupported database type: " + databaseType);
            }
        }
    }

    /**
     * 初始化 SQLite 数据库表结构
     *
     * @param connection 数据库连接
     * @throws SQLException SQL执行异常
     */
    private void initializeSQLiteTables(Connection connection) throws SQLException {
        // server_info 表 - 存储服务器基本信息
        executeStatement(connection, "CREATE TABLE IF NOT EXISTS server_info (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "startup_time TEXT NOT NULL, " +  // yyyy-mm-dd格式
                "last_report_generation_time TEXT NOT NULL" +  // yyyy-mm-dd格式
                ")");

        // server_tracking 表 - 存储历史峰值在线数据
        executeStatement(connection, "CREATE TABLE IF NOT EXISTS server_tracking (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "historical_peak_online INTEGER NOT NULL" +
                ")");

        // daily_peak_online 表 - 存储每日总峰值在线数据
        executeStatement(connection, "CREATE TABLE IF NOT EXISTS daily_peak_online (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "time TEXT NOT NULL UNIQUE, " +  // yyyy-mm-dd格式
                "overall INTEGER NOT NULL" +
                ")");

        // sub_server_peak_online 表 - 存储子服务器每日峰值在线数据
        executeStatement(connection, "CREATE TABLE IF NOT EXISTS sub_server_peak_online (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "time TEXT NOT NULL, " +  // yyyy-mm-dd格式
                "server_name VARCHAR(255) NOT NULL, " +
                "peak_online INTEGER NOT NULL, " +
                "UNIQUE(time, server_name)" +
                ")");

        // daily_new_players 表 - 存储每日新玩家统计数据
        executeStatement(connection, "CREATE TABLE IF NOT EXISTS daily_new_players (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "time TEXT NOT NULL UNIQUE, " +  // yyyy-mm-dd格式
                "overall INTEGER NOT NULL" +
                ")");

        // daily_new_players_info 表 - 存储每日新玩家详细信息
        executeStatement(connection, "CREATE TABLE IF NOT EXISTS daily_new_players_info (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "time TEXT NOT NULL, " +  // yyyy-mm-ddThh:mm:ssXXX格式
                "uuid VARCHAR(36) NOT NULL, " +
                "original_username VARCHAR(16) NOT NULL" +
                ")");

        // player_data 表 - 存储玩家基本信息和游戏时间统计
        executeStatement(connection, "CREATE TABLE IF NOT EXISTS player_data (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "uuid VARCHAR(36) NOT NULL UNIQUE, " +
                "username VARCHAR(16) NOT NULL, " +
                "first_join_time TEXT NOT NULL, " +  // yyyy-mm-ddThh:mm:ssXXX格式
                "last_login_time TEXT NOT NULL, " +  // yyyy-mm-ddThh:mm:ssXXX格式
                "play_time TEXT NOT NULL" +  // HH:mm:ss格式（时间段）
                ")");

        // player_daily_server_paths 表 - 存储玩家服务器路径记录
        executeStatement(connection, "CREATE TABLE IF NOT EXISTS player_daily_server_paths (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "time TEXT NOT NULL, " +  // yyyy-mm-ddThh:mm:ssXXX格式
                "uuid VARCHAR(36) NOT NULL, " +
                "from_server VARCHAR(255), " +
                "to_server VARCHAR(255) NOT NULL" +
                ")");

        logger.info("SQLite database tables initialized successfully");
    }

    /**
     * 初始化 MySQL 数据库表结构
     *
     * @param connection 数据库连接
     * @throws SQLException SQL执行异常
     */
    private void initializeMySQLTables(Connection connection) throws SQLException {
        // server_info 表 - 存储服务器基本信息
        executeStatement(connection, "CREATE TABLE IF NOT EXISTS server_info (" +
                "id INTEGER PRIMARY KEY AUTO_INCREMENT, " +
                "startup_time DATE NOT NULL, " +  // yyyy-mm-dd格式
                "last_report_generation_time DATE NOT NULL" +  // yyyy-mm-dd格式
                ")");

        // server_tracking 表 - 存储历史峰值在线数据
        executeStatement(connection, "CREATE TABLE IF NOT EXISTS server_tracking (" +
                "id INTEGER PRIMARY KEY AUTO_INCREMENT, " +
                "historical_peak_online INTEGER NOT NULL" +
                ")");

        // daily_peak_online 表 - 存储每日总峰值在线数据
        executeStatement(connection, "CREATE TABLE IF NOT EXISTS daily_peak_online (" +
                "id INTEGER PRIMARY KEY AUTO_INCREMENT, " +
                "time DATE NOT NULL UNIQUE, " +  // yyyy-mm-dd格式
                "overall INTEGER NOT NULL" +
                ")");

        // sub_server_peak_online 表 - 存储子服务器每日峰值在线数据
        executeStatement(connection, "CREATE TABLE IF NOT EXISTS sub_server_peak_online (" +
                "id INTEGER PRIMARY KEY AUTO_INCREMENT, " +
                "time DATE NOT NULL, " +  // yyyy-mm-dd格式
                "server_name VARCHAR(255) NOT NULL, " +
                "peak_online INTEGER NOT NULL, " +
                "UNIQUE(time, server_name)" +
                ")");

        // daily_new_players 表 - 存储每日新玩家统计数据
        executeStatement(connection, "CREATE TABLE IF NOT EXISTS daily_new_players (" +
                "id INTEGER PRIMARY KEY AUTO_INCREMENT, " +
                "time DATE NOT NULL UNIQUE, " +  // yyyy-mm-dd格式
                "overall INTEGER NOT NULL" +
                ")");

        // daily_new_players_info 表 - 存储每日新玩家详细信息
        executeStatement(connection, "CREATE TABLE IF NOT EXISTS daily_new_players_info (" +
                "id INTEGER PRIMARY KEY AUTO_INCREMENT, " +
                "time VARCHAR(255) NOT NULL, " +  // yyyy-mm-ddThh:mm:ssXXX格式
                "uuid VARCHAR(36) NOT NULL, " +
                "original_username VARCHAR(16) NOT NULL" +
                ")");

        // player_data 表 - 存储玩家基本信息和游戏时间统计
        executeStatement(connection, "CREATE TABLE IF NOT EXISTS player_data (" +
                "id INTEGER PRIMARY KEY AUTO_INCREMENT, " +
                "uuid VARCHAR(36) NOT NULL UNIQUE, " +
                "username VARCHAR(16) NOT NULL, " +
                "first_join_time VARCHAR(255) NOT NULL, " +  // yyyy-mm-ddThh:mm:ssXXX格式
                "last_login_time VARCHAR(255) NOT NULL, " +  // yyyy-mm-ddThh:mm:ssXXX格式
                "play_time TIME NOT NULL" +  // HH:mm:ss格式（时间段）
                ")");

        // player_daily_server_paths 表 - 存储玩家服务器路径记录
        executeStatement(connection, "CREATE TABLE IF NOT EXISTS player_daily_server_paths (" +
                "id INTEGER PRIMARY KEY AUTO_INCREMENT, " +
                "time VARCHAR(255) NOT NULL, " +  // yyyy-mm-ddThh:mm:ssXXX格式
                "uuid VARCHAR(36) NOT NULL, " +
                "from_server VARCHAR(255), " +
                "to_server VARCHAR(255) NOT NULL" +
                ")");

        logger.info("MySQL database tables initialized successfully");
    }

    /**
     * 执行DDL语句
     *
     * @param connection 数据库连接
     * @param sql        SQL语句
     * @throws SQLException SQL执行异常
     */
    private void executeStatement(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    /**
     * 获取数据库连接
     *
     * @return 数据库连接对象
     * @throws SQLException 数据库连接异常
     */
    public Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("Database connection pool not initialized");
        }
        return dataSource.getConnection();
    }

    /**
     * 执行更新操作（INSERT, UPDATE, DELETE）
     *
     * @param sql    SQL 语句
     * @param params 参数
     * @return 受影响的行数
     * @throws SQLException SQL 执行异常
     */
    public int executeUpdate(String sql, Object... params) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1, params[i]);
            }

            return statement.executeUpdate();
        }
    }

    /**
     * 关闭数据库连接池
     */
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("Database connection pool closed");
        }
    }

    /**
     * 获取数据库类型
     *
     * @return 数据库类型
     */
    public DatabaseType getDatabaseType() {
        return databaseType;
    }

    /**
     * 数据库类型枚举
     */
    public enum DatabaseType {
        SQLITE,
        MYSQL;

        public static DatabaseType fromString(String type) {
            for (DatabaseType databaseType : values()) {
                if (databaseType.name().equalsIgnoreCase(type)) {
                    return databaseType;
                }
            }
            throw new IllegalArgumentException("Unknown database type: " + type);
        }
    }
}
