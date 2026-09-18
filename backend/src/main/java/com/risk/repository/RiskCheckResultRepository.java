package com.risk.repository;

import com.risk.entity.RiskCheckResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RiskCheckResultRepository extends JpaRepository<RiskCheckResult, Long> {
    
    List<RiskCheckResult> findByPlanId(Long planId);
    
    List<RiskCheckResult> findByPlanIdAndRiskLevel(Long planId, String riskLevel);
    
    void deleteByPlanId(Long planId);
}