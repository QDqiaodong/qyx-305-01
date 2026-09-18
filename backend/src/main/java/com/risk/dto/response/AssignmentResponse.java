package com.risk.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 排班提交后的回执：除了排班明细，还直接告诉调用方当前医护覆盖是否已满足，
 * 以及这一改动对已发放行单的连带影响。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignmentResponse {

    private Long planId;
    private String planName;
    private String travelDate;
    private CoverageInfo coverage;
    /** 排班改动后放行单被打成的状态（如 STALE_RECHECK / VOID / PENDING / RELEASED / null=无） */
    private String permitStatusAfterChange;
    /** 连带落档原因 */
    private String permitReason;
}
