package com.risk.repository;

import com.risk.entity.ReleasePermit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReleasePermitRepository extends JpaRepository<ReleasePermit, Long> {

    Optional<ReleasePermit> findByPlanId(Long planId);

    List<ReleasePermit> findByStatus(String status);
}
