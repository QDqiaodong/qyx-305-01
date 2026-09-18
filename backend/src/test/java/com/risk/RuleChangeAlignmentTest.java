package com.risk;

import com.risk.dto.request.AssignmentRequest;
import com.risk.dto.request.MedicalStaffRequest;
import com.risk.dto.request.ReleaseRequest;
import com.risk.dto.request.RiskRuleRequest;
import com.risk.dto.request.RoutePlanRequest;
import com.risk.dto.response.ReleasePermitResponse;
import com.risk.dto.response.ReportResponse;
import com.risk.entity.RiskRule;
import com.risk.exception.BusinessException;
import com.risk.repository.RiskRuleRepository;
import com.risk.service.MedicalScheduleService;
import com.risk.service.ReleaseGateService;
import com.risk.service.RiskRuleService;
import com.risk.service.RoutePlanService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 规则变更场景下“报告页 / 放行看板 / 出门条”三处口径对齐的端到端校验：
 *  - 未记下发车时刻的计划：规则一改，出门条作废并停在待重筛，报告同步不可出门，两处读同一份最新筛查；
 *  - 已记下发车时刻的单据：报告保持出门当时那一版，不被改规则改写；
 *  - 同一规则被连着改（两人同时改的串行等价）：未发车计划一起落到待重筛，无一遗漏。
 *
 * 规则改动一律走 {@link RiskRuleService}（只有服务层才会翻动规则版本并联动出门条）。
 */
@SpringBootTest
@Import(TestRedisConfig.class)
class RuleChangeAlignmentTest {

    @Autowired private RoutePlanService planService;
    @Autowired private MedicalScheduleService scheduleService;
    @Autowired private ReleaseGateService releaseGate;
    @Autowired private RiskRuleService ruleService;
    @Autowired private RiskRuleRepository ruleRepository;

    private static final LocalDate MAR = LocalDate.of(2026, 3, 12); // 周四，中性月份

    private Long plan(String name, List<String> wp) {
        return planService.createPlan(RoutePlanRequest.builder()
                .planName(name).startLocation("学校").endLocation("博物馆").waypoints(wp)
                .travelDate(MAR).ageMin(30).ageMax(50).participantCount(20).build()).getId();
    }

    /** 中风险线：途经点“城市广场”命中 VENUE_003（MEDIUM） */
    private Long medium(String name) {
        return plan(name, List.of("城市广场"));
    }

    /** 低风险线：无途经点 */
    private Long low(String name) {
        return plan(name, null);
    }

    private Long staff(String name) {
        return scheduleService.createStaff(MedicalStaffRequest.builder()
                .staffName(name + "-" + System.nanoTime()).pediatricQualified(false).build()).getId();
    }

    private void check(Long planId) {
        planService.checkRisk(planId);
    }

    private void assign(Long planId, Long... ids) {
        scheduleService.assign(AssignmentRequest.builder()
                .planId(planId).staffIds(ids.length == 0 ? List.of() : List.of(ids)).build());
    }

    private ReleasePermitResponse eval(Long planId) {
        return releaseGate.evaluate(planId);
    }

    private RiskRule venue003() {
        return ruleRepository.findByRuleCode("VENUE_003").orElseThrow();
    }

    private RiskRuleRequest ruleRequest(RiskRule rule, String riskLevel, String warningMessage) {
        return RiskRuleRequest.builder()
                .ruleCode(rule.getRuleCode())
                .ruleName(rule.getRuleName())
                .ruleType(rule.getRuleType())
                .riskLevel(riskLevel)
                .conditionExpression(rule.getConditionExpression())
                .warningMessage(warningMessage)
                .enabled(1)
                .build();
    }

    // 1) 规则一改：未发车的出门条作废并停在待重筛，报告同步不可出门；重筛对齐后才可再放行
    @Test
    void ruleChange_undepartedPermitDropsToStale_andReportAligns() {
        Long p = medium("规则变更对齐线");
        check(p);
        assign(p, staff("对齐线随队"));
        releaseGate.submit(p, ReleaseRequest.builder().informedNote("中风险知情").build());
        assertThat(eval(p).getStatus()).isEqualTo("RELEASED");

        // 改规则前：报告筛查有效、已放行
        ReportResponse before = planService.generateReport(p);
        assertThat(before.isScreeningValid()).isTrue();
        assertThat(before.getPermitStatus()).isEqualTo("RELEASED");

        RiskRule crowded = venue003();
        String origMessage = crowded.getWarningMessage();
        try {
            // 调度改规则（哪怕只改预警文案，也算规则变了）
            ruleService.updateRule(crowded.getId(), ruleRequest(crowded, "MEDIUM", "改过的预警文案"));

            // 出门条：作废并停在待重筛
            ReleasePermitResponse after = eval(p);
            assertThat(after.getStatus()).isEqualTo("STALE_RECHECK");
            assertThat(after.getRecheckReason()).contains("规则");
            assertThat(after.isReleasable()).isFalse();
            assertThat(after.getScreening().isTripFresh()).isTrue();   // 行程没变
            assertThat(after.getScreening().isRulesFresh()).isFalse(); // 规则变了
            assertThat(after.getScreening().isFresh()).isFalse();

            // 报告页：与看板同一份结论，旧中风险不得继续当可出门
            ReportResponse report = planService.generateReport(p);
            assertThat(report.isScreeningValid()).isFalse();
            assertThat(report.getScreeningMismatchReason()).contains("规则");
            assertThat(report.getPermitStatus()).isEqualTo("STALE_RECHECK");
            assertThat(report.isDeparted()).isFalse();

            // 旧筛查不能放行
            assertThatThrownBy(() -> releaseGate.submit(p, null))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("规则");

            // 按新规则重筛 → 两本账重新对齐 → 可再放行
            check(p);
            assertThat(eval(p).getScreening().isRulesFresh()).isTrue();
            assertThat(eval(p).isReleasable()).isTrue();
            assertThat(releaseGate.submit(p, ReleaseRequest.builder().informedNote("重新知情").build()).getStatus())
                    .isEqualTo("RELEASED");
        } finally {
            ruleService.updateRule(crowded.getId(), ruleRequest(crowded, "MEDIUM", origMessage));
        }
    }

