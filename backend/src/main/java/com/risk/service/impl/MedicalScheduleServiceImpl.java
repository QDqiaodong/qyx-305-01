package com.risk.service.impl;

import com.risk.dto.request.AssignmentRequest;
import com.risk.dto.request.MedicalStaffRequest;
import com.risk.dto.response.AssignmentResponse;
import com.risk.dto.response.CoverageInfo;
import com.risk.dto.response.ReleasePermitResponse;
import com.risk.entity.MedicalAssignment;
import com.risk.entity.MedicalStaff;
import com.risk.entity.RoutePlan;
import com.risk.exception.BusinessException;
import com.risk.repository.MedicalAssignmentRepository;
import com.risk.repository.MedicalStaffRepository;
import com.risk.repository.RoutePlanRepository;
import com.risk.service.MedicalScheduleService;
import com.risk.service.ReleaseGateService;
import com.risk.service.support.GateEvaluator;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MedicalScheduleServiceImpl implements MedicalScheduleService {

    private final MedicalStaffRepository staffRepository;
    private final MedicalAssignmentRepository assignmentRepository;
    private final RoutePlanRepository planRepository;
    private final GateEvaluator gateEvaluator;
    private final ReleaseGateService releaseGate;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<MedicalStaff> listStaff() {
        return staffRepository.findAll();
    }

    @Override
    @Transactional
    public MedicalStaff createStaff(MedicalStaffRequest request) {
        MedicalStaff staff = MedicalStaff.builder()
                .staffName(request.getStaffName().trim())
                .certificateNo(trimToNull(request.getCertificateNo()))
                .phone(trimToNull(request.getPhone()))
                .title(trimToNull(request.getTitle()))
                .pediatricQualified(Boolean.TRUE.equals(request.getPediatricQualified()) ? 1 : 0)
                .build();
        return staffRepository.save(staff);
    }

    @Override
    @Transactional
    public MedicalStaff updateStaff(Long id, MedicalStaffRequest request) {
        MedicalStaff staff = staffRepository.findById(id)
                .orElseThrow(() -> new BusinessException("医护不存在: " + id));
        staff.setStaffName(request.getStaffName().trim());
        staff.setCertificateNo(trimToNull(request.getCertificateNo()));
        staff.setPhone(trimToNull(request.getPhone()));
        staff.setTitle(trimToNull(request.getTitle()));
        if (request.getPediatricQualified() != null) {
            staff.setPediatricQualified(request.getPediatricQualified() ? 1 : 0);
        }
        return staffRepository.save(staff);
    }

    @Override
    @Transactional
    public void deleteStaff(Long id) {
        MedicalStaff staff = staffRepository.findById(id)
                .orElseThrow(() -> new BusinessException("医护不存在: " + id));
        List<MedicalAssignment> active = assignmentRepository.findByStaffId(id);
        if (!active.isEmpty()) {
            String plans = active.stream()
                    .map(a -> "计划" + a.getPlanId() + "(" + a.getTravelDate() + ")")
                    .collect(Collectors.joining("、"));
            throw new BusinessException("医护「" + staff.getStaffName() + "」仍有排班在 " + plans
                    + "，请先把其改挂到其它计划，再删除该医护");
        }
        staffRepository.deleteById(id);
    }

    @Override
    @Transactional
    public AssignmentResponse getAssignment(Long planId) {
        RoutePlan plan = mustGetPlan(planId);
        CoverageInfo coverage = gateEvaluator.buildCoverage(plan);
        ReleasePermitResponse permit = releaseGate.evaluate(planId);
        return AssignmentResponse.builder()
                .planId(plan.getId())
                .planName(plan.getPlanName())
                .travelDate(plan.getTravelDate().toString())
                .coverage(coverage)
                .permitStatusAfterChange(permit.getStatus())
                .permitReason(pickPermitReason(permit))
                .build();
    }

    @Override
    @Transactional
    public AssignmentResponse assign(AssignmentRequest request) {
        RoutePlan plan = mustGetPlan(request.getPlanId());

        // 去重并校验医护都存在
        Set<Long> staffIds = new LinkedHashSet<>(request.getStaffIds());
        Map<Long, MedicalStaff> staffMap = staffRepository.findAllById(staffIds).stream()
                .collect(Collectors.toMap(MedicalStaff::getId, s -> s));
        for (Long sid : staffIds) {
            if (!staffMap.containsKey(sid)) {
                throw new BusinessException("医护不存在: " + sid);
            }
        }

        // 冲突校验：同一名医护在同一天不能同时挂两条计划（本计划旧排班不算冲突）
        List<MedicalAssignment> existing = assignmentRepository.findByPlanId(plan.getId());
        Set<Long> onDutyOld = existing.stream()
                .filter(a -> plan.getTravelDate().equals(a.getTravelDate()))
                .map(MedicalAssignment::getStaffId)
                .collect(Collectors.toSet());
        Map<Long, MedicalStaff> oldStaffMap = staffRepository.findAllById(onDutyOld).stream()
                .collect(Collectors.toMap(MedicalStaff::getId, s -> s));

        for (Long sid : staffIds) {
            long clash = assignmentRepository
                    .countByStaffIdAndTravelDateAndPlanIdNot(sid, plan.getTravelDate(), plan.getId());
            if (clash > 0) {
                MedicalStaff s = staffMap.get(sid);
                throw new BusinessException("医护「" + s.getStaffName() + "」在 " + plan.getTravelDate()
                        + " 已挂在其它计划，同一天不能同时挂两条计划，请先在该计划改挂");
            }
        }

        // 计算本次被抽离、且原本在当天在岗的医护（用于放行单落档提示）
        List<String> removedNames = new ArrayList<>();
        for (Long oldId : onDutyOld) {
            if (!staffIds.contains(oldId)) {
                MedicalStaff s = oldStaffMap.get(oldId);
                if (s != null) {
                    removedNames.add(s.getStaffName());
                }
            }
        }

        // 覆盖式重排：先彻底删除旧排班并立即落库，再插入新排班，
        // 避免 Hibernate 先 insert 后 delete 导致 (staff_id, travel_date) 唯一约束误伤“留用”的医护
        assignmentRepository.deleteByPlanId(plan.getId());
        entityManager.flush();
        for (Long sid : staffIds) {
            assignmentRepository.save(MedicalAssignment.builder()
                    .planId(plan.getId())
                    .staffId(sid)
                    .travelDate(plan.getTravelDate())
                    .build());
        }

        // 联动：若已放行单因此不再被覆盖，落到待重评
        releaseGate.onAssignmentsChanged(plan, removedNames);

        CoverageInfo coverage = gateEvaluator.buildCoverage(plan);
        ReleasePermitResponse permit = releaseGate.evaluate(plan.getId());

        return AssignmentResponse.builder()
                .planId(plan.getId())
                .planName(plan.getPlanName())
                .travelDate(plan.getTravelDate().toString())
                .coverage(coverage)
                .permitStatusAfterChange(permit.getStatus())
                .permitReason(pickPermitReason(permit))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public CoverageInfo coverage(Long planId) {
        return gateEvaluator.buildCoverage(mustGetPlan(planId));
    }

    private RoutePlan mustGetPlan(Long planId) {
        return planRepository.findById(planId)
                .orElseThrow(() -> new BusinessException("路线计划不存在: " + planId));
    }

    private String pickPermitReason(ReleasePermitResponse permit) {
        if (permit == null) {
            return null;
        }
        if (permit.getVoidReason() != null) {
            return permit.getVoidReason();
        }
        if (permit.getRecheckReason() != null) {
            return permit.getRecheckReason();
        }
        if ("PENDING".equals(permit.getStatus())) {
            return permit.getBlockReasons().isEmpty()
                    ? "待提交放行"
                    : String.join("；", permit.getBlockReasons());
        }
        return null;
    }

    private String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
