package com.risk;

import com.risk.dto.request.AssignmentRequest;
import com.risk.dto.request.MedicalStaffRequest;
import com.risk.dto.request.RoutePlanRequest;
import com.risk.dto.response.ParticipantView;
import com.risk.dto.response.RollCallResponse;
import com.risk.exception.BusinessException;
import com.risk.repository.TripParticipantRepository;
import com.risk.service.MedicalScheduleService;
import com.risk.service.ReleaseGateService;
import com.risk.service.RollCallService;
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
 * 回程点名端到端校验：
 *  - 发车前维护名册；发车后名册锁定，不能再往车上塞人（晚到只能另开一趟）
 *  - 还没收口的车次必须按发车时带出去的人一个个勾回
 *  - 有人没上车回校：先写缺人说明，才能收口
 *  - 收口后点名结果冻住：勾选/说明/增删人全部拒绝，说明改不没
 *  - 关掉页面再打开（重新读库）：未收口的勾选还在；已收口的仍冻住
 *  - 车上没人点名收不了口；未发车收不了口
 *
 * 取低风险成人线（2026-03 工作日，学校→博物馆）：1 名医护即可放行发车。
 */
@SpringBootTest
@Import(TestRedisConfig.class)
class RollCallLifecycleTest {

    @Autowired private RoutePlanService planService;
    @Autowired private MedicalScheduleService scheduleService;
    @Autowired private ReleaseGateService releaseGate;
    @Autowired private RollCallService rollCall;
    @Autowired private TripParticipantRepository participantRepository;

    private static final LocalDate MAR = LocalDate.of(2026, 3, 12);

    private Long newDepartedPlan(String name) {
        Long planId = planService.createPlan(RoutePlanRequest.builder()
                .planName(name + "-" + System.nanoTime())
                .startLocation("学校").endLocation("博物馆").waypoints(null)
                .travelDate(MAR).ageMin(30).ageMax(50).participantCount(20).build()).getId();
        planService.checkRisk(planId);
        Long s = scheduleService.createStaff(MedicalStaffRequest.builder()
                .staffName("点名随队-" + System.nanoTime()).pediatricQualified(false).build()).getId();
        scheduleService.assign(AssignmentRequest.builder().planId(planId).staffIds(List.of(s)).build());
        releaseGate.submit(planId, null);
        releaseGate.depart(planId);
        return planId;
    }

    private Long pid(RollCallResponse r, String name) {
        return r.getParticipants().stream().filter(p -> p.getPersonName().equals(name))
                .findFirst().map(ParticipantView::getId)
                .orElseThrow(() -> new AssertionError("名册里没有 " + name));
    }

    // 1) 发车前可加人；未发车不能点名、不能收口、车上没人也收不了口
    @Test
    void beforeDepart_rosterEditable_butCannotRollOrClose() {
        Long planId = planService.createPlan(RoutePlanRequest.builder()
                .planName("未发车点名线-" + System.nanoTime())
                .startLocation("学校").endLocation("博物馆").waypoints(null)
                .travelDate(MAR).ageMin(30).ageMax(50).participantCount(20).build()).getId();

        RollCallResponse v = rollCall.addParticipant(planId, "小明");
        assertThat(v.getPhase()).isEqualTo("NOT_DEPARTED");
        assertThat(v.getTotalCount()).isEqualTo(1);

        Long xm = pid(v, "小明");
        // 没发车不能勾回
        assertThatThrownBy(() -> rollCall.markReturned(planId, xm, true))
                .isInstanceOf(BusinessException.class).hasMessageContaining("还没发车");
        // 没发车不能收口（即使名册有人）
        assertThatThrownBy(() -> rollCall.closeRoll(planId))
                .isInstanceOf(BusinessException.class).hasMessageContaining("还没记下发车时刻");

        // 发车了但名册被清空：车上没人点名，值班室仍当全员还没回来，收不了口
        Long departed = newDepartedPlan("空名册线");
        assertThatThrownBy(() -> rollCall.closeRoll(departed))
                .isInstanceOf(BusinessException.class).hasMessageContaining("车上没人点名");
    }

