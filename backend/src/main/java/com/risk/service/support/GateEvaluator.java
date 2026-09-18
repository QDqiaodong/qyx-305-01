package com.risk.service.support;

import com.risk.dto.response.CoverageInfo;
import com.risk.dto.response.ScreeningBook;
import com.risk.dto.response.StaffView;
import com.risk.entity.MedicalAssignment;
import com.risk.entity.MedicalStaff;
import com.risk.entity.RiskScreening;
import com.risk.entity.RoutePlan;
import com.risk.entity.RuleVersion;
import com.risk.repository.MedicalAssignmentRepository;
import com.risk.repository.MedicalStaffRepository;
import com.risk.repository.RiskScreeningRepository;
import com.risk.repository.RuleVersionRepository;
import com.risk.util.PlanFingerprint;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 发车放行要看的两本账的统一计算器：
 * 1) 筛查时效（{@link #buildScreeningBook}）
 * 2) 当天医护覆盖（{@link #buildCoverage}）
 *
 * 排班服务、放行服务、首页/报告都从这里取结论，保证口径一致。
 */
@Component
@RequiredArgsConstructor
public class GateEvaluator {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final RiskScreeningRepository screeningRepository;
    private final MedicalAssignmentRepository assignmentRepository;
    private final MedicalStaffRepository staffRepository;
    private final RuleVersionRepository ruleVersionRepository;

    /** 当前规则版本：规则每改一次 +1；筛查台账记的是筛查当时的版本。 */
    public long currentRuleVersion() {
        return ruleVersionRepository.findById(RuleVersion.SINGLETON_ID)
                .map(RuleVersion::getVersion)
                .orElse(1L);
    }

    /** 当前有效（行程指纹与规则版本都对得上）的最新筛查；无或已失效返回 empty。 */
    public Optional<RiskScreening> freshScreening(RoutePlan plan) {
        long ruleVersion = currentRuleVersion();
        return screeningRepository.findByPlanId(plan.getId())
                .filter(s -> s.getPlanFingerprint().equals(PlanFingerprint.of(plan)))
                .filter(s -> s.getRuleVersion() != null && s.getRuleVersion() == ruleVersion);
    }

    public ScreeningBook buildScreeningBook(RoutePlan plan) {
        String currentWaypoints = PlanFingerprint.normalizeWaypoints(plan.getWaypoints());
        long currentRuleVersion = currentRuleVersion();
        Optional<RiskScreening> opt = screeningRepository.findByPlanId(plan.getId());

        if (opt.isEmpty()) {
            return ScreeningBook.builder()
                    .exists(false)
                    .fresh(false)
                    .tripFresh(false)
                    .rulesFresh(false)
                    .currentRuleVersion(currentRuleVersion)
                    .currentTravelDate(String.valueOf(plan.getTravelDate()))
                    .currentParticipantCount(plan.getParticipantCount())
                    .currentAgeMin(plan.getAgeMin())
                    .currentAgeMax(plan.getAgeMax())
                    .currentWaypoints(currentWaypoints)
                    .mismatchReason("尚未进行风险筛查")
                    .build();
        }

        RiskScreening s = opt.get();
        boolean tripFresh = s.getPlanFingerprint().equals(PlanFingerprint.of(plan));
        boolean rulesFresh = s.getRuleVersion() != null && s.getRuleVersion() == currentRuleVersion;
        boolean fresh = tripFresh && rulesFresh;

        List<String> changed = new ArrayList<>();
        if (!eq(s.getSnapshotTravelDate(), String.valueOf(plan.getTravelDate()))) {
            changed.add("出行日期");
        }
        if (!eqInt(s.getSnapshotParticipantCount(), plan.getParticipantCount())) {
            changed.add("参与人数");
        }
        if (!eqInt(s.getSnapshotAgeMin(), plan.getAgeMin()) || !eqInt(s.getSnapshotAgeMax(), plan.getAgeMax())) {
            changed.add("年龄段");
        }
        if (!eq(s.getSnapshotWaypoints(), currentWaypoints)) {
            changed.add("途经点");
        }

        String mismatchReason = null;
        if (!fresh) {
            List<String> parts = new ArrayList<>();
            if (!tripFresh) {
                parts.add("行程已变更（" + String.join("、", changed) + "），筛查时所依据的行程与当前不一致");
            }
            if (!rulesFresh) {
                parts.add("风险规则已变更（该筛查基于规则版本 v" + s.getRuleVersion()
                        + "，当前为 v" + currentRuleVersion + "），旧结论按旧规则得出");
            }
            mismatchReason = String.join("；", parts) + "，旧筛查已失效";
        }

        return ScreeningBook.builder()
                .exists(true)
                .riskLevel(s.getRiskLevel())
                .screenedAt(s.getScreenedAt() == null ? null : s.getScreenedAt().format(DT))
                .fresh(fresh)
                .tripFresh(tripFresh)
                .rulesFresh(rulesFresh)
                .ruleVersion(s.getRuleVersion())
                .currentRuleVersion(currentRuleVersion)
                .snapshotTravelDate(s.getSnapshotTravelDate())
                .snapshotParticipantCount(s.getSnapshotParticipantCount())
                .snapshotAgeMin(s.getSnapshotAgeMin())
                .snapshotAgeMax(s.getSnapshotAgeMax())
                .snapshotWaypoints(s.getSnapshotWaypoints())
                .currentTravelDate(String.valueOf(plan.getTravelDate()))
                .currentParticipantCount(plan.getParticipantCount())
                .currentAgeMin(plan.getAgeMin())
                .currentAgeMax(plan.getAgeMax())
                .currentWaypoints(currentWaypoints)
                .mismatchReason(mismatchReason)
                .build();
    }

    public CoverageInfo buildCoverage(RoutePlan plan) {
        List<MedicalAssignment> assignments = assignmentRepository.findByPlanId(plan.getId());

        Map<Long, MedicalStaff> staffMap = staffRepository.findAllById(
                        assignments.stream().map(MedicalAssignment::getStaffId).collect(Collectors.toList()))
                .stream().collect(Collectors.toMap(MedicalStaff::getId, s -> s));

        List<StaffView> staffViews = new ArrayList<>();
        List<MedicalStaff> onDuty = new ArrayList<>();

        for (MedicalAssignment a : assignments) {
            MedicalStaff staff = staffMap.get(a.getStaffId());
            if (staff == null) {
                continue;
            }
            boolean dateMatched = plan.getTravelDate() != null && plan.getTravelDate().equals(a.getTravelDate());
            staffViews.add(StaffView.builder()
                    .assignmentId(a.getId())
                    .staffId(staff.getId())
                    .staffName(staff.getStaffName())
                    .title(staff.getTitle())
                    .phone(staff.getPhone())
                    .certificateNo(staff.getCertificateNo())
                    .pediatricQualified(staff.getPediatricQualified() != null && staff.getPediatricQualified() == 1)
                    .dateMatched(dateMatched)
                    .assignmentDate(a.getTravelDate() == null ? null : a.getTravelDate().toString())
                    .build());
            if (dateMatched) {
                onDuty.add(staff);
            }
        }

        // 覆盖要求以“最新一次筛查结论”为准；还没筛过则退回计划状态判断
        String basedRiskLevel = screeningRepository.findByPlanId(plan.getId())
                .map(RiskScreening::getRiskLevel)
                .orElse(plan.getStatus());
        boolean highRisk = "HIGH_RISK".equals(basedRiskLevel);

        int requiredCount = highRisk ? 2 : 1;
        boolean pediatricRequired = plan.getAgeMin() != null && plan.getAgeMin() <= 8;

        int coveredCount = onDuty.size();
        boolean countSatisfied = coveredCount >= requiredCount;
        boolean pediatricSatisfied = !pediatricRequired
                || onDuty.stream().anyMatch(s -> s.getPediatricQualified() != null && s.getPediatricQualified() == 1);

        List<String> reasons = new ArrayList<>();
        if (coveredCount == 0) {
            reasons.add("出行日尚未安排任何随队医护");
        } else if (!countSatisfied) {
            reasons.add("当前筛查结论为高风险，必须双人值班（已安排 " + coveredCount + " 人，需 " + requiredCount + " 人）");
        }
        if (!dateMatchedAll(staffViews)) {
            reasons.add("存在排班日期与计划当前出行日期不一致的医护（计划改期后需重新排班）");
        }
        if (pediatricRequired && !pediatricSatisfied) {
            reasons.add("队伍最小年龄不超过 8 岁，至少须有一名儿科资质医护在岗");
        }

        return CoverageInfo.builder()
                .staff(staffViews)
                .coveredCount(coveredCount)
                .requiredCount(requiredCount)
                .countSatisfied(countSatisfied)
                .pediatricRequired(pediatricRequired)
                .pediatricSatisfied(pediatricSatisfied)
                .satisfied(countSatisfied && pediatricSatisfied && dateMatchedAll(staffViews))
                .basedRiskLevel(basedRiskLevel)
                .reasons(reasons)
                .build();
    }

    private boolean dateMatchedAll(List<StaffView> views) {
        return views.stream().allMatch(StaffView::isDateMatched);
    }

    private boolean eq(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    private boolean eqInt(Integer a, Integer b) {
        int aa = a == null ? 0 : a;
        int bb = b == null ? 0 : b;
        return aa == bb;
    }
}
