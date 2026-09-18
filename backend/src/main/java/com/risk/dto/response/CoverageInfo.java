package com.risk.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 第二本账：当天医护覆盖。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoverageInfo {

    private List<StaffView> staff;

    /** 当天实际在岗（日期对得上）的人数 */
    private int coveredCount;

    /** 至少需要人数：基础 1，高风险 2；低龄队伍叠加儿科要求但不叠加人数 */
    private int requiredCount;

    /** 人数是否满足（高风险需要双人） */
    private boolean countSatisfied;

    /** 队伍最小年龄 ≤ 8 岁，需要儿科资质 */
    private boolean pediatricRequired;

    /** 是否有具备儿科资质的医护在岗 */
    private boolean pediatricSatisfied;

    /** 两本账中医护这本是否整体满足 */
    private boolean satisfied;

    /** 覆盖判定依据的风险等级（最新筛查；无筛查时为 null） */
    private String basedRiskLevel;

    /** 不满足原因（中文，可多条） */
    private List<String> reasons;
}
