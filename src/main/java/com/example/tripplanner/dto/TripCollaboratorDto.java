package com.example.tripplanner.dto;

import java.time.LocalDateTime;

public class TripCollaboratorDto {

    private Long id;
    private String userName;
    private String userEmail;
    private String role;
    private LocalDateTime joinedAt;

    public TripCollaboratorDto() {
    }

    public TripCollaboratorDto(Long id, String userName, String userEmail, String role, LocalDateTime joinedAt) {
        this.id = id;
        this.userName = userName;
        this.userEmail = userEmail;
        this.role = role;
        this.joinedAt = joinedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUserName() {
        return userName;
    }

    public String getName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public String getEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(LocalDateTime joinedAt) {
        this.joinedAt = joinedAt;
    }
}
