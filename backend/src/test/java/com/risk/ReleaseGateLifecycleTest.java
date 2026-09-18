package com.risk;

import com.risk.dto.request.AssignmentRequest;
import com.risk.dto.request.MedicalStaffRequest;
import com.risk.dto.request.ReleaseRequest;
import com.risk.dto.request.RoutePlanRequest;
import com.risk.dto.response.ReleasePermitResponse;
import com.risk.exception.BusinessException;
import com.risk.entity.RiskRule;
import com.risk.repository.RiskRuleRepository;
import com.risk.service.MedicalScheduleService;
import com.risk.service.ReleaseGateService;
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
 * 发车当天两本账（筛查时效 + 医护覆盖）联动的端到端校验。
 *
 * 依据内置规则选取可稳定得到各等级的输入（2026-03 为无天气规则的中性月份，所选日期均为工作日）：
 *  - 低风险：成人 + 普通地点（学校→博物馆/体育馆，无途经点）
 *  - 中风险：成人 + 途经点含“广场”（VENUE_003 人员密集 MEDIUM）
 *  - 高风险(山路)：成人 + 终点“山顶公园”（TRAFFIC_002 MOUNTAIN_ROAD HIGH）
 *  - 高风险(年龄)：ageMin≤12（AGE_001 CHILDREN HIGH）
 *  - 6-8 月还会命中雨季 HIGH，故中/低风险一律用 3 月规避
 */
@SpringBootTest
@Import(TestRedisConfig.class)
class ReleaseGateLifecycleTest {

    @Autowired private RoutePlanService planService;
    @Autowired private MedicalScheduleService scheduleService;
    @Autowired private ReleaseGateService releaseGate;
    @Autowired private RiskRuleRepository ruleRepository;

    private static final LocalDate MAR = LocalDate.of(2026, 3, 12);  // 周四
    private static final LocalDate MAR2 = LocalDate.of(2026, 3, 13); // 周五（异日用）
    private static final LocalDate MAR3 = LocalDate.of(2026, 3, 17); // 周二（改期用）

    private Long plan(String name, LocalDate date, int ageMin, int ageMax, int count,
                      String start, String end, List<String> wp) {
        return planService.createPlan(RoutePlanRequest.builder()
                .planName(name).startLocation(start).endLocation(end).waypoints(wp)
                .travelDate(date).ageMin(ageMin).ageMax(ageMax).participantCount(count).build()).getId();
    }

    private Long low(String name) {
        return plan(name, MAR, 30, 50, 20, "学校", "博物馆", null);
    }

    private Long medium(String name) {
        return plan(name, MAR, 30, 50, 20, "学校", "博物馆", List.of("城市广场"));
    }

    private Long highMountain(String name) {
        return plan(name, MAR, 30, 50, 20, "学校", "山顶公园", null);
    }

    private void update(Long id, String name, LocalDate date, int ageMin, int ageMax, int count,
                        String start, String end, List<String> wp) {
        planService.updatePlan(id, RoutePlanRequest.builder()
                .planName(name).startLocation(start).endLocation(end).waypoints(wp)
                .travelDate(date).ageMin(ageMin).ageMax(ageMax).participantCount(count).build());
    }

