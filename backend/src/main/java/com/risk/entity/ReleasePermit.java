package com.risk.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 发车放行单（同时核对“筛查时效”与“医护覆盖”两本账）。
 *
 * 状态分四档：
 *   PENDING        待齐件（已发起但尚未放行）
 *   RELEASED       已放行（可出门）
 *   VOID           已作废（行程变了 / 人工作废）
 *   STALE_RECHECK  筛查失效待重评（行程未变但筛查结论过期，或医护被抽走导致覆盖断了）
 */
@Entity
@Table(name = "release_permit")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReleasePermit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 每个计划一条放行单（最新一条，反复放行复用同一行） */
    @Column(name = "plan_id", nullable = false, unique = true)
    private Long planId;

    /** PENDING / RELEASED / VOID / STALE_RECHECK */
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private String status = "PENDING";

    /** 放行时的筛查结论（HIGH_RISK / MEDIUM_RISK / LOW_RISK） */
    @Column(name = "risk_level", length = 20)
    private String riskLevel;

    /**
     * 知情备注：只允许出现在中风险单上。
     * 高风险单写知情备注也不能放行（安全岗只认规则：高风险必须改线或关掉触发规则后重新筛）。
     */
    @Column(name = "informed_note", length = 500)
    private String informedNote;

    /** 放行时间 */
    @Column(name = "released_at")
    private LocalDateTime releasedAt;

    /**
     * 发车时刻：一旦记下，单据封存。
     * 之后的规则调整、重筛、排班变动都不再改写这张纸；报告保持出门当时那一版。
     */
    @Column(name = "departed_at")
    private LocalDateTime departedAt;

    /**
     * 回程收口时刻：一旦记下，这趟的点名结果冻住——
     * 不能再往车上塞人、不能改勾选、不能把缺人说明改没。
     * 按安全岗办：收口后晚到的孩子只能另开一趟。
     */
    @Column(name = "roll_closed_at")
    private LocalDateTime rollClosedAt;

    /** 收口时冻住的缺人说明（每个未勾回的人逐条记录） */
    @Column(name = "roll_missing_note", length = 2000)
    private String rollMissingNote;

    /** 作废原因（行程变更 / 人工作废） */
    @Column(name = "void_reason", length = 500)
    private String voidReason;

    /** 待重评原因（筛查失效 / 医护覆盖变化） */
    @Column(name = "recheck_reason", length = 500)
    private String recheckReason;

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
