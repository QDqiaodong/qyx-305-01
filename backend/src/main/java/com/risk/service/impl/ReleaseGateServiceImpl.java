package com.risk.service.impl;

import com.risk.dto.request.ReleaseRequest;
import com.risk.dto.response.CoverageInfo;
import com.risk.dto.response.DashboardStats;
import com.risk.dto.response.ReleasePermitResponse;
import com.risk.dto.response.ScreeningBook;
import com.risk.entity.ReleasePermit;
import com.risk.entity.RoutePlan;
import com.risk.exception.BusinessException;
import com.risk.repository.MedicalAssignmentRepository;
import com.risk.repository.MedicalStaffRepository;
import com.risk.repository.ReleasePermitRepository;
import com.risk.repository.RoutePlanRepository;
import com.risk.service.ReleaseGateService;
import com.risk.service.support.GateEvaluator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReleaseGateServiceImpl implements ReleaseGateService {

    public static final String PENDING = "PENDING";
    public static final String RELEASED = "RELEASED";
    public static final String VOID = "VOID";
    public static final String STALE_RECHECK = "STALE_RECHECK";

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final RoutePlanRepository planRepository;
    private final ReleasePermitRepository permitRepository;
    private final MedicalStaffRepository staffRepository;
    private final MedicalAssignmentRepository assignmentRepository;
    private final GateEvaluator gateEvaluator;

    @Override
    @Transactional
    public ReleasePermitResponse evaluate(Long planId) {
        RoutePlan plan = mustGetPlan(planId);
        return buildResponse(plan);
    }

    @Override
    @Transactional
    public ReleasePermitResponse submit(Long planId, ReleaseRequest request) {
        RoutePlan plan = mustGetPlan(planId);
        ReleasePermitResponse view = buildResponse(plan);

        // 已发车的单据已封存，不能再提交放行改写
        if (view.getDepartedAt() != null) {
            throw new BusinessException("该计划已于 " + view.getDepartedAt() + " 发车，出门条已封存，不能再提交放行");
        }

        // 两道闸 + 安全岗硬规则：任何一条不过都失败，且不改既有放行状态
        if (!view.getBlockReasons().isEmpty()) {
            throw new BusinessException("放行失败，该计划当前不满足放行条件：\n- "
                    + String.join("\n- ", view.getBlockReasons()));
        }

        ScreeningBook screening = view.getScreening();
        String level = screening.getRiskLevel();
        String note = request != null && request.getInformedNote() != null ? request.getInformedNote().trim() : "";

        // 知情备注只能落在中风险单上
        if (!note.isEmpty() && !"MEDIUM_RISK".equals(level)) {
            throw new BusinessException("放行失败：知情备注只允许写在中风险单上，低风险无需备注、高风险不得放行");
        }

        ReleasePermit permit = permitRepository.findByPlanId(planId).orElseGet(() ->
                ReleasePermit.builder().planId(planId).build());
        permit.setStatus(RELEASED);
        permit.setRiskLevel(level);
        permit.setInformedNote("MEDIUM_RISK".equals(level) ? note : null);
        permit.setReleasedAt(LocalDateTime.now());
        permit.setVoidReason(null);
        permit.setRecheckReason(null);
        permitRepository.save(permit);

        log.info("计划 {} 放行成功，依据筛查等级 {}", planId, level);
        return buildResponse(plan);
    }

    @Override
    @Transactional
    public ReleasePermitResponse manualVoid(Long planId, String reason) {
        RoutePlan plan = mustGetPlan(planId);
        ReleasePermit permit = permitRepository.findByPlanId(planId).orElseGet(() ->
                ReleasePermit.builder().planId(planId).build());
        if (permit.getDepartedAt() != null) {
            throw new BusinessException("该计划已于 " + permit.getDepartedAt().format(DT)
                    + " 发车，出门条已封存，不能再作废；已发车的纸面保持当时那一版");
        }
        permit.setStatus(VOID);
        permit.setVoidReason((reason == null || reason.isBlank()) ? "人工作废" : reason.trim());
        permit.setRecheckReason(null);
        permitRepository.save(permit);
        return buildResponse(plan);
    }

    @Override
    @Transactional
    public ReleasePermitResponse depart(Long planId) {
        RoutePlan plan = mustGetPlan(planId);
        // 先做一次实时兜底核对：两本账已经对不上的已放行单会先落档，拦在发车前
        ReleasePermitResponse view = buildResponse(plan);
        if (view.getDepartedAt() != null) {
            throw new BusinessException("该计划已于 " + view.getDepartedAt() + " 记下发车时刻，不能重复发车");
        }
        if (!RELEASED.equals(view.getStatus())) {
            throw new BusinessException("只有「已放行」的出门条才能登记发车时刻，当前为「" + view.getStatusText() + "」");
        }
        ReleasePermit permit = permitRepository.findByPlanId(planId)
                .orElseThrow(() -> new BusinessException("该计划还没有放行单，不能登记发车"));
        permit.setDepartedAt(LocalDateTime.now());
        permitRepository.save(permit);
        log.info("计划 {} 于 {} 发车，出门条 {} 封存，之后的规则调整不再改写",
                planId, permit.getDepartedAt().format(DT), permit.getId());
        return buildResponse(plan);
    }

    @Override
    public void assertNotDeparted(Long planId) {
        permitRepository.findByPlanId(planId)
                .filter(p -> p.getDepartedAt() != null)
                .ifPresent(p -> {
                    throw new BusinessException("该计划已于 " + p.getDepartedAt().format(DT)
                            + " 发车，单据已封存，不能再重新筛查改写结论；已发车的纸面保持当时那一版");
                });
    }

    @Override
    @Transactional
    public List<ReleasePermitResponse> listAll() {
        List<ReleasePermitResponse> all = new ArrayList<>();
        for (RoutePlan plan : planRepository.findAll()) {
            all.add(buildResponse(plan));
        }
        return all;
    }

    @Override
    @Transactional
    public DashboardStats stats() {
        List<RoutePlan> plans = planRepository.findAll();
        List<ReleasePermitResponse> permits = listAll();

        long staffCount = staffRepository.count();
        long pediatricCount = staffRepository.findByPediatricQualified(1).size();
        long assignmentCount = assignmentRepository.count();

        return DashboardStats.builder()
                .totalPlanCount(plans.size())
                .highRiskCount(plans.stream().filter(p -> "HIGH_RISK".equals(p.getStatus())).count())
                .mediumRiskCount(plans.stream().filter(p -> "MEDIUM_RISK".equals(p.getStatus())).count())
                .lowRiskCount(plans.stream().filter(p -> "LOW_RISK".equals(p.getStatus())).count())
                .pendingScreeningCount(plans.stream().filter(p -> "PENDING".equals(p.getStatus())).count())
                .releasedCount(permits.stream().filter(p -> RELEASED.equals(p.getStatus())).count())
                .pendingDocsCount(permits.stream().filter(p -> PENDING.equals(p.getStatus())).count())
                .voidCount(permits.stream().filter(p -> VOID.equals(p.getStatus())).count())
                .staleRecheckCount(permits.stream().filter(p -> STALE_RECHECK.equals(p.getStatus())).count())
                .medicalStaffCount(staffCount)
                .pediatricStaffCount(pediatricCount)
                .assignmentCount(assignmentCount)
                .build();
    }

    // ===================== 联动钩子 =====================

    @Override
    @Transactional
    public void onTripChanged(RoutePlan plan) {
        permitRepository.findByPlanId(plan.getId()).ifPresent(permit -> {
            if (permit.getDepartedAt() != null) {
                // 已发车：纸面保持当时那一版，行程的事后改动不再改写这张单
                return;
            }
            if (VOID.equals(permit.getStatus())) {
                permit.setVoidReason(tripVoidReason());
                permitRepository.save(permit);
                return;
            }
            permit.setStatus(VOID);
            permit.setVoidReason(tripVoidReason());
            permit.setRecheckReason(null);
            permitRepository.save(permit);
            log.info("计划 {} 行程变更，已发放行单 {} 作废", plan.getId(), permit.getId());
        });
    }

    @Override
    @Transactional
    public void onAssignmentsChanged(RoutePlan plan, List<String> removedStaffNames) {
        Optional<ReleasePermit> opt = permitRepository.findByPlanId(plan.getId());
        if (opt.isEmpty() || !RELEASED.equals(opt.get().getStatus()) || opt.get().getDepartedAt() != null) {
            return;
        }
        CoverageInfo coverage = gateEvaluator.buildCoverage(plan);
        if (!coverage.isSatisfied()) {
            ReleasePermit permit = opt.get();
            permit.setStatus(STALE_RECHECK);
            String who = removedStaffNames == null || removedStaffNames.isEmpty()
                    ? "排班调整"
                    : "医护 " + String.join("、", removedStaffNames) + " 被抽离";
            permit.setRecheckReason(who + "，当天医护覆盖不再满足（"
                    + String.join("；", coverage.getReasons()) + "），原放行单待重评，不可继续按已放行出车");
            permit.setVoidReason(null);
            permitRepository.save(permit);
            log.info("计划 {} 医护覆盖不再满足，放行单 {} 落到待重评", plan.getId(), permit.getId());
        }
    }

    @Override
    @Transactional
    public void onScreeningFinished(RoutePlan plan, String newRiskLevel) {
        Optional<ReleasePermit> opt = permitRepository.findByPlanId(plan.getId());
        if (opt.isEmpty() || !RELEASED.equals(opt.get().getStatus()) || opt.get().getDepartedAt() != null) {
            return;
        }
        ReleasePermit permit = opt.get();
        if (permit.getRiskLevel() != null && !permit.getRiskLevel().equals(newRiskLevel)) {
            permit.setStatus(STALE_RECHECK);
            String base = "重新筛查后风险等级由 " + levelText(permit.getRiskLevel())
                    + " 变为 " + levelText(newRiskLevel) + "，原放行依据失效，放行单待重评";
            if ("HIGH_RISK".equals(newRiskLevel)) {
                base += "；当前为高风险，须改线或关闭触发规则后重新筛查，方可重新放行";
            }
            permit.setRecheckReason(base);
            permit.setVoidReason(null);
            permitRepository.save(permit);
            log.info("计划 {} 重筛等级 {} -> {}，放行单 {} 落到待重评",
                    plan.getId(), permit.getRiskLevel(), newRiskLevel, permit.getId());
        }
    }

    @Override
    @Transactional
    public void onRulesChanged() {
        // 规则一变，所有“按旧规则筛查放行”的未发车出门条一律作废并停在待重筛；
        // 已发车的纸面不动。逐条落库（而非只改缓存），保证两个调度同时改规则时
        // 未发车计划只能一起落到待重筛，不会一张作废一张仍绿灯。
        List<ReleasePermit> released = permitRepository.findByStatus(RELEASED).stream()
                .sorted(Comparator.comparing(ReleasePermit::getId))
                .toList();
        int dropped = 0;
        for (ReleasePermit permit : released) {
            if (permit.getDepartedAt() != null) {
                continue;
            }
            permit.setStatus(STALE_RECHECK);
            permit.setRecheckReason("风险规则已变更，旧筛查结论按旧规则得出，出门条作废停在待重筛："
                    + "须按新规则重新筛查，两本账齐后重新放行");
            permit.setVoidReason(null);
            permitRepository.save(permit);
            dropped++;
        }
        if (dropped > 0) {
            log.info("风险规则变更，{} 张未发车出门条落到待重筛", dropped);
        }
    }

    // ===================== 内部 =====================

    private ReleasePermitResponse buildResponse(RoutePlan plan) {
        ScreeningBook screening = gateEvaluator.buildScreeningBook(plan);
        CoverageInfo coverage = gateEvaluator.buildCoverage(plan);

        ReleasePermit permit = permitRepository.findByPlanId(plan.getId()).orElse(null);
        boolean departed = permit != null && permit.getDepartedAt() != null;

        // 已放行且未发车的单做一次实时兜底核对：一旦两本账对不上，立即落档，绝不允许停在“可出门”。
        // 已发车的单据已封存，不再核对、不再改写。
        if (permit != null && RELEASED.equals(permit.getStatus()) && !departed) {
            reconcileReleased(permit, screening, coverage);
        }

        String effectiveStatus;
        String statusText;
        String voidReason = null;
        String recheckReason = null;
        String releasedAt = null;
        String departedAt = null;
        String informedNote = null;
        String riskLevel = null;
        Long permitId = null;

        if (permit == null) {
            effectiveStatus = PENDING;
        } else {
            effectiveStatus = permit.getStatus();
            voidReason = permit.getVoidReason();
            recheckReason = permit.getRecheckReason();
            releasedAt = permit.getReleasedAt() == null ? null : permit.getReleasedAt().format(DT);
            departedAt = permit.getDepartedAt() == null ? null : permit.getDepartedAt().format(DT);
            informedNote = permit.getInformedNote();
            riskLevel = permit.getRiskLevel();
            permitId = permit.getId();
        }
        statusText = departed ? "已发车" : statusText(effectiveStatus);

        // 当前实时拦截原因（决定 releasable，与单据是否已作废无关）。
        // 已发车的单据不再参与实时拦截：纸面保持出门当时那一版。
        List<String> blockReasons = new ArrayList<>();
        if (!departed) {
            if (!screening.isFresh()) {
                blockReasons.add(screening.getMismatchReason());
            }
            if (!coverage.isSatisfied()) {
                blockReasons.addAll(coverage.getReasons());
            }
            if (screening.isFresh() && "HIGH_RISK".equals(screening.getRiskLevel())) {
                blockReasons.add("最新筛查结论为高风险，安全岗不予放行（知情备注不能改变高风险结论）：必须改线或关闭触发规则后重新筛查，再申请放行");
            }
        }
        boolean releasable = !departed && blockReasons.isEmpty();

        // 已放行单若仍有效，放行等级以筛查为准；已发车的单保持出门当时记下的等级
        if (RELEASED.equals(effectiveStatus) && !departed) {
            riskLevel = screening.getRiskLevel();
        }

        return ReleasePermitResponse.builder()
                .permitId(permitId)
                .planId(plan.getId())
                .planName(plan.getPlanName())
                .status(effectiveStatus)
                .statusText(statusText)
                .riskLevel(riskLevel)
                .informedNote(informedNote)
                .releasedAt(releasedAt)
                .departedAt(departedAt)
                .voidReason(voidReason)
                .recheckReason(recheckReason)
                .screening(screening)
                .coverage(coverage)
                .releasable(releasable)
                .blockReasons(blockReasons)
                .build();
    }

    private void reconcileReleased(ReleasePermit permit, ScreeningBook screening, CoverageInfo coverage) {
        if (!screening.isExists() || !screening.isTripFresh()) {
            // 行程变了（或筛查缺失）：旧单直接作废
            permit.setStatus(VOID);
            permit.setVoidReason(tripVoidReason());
            permit.setRecheckReason(null);
            permitRepository.save(permit);
            return;
        }
        if (!screening.isRulesFresh()) {
            // 行程没变、规则变了：出门条作废并停在待重筛，按新规则重筛后才可再放行
            permit.setStatus(STALE_RECHECK);
            permit.setRecheckReason("风险规则已变更，旧筛查结论按旧规则得出，出门条作废停在待重筛："
                    + "须按新规则重新筛查，两本账齐后重新放行");
            permit.setVoidReason(null);
            permitRepository.save(permit);
            return;
        }
        String currentLevel = screening.getRiskLevel();
        if (permit.getRiskLevel() != null && !permit.getRiskLevel().equals(currentLevel)) {
            permit.setStatus(STALE_RECHECK);
            String base = "重新筛查后风险等级由 " + levelText(permit.getRiskLevel())
                    + " 变为 " + levelText(currentLevel) + "，原放行依据失效，放行单待重评";
            if ("HIGH_RISK".equals(currentLevel)) {
                base += "；当前为高风险，须改线或关闭触发规则后重新筛查，方可重新放行";
            }
            permit.setRecheckReason(base);
            permit.setVoidReason(null);
            permitRepository.save(permit);
            return;
        }
        if (!coverage.isSatisfied()) {
            permit.setStatus(STALE_RECHECK);
            permit.setRecheckReason("当天医护覆盖不再满足（"
                    + String.join("；", coverage.getReasons()) + "），原放行单待重评，不可继续按已放行出车");
            permit.setVoidReason(null);
            permitRepository.save(permit);
        }
    }

    private String tripVoidReason() {
        return "行程（出行日期 / 参与人数 / 年龄段 / 途经点）已变更，旧筛查与旧报告不再对得上当前行程，放行单作废，须重新筛查";
    }

    private RoutePlan mustGetPlan(Long planId) {
        return planRepository.findById(planId)
                .orElseThrow(() -> new BusinessException("路线计划不存在: " + planId));
    }

    static String statusText(String status) {
        return switch (status) {
            case PENDING -> "待齐件";
            case RELEASED -> "已放行";
            case VOID -> "已作废";
            case STALE_RECHECK -> "筛查失效待重评";
            default -> status;
        };
    }

    static String levelText(String level) {
        return switch (level) {
            case "HIGH_RISK" -> "高风险";
            case "MEDIUM_RISK" -> "中风险";
            case "LOW_RISK" -> "低风险";
            default -> String.valueOf(level);
        };
    }
}
