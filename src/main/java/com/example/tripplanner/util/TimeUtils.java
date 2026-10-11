package com.example.tripplanner.util;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

public class TimeUtils {

    private static final DateTimeFormatter TIME_FORMATTER_24 = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter TIME_FORMATTER_12 = DateTimeFormatter.ofPattern("h:mm a");

    public static String formatDuration(int totalMinutes) {
        if (totalMinutes <= 0) {
            return "0 min";
        }
        int hours = totalMinutes / 60;
        int mins = totalMinutes % 60;

        if (hours == 0) {
            return mins + " min";
        } else if (mins == 0) {
            return hours + " hr";
        } else {
            return hours + " hr " + mins + " min";
        }
    }

    public static String addMinutes(String time24, int minutesToAdd) {
        try {
            LocalTime time = LocalTime.parse(time24, TIME_FORMATTER_24);
            LocalTime newTime = time.plusMinutes(minutesToAdd);
            return newTime.format(TIME_FORMATTER_24);
        } catch (Exception e) {
            return time24;
        }
    }

    public static int differenceInMinutes(String startTime24, String endTime24) {
        try {
            LocalTime start = LocalTime.parse(startTime24, TIME_FORMATTER_24);
            LocalTime end = LocalTime.parse(endTime24, TIME_FORMATTER_24);
            long diff = ChronoUnit.MINUTES.between(start, end);
            return (int) Math.max(0, diff);
        } catch (Exception e) {
            return 0;
        }
    }

    public static String formatAmPm(String time24) {
        try {
            LocalTime time = LocalTime.parse(time24, TIME_FORMATTER_24);
            return time.format(TIME_FORMATTER_12);
        } catch (Exception e) {
            return time24;
        }
    }

    public static LocalTime parseTime(String time24) {
        try {
            return LocalTime.parse(time24, TIME_FORMATTER_24);
        } catch (Exception e) {
            return LocalTime.of(8, 0);
        }
    }
}
