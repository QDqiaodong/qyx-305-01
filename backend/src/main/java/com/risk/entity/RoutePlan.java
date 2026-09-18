package com.risk.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "route_plan")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoutePlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_name", nullable = false, length = 100)
    private String planName;

    @Column(name = "start_location", nullable = false, length = 200)
    private String startLocation;

    @Column(name = "end_location", nullable = false, length = 200)
    private String endLocation;

    @Column(name = "waypoints", columnDefinition = "TEXT")
    private String waypoints;

    @Column(name = "travel_date", nullable = false)
    private LocalDate travelDate;

    @Column(name = "age_min")
    @Builder.Default
    private Integer ageMin = 0;

    @Column(name = "age_max")
    @Builder.Default
    private Integer ageMax = 100;

    @Column(name = "participant_count")
    @Builder.Default
    private Integer participantCount = 0;

    @Column(name = "status", length = 20)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}