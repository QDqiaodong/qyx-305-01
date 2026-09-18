package com.risk.service.impl;

import com.alibaba.fastjson.JSON;
import com.risk.dto.request.RiskRuleRequest;
import com.risk.entity.RiskRule;
import com.risk.repository.RiskRuleRepository;
import com.risk.repository.RuleVersionRepository;
import com.risk.service.ReleaseGateService;
import com.risk.service.RiskRuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RiskRuleServiceImpl implements RiskRuleService {

    private static final String REDIS_KEY_RULES_ALL = "risk:rules:all";
    private static final String REDIS_KEY_RULES_ENABLED = "risk:rules:enabled";
    private static final String REDIS_KEY_RULES_BY_TYPE = "risk:rules:type:%s";
    private static final int CACHE_EXPIRE_MINUTES = 30;

    private final RiskRuleRepository ruleRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RuleVersionRepository ruleVersionRepository;
    private final ReleaseGateService releaseGate;

    @Override
    public List<RiskRule> getAllRules() {
        String cacheKey = REDIS_KEY_RULES_ALL;
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return JSON.parseArray(cached.toString(), RiskRule.class);
        }
        
        List<RiskRule> rules = ruleRepository.findAll();
        redisTemplate.opsForValue().set(cacheKey, JSON.toJSONString(rules), CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES);
        return rules;
    }

    @Override
    public List<RiskRule> getEnabledRules() {
        String cacheKey = REDIS_KEY_RULES_ENABLED;
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return JSON.parseArray(cached.toString(), RiskRule.class);
        }
        
        List<RiskRule> rules = ruleRepository.findByEnabled(1);
        redisTemplate.opsForValue().set(cacheKey, JSON.toJSONString(rules), CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES);
        return rules;
    }

    @Override
    public List<RiskRule> getRulesByType(String ruleType) {
        String cacheKey = String.format(REDIS_KEY_RULES_BY_TYPE, ruleType);
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return JSON.parseArray(cached.toString(), RiskRule.class);
        }
        
        List<RiskRule> rules = ruleRepository.findByRuleTypeAndEnabled(ruleType, 1);
        redisTemplate.opsForValue().set(cacheKey, JSON.toJSONString(rules), CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES);
        return rules;
    }

    @Override
    public RiskRule getRuleById(Long id) {
        return ruleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("规则不存在: " + id));
    }

    @Override
    public RiskRule getRuleByCode(String code) {
        return ruleRepository.findByRuleCode(code)
                .orElseThrow(() -> new RuntimeException("规则不存在: " + code));
    }

    @Override
    @Transactional
    public RiskRule createRule(RiskRuleRequest request) {
        if (ruleRepository.existsByRuleCode(request.getRuleCode())) {
            throw new RuntimeException("规则代码已存在: " + request.getRuleCode());
        }
        
        RiskRule rule = RiskRule.builder()
                .ruleCode(request.getRuleCode())
                .ruleName(request.getRuleName())
                .ruleType(request.getRuleType())
                .riskLevel(request.getRiskLevel())
                .conditionExpression(request.getConditionExpression())
                .warningMessage(request.getWarningMessage())
                .enabled(request.getEnabled() != null ? request.getEnabled() : 1)
                .build();
        
        RiskRule saved = ruleRepository.save(rule);
        onRulesMutated("新增规则 " + saved.getRuleCode());
        return saved;
    }

    @Override
    @Transactional
    public RiskRule updateRule(Long id, RiskRuleRequest request) {
        RiskRule rule = getRuleById(id);
        
        if (!rule.getRuleCode().equals(request.getRuleCode()) && 
            ruleRepository.existsByRuleCode(request.getRuleCode())) {
            throw new RuntimeException("规则代码已存在: " + request.getRuleCode());
        }
        
        rule.setRuleCode(request.getRuleCode());
        rule.setRuleName(request.getRuleName());
        rule.setRuleType(request.getRuleType());
        rule.setRiskLevel(request.getRiskLevel());
        rule.setConditionExpression(request.getConditionExpression());
        rule.setWarningMessage(request.getWarningMessage());
        if (request.getEnabled() != null) {
            rule.setEnabled(request.getEnabled());
        }
        
        RiskRule updated = ruleRepository.save(rule);
        onRulesMutated("修改规则 " + updated.getRuleCode());
        return updated;
    }

    @Override
    @Transactional
    public void deleteRule(Long id) {
        if (!ruleRepository.existsById(id)) {
            throw new RuntimeException("规则不存在: " + id);
        }
        ruleRepository.deleteById(id);
        onRulesMutated("删除规则 " + id);
    }

    @Override
    @Transactional
    public void toggleRuleStatus(Long id) {
        RiskRule rule = getRuleById(id);
        rule.setEnabled(rule.getEnabled() == 1 ? 0 : 1);
        ruleRepository.save(rule);
        onRulesMutated("切换规则开关 " + rule.getRuleCode());
    }

    @Override
    public Map<String, List<RiskRule>> getRulesGroupedByType() {
        List<RiskRule> rules = getEnabledRules();
        return rules.stream()
                .collect(Collectors.groupingBy(RiskRule::getRuleType));
    }

    @Override
    public void refreshRulesCache() {
        redisTemplate.delete(REDIS_KEY_RULES_ALL);
        redisTemplate.delete(REDIS_KEY_RULES_ENABLED);
        Set<String> keys = redisTemplate.keys(String.format(REDIS_KEY_RULES_BY_TYPE, "*"));
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        log.info("风险规则缓存已刷新");
    }

    /**
     * 规则被改动的统一收口（同一事务内，顺序固定）：
     * 1) 规则版本 +1 —— 所有未重筛的筛查立刻变旧，报告与看板读到的都是“待重筛”；
     * 2) 未发车的已放行出门条一律作废并停在待重筛，已发车的纸面不动；
     * 3) 刷规则缓存。
     * 版本自增是数据库原子操作，两人同时改同一条规则时两次自增串行，
     * 未发车计划只能一起落到待重筛，不会一张作废一张仍绿灯。
     */
    private void onRulesMutated(String what) {
        ruleVersionRepository.bump();
        releaseGate.onRulesChanged();
        refreshRulesCache();
        log.info("{}，规则版本已 +1，未发车出门条已落到待重筛", what);
    }
}