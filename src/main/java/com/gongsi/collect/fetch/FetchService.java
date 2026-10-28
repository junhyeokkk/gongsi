package com.gongsi.collect.fetch;

import com.gongsi.common.ProductType;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/** FetchStep 이 호출하는 오케스트레이터. 상품군×금융권×페이지 반복해서 raw_snapshot 적재 */
@Service
public class FetchService {

    private final FssOpenFinanceClient client;
    private final RawSnapshotRepository rawRepo;

    public FetchService(FssOpenFinanceClient client, RawSnapshotRepository rawRepo) {
        this.client = client;
        this.rawRepo = rawRepo;
    }

    // TODO 2주차: fetchAll(targetDate) 구현
    //   - ProductType 5종 × 금융권 코드 N 개 × 페이지 반복
    //   - 응답 totalCount/pageNo 보고 다음 페이지 호출 여부 결정
    //   - 상품군별로 페이지 단위 트랜잭션 (일부 실패해도 다른 상품군은 계속)
    //   - 모든 상품군 raw 적재 완료 후 수집 요약 로그 (총 페이지/row 추정치)

    public void fetchAll(LocalDate targetDate) {
        throw new UnsupportedOperationException("2주차 구현 예정");
    }

    // TODO 2주차: fetchOne(targetDate, type) — 상품군 단일 수집 (디버깅/재실행용)
}
