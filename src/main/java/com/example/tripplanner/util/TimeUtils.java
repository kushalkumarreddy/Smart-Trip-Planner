package com.example.tripplanner.util;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

public class TimeUtils {

    public static final int DEFAULT_INTERVAL_MINUTES = 15;

    private static final DateTimeFormatter TIME_FORMATTER_24 = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter TIME_FORMATTER_12 = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

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
            LocalTime time = parseTime(time24);
            LocalTime newTime = time.plusMinutes(minutesToAdd);
            return newTime.format(TIME_FORMATTER_24);
        } catch (Exception e) {
            return time24;
        }
    }

    public static int differenceInMinutes(String startTime24, String endTime24) {
        try {
            LocalTime start = parseTime(startTime24);
            LocalTime end = parseTime(endTime24);
            long diff = ChronoUnit.MINUTES.between(start, end);
            return (int) Math.max(0, diff);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Converts a 24-hour time (e.g. "17:57" or "18:00") or 12-hour string to clean 12-hour AM/PM format (e.g. "6:00 PM").
     */
    public static String formatAmPm(String rawTime) {
        if (rawTime == null || rawTime.trim().isEmpty()) {
            return "";
        }
        String clean = rawTime.trim();
        // If already in 12-hour AM/PM format, normalize spacing
        if (clean.toUpperCase().endsWith("AM") || clean.toUpperCase().endsWith("PM")) {
            try {
                LocalTime parsed = parseTime12(clean);
                if (parsed != null) {
                    return parsed.format(TIME_FORMATTER_12);
                }
            } catch (Exception ignored) {
            }
            return clean;
        }
        try {
            LocalTime time = parseTime(clean);
            return time.format(TIME_FORMATTER_12);
        } catch (Exception e) {
            return rawTime;
        }
    }

    /**
     * Parses standard 24-hour time or 12-hour time into LocalTime safely.
     */
    public static LocalTime parseTime(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return LocalTime.of(8, 0);
        }
        String clean = raw.trim();
        try {
            return LocalTime.parse(clean, TIME_FORMATTER_24);
        } catch (Exception e) {
            LocalTime t12 = parseTime12(clean);
            if (t12 != null) {
                return t12;
            }
            try {
                // Try single digit hour format e.g. "8:00"
                DateTimeFormatter dtf = DateTimeFormatter.ofPattern("H:mm");
                return LocalTime.parse(clean, dtf);
            } catch (Exception ignored) {
            }
            return LocalTime.of(8, 0);
        }
    }

    private static LocalTime parseTime12(String raw) {
        try {
            String norm = raw.trim().toUpperCase().replaceAll("\\s*(AM|PM)", " $1");
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
            return LocalTime.parse(norm, dtf);
        } catch (Exception e) {
            try {
                DateTimeFormatter dtf2 = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH);
                return LocalTime.parse(raw.trim().toUpperCase(), dtf2);
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    public static String formatAmPm(LocalTime time) {
        if (time == null) {
            return "";
        }
        return time.format(TIME_FORMATTER_12);
    }

    /**
     * Rounds a LocalTime to the nearest clock interval (default 15 minutes).
     * Examples (15-min interval):
     * 17:57 -> 18:00 (6:00 PM)
     * 10:07 -> 10:00 (10:00 AM)
     * 14:23 -> 14:30 (2:30 PM)
     * 08:44 -> 08:45 (8:45 AM)
     */
    public static LocalTime roundToInterval(LocalTime time, int intervalMinutes) {
        if (time == null) {
            return LocalTime.of(8, 0);
        }
        if (intervalMinutes <= 1) {
            return time.withSecond(0).withNano(0);
        }

        int minuteOfDay = time.getHour() * 60 + time.getMinute();
        // Half-round up: (minuteOfDay + interval / 2) / interval * interval
        int roundedMinutes = ((minuteOfDay + (intervalMinutes / 2)) / intervalMinutes) * intervalMinutes;

        // Handle midnight crossing / 24-hour wrap
        roundedMinutes = roundedMinutes % (24 * 60);

        int hour = roundedMinutes / 60;
        int minute = roundedMinutes % 60;
        return LocalTime.of(hour, minute);
    }

    public static LocalTime roundToInterval(LocalTime time) {
        return roundToInterval(time, DEFAULT_INTERVAL_MINUTES);
    }

    public static String roundTime24(String time24, int intervalMinutes) {
        LocalTime parsed = parseTime(time24);
        LocalTime rounded = roundToInterval(parsed, intervalMinutes);
        return rounded.format(TIME_FORMATTER_24);
    }

    public static String roundTime24(String time24) {
        return roundTime24(time24, DEFAULT_INTERVAL_MINUTES);
    }

    /**
     * Rounds UP to the next interval boundary if not already on boundary.
     * Prevents activities from starting before physical arrival / travel time completes.
     */
    public static LocalTime roundUpToInterval(LocalTime time, int intervalMinutes) {
        if (time == null) {
            return LocalTime.of(8, 0);
        }
        if (intervalMinutes <= 1) {
            return time.withSecond(0).withNano(0);
        }

        int minuteOfDay = time.getHour() * 60 + time.getMinute();
        int rem = minuteOfDay % intervalMinutes;
        int roundedMinutes = (rem == 0) ? minuteOfDay : minuteOfDay + (intervalMinutes - rem);

        roundedMinutes = roundedMinutes % (24 * 60);

        int hour = roundedMinutes / 60;
        int minute = roundedMinutes % 60;
        return LocalTime.of(hour, minute);
    }

    public static String roundUpTime24(String time24, int intervalMinutes) {
        LocalTime parsed = parseTime(time24);
        LocalTime rounded = roundUpToInterval(parsed, intervalMinutes);
        return rounded.format(TIME_FORMATTER_24);
    }

    /**
     * Formats start and end times into clean 12-hour intervals e.g. "10:00 AM - 11:30 AM"
     */
    public static String formatTimeRange(String startTime, String endTime) {
        String s = formatAmPm(startTime);
        String e = formatAmPm(endTime);
        return s + " - " + e;
    }

    /**
     * Checks if two time windows overlap on the same day.
     */
    public static boolean isOverlapping(String startA, String endA, String startB, String endB) {
        try {
            LocalTime sA = parseTime(startA);
            LocalTime eA = parseTime(endA);
            LocalTime sB = parseTime(startB);
            LocalTime eB = parseTime(endB);
            return sA.isBefore(eB) && sB.isBefore(eA);
        } catch (Exception e) {
            return false;
        }
    }
}
