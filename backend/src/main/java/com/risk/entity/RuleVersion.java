package com.risk.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 风险规则版本（全库单行）。
 *
 * 规则每被改一次（新增 / 编辑 / 删除 / 开关），版本号 +1。
 * 筛查台账记录筛查当时的规则版本；版本对不上即“规则改了还没重筛”，
 * 未发车的出门条与报告一起停在待重筛，已发车的纸面不受影响。
 */
@Entity
@Table(name = "rule_version")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleVersion {

    /** 全库只有一行，固定主键 */
    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id;

    @Column(name = "version", nullable = false)
    private Long version;
}
