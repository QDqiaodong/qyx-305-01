package com.risk.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 排班里一名医护的视图。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaffView {

    private Long assignmentId;
    private Long staffId;
    private String staffName;
    private String title;
    private String phone;
    private String certificateNo;
    private boolean pediatricQualified;
    /** 排班记录的出行日期是否仍与计划当前出行日期一致（计划改期后旧排班为 false） */
    private boolean dateMatched;
    private String assignmentDate;
}
