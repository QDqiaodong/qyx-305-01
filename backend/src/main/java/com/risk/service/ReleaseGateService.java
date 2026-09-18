package com.risk.service;

import com.risk.dto.request.ReleaseRequest;
import com.risk.dto.response.DashboardStats;
import com.risk.dto.response.ReleasePermitResponse;
import com.risk.entity.RoutePlan;

import java.util.List;

public interface ReleaseGateService {

    /** 只读评估某计划当前的放行状态（两本账实时核对；必要时把已放行但已失效的单子落档）。 */
    ReleasePermitResponse evaluate(Long planId);

    /**
     * 提交放行：两本账都对得上且非高风险才出绿单；
     * 否则抛出 BusinessException 并逐条写明原因，绝不出绿单、不改既有状态。
     */
    ReleasePermitResponse submit(Long planId, ReleaseRequest request);

    /** 带队老师/安全岗主动作废。 */
    ReleasePermitResponse manualVoid(Long planId, String reason);

    /**
     * 登记发车时刻：只有「已放行」且未发车的出门条能登记。
     * 记下后单据封存，之后的规则调整、重筛、排班变动都不再改写这张纸。
     */
    ReleasePermitResponse depart(Long planId);

    /** 已发车则抛出 BusinessException：单据已封存，不允许再改写（如重新筛查）。 */
    void assertNotDeparted(Long planId);

    /** 全部放行单（含待齐件的计划）。 */
    List<ReleasePermitResponse> listAll();

    /** 首页统计。 */
    DashboardStats stats();

    // —— 供其它领域在状态变化时调用的联动钩子 ——

    /** 行程（日期/人数/年龄段/途经点）被改动：已发出的放行单一律作废，写明行程变了。 */
    void onTripChanged(RoutePlan plan);

    /** 排班改挂后重新核对覆盖；若已放行单不再被满足，落到待重评。 */
    void onAssignmentsChanged(RoutePlan plan, List<String> removedStaffNames);

    /** 重新筛查完成后：已放行单所依据的等级若发生升降，落到待重评。 */
    void onScreeningFinished(RoutePlan plan, String newRiskLevel);

    /** 风险规则被改动：所有未发车的已放行出门条一律作废并停在待重筛，已发车的不动。 */
    void onRulesChanged();
}
