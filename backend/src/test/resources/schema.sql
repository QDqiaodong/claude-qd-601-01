-- ============================================================================
-- H2（MySQL 兼容模式）测试专用建表脚本。
-- 与生产 schema.sql 表结构保持一致，但去掉 H2 不支持的 ENGINE / COMMENT /
-- TINYINT(1) 等 MySQL 方言，由 src/test/resources/application-test.yml
-- 在 Spring 上下文启动时自动执行。
-- ============================================================================

CREATE TABLE IF NOT EXISTS horse (
    id             BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    horse_no       VARCHAR(32)  NOT NULL UNIQUE,
    name           VARCHAR(64)  NOT NULL,
    breed          VARCHAR(32),
    gender         VARCHAR(16),
    birth_year     INT,
    status         VARCHAR(16)  NOT NULL,
    ride_level     VARCHAR(16)  NOT NULL,
    health_version BIGINT       NOT NULL DEFAULT 0,
    created_at     TIMESTAMP,
    updated_at     TIMESTAMP
);

CREATE TABLE IF NOT EXISTS horse_health (
    horse_id        BIGINT NOT NULL PRIMARY KEY,
    last_check_date DATE,
    vaccine_count   INT,
    weight_kg       DECIMAL(6, 1),
    health_note     VARCHAR(255),
    CONSTRAINT fk_hh_horse FOREIGN KEY (horse_id) REFERENCES horse (id)
);

CREATE TABLE IF NOT EXISTS stall (
    id         BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    stall_no   VARCHAR(32)   NOT NULL UNIQUE,
    barn_name  VARCHAR(64)   NOT NULL,
    area_sqm   DECIMAL(6, 1),
    status     VARCHAR(16)   NOT NULL,
    horse_id   BIGINT UNIQUE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT fk_stall_horse FOREIGN KEY (horse_id) REFERENCES horse (id)
);

