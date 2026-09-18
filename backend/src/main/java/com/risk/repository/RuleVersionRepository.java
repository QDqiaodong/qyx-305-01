package com.risk.repository;

import com.risk.entity.RuleVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface RuleVersionRepository extends JpaRepository<RuleVersion, Long> {

    /**
     * 原子地把规则版本 +1。
     * 两人同时改规则时两次自增在数据库层串行，版本号单调前进，
     * 所有未重筛的筛查一起变旧，不会出现“一张作废一张仍绿灯”。
     */
    @Modifying
    @Query("UPDATE RuleVersion rv SET rv.version = rv.version + 1 WHERE rv.id = " + RuleVersion.SINGLETON_ID)
    int bump();
}
