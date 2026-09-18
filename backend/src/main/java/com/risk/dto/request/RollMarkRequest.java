package com.risk.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 回程点名：把某个人勾回 / 取消勾回。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RollMarkRequest {

    /** true=已上车回校；false=还没回（取消勾选） */
    private Boolean returned;
}
