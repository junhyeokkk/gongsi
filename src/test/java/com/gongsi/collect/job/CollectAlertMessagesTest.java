package com.gongsi.collect.job;

import com.gongsi.collect.step.NoRetryExitListener;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;

import java.util.List;

import static com.gongsi.collect.job.RetryPolicyTest.DAY;
import static com.gongsi.collect.job.RetryPolicyTest.SEOUL;
import static com.gongsi.collect.job.RetryPolicyTest.failed;
import static com.gongsi.collect.job.RetryPolicyTest.kst;
import static org.assertj.core.api.Assertions.assertThat;

/** 실패·복구·마감 슬랙 문구 */
class CollectAlertMessagesTest {

    private static final CollectAttempt DONE = new CollectAttempt(BatchStatus.COMPLETED, kst(9, 30), null, "COMPLETED", null);

    @Test
    void 완료면_요약을_싣는다() {
        var summary = new CollectSummary(29, 4, 1057, 1, 2, 89, 6, 0);
        assertThat(CollectAlertMessages.afterRun(DAY, List.of(DONE), null, summary))
                .contains("[gongsi] 2026-11-17 수집 완료\n"
                        + "원문 29페이지 · 4권역 / 판매 중 상품 1,057 (신규 1, 종료 2)\n"
                        + "금리 변동 인상 89 · 인하 6 / 정규화 오류 0");
    }

    @Test
    void 요약을_못_읽어도_완료는_알린다() {
        assertThat(CollectAlertMessages.afterRun(DAY, List.of(DONE), null, null))
                .contains("[gongsi] 2026-11-17 수집 완료");
    }

    @Test
    void 실패_뒤_완료면_복구를_알린다() {
        assertThat(CollectAlertMessages.afterRun(DAY, List.of(failed(kst(9, 5)), DONE), null, null))
                .contains("[gongsi] 2026-11-17 수집 복구 - 실패 1회 뒤 완료");
    }

    @Test
    void 실패면_단계_사유_다음_재시도를_싣는다() {
        var next = DAY.atTime(9, 20).atZone(SEOUL);
        assertThat(CollectAlertMessages.afterRun(DAY, List.of(failed(kst(9, 5))), next, null))
                .contains("[gongsi] 2026-11-17 수집 실패 1회째 (fetchStep)\n사유: timeout\n다음 재시도 09:20");
    }

    @Test
    void 오늘_재시도가_없으면_수동_실행을_안내한다() {
        assertThat(CollectAlertMessages.afterRun(DAY, List.of(failed(kst(17, 5))), null, null))
                .hasValueSatisfying(m -> assertThat(m).endsWith("오늘 자동 재시도 끝 - 수동 실행 필요"));
    }

    @Test
    void 급변이면_재시도_안_함과_원문_확인을_싣는다() {
        var mass = new CollectAttempt(BatchStatus.FAILED, kst(9, 5), "diffStep", NoRetryExitListener.EXIT_CODE, "상품 급변");
        assertThat(CollectAlertMessages.afterRun(DAY, List.of(mass), null, null))
                .contains("[gongsi] 2026-11-17 수집 실패 1회째 (diffStep)\n사유: 상품 급변\n재시도 안 함 - 원문 확인 필요");
    }
}
