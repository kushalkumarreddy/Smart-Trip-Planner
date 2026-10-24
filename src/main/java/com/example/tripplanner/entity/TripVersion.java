package com.example.tripplanner.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "trip_versions")
public class TripVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(name = "change_description", nullable = false, length = 255)
    private String changeDescription;

    @Column(name = "change_source", length = 50)
    private String changeSource = "USER_ACTION";

    @Column(name = "created_by_name", length = 100)
    private String createdByName;

    @Column(name = "created_by_email", length = 120)
    private String createdByEmail;

    @Lob
    @Column(name = "snapshot_data", nullable = false, columnDefinition = "LONGTEXT")
    private String snapshotData;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public TripVersion() {
    }

    public TripVersion(Trip trip, Integer versionNumber, String changeDescription, String changeSource,
                       String createdByName, String createdByEmail, String snapshotData) {
        this.trip = trip;
        this.versionNumber = versionNumber;
        this.changeDescription = changeDescription;
        this.changeSource = changeSource;
        this.createdByName = createdByName;
        this.createdByEmail = createdByEmail;
        this.snapshotData = snapshotData;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Trip getTrip() {
        return trip;
    }

    public void setTrip(Trip trip) {
        this.trip = trip;
    }

    public Integer getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(Integer versionNumber) {
        this.versionNumber = versionNumber;
    }

    public String getChangeDescription() {
        return changeDescription;
    }

    public void setChangeDescription(String changeDescription) {
        this.changeDescription = changeDescription;
    }

    public String getChangeSource() {
        return changeSource;
    }

    public void setChangeSource(String changeSource) {
        this.changeSource = changeSource;
    }

    public String getCreatedByName() {
        return createdByName;
    }

    public void setCreatedByName(String createdByName) {
        this.createdByName = createdByName;
    }

    public String getCreatedByEmail() {
        return createdByEmail;
    }

    public void setCreatedByEmail(String createdByEmail) {
        this.createdByEmail = createdByEmail;
    }

    public String getSnapshotData() {
        return snapshotData;
    }

    public void setSnapshotData(String snapshotData) {
        this.snapshotData = snapshotData;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
