package com.risk.config;

import com.risk.entity.MedicalStaff;
import com.risk.entity.RiskRule;
import com.risk.entity.RuleVersion;
import com.risk.repository.MedicalStaffRepository;
import com.risk.repository.RiskRuleRepository;
import com.risk.repository.RuleVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final RiskRuleRepository ruleRepository;
    private final MedicalStaffRepository staffRepository;
    private final RuleVersionRepository ruleVersionRepository;

    @Override
    public void run(String... args) {
        // 规则版本单行：不存在则补建（筛查台账按它判断“规则改了还没重筛”）
        if (!ruleVersionRepository.existsById(RuleVersion.SINGLETON_ID)) {
            ruleVersionRepository.save(RuleVersion.builder()
                    .id(RuleVersion.SINGLETON_ID)
                    .version(1L)
                    .build());
            log.info("规则版本台账初始化完成，当前版本 v1");
        }

        if (ruleRepository.count() == 0) {
            log.info("初始化风险规则数据...");
            List<RiskRule> rules = Arrays.asList(
                    RiskRule.builder()
                            .ruleCode("WEATHER_001")
                            .ruleName("雨季天气风险")
                            .ruleType("WEATHER")
                            .riskLevel("HIGH")
                            .conditionExpression("RAINY_SEASON")
                            .warningMessage("当前出行日期处于雨季，存在暴雨、洪水等自然灾害风险，建议调整出行日期或做好充分防护")
                            .enabled(1)
                            .build(),
                    
                    RiskRule.builder()
                            .ruleCode("WEATHER_002")
                            .ruleName("高温天气风险")
                            .ruleType("WEATHER")
                            .riskLevel("MEDIUM")
                            .conditionExpression("HOT_WEATHER")
                            .warningMessage("夏季高温天气出行，存在中暑风险，建议避开高温时段，携带充足饮水")
                            .enabled(1)
                            .build(),
                    
                    RiskRule.builder()
                            .ruleCode("WEATHER_003")
                            .ruleName("严寒天气风险")
                            .ruleType("WEATHER")
                            .riskLevel("MEDIUM")
                            .conditionExpression("COLD_WEATHER")
                            .warningMessage("冬季严寒天气出行，存在冻伤风险，建议做好保暖措施")
                            .enabled(1)
                            .build(),
                    
                    RiskRule.builder()
                            .ruleCode("WEATHER_004")
                            .ruleName("节假日出行风险")
                            .ruleType("WEATHER")
                            .riskLevel("LOW")
                            .conditionExpression("HOLIDAY")
                            .warningMessage("节假日出行人流量大，可能影响行程，建议错峰出行")
                            .enabled(1)
                            .build(),
                    
                    RiskRule.builder()
                            .ruleCode("TRAFFIC_001")
                            .ruleName("周末交通拥堵")
                            .ruleType("TRAFFIC")
                            .riskLevel("MEDIUM")
                            .conditionExpression("PEAK_HOUR")
                            .warningMessage("周末出行可能遇到交通拥堵，建议提前规划出发时间")
                            .enabled(1)
                            .build(),
                    
                    RiskRule.builder()
                            .ruleCode("TRAFFIC_002")
                            .ruleName("山路行驶风险")
                            .ruleType("TRAFFIC")
                            .riskLevel("HIGH")
                            .conditionExpression("MOUNTAIN_ROAD")
                            .warningMessage("路线包含山路，存在弯道多、坡度大等交通安全风险")
                            .enabled(1)
                            .build(),
                    
                    RiskRule.builder()
                            .ruleCode("TRAFFIC_003")
                            .ruleName("沿海道路风险")
                            .ruleType("TRAFFIC")
                            .riskLevel("MEDIUM")
                            .conditionExpression("COASTAL_ROAD")
                            .warningMessage("路线包含沿海道路，可能受海风、潮汐影响")
                            .enabled(1)
                            .build(),
                    
                    RiskRule.builder()
                            .ruleCode("AGE_001")
                            .ruleName("未成年人出行风险")
                            .ruleType("AGE")
                            .riskLevel("HIGH")
                            .conditionExpression("CHILDREN")
                            .warningMessage("出行人员包含未成年人，需加强监护措施，确保安全")
                            .enabled(1)
                            .build(),
                    
                    RiskRule.builder()
                            .ruleCode("AGE_002")
                            .ruleName("老年人出行风险")
                            .ruleType("AGE")
                            .riskLevel("MEDIUM")
                            .conditionExpression("ELDERLY")
                            .warningMessage("出行人员包含老年人，需考虑体力限制，安排合理休息")
                            .enabled(1)
                            .build(),
                    
                    RiskRule.builder()
                            .ruleCode("AGE_003")
                            .ruleName("年龄跨度大风险")
                            .ruleType("AGE")
                            .riskLevel("LOW")
                            .conditionExpression("WIDE_AGE_RANGE")
                            .warningMessage("出行人员年龄跨度较大，需兼顾不同年龄段需求")
                            .enabled(1)
                            .build(),
                    
                    RiskRule.builder()
                            .ruleCode("VENUE_001")
                            .ruleName("高海拔场地风险")
                            .ruleType("VENUE")
                            .riskLevel("HIGH")
                            .conditionExpression("HIGH_ALTITUDE")
                            .warningMessage("目的地为高海拔地区，存在高原反应风险，建议提前做好准备")
                            .enabled(1)
                            .build(),
                    
                    RiskRule.builder()
                            .ruleCode("VENUE_002")
                            .ruleName("水上活动风险")
                            .ruleType("VENUE")
                            .riskLevel("HIGH")
                            .conditionExpression("WATER_ACTIVITY")
                            .warningMessage("路线包含水上活动，存在溺水风险，需确保救生设备齐全")
                            .enabled(1)
                            .build(),
                    
                    RiskRule.builder()
                            .ruleCode("VENUE_003")
                            .ruleName("人员密集场所风险")
                            .ruleType("VENUE")
                            .riskLevel("MEDIUM")
                            .conditionExpression("CROWDED_AREA")
                            .warningMessage("途经景区或广场等人员密集场所，需注意人身安全和财物保管")
                            .enabled(1)
                            .build(),
                    
                    RiskRule.builder()
                            .ruleCode("CUSTOM_001")
                            .ruleName("幼儿出行风险")
                            .ruleType("CUSTOM")
                            .riskLevel("HIGH")
                            .conditionExpression("AGE_MIN_LESS_THAN_6")
                            .warningMessage("出行人员包含6岁以下幼儿，需特别加强监护")
                            .enabled(1)
                            .build(),
                    
                    RiskRule.builder()
                            .ruleCode("CUSTOM_002")
                            .ruleName("超大规模团队风险")
                            .ruleType("CUSTOM")
                            .riskLevel("MEDIUM")
                            .conditionExpression("PARTICIPANTS_MORE_THAN_50")
                            .warningMessage("参与人数超过50人，需做好组织管理和应急准备")
                            .enabled(1)
                            .build(),
                    
                    RiskRule.builder()
                            .ruleCode("CUSTOM_003")
                            .ruleName("危险地形风险")
                            .ruleType("CUSTOM")
                            .riskLevel("HIGH")
                            .conditionExpression("LOCATION_CONTAINS_DANGER")
                            .warningMessage("路线包含悬崖、峭壁等危险地形，存在安全隐患")
                            .enabled(1)
                            .build()
            );

            ruleRepository.saveAll(rules);
            log.info("风险规则数据初始化完成，共 {} 条规则", rules.size());
        }

        if (staffRepository.count() == 0) {
            log.info("初始化随队医护数据...");
            List<MedicalStaff> staff = Arrays.asList(
                    MedicalStaff.builder()
                            .staffName("李医生")
                            .certificateNo("PED-1001")
                            .phone("13800000001")
                            .title("儿科主治医师")
                            .pediatricQualified(1)
                            .build(),
                    MedicalStaff.builder()
                            .staffName("王护士")
                            .certificateNo("NUR-2002")
                            .phone("13800000002")
                            .title("急诊护士")
                            .pediatricQualified(1)
                            .build(),
                    MedicalStaff.builder()
                            .staffName("赵医生")
                            .certificateNo("GEN-3003")
                            .phone("13800000003")
                            .title("全科主治医师")
                            .pediatricQualified(0)
                            .build(),
                    MedicalStaff.builder()
                            .staffName("陈护士")
                            .certificateNo("NUR-4004")
                            .phone("13800000004")
                            .title("外科护士")
                            .pediatricQualified(0)
                            .build()
            );
            staffRepository.saveAll(staff);
            log.info("随队医护数据初始化完成，共 {} 人", staff.size());
        }
    }
}