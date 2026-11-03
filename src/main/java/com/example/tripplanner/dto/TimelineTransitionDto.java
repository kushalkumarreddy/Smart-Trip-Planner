package com.example.tripplanner.dto;

import com.example.tripplanner.util.TimeUtils;

import java.io.Serializable;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TimelineTransitionDto implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final Pattern MINUTES_PATTERN = Pattern.compile("(\\d+)\\s*(?:mins|minutes|min)");

    private String icon; // bi-car-front, bi-person-walking, bi-bus-front, bi-cup-hot, bi-sun, bi-clock, bi-building, bi-exclamation-triangle
    private String text; // e.g. "Travel to next stop · 25 min · 12 km"
    private String type; // TRAVEL, BUFFER, FREE_TIME, CONFLICT, HOTEL_TRANSFER, BREAK, WALK, TRANSIT, BIKE
    private Double distanceKm;
    private Integer durationMinutes;
    private String fromLocation;
    private String toLocation;

    // Enhanced Connected Vertical Travel Timeline Fields (Feature 1)
    private String departureTime;         // e.g. "8:00 AM" (departure from previous activity)
    private String expectedArrivalTime;   // e.g. "8:45 AM" (arrival at next destination)
    private String nextActivityStartTime; // e.g. "9:00 AM" (scheduled start of next activity)
    private Integer bufferMinutes;        // e.g. 15 (minutes between arrival and next activity)
    private String mode;                  // CAR, WALKING, TRANSIT, BICYCLE
    private String modeIcon;              // bi-car-front, bi-person-walking, bi-bus-front, bi-bicycle, bi-building
    private String modeLabel;             // e.g. "Travel to next destination", "Walk to next attraction"
    private String bufferLabel;           // e.g. "15 min arrival buffer before next activity"
    private Boolean hasConflict = false;  // true if connection is negative or closed
    private String conflictMessage;

    public TimelineTransitionDto() {
    }

    public TimelineTransitionDto(String icon, String text, String type, Double distanceKm, Integer durationMinutes) {
        this.icon = icon;
        this.type = type;
        this.distanceKm = distanceKm;
        this.durationMinutes = durationMinutes;
        this.text = formatTransitionText(text, durationMinutes, distanceKm);
    }

    public static String formatTransitionText(String rawText, Integer durMins, Double dist) {
        if (rawText != null && !rawText.isEmpty()) {
            // Remove any emoji sequences (e.g. 🚗, 🚙, 🚘, 🏎️, 🚶, 🚌, 🚲, 🏨, 🕒, 🌤, ⚠️) and leading symbols/spaces
            String clean = rawText.replaceAll("[\\p{So}\\p{Cn}\\uD83C-\\uDBFF\\uDC00-\\uDFFF]+", "")
                                  .replaceAll("^[\\s·•-]+", "")
                                  .trim();

            // Match any (\\d+)\\s*(?:mins|minutes|min) and format with TimeUtils.formatDuration (e.g. 468 min -> 7 hr 48 min)
            Matcher matcher = MINUTES_PATTERN.matcher(clean);
            StringBuilder sb = new StringBuilder();
            while (matcher.find()) {
                try {
                    int mins = Integer.parseInt(matcher.group(1));
                    matcher.appendReplacement(sb, Matcher.quoteReplacement(TimeUtils.formatDuration(mins)));
                } catch (Exception e) {
                    matcher.appendReplacement(sb, matcher.group(0));
                }
            }
            matcher.appendTail(sb);
            return sb.toString();
        }

        if (durMins != null && durMins > 0) {
            String dur = TimeUtils.formatDuration(durMins);
            if (dist != null && dist > 0.05) {
                String distStr = (dist >= 1.0)
                        ? String.format(Locale.US, "%.1f km", dist)
                        : Math.round(dist * 1000) + " m";
                return "Travel to next stop · " + dur + " · " + distStr;
            }
            return "Travel to next stop · " + dur;
        }
        return "";
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getFormattedText() {
        return formatTransitionText(this.text, this.durationMinutes, this.distanceKm);
    }

    public String getText() {
        return getFormattedText();
    }

    public void setText(String text) {
        this.text = formatTransitionText(text, this.durationMinutes, this.distanceKm);
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Double getDistanceKm() {
        return distanceKm;
    }

    public void setDistanceKm(Double distanceKm) {
        this.distanceKm = distanceKm;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public String getFromLocation() {
        return fromLocation;
    }

    public void setFromLocation(String fromLocation) {
        this.fromLocation = fromLocation;
    }

    public String getToLocation() {
        return toLocation;
    }

    public void setToLocation(String toLocation) {
        this.toLocation = toLocation;
    }

    public String getDepartureTime() {
        return departureTime;
    }

    public void setDepartureTime(String departureTime) {
        this.departureTime = departureTime;
    }

    public String getExpectedArrivalTime() {
        return expectedArrivalTime;
    }

    public void setExpectedArrivalTime(String expectedArrivalTime) {
        this.expectedArrivalTime = expectedArrivalTime;
    }

    public String getNextActivityStartTime() {
        return nextActivityStartTime;
    }

    public void setNextActivityStartTime(String nextActivityStartTime) {
        this.nextActivityStartTime = nextActivityStartTime;
    }

    public Integer getBufferMinutes() {
        return bufferMinutes;
    }

    public void setBufferMinutes(Integer bufferMinutes) {
        this.bufferMinutes = bufferMinutes;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getModeIcon() {
        return modeIcon != null ? modeIcon : (icon != null ? icon : "bi-car-front");
    }

    public void setModeIcon(String modeIcon) {
        this.modeIcon = modeIcon;
    }

    public String getModeLabel() {
        if (modeLabel != null && !modeLabel.isEmpty()) {
            return modeLabel;
        }
        if ("WALK".equalsIgnoreCase(type) || "WALKING".equalsIgnoreCase(mode)) {
            return "Walk to next attraction";
        } else if ("TRANSIT".equalsIgnoreCase(type) || "PUBLIC_TRANSPORT".equalsIgnoreCase(mode)) {
            return "Public transport to next stop";
        } else if ("BIKE".equalsIgnoreCase(type) || "BICYCLE".equalsIgnoreCase(mode)) {
            return "Bicycle ride to next stop";
        } else if ("HOTEL_TRANSFER".equalsIgnoreCase(type)) {
            return "Transfer to hotel";
        }
        return "Travel to next destination";
    }

    public void setModeLabel(String modeLabel) {
        this.modeLabel = modeLabel;
    }

    public String getBufferLabel() {
        if (bufferLabel != null && !bufferLabel.isEmpty()) {
            return formatTransitionText(bufferLabel, bufferMinutes, 0.0);
        }
        if (bufferMinutes != null && bufferMinutes > 0) {
            return TimeUtils.formatDuration(bufferMinutes) + " arrival buffer before next activity";
        }
        return "Direct start on arrival";
    }

    public void setBufferLabel(String bufferLabel) {
        this.bufferLabel = formatTransitionText(bufferLabel, this.bufferMinutes, 0.0);
    }

    public Boolean getHasConflict() {
        return hasConflict != null ? hasConflict : false;
    }

    public void setHasConflict(Boolean hasConflict) {
        this.hasConflict = hasConflict;
    }

    public String getConflictMessage() {
        return conflictMessage;
    }

    public void setConflictMessage(String conflictMessage) {
        this.conflictMessage = conflictMessage;
    }

    public String getFormattedDuration() {
        return durationMinutes != null && durationMinutes > 0 ? TimeUtils.formatDuration(durationMinutes) : "0 min";
    }

    public String getFormattedDistance() {
        if (distanceKm == null || distanceKm <= 0.05) return "";
        return distanceKm >= 1.0 ? String.format(Locale.US, "%.1f km", distanceKm) : Math.round(distanceKm * 1000) + " m";
    }

    public String getFormattedBuffer() {
        return bufferMinutes != null && bufferMinutes > 0 ? TimeUtils.formatDuration(bufferMinutes) : "0 min";
    }
}