    // 2) 已记下发车时刻：改规则不改写这张纸，报告保持出门当时那一版
    @Test
    void ruleChange_departedPermitFrozen_reportKeepsDepartureVersion() {
        Long p = medium("已发车封存线");
        check(p);
        assign(p, staff("封存线随队"));
        releaseGate.submit(p, ReleaseRequest.builder().informedNote("中风险知情").build());
        releaseGate.depart(p);
        assertThat(eval(p).getDepartedAt()).isNotNull();
        assertThat(eval(p).getStatusText()).isEqualTo("已发车");

        RiskRule crowded = venue003();
        String origMessage = crowded.getWarningMessage();
        try {
            // 把 VENUE_003 从中风险改成高风险：若按新规则重筛会升高风险，但已发车的纸不能被改写
            ruleService.updateRule(crowded.getId(), ruleRequest(crowded, "HIGH", origMessage));

            ReleasePermitResponse after = eval(p);
            assertThat(after.getStatus()).isEqualTo("RELEASED");       // 不落待重筛
            assertThat(after.getDepartedAt()).isNotNull();
            assertThat(after.getRiskLevel()).isEqualTo("MEDIUM_RISK"); // 出门当时那一版

            ReportResponse report = planService.generateReport(p);
            assertThat(report.isDeparted()).isTrue();
            assertThat(report.getDepartedAt()).isNotBlank();
            assertThat(report.getOverallStatus()).isEqualTo("MEDIUM_RISK"); // 没被这次改规则改写
            assertThat(report.isScreeningValid()).isTrue();

            // 已发车：不能再重筛、不能再作废、不能重复发车、不能再提交放行
            assertThatThrownBy(() -> planService.checkRisk(p))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("发车");
            assertThatThrownBy(() -> releaseGate.manualVoid(p, "试试"))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("发车");
            assertThatThrownBy(() -> releaseGate.depart(p))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("发车");
            assertThatThrownBy(() -> releaseGate.submit(p, null))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("发车");
        } finally {
            ruleService.updateRule(crowded.getId(), ruleRequest(crowded, "MEDIUM", origMessage));
        }
    }

    // 3) 同一规则被连着改两次（两人同时改的串行等价）：未发车计划一起落到待重筛，已发车的不动
    @Test
    void twoRuleEdits_allUndepartedPermitsDropUniformly_departedUntouched() {
        Long p1 = medium("对齐线一");
        check(p1);
        assign(p1, staff("对齐线一随队"));
        releaseGate.submit(p1, ReleaseRequest.builder().informedNote("中风险").build());

        Long p2 = low("对齐线二");
        check(p2);
        assign(p2, staff("对齐线二随队"));
        releaseGate.submit(p2, null);

        Long p3 = medium("对齐线三·已发车");
        check(p3);
        assign(p3, staff("对齐线三随队"));
        releaseGate.submit(p3, ReleaseRequest.builder().informedNote("中风险").build());
        releaseGate.depart(p3);

        assertThat(eval(p1).getStatus()).isEqualTo("RELEASED");
        assertThat(eval(p2).getStatus()).isEqualTo("RELEASED");

        RiskRule crowded = venue003();
        String origMessage = crowded.getWarningMessage();
        try {
            ruleService.updateRule(crowded.getId(), ruleRequest(crowded, "MEDIUM", "第一次改"));
            ruleService.updateRule(crowded.getId(), ruleRequest(crowded, "MEDIUM", "第二次改"));

            // 未发车的两张单一起落到待重筛，不会一张作废一张仍绿灯
            assertThat(eval(p1).getStatus()).isEqualTo("STALE_RECHECK");
            assertThat(eval(p2).getStatus()).isEqualTo("STALE_RECHECK");
            assertThat(eval(p1).getStatus()).isEqualTo(eval(p2).getStatus());

            // 报告页与看板同一份待重筛
            assertThat(planService.generateReport(p1).getPermitStatus()).isEqualTo("STALE_RECHECK");
            assertThat(planService.generateReport(p2).getPermitStatus()).isEqualTo("STALE_RECHECK");
            assertThat(planService.generateReport(p1).isScreeningValid()).isFalse();
            assertThat(planService.generateReport(p2).isScreeningValid()).isFalse();

            // 已发车的纸面保持当时那一版
            assertThat(eval(p3).getStatus()).isEqualTo("RELEASED");
            assertThat(eval(p3).getDepartedAt()).isNotNull();
        } finally {
            ruleService.updateRule(crowded.getId(), ruleRequest(crowded, "MEDIUM", origMessage));
        }
    }

    // 4) 发车时刻只能从「已放行」登记；未放行/已作废都不能发车
    @Test
    void depart_onlyFromReleased() {
        Long p = medium("未放行线");
        check(p);
        assertThatThrownBy(() -> releaseGate.depart(p))
                .isInstanceOf(BusinessException.class).hasMessageContaining("已放行");

        assign(p, staff("未放行线随队"));
        releaseGate.submit(p, ReleaseRequest.builder().informedNote("中风险").build());
        releaseGate.manualVoid(p, "行程取消");
        assertThatThrownBy(() -> releaseGate.depart(p))
                .isInstanceOf(BusinessException.class).hasMessageContaining("已放行");
    }
}
