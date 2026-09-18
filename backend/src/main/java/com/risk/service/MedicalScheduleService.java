package com.risk.service;

import com.risk.dto.request.AssignmentRequest;
import com.risk.dto.request.MedicalStaffRequest;
import com.risk.dto.response.AssignmentResponse;
import com.risk.dto.response.CoverageInfo;
import com.risk.entity.MedicalStaff;

import java.util.List;

public interface MedicalScheduleService {

    // —— 医护名册 ——
    List<MedicalStaff> listStaff();

    MedicalStaff createStaff(MedicalStaffRequest request);

    MedicalStaff updateStaff(Long id, MedicalStaffRequest request);

    void deleteStaff(Long id);

    // —— 排班 ——
    /** 查看某计划当天排班与覆盖情况。 */
    AssignmentResponse getAssignment(Long planId);

    /** 覆盖式排班：把给定医护挂到计划出行日，返回覆盖结论并联动放行单。 */
    AssignmentResponse assign(AssignmentRequest request);

    /** 仅查看覆盖（不改数据）。 */
    CoverageInfo coverage(Long planId);
}
