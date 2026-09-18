package com.risk.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 第一本账：筛查时效。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScreeningBook {

    /** 是否做过筛查 */
    private boolean exists;

    /** 筛查结论 HIGH_RISK / MEDIUM_RISK / LOW_RISK */
    private String riskLevel;

    private String screenedAt;

    /** 指纹是否仍对得上当前行程（true=最新筛查仍然有效） */
    private boolean fresh;

    /** 行程这一半是否对得上（日期/人数/年龄段/途经点） */
    private boolean tripFresh;

    /** 规则这一半是否对得上（筛查基于的规则版本是否还是当前版本） */
    private boolean rulesFresh;

    /** 筛查当时的规则版本 */
    private Long ruleVersion;

    /** 当前规则版本 */
    private Long currentRuleVersion;

    /** 筛查当时记录的出行日期 */
    private String snapshotTravelDate;

    private Integer snapshotParticipantCount;

    private Integer snapshotAgeMin;

    private Integer snapshotAgeMax;

    private String snapshotWaypoints;

    /** 当前行程的出行日期，便于前端对比 */
    private String currentTravelDate;

    private Integer currentParticipantCount;

    private Integer currentAgeMin;

    private Integer currentAgeMax;

    private String currentWaypoints;

    /** 失配项（中文描述，用于“为什么这本账对不上”） */
    private String mismatchReason;
}
