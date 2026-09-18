package com.risk.controller;

import com.risk.dto.request.RoutePlanRequest;
import com.risk.dto.response.ApiResponse;
import com.risk.dto.response.ReportResponse;
import com.risk.dto.response.RiskCheckResponse;
import com.risk.entity.RoutePlan;
import com.risk.service.RoutePlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/plans")
@RequiredArgsConstructor
public class RoutePlanController {

    private final RoutePlanService planService;

    @PostMapping
    public ApiResponse<RoutePlan> createPlan(@Valid @RequestBody RoutePlanRequest request) {
        RoutePlan plan = planService.createPlan(request);
        return ApiResponse.success("创建成功", plan);
    }

    @GetMapping
    public ApiResponse<List<RoutePlan>> getAllPlans() {
        List<RoutePlan> plans = planService.getAllPlans();
        return ApiResponse.success(plans);
    }

    @GetMapping("/{id}")
    public ApiResponse<RoutePlan> getPlanById(@PathVariable Long id) {
        RoutePlan plan = planService.getPlanById(id);
        return ApiResponse.success(plan);
    }

    @PutMapping("/{id}")
    public ApiResponse<RoutePlan> updatePlan(@PathVariable Long id, @Valid @RequestBody RoutePlanRequest request) {
        RoutePlan plan = planService.updatePlan(id, request);
        return ApiResponse.success("更新成功", plan);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deletePlan(@PathVariable Long id) {
        planService.deletePlan(id);
        return ApiResponse.success("删除成功", null);
    }

    @PostMapping("/{id}/check")
    public ApiResponse<RiskCheckResponse> checkRisk(@PathVariable Long id) {
        RiskCheckResponse response = planService.checkRisk(id);
        return ApiResponse.success("风险筛查完成", response);
    }

    @GetMapping("/{id}/report")
    public ApiResponse<ReportResponse> generateReport(@PathVariable Long id) {
        ReportResponse response = planService.generateReport(id);
        return ApiResponse.success("报告生成成功", response);
    }
}