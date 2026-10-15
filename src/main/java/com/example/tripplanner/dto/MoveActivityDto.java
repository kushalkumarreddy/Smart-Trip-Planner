package com.example.tripplanner.dto;

import jakarta.validation.constraints.NotNull;

public class MoveActivityDto {

    @NotNull(message = "Item ID is required")
    private Long itemId;

    @NotNull(message = "Target day number is required")
    private Integer targetDayNumber;

    private String preferredStartTime;

    public MoveActivityDto() {
    }

    public MoveActivityDto(Long itemId, Integer targetDayNumber) {
        this.itemId = itemId;
        this.targetDayNumber = targetDayNumber;
    }

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public Integer getTargetDayNumber() {
        return targetDayNumber;
    }

    public void setTargetDayNumber(Integer targetDayNumber) {
        this.targetDayNumber = targetDayNumber;
    }

    public String getPreferredStartTime() {
        return preferredStartTime;
    }

    public void setPreferredStartTime(String preferredStartTime) {
        this.preferredStartTime = preferredStartTime;
    }
}
