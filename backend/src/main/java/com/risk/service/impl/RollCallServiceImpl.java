package com.risk.service.impl;

import com.risk.dto.response.ParticipantView;
import com.risk.dto.response.RollCallResponse;
import com.risk.entity.ReleasePermit;
import com.risk.entity.RoutePlan;
import com.risk.entity.TripParticipant;
import com.risk.exception.BusinessException;
import com.risk.repository.ReleasePermitRepository;
import com.risk.repository.RoutePlanRepository;
import com.risk.repository.TripParticipantRepository;
import com.risk.service.RollCallService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RollCallServiceImpl implements RollCallService {

    public static final String NOT_DEPARTED = "NOT_DEPARTED";
    public static final String ROLLING = "ROLLING";
    public static final String CLOSED = "CLOSED";

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final RoutePlanRepository planRepository;
    private final ReleasePermitRepository permitRepository;
    private final TripParticipantRepository participantRepository;

    @Override
    @Transactional
    public List<RollCallResponse> listAll() {
        return planRepository.findAll().stream().map(plan -> buildResponse(plan, permit(plan.getId()))).toList();
    }

    @Override
    @Transactional
    public RollCallResponse get(Long planId) {
        RoutePlan plan = mustGetPlan(planId);
        return buildResponse(plan, permit(planId));
    }

    @Override
    @Transactional
    public RollCallResponse addParticipant(Long planId, String personName) {
        RoutePlan plan = mustGetPlan(planId);
        ReleasePermit permit = permit(planId);
        if (permit != null && permit.getRollClosedAt() != null) {
            throw new BusinessException("该趟已于 " + permit.getRollClosedAt().format(DT)
                    + " 收口，点名结果已冻住，不能再往车上塞人；晚到的只能另开一趟");
        }
        if (permit != null && permit.getDepartedAt() != null) {
            // 按安全岗办：发车时刻一登记，本趟带出去的人即锁定，晚到的只能另开一趟
            throw new BusinessException("该趟已于 " + permit.getDepartedAt().format(DT)
                    + " 发车，本趟名册已锁定，不能再往车上塞人；晚到的只能另开一趟");
        }
        String name = personName == null ? "" : personName.trim();
        if (name.isEmpty()) {
            throw new BusinessException("姓名不能为空");
        }
        if (participantRepository.existsByPlanIdAndPersonName(planId, name)) {
            throw new BusinessException("「" + name + "」已在这趟的名册里，不能重复加入");
        }
        participantRepository.save(TripParticipant.builder().planId(planId).personName(name).build());
        log.info("计划 {} 发车前名册加入：{}", planId, name);
        return buildResponse(plan, permit);
    }

    @Override
    @Transactional
    public RollCallResponse removeParticipant(Long planId, Long participantId) {
        RoutePlan plan = mustGetPlan(planId);
        ReleasePermit permit = permit(planId);
        if (permit != null && permit.getRollClosedAt() != null) {
            throw new BusinessException("该趟已收口，点名结果已冻住，不能再从名册删人");
        }
        if (permit != null && permit.getDepartedAt() != null) {
            throw new BusinessException("该趟已发车，发车时带出去的人已锁定，不能再删；没上车回校的人请在回程点名中登记缺人说明");
        }
        TripParticipant p = mustGetParticipant(planId, participantId);
        participantRepository.delete(p);
        return buildResponse(plan, permit);
    }

    @Override
    @Transactional
    public RollCallResponse markReturned(Long planId, Long participantId, boolean returned) {
        RoutePlan plan = mustGetPlan(planId);
        ReleasePermit permit = requireRolling(planId);
        TripParticipant p = mustGetParticipant(planId, participantId);
        p.setReturned(returned);
        // 已回的人不再对外算“缺员”，其旧说明留在库里不展示；一旦误勾再取消，原说明还在
        participantRepository.save(p);
        log.info("计划 {} 回程点名：{} {}", planId, p.getPersonName(), returned ? "已勾回到校" : "取消勾回");
        return buildResponse(plan, permit);
    }

    @Override
    @Transactional
    public RollCallResponse writeMissingNote(Long planId, Long participantId, String note) {
        RoutePlan plan = mustGetPlan(planId);
        ReleasePermit permit = requireRolling(planId);
        TripParticipant p = mustGetParticipant(planId, participantId);
        if (p.isReturned()) {
            throw new BusinessException("「" + p.getPersonName() + "」已勾回到校，不属于缺员，无需写缺人说明");
        }
        String text = note == null ? "" : note.trim();
        if (text.isEmpty()) {
            throw new BusinessException("缺人说明不能为空：有人没上车回校，必须先写下说明，才能把这趟收口");
        }
        p.setMissingNote(text);
        participantRepository.save(p);
        return buildResponse(plan, permit);
    }

    @Override
    @Transactional
    public RollCallResponse closeRoll(Long planId) {
        RoutePlan plan = mustGetPlan(planId);
        ReleasePermit permit = permitRepository.findByPlanId(planId).orElse(null);
        if (permit != null && permit.getRollClosedAt() != null) {
            throw new BusinessException("该趟已于 " + permit.getRollClosedAt().format(DT)
                    + " 已收口，点名结果已冻住，不能重复收口、不能再改");
        }
        if (permit == null || permit.getDepartedAt() == null) {
            throw new BusinessException("该趟还没记下发车时刻，没有带出去的人可点，不能收口");
        }
        List<TripParticipant> people = participantRepository.findByPlanIdOrderByIdAsc(planId);
        if (people.isEmpty()) {
            // 车上没人点名，值班室仍当全员还没回来，这趟收不了口
            throw new BusinessException("车上没人点名，值班室仍当全员还没回来，这趟收不了口");
        }
        List<String> noNote = people.stream()
                .filter(p -> !p.isReturned() && (p.getMissingNote() == null || p.getMissingNote().isBlank()))
                .map(TripParticipant::getPersonName).toList();
        if (!noNote.isEmpty()) {
            throw new BusinessException("还有 " + noNote.size() + " 人没上车回校且没写缺人说明（"
                    + String.join("、", noNote) + "）：必须先逐个写下缺人说明，才能把这趟收口");
        }

        // 冻住点名结果：记录收口时刻，并把缺人说明快照到放行单上，之后改名册也改不没
        List<TripParticipant> missing = people.stream().filter(p -> !p.isReturned()).toList();
        String snapshot = missing.isEmpty() ? null : missing.stream()
                .map(p -> "· " + p.getPersonName() + "：" + p.getMissingNote().trim())
                .reduce((a, b) -> a + "\n" + b).orElse(null);
        permit.setRollClosedAt(LocalDateTime.now());
        permit.setRollMissingNote(snapshot);
        permitRepository.save(permit);
        log.info("计划 {} 回程点名收口，缺员 {} 人，说明已随单冻住", planId, missing.size());
        return buildResponse(plan, permit);
    }

    // ===================== 内部 =====================

    /** 要求车次处于「已发车、未收口」的可点名阶段，否则明确拒绝 */
    private ReleasePermit requireRolling(Long planId) {
        ReleasePermit permit = permitRepository.findByPlanId(planId).orElse(null);
        if (permit != null && permit.getRollClosedAt() != null) {
            throw new BusinessException("该趟已于 " + permit.getRollClosedAt().format(DT)
                    + " 已收口，点名结果已冻住：不能再勾选、不能改缺人说明、不能增删人；晚到的只能另开一趟");
        }
        if (permit == null || permit.getDepartedAt() == null) {
            throw new BusinessException("该趟还没发车，先在发车放行看板确认发车；发车前只能维护名册，不能回程点名");
        }
        return permit;
    }

    private TripParticipant mustGetParticipant(Long planId, Long participantId) {
        return participantRepository.findByIdAndPlanId(participantId, planId)
                .orElseThrow(() -> new BusinessException("该车次上没有这个人: " + participantId));
    }

    private ReleasePermit permit(Long planId) {
        return permitRepository.findByPlanId(planId).orElse(null);
    }

    private RoutePlan mustGetPlan(Long planId) {
        return planRepository.findById(planId)
                .orElseThrow(() -> new BusinessException("路线计划不存在: " + planId));
    }

    private RollCallResponse buildResponse(RoutePlan plan, ReleasePermit permit) {
        boolean departed = permit != null && permit.getDepartedAt() != null;
        boolean closed = permit != null && permit.getRollClosedAt() != null;

        String phase = closed ? CLOSED : departed ? ROLLING : NOT_DEPARTED;
        String phaseText = closed ? "已收口（点名冻住）" : departed ? "回程点名中" : "未发车";

        List<ParticipantView> views = participantRepository.findByPlanIdOrderByIdAsc(plan.getId()).stream()
                .map(p -> ParticipantView.builder()
                        .id(p.getId())
                        .personName(p.getPersonName())
                        .returned(p.isReturned())
                        .missingNote(p.getMissingNote())
                        .build())
                .toList();

        int total = views.size();
        int returned = (int) views.stream().filter(ParticipantView::isReturned).count();
        int missing = total - returned;
        int missingWithoutNote = (int) views.stream()
                .filter(v -> !v.isReturned() && (v.getMissingNote() == null || v.getMissingNote().isBlank()))
                .count();

        // 收口条件：已发车、未收口、车上有人、缺员都写了说明。
        // 仅在回程页改个口号、车上没人点名，不满足；值班室只认这一份按人勾回的点名结果。
        List<String> blockReasons = new ArrayList<>();
        if (!departed) {
            blockReasons.add("该趟还没记下发车时刻");
        }
        if (total == 0) {
            blockReasons.add("车上没人点名，值班室仍当全员还没回来，这趟收不了口");
        }
        if (missingWithoutNote > 0) {
            blockReasons.add("还有 " + missingWithoutNote + " 人没上车回校且没写缺人说明，必须先写说明才能收口");
        }
        boolean closable = departed && !closed && total > 0 && missingWithoutNote == 0;

        return RollCallResponse.builder()
                .planId(plan.getId())
                .planName(plan.getPlanName())
                .phase(phase)
                .phaseText(phaseText)
                .departedAt(departed ? permit.getDepartedAt().format(DT) : null)
                .closedAt(closed ? permit.getRollClosedAt().format(DT) : null)
                .participants(views)
                .totalCount(total)
                .returnedCount(returned)
                .missingCount(missing)
                .missingWithoutNoteCount(missingWithoutNote)
                .closable(closable)
                .frozenMissingNote(closed ? permit.getRollMissingNote() : null)
                .blockReasons(blockReasons)
                .build();
    }
}
