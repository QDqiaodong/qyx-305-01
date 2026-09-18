package com.risk.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskRuleRequest {

    @NotBlank(message = "规则代码不能为空")
    private String ruleCode;

    @NotBlank(message = "规则名称不能为空")
    private String ruleName;

    @NotBlank(message = "规则类型不能为空")
    private String ruleType;

    @NotBlank(message = "风险等级不能为空")
    private String riskLevel;

    @NotBlank(message = "条件表达式不能为空")
    private String conditionExpression;

    @NotBlank(message = "预警信息不能为空")
    private String warningMessage;

    private Integer enabled;
}