package com.risk.service;

import com.risk.dto.response.RollCallResponse;

import java.util.List;

/**
 * 回程点名：按发车时带出去的人一个个勾回；缺员先写缺人说明，再收口；收口即冻住。
 */
public interface RollCallService {

    /** 全部车次的回程点名视图（回程点名页列表） */
    List<RollCallResponse> listAll();

    /** 单车次回程点名视图 */
    RollCallResponse get(Long planId);

    /** 发车前往名册加人；发车后 / 收口后拒绝（晚到的只能另开一趟） */
    RollCallResponse addParticipant(Long planId, String personName);

    /** 发车前从名册删人；发车后 / 收口后拒绝 */
    RollCallResponse removeParticipant(Long planId, Long participantId);

    /** 回程勾选：true 勾回到校 / false 取消勾选；只有「已发车、未收口」能改 */
    RollCallResponse markReturned(Long planId, Long participantId, boolean returned);

    /** 给未勾回的人写缺人说明；只有「已发车、未收口」能写 */
    RollCallResponse writeMissingNote(Long planId, Long participantId, String note);

    /**
     * 收口这趟：车上有人、且未勾回的人都写了缺人说明才收得了。
     * 收口后点名结果冻住，不能再勾选、不能改没说明、不能增删人。
     */
    RollCallResponse closeRoll(Long planId);
}
