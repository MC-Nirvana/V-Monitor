package cn.nirvana.vMonitor.util;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.google.gson.JsonParseException;

import java.io.IOException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

public class TimeUtil extends TypeAdapter<Long> {

    private static final DateTimeFormatter DATE_FORMAT_NO_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd");

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

    public static String formatSecondsToHHmm(long totalSeconds) {
        if (totalSeconds < 0) {
            totalSeconds = 0;
        }
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        return String.format("%02d:%02d", hours, minutes);
    }

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
}