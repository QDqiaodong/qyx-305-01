package com.risk.service;

import com.risk.dto.request.RoutePlanRequest;
import com.risk.dto.response.RiskCheckResponse;
import com.risk.dto.response.ReportResponse;
import com.risk.entity.RoutePlan;

import java.util.List;

public interface RoutePlanService {
    
    RoutePlan createPlan(RoutePlanRequest request);
    
    RoutePlan getPlanById(Long id);
    
    List<RoutePlan> getAllPlans();
    
    RoutePlan updatePlan(Long id, RoutePlanRequest request);
    
    void deletePlan(Long id);
    
    RiskCheckResponse checkRisk(Long planId);
    
    ReportResponse generateReport(Long planId);
}