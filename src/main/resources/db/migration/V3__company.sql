-- 금융회사 - companySearch baseList 한 건. product.inst_code 와 fin_co_no 로 이어진다
-- 원문 레코드에 권역이 없어 요청 권역(raw_snapshot.fin_grp_no)을 옮겨 적는다
CREATE TABLE company (
    id                 BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fin_co_no          VARCHAR(7)   NOT NULL,
    fin_grp_no         VARCHAR(6)   NOT NULL,  -- 020000 은행 / 030300 저축은행
    company_name       VARCHAR(100) NOT NULL,
    homepage_url       VARCHAR(300),
    call_tel           VARCHAR(20),            -- 콜센터, 숫자만
    disclosure_contact TEXT,                   -- 공시 담당 부서·연락처 자유 서술
    disclosure_month   CHAR(6)      NOT NULL,
    is_active          BOOLEAN      NOT NULL,
    first_seen_on      DATE         NOT NULL,
    last_seen_on       DATE         NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_company UNIQUE (fin_co_no)
);

CREATE INDEX idx_company_grp_active ON company (fin_grp_no, is_active);

-- 회사 × 시·도 영업점 유무 - companySearch optionList 한 건. 회사마다 17개 시·도가 모두 온다
CREATE TABLE company_area (
    id           BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id   BIGINT       NOT NULL REFERENCES company (id),
    area_code    VARCHAR(2)   NOT NULL,  -- 01 서울 … 17 제주
    area_name    VARCHAR(16)  NOT NULL,
    has_branch   BOOLEAN      NOT NULL,  -- exis_yn
    last_seen_on DATE         NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_company_area UNIQUE (company_id, area_code)
);

CREATE INDEX idx_company_area_branch ON company_area (area_code, has_branch);