CREATE TABLE IF NOT EXISTS coach (
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    coach_no   VARCHAR(32)  NOT NULL UNIQUE,
    name       VARCHAR(64)  NOT NULL,
    level      VARCHAR(16)  NOT NULL,
    phone      VARCHAR(32),
    status     VARCHAR(16)  NOT NULL,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS lesson (
    id           BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    lesson_no    VARCHAR(32)    NOT NULL UNIQUE,
    name         VARCHAR(64)    NOT NULL,
    category     VARCHAR(16)    NOT NULL,
    capacity     INT            NOT NULL,
    duration_min INT            NOT NULL,
    price        DECIMAL(10, 2) NOT NULL,
    times_cost   INT            NOT NULL,
    status       VARCHAR(16)    NOT NULL,
    created_at   TIMESTAMP,
    updated_at   TIMESTAMP
);

CREATE TABLE IF NOT EXISTS lesson_session (
    id              BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    lesson_id       BIGINT      NOT NULL,
    coach_id        BIGINT      NOT NULL,
    horse_id        BIGINT,
    session_date    DATE        NOT NULL,
    start_time      VARCHAR(5)  NOT NULL,
    end_time        VARCHAR(5)  NOT NULL,
    capacity        INT         NOT NULL,
    booked_count    INT         NOT NULL,
    status          VARCHAR(16) NOT NULL,
    health_affected BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP,
    updated_at      TIMESTAMP,
    CONSTRAINT fk_ls_lesson FOREIGN KEY (lesson_id) REFERENCES lesson (id),
    CONSTRAINT fk_ls_coach FOREIGN KEY (coach_id) REFERENCES coach (id),
    CONSTRAINT fk_ls_horse FOREIGN KEY (horse_id) REFERENCES horse (id)
);

CREATE TABLE IF NOT EXISTS club_member (
    id           BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    member_no    VARCHAR(32)    NOT NULL UNIQUE,
    name         VARCHAR(64)    NOT NULL,
    phone        VARCHAR(32),
    level        VARCHAR(16)    NOT NULL,
    card_balance DECIMAL(10, 2) NOT NULL,
    card_times   INT            NOT NULL,
    passed_basic BOOLEAN        NOT NULL,
    status       VARCHAR(16)    NOT NULL,
    created_at   TIMESTAMP,
    updated_at   TIMESTAMP
);

CREATE TABLE IF NOT EXISTS riding_record (
    id          BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    member_id   BIGINT         NOT NULL,
    session_id  BIGINT         NOT NULL,
    horse_id    BIGINT,
    record_date DATE           NOT NULL,
    start_time  VARCHAR(5)     NOT NULL,
    end_time    VARCHAR(5)     NOT NULL,
    status      VARCHAR(16)    NOT NULL,
    fee         DECIMAL(10, 2) NOT NULL,
    times_used  INT            NOT NULL,
    remark      VARCHAR(255),
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP,
    CONSTRAINT fk_rr_member FOREIGN KEY (member_id) REFERENCES club_member (id),
    CONSTRAINT fk_rr_session FOREIGN KEY (session_id) REFERENCES lesson_session (id),
    CONSTRAINT fk_rr_horse FOREIGN KEY (horse_id) REFERENCES horse (id)
);

CREATE TABLE IF NOT EXISTS health_event (
    id                 BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    event_no           VARCHAR(32) NOT NULL UNIQUE,
    horse_id           BIGINT      NOT NULL,
    occurred_at        TIMESTAMP   NOT NULL,
    severity           VARCHAR(16) NOT NULL,
    symptom            VARCHAR(500) NOT NULL,
    treatment_advice   VARCHAR(500) NOT NULL,
    expected_review_date DATE,
    status             VARCHAR(20) NOT NULL,
    registered_version BIGINT      NOT NULL DEFAULT 0,
    latest_review_id   BIGINT,
    created_by         VARCHAR(64) NOT NULL,
    created_role       VARCHAR(16) NOT NULL,
    closed_at          TIMESTAMP,
    closed_by          VARCHAR(64),
    created_at         TIMESTAMP   NOT NULL,
    updated_at         TIMESTAMP   NOT NULL,
    CONSTRAINT fk_he_horse FOREIGN KEY (horse_id) REFERENCES horse (id)
);

CREATE TABLE IF NOT EXISTS health_event_log (
    id          BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    event_id    BIGINT      NOT NULL,
    horse_id    BIGINT      NOT NULL,
    action      VARCHAR(24) NOT NULL,
    from_status VARCHAR(20),
    to_status   VARCHAR(20),
    note        VARCHAR(500) NOT NULL,
    operator    VARCHAR(64) NOT NULL,
    role        VARCHAR(16) NOT NULL,
    request_id  VARCHAR(64) UNIQUE,
    created_at  TIMESTAMP   NOT NULL,
    CONSTRAINT fk_hel_event FOREIGN KEY (event_id) REFERENCES health_event (id),
    CONSTRAINT fk_hel_horse FOREIGN KEY (horse_id) REFERENCES horse (id)
);

CREATE TABLE IF NOT EXISTS health_review (
    id               BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    event_id         BIGINT      NOT NULL,
    horse_id         BIGINT      NOT NULL,
    review_date      DATE        NOT NULL,
    result           VARCHAR(16) NOT NULL,
    conclusion       VARCHAR(500) NOT NULL,
    next_review_date DATE,
    based_version    BIGINT      NOT NULL DEFAULT 0,
    reviewer         VARCHAR(64) NOT NULL,
    role             VARCHAR(16) NOT NULL,
    request_id       VARCHAR(64) UNIQUE,
    created_at       TIMESTAMP   NOT NULL,
    CONSTRAINT fk_hr_event FOREIGN KEY (event_id) REFERENCES health_event (id),
    CONSTRAINT fk_hr_horse FOREIGN KEY (horse_id) REFERENCES horse (id)
);

CREATE TABLE IF NOT EXISTS health_event_session (
    id         BIGINT    NOT NULL AUTO_INCREMENT PRIMARY KEY,
    event_id   BIGINT    NOT NULL,
    horse_id   BIGINT    NOT NULL,
    session_id BIGINT    NOT NULL,
    marked_at  TIMESTAMP NOT NULL,
    marked_by  VARCHAR(64) NOT NULL,
    CONSTRAINT fk_hes_event UNIQUE (event_id, session_id),
    CONSTRAINT fk_hes_e FOREIGN KEY (event_id) REFERENCES health_event (id),
    CONSTRAINT fk_hes_h FOREIGN KEY (horse_id) REFERENCES horse (id),
    CONSTRAINT fk_hes_s FOREIGN KEY (session_id) REFERENCES lesson_session (id)
);

-- ============================ 值班交接模块 ============================

CREATE TABLE IF NOT EXISTS duty_shift (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    shift_no      VARCHAR(32)  NOT NULL UNIQUE,
    business_date DATE         NOT NULL,
    period        VARCHAR(16)  NOT NULL,
    manager_name  VARCHAR(64)  NOT NULL,
    handover_note VARCHAR(1000),
    status        VARCHAR(16)  NOT NULL,
    prev_shift_id BIGINT,
    started_at    TIMESTAMP    NOT NULL,
    closed_at     TIMESTAMP,
    closed_by     VARCHAR(64),
    delay_reason  VARCHAR(500),
    version       BIGINT       NOT NULL DEFAULT 0,
    list_seq      BIGINT       NOT NULL DEFAULT 0,
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL,
    CONSTRAINT fk_shift_prev FOREIGN KEY (prev_shift_id) REFERENCES duty_shift (id)
);

CREATE TABLE IF NOT EXISTS duty_item (
    id                   BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    item_no              VARCHAR(32)  NOT NULL UNIQUE,
    originating_shift_id BIGINT       NOT NULL,
    current_shift_id     BIGINT       NOT NULL,
    item_type            VARCHAR(16)  NOT NULL,
    ref_id               BIGINT,
    title                VARCHAR(200) NOT NULL,
    detail               VARCHAR(1000),
    priority             VARCHAR(16)  NOT NULL DEFAULT 'NORMAL',
    status               VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    owner_coach_id       BIGINT,
    delay_note           VARCHAR(500),
    accepted_shift_id    BIGINT,
    accepted_at          TIMESTAMP,
    accepted_by          VARCHAR(64),
    version              BIGINT       NOT NULL DEFAULT 0,
    created_at           TIMESTAMP    NOT NULL,
    updated_at           TIMESTAMP    NOT NULL,
    CONSTRAINT fk_item_origin FOREIGN KEY (originating_shift_id) REFERENCES duty_shift (id),
    CONSTRAINT fk_item_current FOREIGN KEY (current_shift_id) REFERENCES duty_shift (id),
    CONSTRAINT fk_item_accepted FOREIGN KEY (accepted_shift_id) REFERENCES duty_shift (id),
    CONSTRAINT fk_item_coach FOREIGN KEY (owner_coach_id) REFERENCES coach (id)
);

CREATE TABLE IF NOT EXISTS duty_item_note (
    id         BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    item_id    BIGINT        NOT NULL,
    note       VARCHAR(1000) NOT NULL,
    operator   VARCHAR(64)   NOT NULL,
    role       VARCHAR(16)   NOT NULL,
    coach_id   BIGINT,
    created_at TIMESTAMP     NOT NULL,
    CONSTRAINT fk_note_item FOREIGN KEY (item_id) REFERENCES duty_item (id)
);

CREATE TABLE IF NOT EXISTS duty_acceptance (
    id            BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    item_id       BIGINT      NOT NULL UNIQUE,
    shift_id      BIGINT      NOT NULL,
    acceptor_name VARCHAR(64) NOT NULL,
    note          VARCHAR(500),
    request_id    VARCHAR(64) UNIQUE,
    accepted_at   TIMESTAMP   NOT NULL,
    CONSTRAINT fk_accept_item FOREIGN KEY (item_id) REFERENCES duty_item (id),
    CONSTRAINT fk_accept_shift FOREIGN KEY (shift_id) REFERENCES duty_shift (id)
);
