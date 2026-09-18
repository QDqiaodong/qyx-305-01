package com.risk.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskCheckResponse {

    private Long planId;
    
    private String planName;
    
    private List<RiskItem> highRisks;
    
    private List<RiskItem> mediumRisks;
    
    private List<RiskItem> lowRisks;
    
    private String overallStatus;
    
    private int totalRiskCount;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskItem {
        private Long ruleId;
        private String ruleCode;
        private String ruleName;
        private String ruleType;
        private String riskLevel;
        private String riskMessage;
        private String location;
    }
}