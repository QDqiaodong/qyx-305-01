package com.risk.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 医护排班：把某名医护在某个出行日挂到具体路线计划上。
 *
 * 唯一约束 (staff_id, travel_date) 保证同一名医护在同一天不能同时挂两条计划。
 */
@Entity
@Table(
        name = "medical_assignment",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_staff_date",
                columnNames = {"staff_id", "travel_date"}
        )
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MedicalAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Column(name = "staff_id", nullable = false)
    private Long staffId;

    /** 冗余出行日期：既用于唯一约束，也用于计划改期后让旧排班自然失配 */
    @Column(name = "travel_date", nullable = false)
    private LocalDate travelDate;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
