package com.risk.repository;

import com.risk.entity.MedicalStaff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MedicalStaffRepository extends JpaRepository<MedicalStaff, Long> {

    List<MedicalStaff> findByPediatricQualified(Integer pediatricQualified);
}
