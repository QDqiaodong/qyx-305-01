package com.risk.service.impl;

import com.risk.entity.RiskCheckResult;
import com.risk.entity.RiskRule;
import com.risk.entity.RiskScreening;
import com.risk.entity.RoutePlan;
import com.risk.repository.RiskCheckResultRepository;
import com.risk.repository.RiskScreeningRepository;
import com.risk.repository.RoutePlanRepository;
import com.risk.dto.response.RiskCheckResponse;
import com.risk.service.ReleaseGateService;
import com.risk.service.RiskEngineService;
import com.risk.service.RiskRuleService;
import com.risk.service.support.GateEvaluator;
import com.risk.util.PlanFingerprint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RiskEngineServiceImpl implements RiskEngineService {

    private final RoutePlanRepository planRepository;
    private final RiskRuleService ruleService;
    private final RiskCheckResultRepository checkResultRepository;
    private final RiskScreeningRepository screeningRepository;
    private final ReleaseGateService releaseGate;
    private final GateEvaluator gateEvaluator;

    @Override
    @Transactional
    public RiskCheckResponse evaluatePlan(Long planId) {
        RoutePlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new RuntimeException("路线计划不存在: " + planId));

        // 已发车的单据已封存：不允许再重筛改写出门当时那一版
        releaseGate.assertNotDeparted(planId);

        checkResultRepository.deleteByPlanId(planId);

        List<RiskRule> enabledRules = ruleService.getEnabledRules();
        
        List<RiskCheckResponse.RiskItem> allRiskItems = new ArrayList<>();
        
        List<String> allLocations = getAllLocations(plan);
        for (String location : allLocations) {
            for (RiskRule rule : enabledRules) {
                if (evaluateRule(rule, plan, location)) {
                    RiskCheckResponse.RiskItem item = RiskCheckResponse.RiskItem.builder()
                            .ruleId(rule.getId())
                            .ruleCode(rule.getRuleCode())
                            .ruleName(rule.getRuleName())
                            .ruleType(rule.getRuleType())
                            .riskLevel(rule.getRiskLevel())
                            .riskMessage(rule.getWarningMessage())
                            .location(location)
                            .build();
                    allRiskItems.add(item);
                    
                    RiskCheckResult result = RiskCheckResult.builder()
                            .planId(planId)
                            .ruleId(rule.getId())
                            .riskLevel(rule.getRiskLevel())
                            .riskMessage(rule.getWarningMessage())
                            .location(location)
                            .build();
                    checkResultRepository.save(result);
                }
            }
        }

        String level = generateStatus(allRiskItems);
        plan.setStatus(level);
        planRepository.save(plan);

        // 台账（第一本账）：每次筛查都按当前行程重录指纹、记下当前规则版本；旧指纹/旧版本自此作废
        RiskScreening screening = screeningRepository.findByPlanId(planId)
                .orElseGet(() -> RiskScreening.builder().planId(planId).build());
        screening.setRiskLevel(level);
        screening.setScreenedAt(LocalDateTime.now());
        screening.setPlanFingerprint(PlanFingerprint.of(plan));
        screening.setRuleVersion(gateEvaluator.currentRuleVersion());
        screening.setSnapshotTravelDate(String.valueOf(plan.getTravelDate()));
        screening.setSnapshotParticipantCount(plan.getParticipantCount());
        screening.setSnapshotAgeMin(plan.getAgeMin());
        screening.setSnapshotAgeMax(plan.getAgeMax());
        screening.setSnapshotWaypoints(PlanFingerprint.normalizeWaypoints(plan.getWaypoints()));
        screeningRepository.save(screening);

        // 已放行单若所依据的等级发生升降，落到“筛查失效待重评”
        releaseGate.onScreeningFinished(plan, level);

        return buildRiskCheckResponse(plan, allRiskItems);
    }

    @Override
    public List<RiskCheckResponse.RiskItem> evaluateAgainstRules(RoutePlan plan, List<RiskRule> rules) {
        List<RiskCheckResponse.RiskItem> riskItems = new ArrayList<>();
        List<String> allLocations = getAllLocations(plan);
        
        for (String location : allLocations) {
            for (RiskRule rule : rules) {
                if (evaluateRule(rule, plan, location)) {
                    riskItems.add(RiskCheckResponse.RiskItem.builder()
                            .ruleId(rule.getId())
                            .ruleCode(rule.getRuleCode())
                            .ruleName(rule.getRuleName())
                            .ruleType(rule.getRuleType())
                            .riskLevel(rule.getRiskLevel())
                            .riskMessage(rule.getWarningMessage())
                            .location(location)
                            .build());
                }
            }
        }
        
        return riskItems;
    }

    @Override
    public boolean evaluateRule(RiskRule rule, RoutePlan plan, String location) {
        String expression = rule.getConditionExpression();
        String ruleType = rule.getRuleType();
        
        try {
            return switch (ruleType) {
                case "WEATHER" -> evaluateWeatherRule(expression, plan);
                case "TRAFFIC" -> evaluateTrafficRule(expression, plan);
                case "AGE" -> evaluateAgeRule(expression, plan);
                case "VENUE" -> evaluateVenueRule(expression, plan, location);
                default -> evaluateCustomRule(expression, plan, location);
            };
        } catch (Exception e) {
            log.warn("规则评估失败: ruleCode={}, error={}", rule.getRuleCode(), e.getMessage());
            return false;
        }
    }

    private boolean evaluateWeatherRule(String expression, RoutePlan plan) {
        DayOfWeek dayOfWeek = plan.getTravelDate().getDayOfWeek();
        
        if (expression.contains("RAINY_SEASON") && isRainySeason(plan.getTravelDate().getMonthValue())) {
            return true;
        }
        
        if (expression.contains("HOT_WEATHER") && plan.getTravelDate().getMonthValue() >= 6 && plan.getTravelDate().getMonthValue() <= 8) {
            return true;
        }
        
        if (expression.contains("COLD_WEATHER") && (plan.getTravelDate().getMonthValue() >= 12 || plan.getTravelDate().getMonthValue() <= 2)) {
            return true;
        }
        
        if (expression.contains("WEEKEND") && (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY)) {
            return true;
        }
        
        if (expression.contains("HOLIDAY") && isHoliday(plan.getTravelDate().getMonthValue(), plan.getTravelDate().getDayOfMonth())) {
            return true;
        }
        
        return false;
    }

    private boolean evaluateTrafficRule(String expression, RoutePlan plan) {
        DayOfWeek dayOfWeek = plan.getTravelDate().getDayOfWeek();
        
        if (expression.contains("PEAK_HOUR") && (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY)) {
            return true;
        }
        
        if (expression.contains("LONG_DISTANCE")) {
            return true;
        }
        
        if (expression.contains("MOUNTAIN_ROAD") && (plan.getStartLocation().contains("山") || plan.getEndLocation().contains("山") || 
            (plan.getWaypoints() != null && plan.getWaypoints().contains("山")))) {
            return true;
        }
        
        if (expression.contains("COASTAL_ROAD") && (plan.getStartLocation().contains("海") || plan.getEndLocation().contains("海") ||
            (plan.getWaypoints() != null && plan.getWaypoints().contains("海")))) {
            return true;
        }
        
        return false;
    }

    private boolean evaluateAgeRule(String expression, RoutePlan plan) {
        int ageMin = plan.getAgeMin() != null ? plan.getAgeMin() : 0;
        int ageMax = plan.getAgeMax() != null ? plan.getAgeMax() : 100;
        
        if (expression.contains("CHILDREN") && ageMin <= 12) {
            return true;
        }
        
        if (expression.contains("ELDERLY") && ageMax >= 65) {
            return true;
        }
        
        if (expression.contains("WIDE_AGE_RANGE") && (ageMax - ageMin) > 30) {
            return true;
        }
        
        if (expression.contains("TEENAGER") && ageMin >= 13 && ageMax <= 18) {
            return true;
        }
        
        return false;
    }

    private boolean evaluateVenueRule(String expression, RoutePlan plan, String location) {
        if (expression.contains("HIGH_ALTITUDE") && (location.contains("山") || location.contains("高原"))) {
            return true;
        }
        
        if (expression.contains("WATER_ACTIVITY") && (location.contains("湖") || location.contains("河") || location.contains("海"))) {
            return true;
        }
        
        if (expression.contains("CROWDED_AREA") && (location.contains("景区") || location.contains("公园") || location.contains("广场"))) {
            return true;
        }
        
        if (expression.contains("CONSTRUCTION") && location.contains("施工")) {
            return true;
        }
        
        return false;
    }

    private boolean evaluateCustomRule(String expression, RoutePlan plan, String location) {
        int ageMin = plan.getAgeMin() != null ? plan.getAgeMin() : 0;
        int ageMax = plan.getAgeMax() != null ? plan.getAgeMax() : 100;
        int count = plan.getParticipantCount() != null ? plan.getParticipantCount() : 0;
        
        if (expression.contains("AGE_MIN_LESS_THAN_6") && ageMin < 6) {
            return true;
        }
        
        if (expression.contains("AGE_MAX_GREATER_THAN_70") && ageMax > 70) {
            return true;
        }
        
        if (expression.contains("PARTICIPANTS_MORE_THAN_50") && count > 50) {
            return true;
        }
        
        if (expression.contains("PARTICIPANTS_MORE_THAN_100") && count > 100) {
            return true;
        }
        
        if (expression.contains("LOCATION_CONTAINS_DANGER") && 
            (location.contains("悬崖") || location.contains("峭壁") || location.contains("深谷"))) {
            return true;
        }
        
        return false;
    }

    private List<String> getAllLocations(RoutePlan plan) {
        List<String> locations = new ArrayList<>();
        locations.add(plan.getStartLocation());
        locations.add(plan.getEndLocation());
        
        if (plan.getWaypoints() != null && !plan.getWaypoints().isEmpty()) {
            try {
                List<String> waypointsList = Arrays.asList(plan.getWaypoints().split(","));
                locations.addAll(waypointsList.stream()
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList()));
            } catch (Exception e) {
                log.warn("解析途经点失败: {}", plan.getWaypoints());
            }
        }
        
        return locations;
    }

    private boolean isRainySeason(int month) {
        return month >= 5 && month <= 9;
    }

    private boolean isHoliday(int month, int day) {
        int[][] holidays = {
            {1, 1}, {1, 2}, {1, 3},
            {5, 1}, {5, 2}, {5, 3},
            {10, 1}, {10, 2}, {10, 3}, {10, 4}, {10, 5}, {10, 6}, {10, 7},
            {4, 4}, {4, 5}, {4, 6},
            {6, 22}, {6, 23}, {6, 24},
            {9, 15}, {9, 16}, {9, 17}
        };
        
        for (int[] holiday : holidays) {
            if (holiday[0] == month && holiday[1] == day) {
                return true;
            }
        }
        return false;
    }

    private String generateStatus(List<RiskCheckResponse.RiskItem> items) {
        long highCount = items.stream().filter(i -> "HIGH".equals(i.getRiskLevel())).count();
        long mediumCount = items.stream().filter(i -> "MEDIUM".equals(i.getRiskLevel())).count();
        
        if (highCount > 0) return "HIGH_RISK";
        if (mediumCount > 0) return "MEDIUM_RISK";
        return "LOW_RISK";
    }

    private RiskCheckResponse buildRiskCheckResponse(RoutePlan plan, List<RiskCheckResponse.RiskItem> items) {
        List<RiskCheckResponse.RiskItem> highRisks = items.stream()
                .filter(i -> "HIGH".equals(i.getRiskLevel()))
                .collect(Collectors.toList());
        
        List<RiskCheckResponse.RiskItem> mediumRisks = items.stream()
                .filter(i -> "MEDIUM".equals(i.getRiskLevel()))
                .collect(Collectors.toList());
        
        List<RiskCheckResponse.RiskItem> lowRisks = items.stream()
                .filter(i -> "LOW".equals(i.getRiskLevel()))
                .collect(Collectors.toList());
        
        return RiskCheckResponse.builder()
                .planId(plan.getId())
                .planName(plan.getPlanName())
                .highRisks(highRisks)
                .mediumRisks(mediumRisks)
                .lowRisks(lowRisks)
                .overallStatus(plan.getStatus())
                .totalRiskCount(items.size())
                .build();
    }
}