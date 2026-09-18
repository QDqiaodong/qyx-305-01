package com.risk.controller;

import com.risk.dto.request.ReleaseRequest;
import com.risk.dto.response.ApiResponse;
import com.risk.dto.response.DashboardStats;
import com.risk.dto.response.ReleasePermitResponse;
import com.risk.service.ReleaseGateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 发车放行：同时核对“筛查时效”与“医护覆盖”两本账。
 */
@RestController
@RequestMapping("/api/release")
@RequiredArgsConstructor
public class ReleaseController {

    private final ReleaseGateService releaseGate;

    /** 全部放行单（含待齐件计划），用于发车看板 */
    @GetMapping("/permits")
    public ApiResponse<List<ReleasePermitResponse>> listAll() {
        return ApiResponse.success(releaseGate.listAll());
    }

    /** 首页统计（风险预警 + 放行四档 + 医护） */
    @GetMapping("/stats")
    public ApiResponse<DashboardStats> stats() {
        return ApiResponse.success(releaseGate.stats());
    }

    /** 只读评估某计划当前放行状态（两本账实时核对） */
    @GetMapping("/plan/{planId}")
    public ApiResponse<ReleasePermitResponse> evaluate(@PathVariable Long planId) {
        return ApiResponse.success(releaseGate.evaluate(planId));
    }

    /**
     * 提交放行。两本账都对得上且非高风险才会出绿单；
     * 否则返回 400 并逐条说明原因，绝不再悄悄出一张可出门的单子。
     */
    @PostMapping("/plan/{planId}/submit")
    public ApiResponse<ReleasePermitResponse> submit(@PathVariable Long planId,
                                                     @RequestBody(required = false) ReleaseRequest request) {
        return ApiResponse.success("放行成功", releaseGate.submit(planId, request));
    }

    /**
     * 登记发车时刻。只有「已放行」的出门条能登记；
     * 记下后单据封存，之后的规则调整、重筛、排班变动都不再改写这张纸。
     */
    @PostMapping("/plan/{planId}/depart")
    public ApiResponse<ReleasePermitResponse> depart(@PathVariable Long planId) {
        return ApiResponse.success("已登记发车时刻，单据封存", releaseGate.depart(planId));
    }

    /** 人工作废 */
    @PostMapping("/plan/{planId}/void")
    public ApiResponse<ReleasePermitResponse> manualVoid(@PathVariable Long planId,
                                                        @RequestBody(required = false) Map<String, String> body) {
        String reason = body == null ? null : body.get("reason");
        return ApiResponse.success("已作废", releaseGate.manualVoid(planId, reason));
    }
}
