CREATE TABLE IF NOT EXISTS risk_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_code VARCHAR(50) UNIQUE NOT NULL,
    rule_name VARCHAR(100) NOT NULL,
    rule_type VARCHAR(20) NOT NULL,
    risk_level VARCHAR(10) NOT NULL,
    condition_expression TEXT NOT NULL,
    warning_message VARCHAR(500) NOT NULL,
    enabled TINYINT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS route_plan (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    plan_name VARCHAR(100) NOT NULL,
    start_location VARCHAR(200) NOT NULL,
    end_location VARCHAR(200) NOT NULL,
    waypoints TEXT,
    travel_date DATE NOT NULL,
    age_min INT DEFAULT 0,
    age_max INT DEFAULT 100,
    participant_count INT DEFAULT 0,
    status VARCHAR(20) DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS risk_check_result (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    plan_id BIGINT NOT NULL,
    rule_id BIGINT NOT NULL,
    risk_level VARCHAR(10) NOT NULL,
    risk_message VARCHAR(500) NOT NULL,
    location VARCHAR(200),
    checked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (plan_id) REFERENCES route_plan(id),
    FOREIGN KEY (rule_id) REFERENCES risk_rule(id)
);

CREATE INDEX IF NOT EXISTS idx_risk_rule_type ON risk_rule(rule_type);
CREATE INDEX IF NOT EXISTS idx_risk_rule_enabled ON risk_rule(enabled);
CREATE INDEX IF NOT EXISTS idx_check_result_plan ON risk_check_result(plan_id);
CREATE INDEX IF NOT EXISTS idx_check_result_level ON risk_check_result(risk_level);

-- 随队医护名册
CREATE TABLE IF NOT EXISTS medical_staff (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    staff_name VARCHAR(100) NOT NULL,
    certificate_no VARCHAR(50),
    phone VARCHAR(30),
    pediatric_qualified TINYINT NOT NULL DEFAULT 0,
    title VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 排班：医护按出行日挂到具体计划；(staff_id, travel_date) 唯一保证同一天不撞两条计划
CREATE TABLE IF NOT EXISTS medical_assignment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    plan_id BIGINT NOT NULL,
    staff_id BIGINT NOT NULL,
    travel_date DATE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_staff_date (staff_id, travel_date)
);
CREATE INDEX IF NOT EXISTS idx_assignment_plan ON medical_assignment(plan_id);
CREATE INDEX IF NOT EXISTS idx_assignment_staff ON medical_assignment(staff_id);

-- 风险规则版本（全库单行）：规则每改一次 +1，筛查台账按它判断“规则改了还没重筛”
CREATE TABLE IF NOT EXISTS rule_version (
    id BIGINT PRIMARY KEY,
    version BIGINT NOT NULL
);

-- 风险筛查台账（第一本账）：每个计划一条最新筛查，带行程指纹 + 规则版本
CREATE TABLE IF NOT EXISTS risk_screening (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    plan_id BIGINT NOT NULL UNIQUE,
    risk_level VARCHAR(20) NOT NULL,
    screened_at TIMESTAMP NOT NULL,
    plan_fingerprint VARCHAR(64) NOT NULL,
    rule_version BIGINT,
    snapshot_travel_date VARCHAR(30),
    snapshot_participant_count INT,
    snapshot_age_min INT,
    snapshot_age_max INT,
    snapshot_waypoints TEXT
);

-- 发车放行单（每个计划一条）：PENDING 不落库，RELEASED / VOID / STALE_RECHECK 落库
-- departed_at 一旦记下即封存：之后的规则调整不再改写这张已发车的纸
CREATE TABLE IF NOT EXISTS release_permit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    plan_id BIGINT NOT NULL UNIQUE,
    status VARCHAR(30) NOT NULL,
    risk_level VARCHAR(20),
    informed_note VARCHAR(500),
    released_at TIMESTAMP NULL,
    departed_at TIMESTAMP NULL,
    roll_closed_at TIMESTAMP NULL,
    roll_missing_note VARCHAR(2000),
    void_reason VARCHAR(500),
    recheck_reason VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 车次出行人员（发车时带出去的人）：发车前可维护，发车后名册锁定，收口后整趟点名冻住
CREATE TABLE IF NOT EXISTS trip_participant (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    plan_id BIGINT NOT NULL,
    person_name VARCHAR(100) NOT NULL,
    returned TINYINT NOT NULL DEFAULT 0,
    missing_note VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_plan_person (plan_id, person_name)
);
CREATE INDEX IF NOT EXISTS idx_trip_participant_plan ON trip_participant(plan_id);