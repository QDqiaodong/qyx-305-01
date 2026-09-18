package com.risk.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 发车放行提交。
 *
 * informedNote（知情备注）只能写在中风险单上；高风险单即便带了备注也必须失败，
 * 安全岗只认“高风险必须改线或关掉触发规则后重新筛”。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReleaseRequest {

    /** 带队老师/安全岗填写的知情备注，可空；中风险方可填写 */
    private String informedNote;
}
