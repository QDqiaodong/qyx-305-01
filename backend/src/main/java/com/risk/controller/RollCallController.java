package com.risk.controller;

import com.risk.dto.request.MissingNoteRequest;
import com.risk.dto.request.ParticipantRequest;
import com.risk.dto.request.RollMarkRequest;
import com.risk.dto.response.ApiResponse;
import com.risk.dto.response.RollCallResponse;
import com.risk.service.RollCallService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 回程点名：还没收口的车次，必须按发车时带出去的人一个个勾回；
 * 有人没上车回校，要先写下缺人说明才能收口；收口后点名结果冻住。
 */
@RestController
@RequestMapping("/api/rollcall")
@RequiredArgsConstructor
public class RollCallController {

    private final RollCallService rollCallService;

    /** 全部车次的回程点名情况 */
    @GetMapping("/plans")
    public ApiResponse<List<RollCallResponse>> listAll() {
        return ApiResponse.success(rollCallService.listAll());
    }

    /** 单车次回程点名详情（名册 + 勾选 + 收口状态） */
    @GetMapping("/plan/{planId}")
    public ApiResponse<RollCallResponse> get(@PathVariable Long planId) {
        return ApiResponse.success(rollCallService.get(planId));
    }

    /** 发车前往这趟的名册加人；发车后/收口后拒绝（晚到的只能另开一趟） */
    @PostMapping("/plan/{planId}/participants")
    public ApiResponse<RollCallResponse> add(@PathVariable Long planId,
                                             @RequestBody ParticipantRequest request) {
        return ApiResponse.success("已加入名册",
                rollCallService.addParticipant(planId, request == null ? null : request.getPersonName()));
    }

    /** 发车前从名册删人 */
    @DeleteMapping("/plan/{planId}/participants/{participantId}")
    public ApiResponse<RollCallResponse> remove(@PathVariable Long planId,
                                                @PathVariable Long participantId) {
        return ApiResponse.success("已从名册删除", rollCallService.removeParticipant(planId, participantId));
    }

    /** 回程勾选：把某个人勾回到校 / 取消勾选（只有已发车、未收口能改） */
    @PostMapping("/plan/{planId}/participants/{participantId}/mark")
    public ApiResponse<RollCallResponse> mark(@PathVariable Long planId,
                                              @PathVariable Long participantId,
                                              @RequestBody RollMarkRequest request) {
        boolean returned = request != null && Boolean.TRUE.equals(request.getReturned());
        return ApiResponse.success(returned ? "已勾回到校" : "已取消勾回",
                rollCallService.markReturned(planId, participantId, returned));
    }

    /** 给没上车回校的人写缺人说明（收口前必须写齐） */
    @PostMapping("/plan/{planId}/participants/{participantId}/missing-note")
    public ApiResponse<RollCallResponse> missingNote(@PathVariable Long planId,
                                                     @PathVariable Long participantId,
                                                     @RequestBody MissingNoteRequest request) {
        return ApiResponse.success("缺人说明已记下",
                rollCallService.writeMissingNote(planId, participantId, request == null ? null : request.getNote()));
    }

    /** 收口这趟：缺员说明写齐才收得了；收口后冻住，不能再改 */
    @PostMapping("/plan/{planId}/close")
    public ApiResponse<RollCallResponse> close(@PathVariable Long planId) {
        return ApiResponse.success("已收口，点名结果冻住", rollCallService.closeRoll(planId));
    }
}
