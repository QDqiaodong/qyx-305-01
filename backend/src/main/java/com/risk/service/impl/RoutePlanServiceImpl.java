package com.risk.service.impl;

import com.alibaba.fastjson.JSON;
import com.risk.dto.request.RoutePlanRequest;
import com.risk.dto.response.ReleasePermitResponse;
import com.risk.dto.response.ReportResponse;
import com.risk.dto.response.RiskCheckResponse;
import com.risk.dto.response.ScreeningBook;
import com.risk.entity.RiskCheckResult;
import com.risk.entity.RiskRule;
import com.risk.entity.RoutePlan;
import com.risk.repository.MedicalAssignmentRepository;
import com.risk.repository.ReleasePermitRepository;
import com.risk.repository.RiskCheckResultRepository;
import com.risk.repository.RiskRuleRepository;
import com.risk.repository.RiskScreeningRepository;
import com.risk.repository.RoutePlanRepository;
import com.risk.repository.TripParticipantRepository;
import com.risk.service.ReleaseGateService;
import com.risk.service.RoutePlanService;
import com.risk.service.support.GateEvaluator;
import com.risk.util.PlanFingerprint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoutePlanServiceImpl implements RoutePlanService {

    private final RoutePlanRepository planRepository;
    private final RiskCheckResultRepository checkResultRepository;
    private final RiskRuleRepository ruleRepository;
    private final RiskScreeningRepository screeningRepository;
    private final MedicalAssignmentRepository assignmentRepository;
    private final ReleasePermitRepository permitRepository;
    private final TripParticipantRepository participantRepository;
    private final RiskEngineServiceImpl riskEngineService;
    private final GateEvaluator gateEvaluator;
    private final ReleaseGateService releaseGate;

    @Override
    @Transactional
    public RoutePlan createPlan(RoutePlanRequest request) {
        String waypointsStr = null;
        if (request.getWaypoints() != null && !request.getWaypoints().isEmpty()) {
            waypointsStr = String.join(",", request.getWaypoints());
        }

        RoutePlan plan = RoutePlan.builder()
                .planName(request.getPlanName())
                .startLocation(request.getStartLocation())
                .endLocation(request.getEndLocation())
                .waypoints(waypointsStr)
                .travelDate(request.getTravelDate())
                .ageMin(request.getAgeMin() != null ? request.getAgeMin() : 0)
                .ageMax(request.getAgeMax() != null ? request.getAgeMax() : 100)
                .participantCount(request.getParticipantCount() != null ? request.getParticipantCount() : 0)
                .status("PENDING")
                .build();

        return planRepository.save(plan);
    }

    @Override
    public RoutePlan getPlanById(Long id) {
        return planRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("路线计划不存在: " + id));
    }

    @Override
    public List<RoutePlan> getAllPlans() {
        return planRepository.findAll();
    }

    @Override
    @Transactional
    public RoutePlan updatePlan(Long id, RoutePlanRequest request) {
        RoutePlan plan = getPlanById(id);

        String waypointsStr = null;
        if (request.getWaypoints() != null && !request.getWaypoints().isEmpty()) {
            waypointsStr = String.join(",", request.getWaypoints());
        }

        Integer newAgeMin = request.getAgeMin() != null ? request.getAgeMin() : 0;
        Integer newAgeMax = request.getAgeMax() != null ? request.getAgeMax() : 100;
        Integer newCount = request.getParticipantCount() != null ? request.getParticipantCount() : 0;

        // 出门凭证只认四件事：日期、人数、年龄段、途经点。任一变化都让旧筛查/旧报告失效。
        boolean tripChanged =
                !Objects.equals(plan.getTravelDate(), request.getTravelDate())
                        || !Objects.equals(plan.getParticipantCount(), newCount)
                        || !Objects.equals(plan.getAgeMin(), newAgeMin)
                        || !Objects.equals(plan.getAgeMax(), newAgeMax)
                        || !Objects.equals(
                                PlanFingerprint.normalizeWaypoints(plan.getWaypoints()),
                                PlanFingerprint.normalizeWaypoints(waypointsStr));

        plan.setPlanName(request.getPlanName());
        plan.setStartLocation(request.getStartLocation());
        plan.setEndLocation(request.getEndLocation());
        plan.setWaypoints(waypointsStr);
        plan.setTravelDate(request.getTravelDate());
        plan.setAgeMin(newAgeMin);
        plan.setAgeMax(newAgeMax);
        plan.setParticipantCount(newCount);

        RoutePlan saved = planRepository.save(plan);

        if (tripChanged) {
            // 行程变了：旧筛查/旧报告立即作废，不能再当出门凭证；已发出的放行单落到“已作废（行程变了）”
            log.info("计划 {} 行程关键字段变更，旧筛查/报告失效，作废已发出的放行单", id);
            releaseGate.onTripChanged(saved);
        }

        return saved;
    }

    @Override
    @Transactional
    public void deletePlan(Long id) {
        if (!planRepository.existsById(id)) {
            throw new RuntimeException("路线计划不存在: " + id);
        }
        checkResultRepository.deleteByPlanId(id);
        screeningRepository.deleteByPlanId(id);
        assignmentRepository.deleteByPlanId(id);
        participantRepository.deleteAll(participantRepository.findByPlanIdOrderByIdAsc(id));
        permitRepository.findByPlanId(id).ifPresent(permitRepository::delete);
        planRepository.deleteById(id);
    }

    @Override
    public RiskCheckResponse checkRisk(Long planId) {
        return riskEngineService.evaluatePlan(planId);
    }

    @Override
    public ReportResponse generateReport(Long planId) {
        RoutePlan plan = getPlanById(planId);
        
        List<RiskCheckResult> results = checkResultRepository.findByPlanId(planId);
        
        List<String> waypointsList = new ArrayList<>();
        if (plan.getWaypoints() != null && !plan.getWaypoints().isEmpty()) {
            waypointsList = Arrays.asList(plan.getWaypoints().split(","))
                    .stream()
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }

        int highCount = (int) results.stream().filter(r -> "HIGH".equals(r.getRiskLevel())).count();
        int mediumCount = (int) results.stream().filter(r -> "MEDIUM".equals(r.getRiskLevel())).count();
        int lowCount = (int) results.stream().filter(r -> "LOW".equals(r.getRiskLevel())).count();

        List<ReportResponse.RiskDetail> riskDetails = results.stream()
                .map(r -> ReportResponse.RiskDetail.builder()
                        .location(r.getLocation())
                        .riskLevel(r.getRiskLevel())
                        .ruleName(getRuleNameById(r.getRuleId()))
                        .riskMessage(r.getRiskMessage())
                        .riskType(getRuleTypeById(r.getRuleId()))
                        .build())
                .collect(Collectors.toList());

        // 第一本账：这份报告背后的筛查是否仍对得上当前行程与当前规则
        ScreeningBook screeningBook = gateEvaluator.buildScreeningBook(plan);
        // 当前放行状态（报告页要跟着变；与放行看板读同一份结论）
        ReleasePermitResponse permit = releaseGate.evaluate(planId);

        // 已记下发车时刻的单据：报告保持出门当时那一版，之后的规则调整不再改写
        boolean departed = permit.getDepartedAt() != null;
        String overallStatus = departed && permit.getRiskLevel() != null
                ? permit.getRiskLevel()
                : plan.getStatus();
        boolean screeningValid = departed || (screeningBook.isExists() && screeningBook.isFresh());
        String mismatchReason = departed ? null : screeningBook.getMismatchReason();

        return ReportResponse.builder()
                .reportId(UUID.randomUUID().toString())
                .planId(planId)
                .planName(plan.getPlanName())
                .startLocation(plan.getStartLocation())
                .endLocation(plan.getEndLocation())
                .waypoints(waypointsList)
                .travelDate(plan.getTravelDate())
                .ageMin(plan.getAgeMin())
                .ageMax(plan.getAgeMax())
                .participantCount(plan.getParticipantCount())
                .generatedAt(LocalDateTime.now())
                .overallStatus(overallStatus)
                .screened(screeningBook.isExists())
                .screeningValid(screeningValid)
                .screeningMismatchReason(mismatchReason)
                .permitStatus(permit.getStatus())
                .permitStatusText(permit.getStatusText())
                .permitBlockReason(permit.getBlockReasons().isEmpty()
                        ? null : String.join("；", permit.getBlockReasons()))
                .departed(departed)
                .departedAt(permit.getDepartedAt())
                .riskSummary(ReportResponse.RiskSummary.builder()
                        .highCount(highCount)
                        .mediumCount(mediumCount)
                        .lowCount(lowCount)
                        .totalCount(results.size())
                        .build())
                .riskDetails(riskDetails)
                .build();
    }

    private String getRuleNameById(Long ruleId) {
        return ruleRepository.findById(ruleId)
                .map(RiskRule::getRuleName)
                .orElse("未知规则");
    }

    private String getRuleTypeById(Long ruleId) {
        return ruleRepository.findById(ruleId)
                .map(RiskRule::getRuleType)
                .orElse("UNKNOWN");
    }
}