    // 2) 发车后名册锁定：不能加人（调度的“晚到补进这趟”被安全岗驳回），也不能删人
    @Test
    void afterDepart_rosterLocked_cannotAddOrRemove() {
        Long planId = newDepartedPlan("锁名册线");

        assertThatThrownBy(() -> rollCall.addParticipant(planId, "晚到的小红"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("名册已锁定");

        // 重新建一趟：先在发车前放一个人，再验证发车后删不掉
        Long planId2 = planService.createPlan(RoutePlanRequest.builder()
                .planName("锁名册线二-" + System.nanoTime())
                .startLocation("学校").endLocation("博物馆").waypoints(null)
                .travelDate(MAR).ageMin(30).ageMax(50).participantCount(20).build()).getId();
        planService.checkRisk(planId2);
        Long s = scheduleService.createStaff(MedicalStaffRequest.builder()
                .staffName("锁名册随队-" + System.nanoTime()).pediatricQualified(false).build()).getId();
        scheduleService.assign(AssignmentRequest.builder().planId(planId2).staffIds(List.of(s)).build());
        releaseGate.submit(planId2, null);
        RollCallResponse before = rollCall.addParticipant(planId2, "小刚");
        Long xg = pid(before, "小刚");
        releaseGate.depart(planId2);

        assertThatThrownBy(() -> rollCall.addParticipant(planId2, "晚到的小丽"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("另开一趟");
        assertThatThrownBy(() -> rollCall.removeParticipant(planId2, xg))
                .isInstanceOf(BusinessException.class).hasMessageContaining("已发车");
    }

    // 3) 核心流程：发车带出去 3 人 → 逐个勾回 → 未写缺人说明收不了口 → 写齐后收口
    @Test
    void rollOneByOne_missingNoteRequired_thenCloseFreezes() {
        Long planId = planService.createPlan(RoutePlanRequest.builder()
                .planName("回程主线-" + System.nanoTime())
                .startLocation("学校").endLocation("博物馆").waypoints(null)
                .travelDate(MAR).ageMin(30).ageMax(50).participantCount(20).build()).getId();
        planService.checkRisk(planId);
        Long s = scheduleService.createStaff(MedicalStaffRequest.builder()
                .staffName("主线随队-" + System.nanoTime()).pediatricQualified(false).build()).getId();
        scheduleService.assign(AssignmentRequest.builder().planId(planId).staffIds(List.of(s)).build());
        releaseGate.submit(planId, null);

        RollCallResponse roster = rollCall.addParticipant(planId, "小明");
        rollCall.addParticipant(planId, "小红");
        rollCall.addParticipant(planId, "小刚");
        // 同名不能重复进这趟
        assertThatThrownBy(() -> rollCall.addParticipant(planId, "小明"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("重复");

        releaseGate.depart(planId);
        RollCallResponse rolling = rollCall.get(planId);
        assertThat(rolling.getPhase()).isEqualTo("ROLLING");
        assertThat(rolling.getTotalCount()).isEqualTo(3);
        assertThat(rolling.getReturnedCount()).isZero();
        assertThat(rolling.isClosable()).isFalse();

        Long xm = pid(rolling, "小明");
        Long xh = pid(rolling, "小红");
        Long xg = pid(rolling, "小刚");

        // 一个个勾回：勾两个，留一个没上车回校
        rolling = rollCall.markReturned(planId, xm, true);
        assertThat(rolling.getReturnedCount()).isEqualTo(1);
        rolling = rollCall.markReturned(planId, xh, true);
        assertThat(rolling.getMissingCount()).isEqualTo(1);

        // 还有人没回且没写说明：收不了口
        assertThat(rolling.isClosable()).isFalse();
        assertThatThrownBy(() -> rollCall.closeRoll(planId))
                .isInstanceOf(BusinessException.class).hasMessageContaining("缺人说明");

        // 空说明也不行
        assertThatThrownBy(() -> rollCall.writeMissingNote(planId, xg, "  "))
                .isInstanceOf(BusinessException.class).hasMessageContaining("缺人说明");
        // 已勾回的人不需要写说明
        assertThatThrownBy(() -> rollCall.writeMissingNote(planId, xm, "他回来了"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("已勾回到校");

        // 写下缺人说明后可收口
        rolling = rollCall.writeMissingNote(planId, xg, "小刚由家长直接接走，未随车回校");
        assertThat(rolling.getMissingWithoutNoteCount()).isZero();
        assertThat(rolling.isClosable()).isTrue();

        RollCallResponse closed = rollCall.closeRoll(planId);
        assertThat(closed.getPhase()).isEqualTo("CLOSED");
        assertThat(closed.getClosedAt()).isNotBlank();
        assertThat(closed.getFrozenMissingNote()).contains("小刚").contains("家长直接接走");

        // 重复收口被拒
        assertThatThrownBy(() -> rollCall.closeRoll(planId))
                .isInstanceOf(BusinessException.class).hasMessageContaining("已收口");
    }

    // 4) 收口后一切冻住：勾选改不动、说明改不没、人塞不进来，重新读库仍是冻住状态
    @Test
    void afterClose_everythingFrozen_andPersistsAcrossReads() {
        Long planId = newDepartedPlanWithRoster("冻住线", List.of("小明", "小红"), "小红");

        RollCallResponse closed = rollCall.closeRoll(planId);
        Long xm = pid(closed, "小明");
        Long xh = pid(closed, "小红");
        String frozen = closed.getFrozenMissingNote();
        assertThat(frozen).contains("小红");

        // 不能取消已勾回的勾选
        assertThatThrownBy(() -> rollCall.markReturned(planId, xm, false))
                .isInstanceOf(BusinessException.class).hasMessageContaining("冻住");
        // 不能把没回的人勾回来
        assertThatThrownBy(() -> rollCall.markReturned(planId, xh, true))
                .isInstanceOf(BusinessException.class).hasMessageContaining("冻住");
        // 不能把缺人说明改没 / 改掉
        assertThatThrownBy(() -> rollCall.writeMissingNote(planId, xh, "小红其实回来了"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("冻住");
        // 不能再往车上塞人
        assertThatThrownBy(() -> rollCall.addParticipant(planId, "晚到的小芳"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("冻住");
        // 不能删人
        assertThatThrownBy(() -> rollCall.removeParticipant(planId, xm))
                .isInstanceOf(BusinessException.class).hasMessageContaining("冻住");

        // 模拟“关掉回程页再打开”：全部重新从库里读
        RollCallResponse reopened = rollCall.listAll().stream()
                .filter(r -> r.getPlanId().equals(planId)).findFirst().orElseThrow();
        assertThat(reopened.getPhase()).isEqualTo("CLOSED");
        assertThat(reopened.getFrozenMissingNote()).isEqualTo(frozen);
        assertThat(reopened.getParticipants()).filteredOn(ParticipantView::isReturned).hasSize(1);
        // 直接绕过服务层改库也不该发生；校验服务层没有留可写口子（状态仍冻）
        assertThat(reopened.isClosable()).isFalse();
    }

    // 5) 还没收口：勾选取决于落库——关掉页面再打开勾选还在，可以继续点直到收口
    @Test
    void beforeClose_checkmarksPersistAcrossReads() {
        Long planId = newDepartedPlanWithRoster("勾选持久线", List.of("甲", "乙", "丙"), null, false);
        RollCallResponse v = rollCall.get(planId);
        Long jia = pid(v, "甲");
        Long yi = pid(v, "乙");
        rollCall.markReturned(planId, jia, true);
        rollCall.markReturned(planId, yi, true);

        // 重新读：勾还在，未收口仍是可改状态
        RollCallResponse reopened = rollCall.get(planId);
        assertThat(reopened.getPhase()).isEqualTo("ROLLING");
        assertThat(reopened.getReturnedCount()).isEqualTo(2);
        assertThat(reopened.getParticipants()).filteredOn(ParticipantView::isReturned)
                .extracting(ParticipantView::getPersonName).containsExactly("甲", "乙");

        // 取消勾选也落库
        rollCall.markReturned(planId, jia, false);
        assertThat(rollCall.get(planId).getReturnedCount()).isEqualTo(1);

        // 剩下的点完即可全员收口
        rollCall.markReturned(planId, jia, true);
        Long bing = pid(reopened, "丙");
        rollCall.markReturned(planId, bing, true);
        RollCallResponse closed = rollCall.closeRoll(planId);
        assertThat(closed.getPhase()).isEqualTo("CLOSED");
        assertThat(closed.getFrozenMissingNote()).isNull(); // 全员回来，无缺员说明
    }

    // 6) 全员勾回不需要缺人说明，直接收口
    @Test
    void allReturned_closeWithoutNote() {
        Long planId = newDepartedPlanWithRoster("全员回线", List.of("独苗"), null);
        RollCallResponse v = rollCall.get(planId);
        rollCall.markReturned(planId, pid(v, "独苗"), true);
        RollCallResponse closed = rollCall.closeRoll(planId);
        assertThat(closed.getPhase()).isEqualTo("CLOSED");
        assertThat(closed.getReturnedCount()).isEqualTo(1);
        assertThat(closed.getMissingCount()).isZero();
    }

    // 7) 误勾回再取消：写过的缺人说明不丢，仍可继续用于收口
    @Test
    void accidentalUnmark_keepsMissingNote() {
        Long planId = newDepartedPlanWithRoster("说明保留线", List.of("甲"), null, false);
        RollCallResponse v = rollCall.get(planId);
        Long jia = pid(v, "甲");
        rollCall.writeMissingNote(planId, jia, "甲去洗手间没上车");
        assertThat(participantRepository.findById(jia).orElseThrow().getMissingNote()).contains("洗手间");

        // 误勾成已回：人不再算缺员，视图不带说明
        rollCall.markReturned(planId, jia, true);
        RollCallResponse backView = rollCall.get(planId);
        ParticipantView back = backView.getParticipants().get(0);
        assertThat(back.isReturned()).isTrue();
        assertThat(backView.getMissingCount()).isZero();

        // 取消勾选：原说明仍在，不需要重写即可收口
        rollCall.markReturned(planId, jia, false);
        RollCallResponse again = rollCall.get(planId);
        assertThat(again.getMissingWithoutNoteCount()).isZero();
        assertThat(again.isClosable()).isTrue();
        assertThat(pid(again, "甲")).isNotNull();
    }

    /** 建一趟已发车车：发车前把 roster 放上去；missingName 非空时该人保持未回，其他全勾回并写齐说明。 */
    private Long newDepartedPlanWithRoster(String name, List<String> roster, String missingName) {
        return newDepartedPlanWithRoster(name, roster, missingName, true);
    }

    /**
     * @param tickOthers missingName 非空时，是否先把其他人勾回；
     *                   missingName 为 null 时，是否先把全员勾回（false 则全部保持未回，留给用例自己点）
     */
    private Long newDepartedPlanWithRoster(String name, List<String> roster, String missingName, boolean tickOthers) {
        Long planId = planService.createPlan(RoutePlanRequest.builder()
                .planName(name + "-" + System.nanoTime())
                .startLocation("学校").endLocation("博物馆").waypoints(null)
                .travelDate(MAR).ageMin(30).ageMax(50).participantCount(20).build()).getId();
        planService.checkRisk(planId);
        Long s = scheduleService.createStaff(MedicalStaffRequest.builder()
                .staffName(name + "随队-" + System.nanoTime()).pediatricQualified(false).build()).getId();
        scheduleService.assign(AssignmentRequest.builder().planId(planId).staffIds(List.of(s)).build());
        releaseGate.submit(planId, null);
        for (String p : roster) {
            rollCall.addParticipant(planId, p);
        }
        releaseGate.depart(planId);
        if (!tickOthers) {
            return planId;
        }
        RollCallResponse v = rollCall.get(planId);
        for (String p : roster) {
            if (!p.equals(missingName)) {
                rollCall.markReturned(planId, pid(v, p), true);
            }
        }
        if (missingName != null) {
            rollCall.writeMissingNote(planId, pid(rollCall.get(planId), missingName),
                    missingName + " 未上车回校，已联系家长接走");
        }
        return planId;
    }
}
