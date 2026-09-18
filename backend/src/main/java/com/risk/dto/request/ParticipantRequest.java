package com.risk.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 往车次名册加人（发车前可用）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParticipantRequest {

    /** 孩子/随队人员姓名，同一趟车内不重名 */
    private String personName;
}
