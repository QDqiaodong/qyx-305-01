package com.risk.service;

import com.risk.dto.request.RiskRuleRequest;
import com.risk.entity.RiskRule;

import java.util.List;
import java.util.Map;

public interface RiskRuleService {
    
    List<RiskRule> getAllRules();
    
    List<RiskRule> getEnabledRules();
    
    List<RiskRule> getRulesByType(String ruleType);
    
    RiskRule getRuleById(Long id);
    
    RiskRule getRuleByCode(String code);
    
    RiskRule createRule(RiskRuleRequest request);
    
    RiskRule updateRule(Long id, RiskRuleRequest request);
    
    void deleteRule(Long id);
    
    void toggleRuleStatus(Long id);
    
    Map<String, List<RiskRule>> getRulesGroupedByType();
    
    void refreshRulesCache();
}