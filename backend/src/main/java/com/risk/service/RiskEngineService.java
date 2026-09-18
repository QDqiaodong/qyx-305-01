package com.risk.service;

import com.risk.entity.RiskRule;
import com.risk.entity.RoutePlan;
import com.risk.dto.response.RiskCheckResponse;

import java.util.List;

public interface RiskEngineService {
    
    RiskCheckResponse evaluatePlan(Long planId);
    
    List<RiskCheckResponse.RiskItem> evaluateAgainstRules(RoutePlan plan, List<RiskRule> rules);
    
    boolean evaluateRule(RiskRule rule, RoutePlan plan, String location);
}