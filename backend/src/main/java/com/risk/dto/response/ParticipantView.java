package com.risk.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 车次名册中的一个人，带上回程点名结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParticipantView {

    private Long id;

    private String personName;

    /** 回程是否已勾回到校 */
    private boolean returned;

    /** 缺人说明（未勾回的人可能带着说明） */
    private String missingNote;
}