    private Long staff(String name, boolean pediatric) {
        return scheduleService.createStaff(MedicalStaffRequest.builder()
                .staffName(name + "-" + System.nanoTime()).pediatricQualified(pediatric).build()).getId();
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

    // 1) 中风险：覆盖满足 + 知情备注可放行；撤光医护后已放行单掉到待重评
    @Test
    void mediumRisk_releaseWithNote_thenClearStaff_dropsToStale() {
        Long p = medium("成人中风险线");
        check(p);
        assertThat(eval(p).getScreening().getRiskLevel()).isEqualTo("MEDIUM_RISK");

        Long s1 = staff("随队赵医生", false);

        // 未排班：医护账不满足，放行失败且不出单
        assertThatThrownBy(() -> releaseGate.submit(p, null))
                .isInstanceOf(BusinessException.class).hasMessageContaining("医护");
        assertThat(eval(p).getStatus()).isEqualTo("PENDING");

        assign(p, s1);
        ReleasePermitResponse ok = releaseGate.submit(p, ReleaseRequest.builder().informedNote("带队已知悉中风险").build());
        assertThat(ok.getStatus()).isEqualTo("RELEASED");
        assertThat(ok.getInformedNote()).contains("中风险");

        // 医护被全部抽走 → 已放行单不能继续显示可出门，掉到待重评
        assign(p);
        ReleasePermitResponse after = eval(p);
        assertThat(after.getStatus()).isEqualTo("STALE_RECHECK");
        assertThat(after.getRecheckReason()).contains("医护");
        assertThat(after.isReleasable()).isFalse();

        // 重新补回医护、筛查等级未变 → 可再次放行
        assign(p, s1);
        assertThat(releaseGate.submit(p, null).getStatus()).isEqualTo("RELEASED");
    }

    // 2) 最小年龄 ≤ 8 岁：至少一名儿科资质；高风险带备注也不放行
    @Test
    void pediatricRequired_whenMinAgeAtMost8() {
        Long p = plan("低龄高风险线", MAR, 8, 10, 20, "学校", "博物馆", null);
        check(p);
        assertThat(eval(p).getScreening().getRiskLevel()).isEqualTo("HIGH_RISK"); // AGE_001

        Long general = staff("全科医生甲", false);
        Long general2 = staff("全科医生乙", false);
        Long pediatric = staff("儿科李医生", true);

        // 一名全科：高风险需双人 + 缺儿科
        assign(p, general);
        assertThat(eval(p).getCoverage().isSatisfied()).isFalse();

        // 两名全科：人数够但儿科缺失
        assign(p, general, general2);
        assertThat(eval(p).getCoverage().isCountSatisfied()).isTrue();
        assertThat(eval(p).getCoverage().isPediatricSatisfied()).isFalse();
        assertThat(eval(p).getCoverage().isSatisfied()).isFalse();

        // 一儿科 + 一全科：覆盖满足
        assign(p, pediatric, general);
        assertThat(eval(p).getCoverage().isPediatricSatisfied()).isTrue();
        assertThat(eval(p).getCoverage().isSatisfied()).isTrue();

        // 高风险：知情备注也不能放行
        assertThatThrownBy(() -> releaseGate.submit(p, ReleaseRequest.builder().informedNote("带队知情").build()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("高风险");
        assertThat(eval(p).getStatus()).isEqualTo("PENDING");
    }

    // 3) 高风险双人值班；两本账齐也不放行；第二次提交仍明确失败，不出绿单
    @Test
    void highRisk_doubleStaffRequired_andNeverReleases() {
        Long p = highMountain("高风险山区线");
        check(p);
        assertThat(eval(p).getScreening().getRiskLevel()).isEqualTo("HIGH_RISK"); // MOUNTAIN_ROAD
        assertThat(eval(p).getCoverage().getRequiredCount()).isEqualTo(2);

        Long s1 = staff("随队甲", false);
        Long s2 = staff("随队乙", false);

        assign(p, s1);
        assertThat(eval(p).getCoverage().isSatisfied()).isFalse();

        assign(p, s1, s2);
        assertThat(eval(p).getCoverage().isSatisfied()).isTrue();

        // 第一次：带备注也拒
        assertThatThrownBy(() -> releaseGate.submit(p, ReleaseRequest.builder().informedNote("知情").build()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("高风险");
        // 第二次：不改任何条件仍明确失败
        assertThatThrownBy(() -> releaseGate.submit(p, null))
                .isInstanceOf(BusinessException.class).hasMessageContaining("高风险");
        assertThat(eval(p).getStatus()).isEqualTo("PENDING");
    }

    // 4) 知情备注只能写在中风险单上（低风险写备注被拒，不写可放行）
    @Test
    void informedNoteOnlyAllowedOnMedium() {
        Long p = low("低风险成人线");
        check(p);
        assertThat(eval(p).getScreening().getRiskLevel()).isEqualTo("LOW_RISK");
        Long s1 = staff("低风险随队", false);
        assign(p, s1);

        assertThatThrownBy(() -> releaseGate.submit(p, ReleaseRequest.builder().informedNote("多余备注").build()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("知情备注");

        assertThat(releaseGate.submit(p, null).getStatus()).isEqualTo("RELEASED");
        assertThat(eval(p).getInformedNote()).isNull();
    }

    // 5) 行程四要素变更 → 放行单作废（写明行程变了）、旧筛查失效、必须重筛
    @Test
    void tripChangeAfterRelease_voidsPermit_andMustRescreen() {
        Long p = medium("行程变更线");
        check(p);
        Long s1 = staff("行程线随队", false);
        assign(p, s1);
        releaseGate.submit(p, ReleaseRequest.builder().informedNote("中风险知情").build());
        assertThat(eval(p).getStatus()).isEqualTo("RELEASED");

        // 改人数
        update(p, "行程变更线", MAR, 30, 50, 45, "学校", "博物馆", List.of("城市广场"));
        ReleasePermitResponse after = eval(p);
        assertThat(after.getStatus()).isEqualTo("VOID");
        assertThat(after.getVoidReason()).contains("行程");
        assertThat(after.getScreening().isFresh()).isFalse();
        assertThat(after.getScreening().getMismatchReason()).contains("参与人数");
        assertThat(after.isReleasable()).isFalse();

        // 旧筛查不能当出门凭证
        assertThatThrownBy(() -> releaseGate.submit(p, null))
                .isInstanceOf(BusinessException.class).hasMessageContaining("行程");

        // 重筛（仍中风险，医护仍满足）→ 再次放行
        check(p);
        assertThat(eval(p).isReleasable()).isTrue();
        assertThat(releaseGate.submit(p, ReleaseRequest.builder().informedNote("重新知情").build()).getStatus())
                .isEqualTo("RELEASED");
    }

    // 6) 改日期：旧筛查失效，且按旧日期挂的排班失配
    @Test
    void dateChange_voidsPermit_andOldAssignmentDateMismatches() {
        Long p = medium("改期线");
        check(p);
        Long s1 = staff("改期随队", false);
        assign(p, s1);
        releaseGate.submit(p, null);
        assertThat(eval(p).getStatus()).isEqualTo("RELEASED");

        update(p, "改期线", MAR3, 30, 50, 20, "学校", "博物馆", List.of("城市广场"));
        ReleasePermitResponse after = eval(p);
        assertThat(after.getStatus()).isEqualTo("VOID");
        assertThat(after.getScreening().isFresh()).isFalse();
        assertThat(after.getCoverage().getStaff()).isNotEmpty();
        assertThat(after.getCoverage().getStaff().get(0).isDateMatched()).isFalse();
        assertThat(after.getCoverage().isSatisfied()).isFalse();
    }

    // 7) 同一医护同一天不能挂两条计划；不同日期可以
    @Test
    void sameStaffSameDay_conflict_butDifferentDaysAllowed() {
        Long p1 = plan("同日线甲", MAR, 30, 50, 20, "学校", "博物馆", null);
        Long p2 = plan("同日线乙", MAR, 30, 50, 20, "学校", "体育馆", null);
        check(p1);
        check(p2);
        Long s1 = staff("抢手医护", false);
        assign(p1, s1);

        assertThatThrownBy(() -> assign(p2, s1))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("同一天");

        update(p2, "同日线乙", MAR2, 30, 50, 20, "学校", "体育馆", null);
        check(p2);
        assign(p2, s1); // 不同日期，允许
        assertThat(eval(p2).getCoverage().getCoveredCount()).isEqualTo(1);
        assertThat(eval(p1).getStatus()).isEqualTo("PENDING");
    }

    // 8) 改线去掉高风险触发条件后重筛，等级降下来方可放行
    @Test
    void rerouteAwayFromHighRisk_thenRescreen_allowsRelease() {
        Long p = highMountain("关规则线");
        check(p);
        assertThat(eval(p).getScreening().getRiskLevel()).isEqualTo("HIGH_RISK");
        Long s1 = staff("改线随队", false);
        assign(p, s1);
        assertThatThrownBy(() -> releaseGate.submit(p, null))
                .isInstanceOf(BusinessException.class).hasMessageContaining("高风险");

        // 改线：山路终点换成普通地点（等价于关掉 MOUNTAIN_ROAD 触发条件），重筛
        update(p, "关规则线", MAR, 30, 50, 20, "学校", "博物馆", List.of("城市广场"));
        check(p);
        assertThat(eval(p).getScreening().getRiskLevel()).isEqualTo("MEDIUM_RISK");
        assertThat(eval(p).isReleasable()).isTrue();
        assertThat(releaseGate.submit(p, ReleaseRequest.builder().informedNote("已改线，知情").build()).getStatus())
                .isEqualTo("RELEASED");
    }

    // 9) 已放行后行程改成高风险并重筛：旧单作废、高风险拦截、双人覆盖仍满足
    @Test
    void releasedThenRouteBecomesHighRisk_blocks() {
        Long p = medium("等级变化线");
        check(p);
        Long s1 = staff("等级线随队一", false);
        Long s2 = staff("等级线随队二", false);
        assign(p, s1, s2);
        releaseGate.submit(p, ReleaseRequest.builder().informedNote("中风险").build());
        assertThat(eval(p).getStatus()).isEqualTo("RELEASED");

        update(p, "等级变化线", MAR, 30, 50, 20, "学校", "山顶公园", null);
        check(p);
        assertThat(eval(p).getScreening().getRiskLevel()).isEqualTo("HIGH_RISK");
        assertThat(eval(p).isReleasable()).isFalse();
        assertThat(eval(p).getCoverage().isSatisfied()).isTrue(); // 双人仍在岗
    }

    // 11) 行程不变、仅切换规则开关后重筛：等级“升”到高风险 → 待重评且安全岗拦截
    @Test
    void ruleOnlyToggle_levelUpToHigh_dropsToStaleAndBlocks() {
        RiskRule rainy = ruleRepository.findByRuleCode("WEATHER_001").orElseThrow();
        int orig = rainy.getEnabled();
        try {
            // 先关掉雨季规则：6 月成人普通线只剩“高温中风险”
            rainy.setEnabled(0);
            ruleRepository.save(rainy);

            Long p = plan("规则升级线", LocalDate.of(2026, 6, 18), 30, 50, 20, "学校", "博物馆", null);
            check(p);
            assertThat(eval(p).getScreening().getRiskLevel()).isEqualTo("MEDIUM_RISK");
            Long s1 = staff("升级线随队一", false);
            Long s2 = staff("升级线随队二", false);
            assign(p, s1, s2);
            releaseGate.submit(p, ReleaseRequest.builder().informedNote("中风险").build());
            assertThat(eval(p).getStatus()).isEqualTo("RELEASED");

            // 行程不变，重新启用雨季规则后重筛 → 升到 HIGH
            rainy.setEnabled(1);
            ruleRepository.save(rainy);
            check(p);
            ReleasePermitResponse v = eval(p);
            assertThat(v.getScreening().getRiskLevel()).isEqualTo("HIGH_RISK");
            assertThat(v.getStatus()).isEqualTo("STALE_RECHECK");
            assertThat(v.getRecheckReason()).contains("高风险");
            assertThat(v.isReleasable()).isFalse();
            // 高风险：补多少人、写不写备注都不放行
            assertThatThrownBy(() -> releaseGate.submit(p, ReleaseRequest.builder().informedNote("带队强知情").build()))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("高风险");
        } finally {
            rainy.setEnabled(orig);
            ruleRepository.save(rainy);
        }
    }

    // 12) 行程不变、关掉中风险规则后重筛：等级“降”到低风险 → 待重评，之后可重新放行
    @Test
    void ruleOnlyToggle_levelDownToLow_dropsToStale_thenRereleasable() {
        RiskRule crowded = ruleRepository.findByRuleCode("VENUE_003").orElseThrow();
        int orig = crowded.getEnabled();
        try {
            Long p = medium("规则降级线");
            check(p);
            assertThat(eval(p).getScreening().getRiskLevel()).isEqualTo("MEDIUM_RISK");
            Long s1 = staff("降级线随队", false);
            assign(p, s1);
            releaseGate.submit(p, ReleaseRequest.builder().informedNote("中风险").build());
            assertThat(eval(p).getStatus()).isEqualTo("RELEASED");

            crowded.setEnabled(0);
            ruleRepository.save(crowded);
            check(p);
            ReleasePermitResponse v = eval(p);
            assertThat(v.getScreening().getRiskLevel()).isEqualTo("LOW_RISK");
            assertThat(v.getStatus()).isEqualTo("STALE_RECHECK");
            assertThat(v.getRecheckReason()).contains("低风险");

            // 低风险无需备注，两本账齐 → 重新放行
            assertThat(v.isReleasable()).isTrue();
            assertThat(releaseGate.submit(p, null).getStatus()).isEqualTo("RELEASED");
            assertThat(eval(p).getInformedNote()).isNull();
        } finally {
            crowded.setEnabled(orig);
            ruleRepository.save(crowded);
        }
    }

    // 10) 首页统计四档计数随状态变化
    @Test
    void dashboardStats_tracksFourPermitBuckets() {
        long beforePending = releaseGate.stats().getPendingDocsCount();

        Long p = medium("统计线");
        check(p);
        Long s1 = staff("统计线随队", false);

        assertThat(eval(p).getStatus()).isEqualTo("PENDING");
        assertThat(releaseGate.stats().getPendingDocsCount()).isEqualTo(beforePending + 1);

        assign(p, s1);
        releaseGate.submit(p, null);
        assertThat(releaseGate.stats().getReleasedCount()).isGreaterThanOrEqualTo(1);

        assign(p); // 撤光 → 待重评
        eval(p);
        assertThat(releaseGate.stats().getStaleRecheckCount()).isGreaterThanOrEqualTo(1);
    }
}
