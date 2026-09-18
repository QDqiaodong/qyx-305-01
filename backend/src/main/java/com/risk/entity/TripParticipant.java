package com.risk.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 车次出行人员（发车时带出去的人）。
 *
 * - 发车前可在回程点名页维护名册（增人 / 删人），作为这趟发车带出去的人；
 * - 发车时刻一登记，名册锁定：不能再往车上塞人、也不能删人；
 *   调度想把晚到的孩子补进这趟的诉求被安全岗驳回——晚到的只能另开一趟；
 * - 回程点名按 personId 逐个勾回；未勾回的须先写缺人说明，车次才能收口；
 * - 车次收口（roll_closed_at）后点名结果冻住：勾选、缺人说明、名册一律不能再改。
 */
@Entity
@Table(name = "trip_participant",
        uniqueConstraints = @UniqueConstraint(name = "uk_plan_person", columnNames = {"plan_id", "person_name"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    /** 同一趟车内不重名 */
    @Column(name = "person_name", nullable = false, length = 100)
    private String personName;

    /** 回程是否已勾回到校：默认未回 */
    @Column(name = "returned", nullable = false)
    @Builder.Default
    private boolean returned = false;

    /** 缺人说明：只有未勾回的人才会带说明；人勾回后说明清空 */
    @Column(name = "missing_note", length = 500)
    private String missingNote;

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
