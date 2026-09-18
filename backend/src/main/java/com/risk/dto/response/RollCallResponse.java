package com.risk.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 回程点名视图：发车带出去的人 + 回程勾回情况 + 收口状态。
 *
 * 阶段：
 *   NOT_DEPARTED  未发车（只能维护名册，不能点名）
 *   ROLLING       已发车、还没收口（逐个勾回、写缺人说明、收口）
 *   CLOSED        已收口（点名结果冻住，一切只读）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RollCallResponse {

    private Long planId;

    private String planName;

    /** NOT_DEPARTED / ROLLING / CLOSED */
    private String phase;

    private String phaseText;

    private String departedAt;

    private String closedAt;

    /** 发车时带出去的人（名册） */
    private List<ParticipantView> participants;

    private int totalCount;

    private int returnedCount;

    /** 还没勾回的人数 */
    private int missingCount;

    /**
     * 未写缺人说明的缺员人数：收口时必须为 0
     * （有人没上车回校，要先写下缺人说明，才能把这趟收口）
     */
    private int missingWithoutNoteCount;

    /** 当前是否允许收口：已发车、未收口、车上有人、且缺员都有说明 */
    private boolean closable;

    /** 收口时冻住的缺人说明（收口后不可改没） */
    private String frozenMissingNote;

    /** 还不能收口的原因（closable=true 时为空） */
    private List<String> blockReasons;
}
