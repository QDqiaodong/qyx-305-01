package com.risk.util;

import com.risk.entity.RoutePlan;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 行程指纹工具：筛查只对“出行日期、参与人数、年龄段、途经点”这四项负责。
 * 计划这四项任一被改动，指纹就对不上，旧筛查/旧报告立即失效，必须重新筛。
 */
public final class PlanFingerprint {

    private PlanFingerprint() {
    }

    /** 规范化途经点：去空白、去空项、统一以英文逗号连接，避免“a, b”和“a,b”被当成不同行程。 */
    public static String normalizeWaypoints(String waypoints) {
        if (waypoints == null || waypoints.isBlank()) {
            return "";
        }
        return Arrays.stream(waypoints.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(","));
    }

    public static List<String> waypointList(String waypoints) {
        String normalized = normalizeWaypoints(waypoints);
        if (normalized.isEmpty()) {
            return List.of();
        }
        return Arrays.asList(normalized.split(","));
    }

    public static String of(RoutePlan plan) {
        String raw = String.join("|",
                String.valueOf(plan.getTravelDate()),
                String.valueOf(plan.getParticipantCount()),
                String.valueOf(plan.getAgeMin()),
                String.valueOf(plan.getAgeMax()),
                normalizeWaypoints(plan.getWaypoints()));
        return sha256(raw);
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("生成行程指纹失败", e);
        }
    }
}
