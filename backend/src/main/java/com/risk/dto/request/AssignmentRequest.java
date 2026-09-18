package com.risk.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 排班提交：把一组医护挂到某计划的出行日上。
 * staffIds 传空列表表示清空该计划排班（全部撤岗）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignmentRequest {

    @NotNull(message = "路线计划不能为空")
    private Long planId;

    @NotNull(message = "医护列表不能为空（无人请传空数组以撤岗）")
    private List<Long> staffIds;
}
