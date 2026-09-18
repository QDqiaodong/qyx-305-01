package com.risk.repository;

import com.risk.entity.RoutePlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoutePlanRepository extends JpaRepository<RoutePlan, Long> {
    
    List<RoutePlan> findByStatus(String status);
    
    List<RoutePlan> findByPlanNameContaining(String planName);
}