package com.risk.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 缺人说明：有人没上车回校时，必须先写下说明，车次才能收口。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MissingNoteRequest {

    private String note;
}
