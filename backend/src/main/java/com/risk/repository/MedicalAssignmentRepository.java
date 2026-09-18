package com.risk.repository;

import com.risk.entity.MedicalAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface MedicalAssignmentRepository extends JpaRepository<MedicalAssignment, Long> {

    /** 某计划当前挂的排班 */
    List<MedicalAssignment> findByPlanId(Long planId);

    /** 某名医护挂着的全部排班（删除医护前校验） */
    List<MedicalAssignment> findByStaffId(Long staffId);

    /** 某名医护在某天、且不是某计划的排班数量（服务层做同一天冲突硬校验） */
    long countByStaffIdAndTravelDateAndPlanIdNot(Long staffId, LocalDate travelDate, Long planId);

    void deleteByPlanId(Long planId);
}
