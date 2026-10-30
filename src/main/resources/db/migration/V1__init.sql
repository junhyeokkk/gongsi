-- =============================================================
-- V1__init.sql  —  gongsi 전체 스키마 (2026-10-28 합의 완료)
-- 생성 순서: FK 의존성에 따라 피참조 테이블 먼저
-- =============================================================

-- ─── 수집 전용 ────────────────────────────────────────────────

CREATE TABLE raw_snapshot (
    id           BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fetched_on   DATE         NOT NULL,  -- 어느 날짜 수집인지
    product_type VARCHAR(8)   NOT NULL,  -- DEP / SAV / MORT / RENT / CRED
    fin_grp_no   VARCHAR(6)   NOT NULL,  -- 020000 은행 / 030300 저축은행
    page_no      SMALLINT     NOT NULL,
    payload      JSONB        NOT NULL,  -- 응답 원문
    fetched_at   TIMESTAMPTZ  NOT NULL,  -- 실제로 받은 시각
    CONSTRAINT uk_raw_snapshot UNIQUE (fetched_on, product_type, fin_grp_no, page_no)
);

CREATE TABLE normalize_error (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fetched_on      DATE         NOT NULL,
    product_type    VARCHAR(8)   NOT NULL,
    raw_snapshot_id BIGINT       NOT NULL REFERENCES raw_snapshot (id),
    list_kind       VARCHAR(8)   NOT NULL,  -- BASE / OPTION
    record_index    INT          NOT NULL,
    reason          VARCHAR(32)  NOT NULL,  -- MISSING_REQUIRED / TYPE_MISMATCH / UNKNOWN_ENUM / ORPHAN_OPTION
    detail          TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_normalize_error_fetched_on ON normalize_error (fetched_on);

-- ─── 공유 ─────────────────────────────────────────────────────

-- 상품군별 전용 컬럼 7개는 nullable로 한 테이블에 둠 (합의 항목 2 확정)
CREATE TABLE product (
    id                       BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    inst_code                VARCHAR(7)   NOT NULL,
    product_code             VARCHAR(64)  NOT NULL,
    product_type             VARCHAR(8)   NOT NULL,   -- DEP / SAV / MORT / RENT
    inst_name                VARCHAR(100) NOT NULL,
    product_name             VARCHAR(200) NOT NULL,
    join_way                 VARCHAR(100),
    join_deny                SMALLINT,                -- 1 제한없음 / 2 서민전용 / 3 일부제한
    join_member              TEXT,
    etc_note                 TEXT,
    max_limit                BIGINT,                  -- 예·적금만
    special_condition        TEXT,                    -- 예·적금만
    maturity_interest_note   TEXT,                    -- 예·적금만
    loan_limit_note          TEXT,                    -- 대출만
    early_repayment_fee_note TEXT,                    -- 대출만
    delinquency_rate_note    TEXT,                    -- 대출만
    incidental_expense_note  TEXT,                    -- 대출만
    disclosure_month         CHAR(6)      NOT NULL,
    disclosure_start_date    DATE,
    disclosure_end_date      DATE,                    -- "99991231" → null 정규화
    submitted_at             TIMESTAMP,
    is_active                BOOLEAN      NOT NULL,
    first_seen_on            DATE         NOT NULL,
    last_seen_on             DATE         NOT NULL,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_product UNIQUE (inst_code, product_code, product_type)
);

CREATE INDEX idx_product_type_active ON product (product_type, is_active);
CREATE INDEX idx_product_inst_code   ON product (inst_code);

-- option_key 구성: 예·적금 save_term|rate_type|reserve_type, 대출 mortgage_type|repay_type|lend_rate_type 등
CREATE TABLE product_option (
    id             BIGINT         GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id     BIGINT         NOT NULL REFERENCES product (id),
    option_key     VARCHAR(32)    NOT NULL,
    save_term      SMALLINT,                           -- 예·적금만
    rate_type      CHAR(1),                            -- S 단리 / M 복리
    reserve_type   CHAR(1),                            -- S 정액 / F 자유 (적금만)
    base_rate      NUMERIC(5, 2),                      -- 예·적금만
    max_rate       NUMERIC(5, 2),                      -- 예·적금만
    mortgage_type  CHAR(1),                            -- A 아파트 / E 아파트외 (주담대만)
    repay_type     CHAR(1),                            -- D 분할 / S 만기일시 (대출만)
    lend_rate_type CHAR(1),                            -- F 고정 / C 변동 (대출만)
    rate_min       NUMERIC(5, 2),                      -- 대출만
    rate_max       NUMERIC(5, 2),                      -- 대출만
    rate_avg       NUMERIC(5, 2),                      -- 대출만, 변동 감지 제외
    is_active      BOOLEAN        NOT NULL,
    last_seen_on   DATE           NOT NULL,
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uk_product_option UNIQUE (product_id, option_key)
);

CREATE INDEX idx_product_option_product_term ON product_option (product_id, save_term);

CREATE TABLE rate_history (
    id                BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_option_id BIGINT        NOT NULL REFERENCES product_option (id),
    changed_on        DATE          NOT NULL,
    field             VARCHAR(16)   NOT NULL,  -- base_rate / max_rate / rate_min / rate_max
    old_value         NUMERIC(5, 2),           -- 신규 옵션이면 null
    new_value         NUMERIC(5, 2),           -- 옵션 소멸이면 null
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uk_rate_history UNIQUE (product_option_id, changed_on, field)
);

CREATE INDEX idx_rate_history_option_date ON rate_history (product_option_id, changed_on DESC);

-- 신용대출: 자연키에 credit_product_type 포함 (합의 항목 1 확정)
CREATE TABLE credit_loan_product (
    id                    BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    inst_code             VARCHAR(7)   NOT NULL,
    product_code          VARCHAR(64)  NOT NULL,
    credit_product_type   CHAR(1)      NOT NULL,  -- 1 일반 / 2 마이너스한도 / 3 카드론
    inst_name             VARCHAR(100) NOT NULL,
    product_name          VARCHAR(200) NOT NULL,
    join_way              VARCHAR(100),
    cb_name               VARCHAR(16),            -- KCB / NICE
    disclosure_month      CHAR(6)      NOT NULL,
    disclosure_start_date DATE,
    disclosure_end_date   DATE,
    submitted_at          TIMESTAMP,
    is_active             BOOLEAN      NOT NULL,
    first_seen_on         DATE         NOT NULL,
    last_seen_on          DATE         NOT NULL,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_credit_loan_product UNIQUE (inst_code, product_code, credit_product_type)
);

CREATE INDEX idx_credit_loan_product_active ON credit_loan_product (is_active);
CREATE INDEX idx_credit_loan_product_inst   ON credit_loan_product (inst_code);

-- 구간 8개를 가로 컬럼으로 (행 펼침보다 1:1 비교가 단순, 합의 항목 1)
CREATE TABLE credit_loan_option (
    id                     BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    credit_loan_product_id BIGINT        NOT NULL REFERENCES credit_loan_product (id),
    lend_rate_type         CHAR(1)       NOT NULL,  -- A 대출 / B 기준 / C 가산 / D 가감조정
    rate_grade_1           NUMERIC(5, 2),
    rate_grade_4           NUMERIC(5, 2),
    rate_grade_5           NUMERIC(5, 2),
    rate_grade_6           NUMERIC(5, 2),
    rate_grade_10          NUMERIC(5, 2),
    rate_grade_11          NUMERIC(5, 2),
    rate_grade_12          NUMERIC(5, 2),
    rate_grade_13          NUMERIC(5, 2),
    rate_avg               NUMERIC(5, 2),
    is_active              BOOLEAN       NOT NULL,
    last_seen_on           DATE          NOT NULL,
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uk_credit_loan_option UNIQUE (credit_loan_product_id, lend_rate_type)
);

CREATE TABLE credit_rate_history (
    id                    BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    credit_loan_option_id BIGINT        NOT NULL REFERENCES credit_loan_option (id),
    changed_on            DATE          NOT NULL,
    field                 VARCHAR(16)   NOT NULL,  -- rate_grade_1 … rate_grade_13 / rate_avg
    old_value             NUMERIC(5, 2),
    new_value             NUMERIC(5, 2),
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uk_credit_rate_history UNIQUE (credit_loan_option_id, changed_on, field)
);

CREATE INDEX idx_credit_rate_history_option_date
    ON credit_rate_history (credit_loan_option_id, changed_on DESC);

-- product_id XOR credit_loan_product_id — 둘 중 하나만 채움
CREATE TABLE change_event (
    id                     BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id             BIGINT       REFERENCES product (id),
    product_option_id      BIGINT       REFERENCES product_option (id),
    credit_loan_product_id BIGINT       REFERENCES credit_loan_product (id),
    credit_loan_option_id  BIGINT       REFERENCES credit_loan_option (id),
    event_type             VARCHAR(16)  NOT NULL,  -- NEW / RATE_UP / RATE_DOWN / CLOSED / OPTION_REMOVED
    occurred_on            DATE         NOT NULL,
    payload                JSONB,
    consumed_at            TIMESTAMPTZ,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_change_event_product_xor
        CHECK ((product_id IS NOT NULL) <> (credit_loan_product_id IS NOT NULL))
);

-- COALESCE 표현식 인덱스로 멱등 보장 (일반 UNIQUE 제약은 표현식 미지원)
CREATE UNIQUE INDEX uk_change_event_product
    ON change_event (product_id, COALESCE(product_option_id, 0), event_type, occurred_on)
    WHERE product_id IS NOT NULL;

CREATE UNIQUE INDEX uk_change_event_credit
    ON change_event (credit_loan_product_id, COALESCE(credit_loan_option_id, 0), event_type, occurred_on)
    WHERE credit_loan_product_id IS NOT NULL;

-- 제공 측이 미처리분만 빠르게 읽는 부분 인덱스
CREATE INDEX idx_change_event_pending
    ON change_event (consumed_at)
    WHERE consumed_at IS NULL;

-- ─── 제공 전용 ────────────────────────────────────────────────

CREATE TABLE alert_subscription (
    id           BIGINT         GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email        VARCHAR(255)   NOT NULL,
    product_type VARCHAR(8),    -- null = 전체
    save_term    SMALLINT,
    min_rate     NUMERIC(5, 2),
    inst_code    VARCHAR(7),
    event_types  VARCHAR(64)    NOT NULL,
    is_active    BOOLEAN        NOT NULL,
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE TABLE alert_delivery (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    subscription_id BIGINT       NOT NULL REFERENCES alert_subscription (id),
    change_event_id BIGINT       NOT NULL REFERENCES change_event (id),
    status          VARCHAR(16)  NOT NULL,  -- PENDING / SENT / FAILED
    attempt_count   SMALLINT     NOT NULL DEFAULT 0,
    sent_at         TIMESTAMPTZ,
    last_error      TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_alert_delivery UNIQUE (subscription_id, change_event_id)
);

CREATE TABLE audit_log (
    id           BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor        VARCHAR(64)  NOT NULL,
    target_table VARCHAR(32)  NOT NULL,
    target_id    BIGINT       NOT NULL,
    action       VARCHAR(16)  NOT NULL,  -- UPDATE / RERUN
    before       JSONB,
    after        JSONB,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
