package com.risk.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 首页统计：风险预警 + 放行四档 + 医护/排班总览。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStats {

    private long totalPlanCount;
    private long highRiskCount;
    private long mediumRiskCount;
    private long lowRiskCount;
    private long pendingScreeningCount;

    /** 放行四档 */
    private long releasedCount;
    private long pendingDocsCount;
    private long voidCount;
    private long staleRecheckCount;

    private long medicalStaffCount;
    private long pediatricStaffCount;
    private long assignmentCount;
}
