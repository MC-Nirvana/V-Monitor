package cn.nirvana.vMonitor.util;

import com.google.gson.JsonParseException;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

public class TimeUtil {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * 系统时间工具类
     * 提供获取当前系统时间的功能
     */
    public static class SystemTime {
        /**
         * 获取当前时间戳
         *
         * @return 当前时间的UNIX时间戳（秒）
         */
        public static long getCurrentTimestamp() {
            return System.currentTimeMillis() / 1000;
        }
    }

    /**
     * ISO 8601日期格式 (yyyy-MM-dd) 的双向转换工具类
     */
    public static class DateConverter {
        /**
         * 将时间戳转换为日期字符串
         */
        public static String fromTimestamp(long timestamp) {
            return Instant.ofEpochSecond(timestamp)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                    .format(DATE_FORMATTER);
        }

        /**
         * 将日期字符串转换为时间戳
         */
        public static long toTimestamp(String dateStr) {
            try {
                LocalDate date = LocalDate.parse(dateStr, DATE_FORMATTER);
                return date.atStartOfDay(ZoneId.systemDefault()).toEpochSecond();
            } catch (Exception e) {
                System.err.println("Error parsing date string '" + dateStr + "': " + e.getMessage());
                return 0L;
            }
        }
    }

    /**
     * ISO 8601时间格式 (HH:mm:ss) 的双向转换工具类
     */
    public static class TimePeriodConverter {
        /**
         * 将秒数格式化为 HH:mm:ss 格式（用于时间段）
         *
         * @param totalSeconds 总秒数
         * @return 格式化后的时间字符串
         */
        public static String fromSeconds(long totalSeconds) {
            if (totalSeconds < 0) {
                totalSeconds = 0;
            }
            return LocalTime.ofSecondOfDay(totalSeconds % 86400)
                    .format(TIME_FORMATTER);
        }

        /**
         * 从时间戳获取本地时间的 HH:mm:ss 格式（用于时间点）
         *
         * @param timestamp 时间戳（秒）
         * @return 格式化后的时间字符串
         */
        public static String fromTimestamp(long timestamp) {
            return Instant.ofEpochSecond(timestamp)
                    .atZone(ZoneId.systemDefault())
                    .toLocalTime()
                    .format(TIME_FORMATTER);
        }

        /**
         * 解析 HH:mm:ss 格式的时间字符串为秒数
         *
         * @param timeStr HH:mm:ss 格式的时间字符串
         * @return 总秒数（当天的秒数）
         * @throws JsonParseException 当格式不正确时抛出异常
         */
        public static long toSeconds(String timeStr) throws JsonParseException {
            if (timeStr == null || timeStr.isEmpty()) {
                return 0L;
            }
            try {
                LocalTime time = LocalTime.parse(timeStr, TIME_FORMATTER);
                return time.toSecondOfDay();
            } catch (Exception e) {
                throw new JsonParseException("Invalid time format, expected HH:mm:ss: " + timeStr, e);
            }
        }
    }


    /**
     * ISO 8601标准格式 (yyyy-MM-ddTHH:mm:ss+/-时区/Z) 的双向转换工具类
     */
    public static class DateTimeConverter {
        /**
         * 将时间戳转换为ISO 8601标准格式字符串
         */
        public static String fromTimestamp(long timestamp) {
            // 使用系统默认时区而不是UTC
            return Instant.ofEpochSecond(timestamp)
                    .atZone(ZoneId.systemDefault())
                    .format(DATE_TIME_FORMATTER);
        }

        /**
         * 将ISO 8601标准格式字符串转换为时间戳
         */
        public static long toTimestamp(String dateTimeStr) {
            try {
                // 使用ZonedDateTime来处理时区信息
                ZonedDateTime zonedDateTime = ZonedDateTime.parse(dateTimeStr, DATE_TIME_FORMATTER);
                return zonedDateTime.toEpochSecond();
            } catch (Exception e) {
                System.err.println("Error parsing date time string '" + dateTimeStr + "': " + e.getMessage());
                return 0L;
            }
        }
    }


    /**
     * 服务器运行时间计算工具类
     */
    public static class UptimeCalculator {
        /**
         * 根据开服时间和当前时间计算服务器已运行的天数
         *
         * @param bootTime 开服时间字符串 (ISO 8601格式)
         * @return 运行天数
         */
        public static long calculateUptimeDays(String bootTime) {
            if (bootTime == null || bootTime.isEmpty()) {
                return 0;
            }

            try {
                long bootTimestamp = DateTimeConverter.toTimestamp(bootTime);
                long currentTimestamp = SystemTime.getCurrentTimestamp();

                // 如果当前时间早于开服时间，则返回0
                if (currentTimestamp < bootTimestamp) {
                    return 0;
                }

                LocalDateTime bootDateTime = LocalDateTime.ofInstant(
                        Instant.ofEpochSecond(bootTimestamp),
                        ZoneId.systemDefault()
                );
                LocalDateTime currentDateTime = LocalDateTime.ofInstant(
                        Instant.ofEpochSecond(currentTimestamp),
                        ZoneId.systemDefault()
                );

                return ChronoUnit.DAYS.between(bootDateTime, currentDateTime);
            } catch (Exception e) {
                System.err.println("Error calculating uptime: " + e.getMessage());
                return 0;
            }
        }
    }
}
