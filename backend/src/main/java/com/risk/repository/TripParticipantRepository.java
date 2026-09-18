package com.risk.repository;

import com.risk.entity.TripParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TripParticipantRepository extends JpaRepository<TripParticipant, Long> {

    List<TripParticipant> findByPlanIdOrderByIdAsc(Long planId);

    Optional<TripParticipant> findByIdAndPlanId(Long id, Long planId);

    boolean existsByPlanIdAndPersonName(Long planId, String personName);

    long countByPlanId(Long planId);
}
