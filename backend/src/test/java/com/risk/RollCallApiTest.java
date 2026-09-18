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
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 走真实 HTTP 栈验证回程点名：
 *  - 发车前名册可加人；发车后加人 400（晚到只能另开一趟）
 *  - 缺员说明没写齐收口 400；写齐后收口成功
 *  - 收口后勾选/改说明/加人/再收口全部 400，点名结果冻住
 *  - 空名册（只改口号没人点名）收口 400
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestRedisConfig.class)
class RollCallApiTest {

    @Autowired private MockMvc mockMvc;
    private final ObjectMapper json = new ObjectMapper();

    private static final LocalDate MAR = LocalDate.of(2026, 3, 12);

    private Long createPlan() throws Exception {
        Map<String, Object> body = Map.of(
                "planName", "点名API线-" + System.nanoTime(),
                "startLocation", "学校",
                "endLocation", "博物馆",
                "waypoints", List.of(),
                "travelDate", MAR.toString(),
                "ageMin", 30, "ageMax", 50, "participantCount", 20);
        return node(mockMvc.perform(post("/api/plans").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body))).andExpect(status().isOk()).andReturn())
                .get("data").get("id").asLong();
    }

    private Long createStaff() throws Exception {
        return node(mockMvc.perform(post("/api/medical/staff").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("staffName", "点名API随队-" + System.nanoTime(),
                                "pediatricQualified", false))))
                .andExpect(status().isOk()).andReturn()).get("data").get("id").asLong();
    }

    private void releaseAndDepart(Long planId) throws Exception {
        mockMvc.perform(post("/api/plans/" + planId + "/check")).andExpect(status().isOk());
        mockMvc.perform(post("/api/medical/assignments").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("planId", planId, "staffIds", List.of(createStaff())))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/release/plan/" + planId + "/submit")
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
        mockMvc.perform(post("/api/release/plan/" + planId + "/depart")).andExpect(status().isOk());
    }

    private JsonNode roll(Long planId) throws Exception {
        return node(mockMvc.perform(get("/api/rollcall/plan/" + planId))
                .andExpect(status().isOk()).andReturn()).get("data");
    }

    private Long participantId(JsonNode roll, String name) {
        for (JsonNode p : roll.get("participants")) {
            if (name.equals(p.get("personName").asText())) {
                return p.get("id").asLong();
            }
        }
        throw new IllegalStateException("名册里没有 " + name);
    }

    private ResultActions rollPost(Long planId, String suffix, Object body) throws Exception {
        return mockMvc.perform(post("/api/rollcall/plan/" + planId + suffix)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    @Test
    void fullRollFlow_overHttp_freezesAfterClose() throws Exception {
        Long planId = createPlan();

        // 发车前加名册
        rollPost(planId, "/participants", Map.of("personName", "小明")).andExpect(status().isOk());
        rollPost(planId, "/participants", Map.of("personName", "小红")).andExpect(status().isOk());
        assertThat(roll(planId).get("phase").asText()).isEqualTo("NOT_DEPARTED");

        releaseAndDepart(planId);
        JsonNode rolling = roll(planId);
        assertThat(rolling.get("phase").asText()).isEqualTo("ROLLING");

        Long xm = participantId(rolling, "小明");
        Long xh = participantId(rolling, "小红");

        // 发车后晚到的孩子补不进来：400
        ResultActions late = rollPost(planId, "/participants", Map.of("personName", "晚到小芳"));
        assertThat(late.andReturn().getResponse().getStatus()).isEqualTo(400);
        assertThat(node(late.andReturn()).get("message").asText()).contains("另开一趟");

        // 勾回小明，小红没回；没写说明收口 400
        rollPost(planId, "/participants/" + xm + "/mark", Map.of("returned", true)).andExpect(status().isOk());
        mockMvc.perform(post("/api/rollcall/plan/" + planId + "/close"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("缺人说明")));

        // 空说明也 400
        rollPost(planId, "/participants/" + xh + "/missing-note", Map.of("note", "  "))
                .andExpect(status().isBadRequest());

        // 写齐缺人说明，收口成功
        rollPost(planId, "/participants/" + xh + "/missing-note",
                Map.of("note", "小红由家长接走，未随车回校")).andExpect(status().isOk());
        mockMvc.perform(post("/api/rollcall/plan/" + planId + "/close")).andExpect(status().isOk());

        JsonNode closed = roll(planId);
        assertThat(closed.get("phase").asText()).isEqualTo("CLOSED");
        assertThat(closed.get("frozenMissingNote").asText()).contains("小红").contains("家长接走");

        // 收口后：勾人、改说明、塞人、再收口全部 400
        assertThat(rollPost(planId, "/participants/" + xh + "/mark", Map.of("returned", true))
                .andReturn().getResponse().getStatus()).isEqualTo(400);
        assertThat(rollPost(planId, "/participants/" + xh + "/missing-note", Map.of("note", "小红回来了"))
                .andReturn().getResponse().getStatus()).isEqualTo(400);
        assertThat(rollPost(planId, "/participants", Map.of("personName", "又来一个"))
                .andReturn().getResponse().getStatus()).isEqualTo(400);
        mockMvc.perform(post("/api/rollcall/plan/" + planId + "/close"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("已收口")));

        // 列表读回来仍是冻住的（关掉页面再打开）
        JsonNode inList = node(mockMvc.perform(get("/api/rollcall/plans")).andExpect(status().isOk()).andReturn())
                .get("data");
        boolean found = false;
        for (JsonNode r : inList) {
            if (r.get("planId").asLong() == planId) {
                assertThat(r.get("phase").asText()).isEqualTo("CLOSED");
                assertThat(r.get("frozenMissingNote").asText()).contains("小红");
                found = true;
            }
        }
        assertThat(found).isTrue();
    }

    @Test
    void emptyRoster_afterDepart_cannotClose() throws Exception {
        Long planId = createPlan();
        releaseAndDepart(planId);
        // 光发车、车上没人点名：值班室仍当全员还没回来，收不了口
        mockMvc.perform(post("/api/rollcall/plan/" + planId + "/close"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("车上没人点名")));
    }

    private JsonNode node(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
