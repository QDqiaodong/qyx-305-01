package com.risk.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 风险筛查台账（第一本账：筛查时效）。
 *
 * 每个计划只保留一条最新筛查记录。记录筛查当时的日期/人数/年龄段/途经点指纹，
 * 之后计划上述任何一项被改动，指纹对不上，旧筛查即“失效待重评”，不得再当出门凭证。
 */
@Entity
@Table(name = "risk_screening")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskScreening {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_id", nullable = false, unique = true)
    private Long planId;

    /** 筛查结论：HIGH_RISK / MEDIUM_RISK / LOW_RISK */
    @Column(name = "risk_level", nullable = false, length = 20)
    private String riskLevel;

    /** 筛查时间 */
    @Column(name = "screened_at", nullable = false)
    private LocalDateTime screenedAt;

    /** 行程指纹：对出行日期、人数、年龄段、途经点取哈希 */
    @Column(name = "plan_fingerprint", nullable = false, length = 64)
    private String planFingerprint;

    /** 筛查当时的规则版本；规则一改版本号前进，旧筛查即“规则变了还没重筛” */
    @Column(name = "rule_version")
    private Long ruleVersion;

    // 下列为筛查当时的行程快照，便于在放行页直观展示“筛查对的是不是当前这版行程”
    @Column(name = "snapshot_travel_date", length = 30)
    private String snapshotTravelDate;

    @Column(name = "snapshot_participant_count")
    private Integer snapshotParticipantCount;

    @Column(name = "snapshot_age_min")
    private Integer snapshotAgeMin;

    @Column(name = "snapshot_age_max")
    private Integer snapshotAgeMax;

    @Column(name = "snapshot_waypoints", columnDefinition = "TEXT")
    private String snapshotWaypoints;
}
