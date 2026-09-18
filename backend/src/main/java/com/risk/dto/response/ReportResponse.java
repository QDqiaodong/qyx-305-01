package com.risk.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportResponse {

    private String reportId;
    
    private Long planId;
    
    private String planName;
    
    private String startLocation;
    
    private String endLocation;
    
    private List<String> waypoints;
    
    private LocalDate travelDate;
    
    private Integer ageMin;
    
    private Integer ageMax;
    
    private Integer participantCount;
    
    private LocalDateTime generatedAt;

    private String overallStatus;

    /** 是否做过筛查 */
    private boolean screened;

    /** 报告所依据的筛查是否仍对得上当前行程（旧报告行程一改即为 false） */
    private boolean screeningValid;

    /** 筛查失效/未筛查原因 */
    private String screeningMismatchReason;

    /** 该计划当前放行单状态：PENDING / RELEASED / VOID / STALE_RECHECK（无放行单为 null） */
    private String permitStatus;

    private String permitStatusText;

    private String permitBlockReason;

    /** 是否已记下发车时刻：已发车的报告保持出门当时那一版，不被后续规则调整改写 */
    private boolean departed;

    /** 发车时刻（未发车为 null） */
    private String departedAt;

    private RiskSummary riskSummary;
    
    private List<RiskDetail> riskDetails;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskSummary {
        private int highCount;
        private int mediumCount;
        private int lowCount;
        private int totalCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskDetail {
        private String location;
        private String riskLevel;
        private String ruleName;
        private String riskMessage;
        private String riskType;
    }
}