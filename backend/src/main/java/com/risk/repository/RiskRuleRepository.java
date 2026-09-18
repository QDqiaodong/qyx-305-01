package com.risk.repository;

import com.risk.entity.RiskRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RiskRuleRepository extends JpaRepository<RiskRule, Long> {
    
    List<RiskRule> findByEnabled(Integer enabled);
    
    List<RiskRule> findByRuleType(String ruleType);
    
    List<RiskRule> findByRuleTypeAndEnabled(String ruleType, Integer enabled);
    
    Optional<RiskRule> findByRuleCode(String ruleCode);
    
    boolean existsByRuleCode(String ruleCode);
}