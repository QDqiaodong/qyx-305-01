package com.risk.repository;

import com.risk.entity.RiskScreening;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RiskScreeningRepository extends JpaRepository<RiskScreening, Long> {

    Optional<RiskScreening> findByPlanId(Long planId);

    void deleteByPlanId(Long planId);
}
