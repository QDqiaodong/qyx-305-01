package com.risk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 走真实 HTTP 栈：验证放行成功/失败的状态码与“说清原因”，以及行程变更后的单据状态。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestRedisConfig.class)
class ReleaseGateApiTest {

    @Autowired private MockMvc mockMvc;
    private final ObjectMapper json = new ObjectMapper();

    private static final LocalDate MAR = LocalDate.of(2026, 3, 12);

    private Long createPlan(String end, List<String> wp) throws Exception {
        Map<String, Object> body = Map.of(
                "planName", "API线-" + System.nanoTime(),
                "startLocation", "学校",
                "endLocation", end,
                "waypoints", wp == null ? List.of() : wp,
                "travelDate", MAR.toString(),
                "ageMin", 30, "ageMax", 50, "participantCount", 20);
        MvcResult r = mockMvc.perform(post("/api/plans").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isOk()).andReturn();
        return node(r).get("data").get("id").asLong();
    }

    private Long createStaff(String name, boolean pediatric) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/medical/staff").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("staffName", name, "pediatricQualified", pediatric))))
                .andExpect(status().isOk()).andReturn();
        return node(r).get("data").get("id").asLong();
    }

    private void check(Long planId) throws Exception {
        mockMvc.perform(post("/api/plans/" + planId + "/check")).andExpect(status().isOk());
    }

    private void assign(Long planId, Long... ids) throws Exception {
        mockMvc.perform(post("/api/medical/assignments").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("planId", planId, "staffIds", List.of(ids)))))
                .andExpect(status().isOk());
    }

    private JsonNode permit(Long planId) throws Exception {
        MvcResult r = mockMvc.perform(get("/api/release/plan/" + planId)).andExpect(status().isOk()).andReturn();
        return node(r).get("data");
    }

    @Test
    void highRisk_submitTwice_bothRejectedWithReason_thenRerouteReleases() throws Exception {
        Long high = createPlan("山顶公园", null);
        check(high);
        Long s1 = createStaff("API甲", false);
        Long s2 = createStaff("API乙", false);
        assign(high, s1, s2);
        assertThat(permit(high).get("screening").get("riskLevel").asText()).isEqualTo("HIGH_RISK");

        // 第一次带知情备注：400 且原因写明高风险
        MvcResult first = mockMvc.perform(post("/api/release/plan/" + high + "/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("informedNote", "带队老师知情"))))
                .andExpect(status().isBadRequest()).andReturn();
        String msg1 = node(first).get("message").asText();
        assertThat(msg1).contains("高风险");

        // 第二次不带备注、不改条件：仍 400，绝不会悄悄出绿单
        MvcResult second = mockMvc.perform(post("/api/release/plan/" + high + "/submit")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andReturn();
        assertThat(node(second).get("message").asText()).contains("高风险");
        assertThat(permit(high).get("status").asText()).isEqualTo("PENDING");

        // 中风险正常放行（用独立医护，避免与高风险计划同一天撞车）
        Long mid = createPlan("博物馆", List.of("城市广场"));
        check(mid);
        Long s3 = createStaff("API丙", false);
        assign(mid, s3);
        MvcResult ok = mockMvc.perform(post("/api/release/plan/" + mid + "/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("informedNote", "已知悉中风险"))))
                .andExpect(status().isOk()).andReturn();
        JsonNode released = node(ok).get("data");
        assertThat(released.get("status").asText()).isEqualTo("RELEASED");
        assertThat(released.get("informedNote").asText()).contains("中风险");

        // 行程改人数 → 已发放行单作废（写明行程变了）
        Map<String, Object> changed = Map.of(
                "planName", "改人数线", "startLocation", "学校", "endLocation", "博物馆",
                "waypoints", List.of("城市广场"), "travelDate", MAR.toString(),
                "ageMin", 30, "ageMax", 50, "participantCount", 60);
        mockMvc.perform(put("/api/plans/" + mid).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(changed)))
                .andExpect(status().isOk());
        JsonNode after = permit(mid);
        assertThat(after.get("status").asText()).isEqualTo("VOID");
        assertThat(after.get("voidReason").asText()).contains("行程");
        assertThat(after.get("screening").get("fresh").asBoolean()).isFalse();

        // 已放行统计不应再把这条算进 RELEASED
        MvcResult stats = mockMvc.perform(get("/api/release/stats")).andExpect(status().isOk()).andReturn();
        assertThat(node(stats).get("data").get("voidCount").asInt()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void informedNoteOnLowRisk_rejected() throws Exception {
        Long low = createPlan("博物馆", null);
        check(low);
        Long s1 = createStaff("低风险API随队", false);
        assign(low, s1);
        mockMvc.perform(post("/api/release/plan/" + low + "/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("informedNote", "不该出现的备注"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("知情备注")));
    }

    /**
     * 走真实 HTTP 栈：登记发车时刻后改规则——已发车的纸面保持当时那一版，
     * 未发车的出门条落到待重筛，报告页与看板读同一份结论。
     */
    @Test
    void depart_thenRuleChange_departedFrozen_undepartedDropsToStale() throws Exception {
        // 两条中风险线都放行；其中一条登记发车
        Long departedPlan = createPlan("博物馆", List.of("城市广场"));
        Long undepartedPlan = createPlan("博物馆", List.of("城市广场"));
        check(departedPlan);
        check(undepartedPlan);
        assign(departedPlan, createStaff("发车线随队", false));
        assign(undepartedPlan, createStaff("未发车线随队", false));
        for (Long p : List.of(departedPlan, undepartedPlan)) {
            mockMvc.perform(post("/api/release/plan/" + p + "/submit")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("informedNote", "已知悉中风险"))))
                    .andExpect(status().isOk());
        }

        // 未放行计划不能登记发车
        Long pending = createPlan("体育馆", null);
        mockMvc.perform(post("/api/release/plan/" + pending + "/depart"))
                .andExpect(status().isBadRequest());

        // 登记发车时刻
        mockMvc.perform(post("/api/release/plan/" + departedPlan + "/depart"))
                .andExpect(status().isOk());
        JsonNode departed = permit(departedPlan);
        assertThat(departed.get("status").asText()).isEqualTo("RELEASED");
        assertThat(departed.get("statusText").asText()).isEqualTo("已发车");
        assertThat(departed.get("departedAt").asText()).isNotBlank();

        // 调度改规则：VENUE_003 中风险 → 高风险
        JsonNode rule = findRule("VENUE_003");
        Map<String, Object> toHigh = ruleBody(rule, "HIGH");
        mockMvc.perform(put("/api/rules/" + rule.get("id").asLong())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(toHigh)))
                .andExpect(status().isOk());
        try {
            // 未发车：出门条作废并停在待重筛
            JsonNode undeparted = permit(undepartedPlan);
            assertThat(undeparted.get("status").asText()).isEqualTo("STALE_RECHECK");
            assertThat(undeparted.get("recheckReason").asText()).contains("规则");
            assertThat(undeparted.get("screening").get("rulesFresh").asBoolean()).isFalse();

            // 报告页与看板同一份结论：旧中风险不得继续当可出门
            JsonNode undepartedReport = report(undepartedPlan);
            assertThat(undepartedReport.get("screeningValid").asBoolean()).isFalse();
            assertThat(undepartedReport.get("permitStatus").asText()).isEqualTo("STALE_RECHECK");

            // 已发车：纸面封存，报告保持出门当时那一版
            JsonNode departedAfter = permit(departedPlan);
            assertThat(departedAfter.get("status").asText()).isEqualTo("RELEASED");
            assertThat(departedAfter.get("riskLevel").asText()).isEqualTo("MEDIUM_RISK");
            JsonNode departedReport = report(departedPlan);
            assertThat(departedReport.get("departed").asBoolean()).isTrue();
            assertThat(departedReport.get("overallStatus").asText()).isEqualTo("MEDIUM_RISK");
            assertThat(departedReport.get("screeningValid").asBoolean()).isTrue();

            // 已发车不能再重筛（400，写明发车）
            mockMvc.perform(post("/api/plans/" + departedPlan + "/check"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("发车")));
        } finally {
            // 还原规则等级
            mockMvc.perform(put("/api/rules/" + rule.get("id").asLong())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(ruleBody(rule, "MEDIUM"))))
                    .andExpect(status().isOk());
        }
    }

    private JsonNode findRule(String ruleCode) throws Exception {
        MvcResult r = mockMvc.perform(get("/api/rules")).andExpect(status().isOk()).andReturn();
        for (JsonNode rule : node(r).get("data")) {
            if (ruleCode.equals(rule.get("ruleCode").asText())) {
                return rule;
            }
        }
        throw new IllegalStateException("规则不存在: " + ruleCode);
    }

    private Map<String, Object> ruleBody(JsonNode rule, String riskLevel) {
        return Map.of(
                "ruleCode", rule.get("ruleCode").asText(),
                "ruleName", rule.get("ruleName").asText(),
                "ruleType", rule.get("ruleType").asText(),
                "riskLevel", riskLevel,
                "conditionExpression", rule.get("conditionExpression").asText(),
                "warningMessage", rule.get("warningMessage").asText(),
                "enabled", 1);
    }

    private JsonNode report(Long planId) throws Exception {
        MvcResult r = mockMvc.perform(get("/api/plans/" + planId + "/report")).andExpect(status().isOk()).andReturn();
        return node(r).get("data");
    }

    private JsonNode node(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
