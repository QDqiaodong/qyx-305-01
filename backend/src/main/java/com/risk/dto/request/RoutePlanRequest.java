package com.risk.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoutePlanRequest {

    @NotBlank(message = "计划名称不能为空")
    private String planName;

    @NotBlank(message = "出发地点不能为空")
    private String startLocation;

    @NotBlank(message = "目的地不能为空")
    private String endLocation;

    private List<String> waypoints;

    @NotNull(message = "出行日期不能为空")
    private LocalDate travelDate;

    private Integer ageMin;

    private Integer ageMax;

    private Integer participantCount;
}