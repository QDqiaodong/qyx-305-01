package com.risk.controller;

import com.risk.dto.request.AssignmentRequest;
import com.risk.dto.request.MedicalStaffRequest;
import com.risk.dto.response.ApiResponse;
import com.risk.dto.response.AssignmentResponse;
import com.risk.dto.response.CoverageInfo;
import com.risk.entity.MedicalStaff;
import com.risk.service.MedicalScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 随队医护名册 + 按出行日挂计划的排班。
 */
@RestController
@RequestMapping("/api/medical")
@RequiredArgsConstructor
public class MedicalScheduleController {

    private final MedicalScheduleService scheduleService;

    // —— 医护名册 ——
    @GetMapping("/staff")
    public ApiResponse<List<MedicalStaff>> listStaff() {
        return ApiResponse.success(scheduleService.listStaff());
    }

    @PostMapping("/staff")
    public ApiResponse<MedicalStaff> createStaff(@Valid @RequestBody MedicalStaffRequest request) {
        return ApiResponse.success("医护创建成功", scheduleService.createStaff(request));
    }

    @PutMapping("/staff/{id}")
    public ApiResponse<MedicalStaff> updateStaff(@PathVariable Long id,
                                                 @Valid @RequestBody MedicalStaffRequest request) {
        return ApiResponse.success("医护更新成功", scheduleService.updateStaff(id, request));
    }

    @DeleteMapping("/staff/{id}")
    public ApiResponse<Void> deleteStaff(@PathVariable Long id) {
        scheduleService.deleteStaff(id);
        return ApiResponse.success("医护删除成功", null);
    }

    // —— 排班 ——

    /** 查看某计划当前排班 + 覆盖结论 */
    @GetMapping("/assignments/plan/{planId}")
    public ApiResponse<AssignmentResponse> getAssignment(@PathVariable Long planId) {
        return ApiResponse.success(scheduleService.getAssignment(planId));
    }

    /** 覆盖式排班（改挂走同一个入口） */
    @PostMapping("/assignments")
    public ApiResponse<AssignmentResponse> assign(@Valid @RequestBody AssignmentRequest request) {
        AssignmentResponse resp = scheduleService.assign(request);
        return ApiResponse.success("排班保存成功", resp);
    }

    /** 只看覆盖情况 */
    @GetMapping("/coverage/{planId}")
    public ApiResponse<CoverageInfo> coverage(@PathVariable Long planId) {
        return ApiResponse.success(scheduleService.coverage(planId));
    }
}
