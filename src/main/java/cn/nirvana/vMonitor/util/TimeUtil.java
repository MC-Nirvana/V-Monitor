// File: src/main/java/cn/nirvana/vMonitor/util/TimeUtil.java
package cn.nirvana.vMonitor.util;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.google.gson.JsonParseException;

import java.io.IOException;

import java.time.Duration; // 导入 Duration
import java.time.LocalDate;
import java.time.LocalDateTime; // 导入 LocalDateTime
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

public class TimeUtil extends TypeAdapter<Long> {

    // 常量定义日期时间格式
    private static final DateTimeFormatter DATE_FORMAT_NO_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_FORMAT_WITH_MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DATE_FORMAT_WITH_SECOND = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter WEEK_FORMATTER = DateTimeFormatter.ofPattern("yyyy-'W'ww", Locale.ENGLISH);


    @Override
    public void write(JsonWriter out, Long value) throws IOException {
        if (value == null) {
            out.nullValue();
            return;
        }
        out.value(formatSecondsToHHmm(value));
    }

    @Override
    public Long read(JsonReader in) throws IOException {
        String timeStr = in.nextString();
        if (timeStr == null || timeStr.isEmpty()) {
            return 0L;
        }
        try {
            return parseHHmmToSeconds(timeStr);
        } catch (NumberFormatException | JsonParseException e) {
            throw new IOException("Error parsing time string '" + timeStr + "' into seconds: " + e.getMessage(), e);
        }
    }

    /**
     * 将秒数格式化为 HH:mm 字符串。
     * @param totalSeconds 总秒数
     * @return HH:mm 格式的字符串
     */
    public static String formatSecondsToHHmm(long totalSeconds) {
        if (totalSeconds < 0) {
            totalSeconds = 0;
        }
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        return String.format("%02d:%02d", hours, minutes);
    }

    /**
     * 将 HH:mm 字符串解析为总秒数。
     * @param hhmmString HH:mm 格式的字符串
     * @return 总秒数
     * @throws JsonParseException 如果格式不正确
     */
    public static long parseHHmmToSeconds(String hhmmString) throws JsonParseException {
        if (hhmmString == null || hhmmString.isEmpty()) {
            return 0L;
        }
        String[] parts = hhmmString.split(":");
        if (parts.length == 2) {
            try {
                long hours = Long.parseLong(parts[0]);
                long minutes = Long.parseLong(parts[1]);
                return hours * 3600 + minutes * 60;
            } catch (NumberFormatException e) {
                throw new JsonParseException("Invalid number format in HH:mm string: " + hhmmString, e);
            }
        } else {
            throw new JsonParseException("Invalid time format, expected HH:mm: " + hhmmString);
        }
    }

    /**
     * 计算从给定开始日期到当前日期的天数（包含开始日期）。
     * @param startDateString 开始日期字符串 (yyyy-MM-dd)
     * @return 天数
     */
    public static long getDaysBetweenStartDateAndNow(String startDateString) {
        if (startDateString == null || startDateString.isEmpty()) {
            return 0L;
        }
        try {
            LocalDate startDate = LocalDate.parse(startDateString, DATE_FORMAT_NO_TIME);
            LocalDate currentDate = LocalDate.now();
            if (startDate.isAfter(currentDate)) {
                return 0L;
            }
            return ChronoUnit.DAYS.between(startDate, currentDate) + 1;

        } catch (java.time.format.DateTimeParseException e) {
            System.err.println("Error parsing date string '" + startDateString + "': " + e.getMessage());
            return 0L;
        }
    }

    /**
     * 获取当前日期字符串 (yyyy-MM-dd)。
     * @return 当前日期字符串
     */
    public static String getCurrentDateString() {
        return LocalDate.now().format(DATE_FORMAT_NO_TIME);
    }

    /**
     * 获取当前日期时间字符串 (yyyy-MM-dd HH:mm)。
     * @return 当前日期时间字符串
     */
    public static String getCurrentDateTimeMinuteString() {
        return LocalDateTime.now().format(DATE_FORMAT_WITH_MINUTE);
    }

    /**
     * 获取当前日期时间字符串 (yyyy-MM-dd HH:mm:ss)。
     * @return 当前日期时间字符串
     */
    public static String getCurrentDateTimeSecondString() {
        return LocalDateTime.now().format(DATE_FORMAT_WITH_SECOND);
    }

    /**
     * 获取当前周字符串 (yyyy-'W'ww)。
     * @return 当前周字符串
     */
    public static String getCurrentWeekString() {
        return LocalDate.now().format(WEEK_FORMATTER);
    }

    /**
     * 将 LocalDateTime 格式化为 yyyy-MM-dd HH:mm 字符串。
     * @param dateTime LocalDateTime 对象
     * @return 格式化后的字符串
     */
    public static String formatDateTimeMinute(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        return dateTime.format(DATE_FORMAT_WITH_MINUTE);
    }

    /**
     * 将 LocalDateTime 格式化为 yyyy-MM-dd HH:mm:ss 字符串。
     * @param dateTime LocalDateTime 对象
     * @return 格式化后的字符串
     */
    public static String formatDateTimeSecond(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        return dateTime.format(DATE_FORMAT_WITH_SECOND);
    }

    /**
     * 将日期字符串 (yyyy-MM-dd HH:mm:ss) 解析为 LocalDateTime。
     * @param dateTimeString 日期时间字符串
     * @return LocalDateTime 对象
     */
    public static LocalDateTime parseDateTimeSecond(String dateTimeString) {
        if (dateTimeString == null || dateTimeString.isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(dateTimeString, DATE_FORMAT_WITH_SECOND);
        } catch (java.time.format.DateTimeParseException e) {
            System.err.println("Error parsing date time string '" + dateTimeString + "' with seconds: " + e.getMessage());
            return null;
        }
    }

    /**
     * 计算两个 LocalDateTime 之间的持续时间（秒）。
     * @param start 开始时间
     * @param end 结束时间
     * @return 持续时间（秒）
     */
    public static long getDurationInSeconds(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            return 0L;
        }
        return Duration.between(start, end).getSeconds();
    }
}