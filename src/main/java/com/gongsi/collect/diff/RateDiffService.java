package com.gongsi.collect.diff;

import org.springframework.stereotype.Service;

import java.time.LocalDate;

/** 어제 vs 오늘 요율 비교 후 rate_history + change_event 적재. DiffStep 의 핵심 */
@Service
public class RateDiffService {

    // TODO 4주차: 입력 — targetDate (오늘)
    // TODO 4주차: 비교 대상 — product_option.base_rate / max_rate / rate_min / rate_max
    //   - rate_avg 는 비교 제외 (설계 결정, V1 SQL 주석 참조)
    //   - credit_loan_option 은 rate_grade_1 ~ 13 전부 비교 (스펙 재확인 필요)
    // TODO 4주차: 로직
    //   1. 어제 last_seen_on = targetDate-1 인 option 전부 로드
    //   2. 오늘 last_seen_on = targetDate 인 option 과 (product_option.id) 로 join
    //   3. 요율 필드별로 다르면 rate_history insert, change_event (RATE_UP/DOWN) publish
    //   4. 어제만 있던 옵션 → OPTION_REMOVED
    //   5. 오늘만 있는 옵션 → NEW (옵션 단위) 또는 product_id 신규면 (NEW 상품 단위)
    // TODO 4주차: 원자성 — 하나의 트랜잭션으로 rate_history + change_event 함께 커밋

    public void detectChanges(LocalDate targetDate) {
        throw new UnsupportedOperationException("4주차 구현 예정");
    }
}
