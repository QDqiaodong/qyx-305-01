package com.risk.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 发车放行单视图：四档状态 + 两本账 + 放行结论。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReleasePermitResponse {

    private Long permitId;

    private Long planId;

    private String planName;

    /** PENDING 待齐件 / RELEASED 已放行 / VOID 已作废 / STALE_RECHECK 筛查失效待重评 */
    private String status;

    private String statusText;

    /** 放行所依据的筛查结论 */
    private String riskLevel;

    private String informedNote;

    private String releasedAt;

    /** 发车时刻：记下后单据封存，之后的规则调整不再改写这张纸 */
    private String departedAt;

    private String voidReason;

    private String recheckReason;

    /** 第一本账：筛查时效 */
    private ScreeningBook screening;

    /** 第二本账：医护覆盖 */
    private CoverageInfo coverage;

    /** 当前是否处于可放行条件（两本账都对得上且非高风险） */
    private boolean releasable;

    /** 当前为什么不能放行（可多条；releasable=true 时为空） */
    private java.util.List<String> blockReasons;
}
