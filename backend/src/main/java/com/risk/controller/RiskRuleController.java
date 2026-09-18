package com.risk.controller;

import com.risk.dto.request.RiskRuleRequest;
import com.risk.dto.response.ApiResponse;
import com.risk.entity.RiskRule;
import com.risk.service.RiskRuleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rules")
@RequiredArgsConstructor
public class RiskRuleController {

    private final RiskRuleService ruleService;

    @GetMapping
    public ApiResponse<List<RiskRule>> getAllRules() {
        List<RiskRule> rules = ruleService.getAllRules();
        return ApiResponse.success(rules);
    }

    @GetMapping("/enabled")
    public ApiResponse<List<RiskRule>> getEnabledRules() {
        List<RiskRule> rules = ruleService.getEnabledRules();
        return ApiResponse.success(rules);
    }

    @GetMapping("/grouped")
    public ApiResponse<Map<String, List<RiskRule>>> getRulesGroupedByType() {
        Map<String, List<RiskRule>> rules = ruleService.getRulesGroupedByType();
        return ApiResponse.success(rules);
    }

    @GetMapping("/type/{type}")
    public ApiResponse<List<RiskRule>> getRulesByType(@PathVariable String type) {
        List<RiskRule> rules = ruleService.getRulesByType(type);
        return ApiResponse.success(rules);
    }

    @GetMapping("/{id}")
    public ApiResponse<RiskRule> getRuleById(@PathVariable Long id) {
        RiskRule rule = ruleService.getRuleById(id);
        return ApiResponse.success(rule);
    }

    @PostMapping
    public ApiResponse<RiskRule> createRule(@Valid @RequestBody RiskRuleRequest request) {
        RiskRule rule = ruleService.createRule(request);
        return ApiResponse.success("创建成功", rule);
    }

    @PutMapping("/{id}")
    public ApiResponse<RiskRule> updateRule(@PathVariable Long id, @Valid @RequestBody RiskRuleRequest request) {
        RiskRule rule = ruleService.updateRule(id, request);
        return ApiResponse.success("更新成功", rule);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteRule(@PathVariable Long id) {
        ruleService.deleteRule(id);
        return ApiResponse.success("删除成功", null);
    }

    @PostMapping("/{id}/toggle")
    public ApiResponse<RiskRule> toggleRuleStatus(@PathVariable Long id) {
        ruleService.toggleRuleStatus(id);
        RiskRule rule = ruleService.getRuleById(id);
        return ApiResponse.success("状态切换成功", rule);
    }

    @PostMapping("/refresh-cache")
    public ApiResponse<Void> refreshRulesCache() {
        ruleService.refreshRulesCache();
        return ApiResponse.success("缓存刷新成功", null);
    }
}