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

/** 완료·복구·실패·중단·마감 슬랙 문구 */
class CollectAlertMessagesTest {

    private static final CollectAttempt DONE = new CollectAttempt(BatchStatus.COMPLETED, kst(9, 30), null, "COMPLETED", null);
    private static final CollectSummary SUMMARY = new CollectSummary(29, 4, 1057, 1, 2, 89, 6, 0);

    @Test
    void 완료면_요약을_싣는다() {
        assertThat(CollectAlertMessages.afterRun(DAY, List.of(DONE), null, SUMMARY)).contains("""
                :white_check_mark: *gongsi 수집 완료* · 2026-11-17
                • *원문*  29페이지 · 4권역
                • *상품*  판매 중 1,057 (신규 1 · 종료 2)
                • *금리*  인상 89 · 인하 6
                • *오류*  정규화 0""");
    }

    @Test
    void 요약을_못_읽어도_완료는_알린다() {
        assertThat(CollectAlertMessages.afterRun(DAY, List.of(DONE), null, null))
                .contains(":white_check_mark: *gongsi 수집 완료* · 2026-11-17");
    }

    @Test
    void 실패_뒤_완료면_복구를_알린다() {
        assertThat(CollectAlertMessages.afterRun(DAY, List.of(failed(kst(9, 5)), DONE), null, SUMMARY))
                .hasValueSatisfying(m -> assertThat(m)
                        .startsWith(":large_green_circle: *gongsi 수집 복구* · 2026-11-17 (실패 1회 뒤 완료)\n• *원문*"));
    }

    @Test
    void 실패면_단계_사유_다음_재시도를_싣는다() {
        var next = DAY.atTime(9, 20).atZone(SEOUL);
        assertThat(CollectAlertMessages.afterRun(DAY, List.of(failed(kst(9, 5))), next, null)).contains("""
                :warning: *gongsi 수집 실패* · 2026-11-17 (1회째)
                • *단계*  fetchStep
                • *사유*  timeout
                • *다음*  09:20 재시도""");
    }

    @Test
    void 오늘_재시도가_없으면_수동_실행을_안내한다() {
        assertThat(CollectAlertMessages.afterRun(DAY, List.of(failed(kst(17, 5))), null, null))
                .hasValueSatisfying(m -> assertThat(m)
                        .startsWith(":red_circle: *gongsi 수집 실패*")
                        .endsWith("• *조치*  오늘 자동 재시도 끝 - 수동 실행 필요"));
    }

    @Test
    void 급변이면_중단과_원문_확인을_싣는다() {
        var mass = new CollectAttempt(BatchStatus.FAILED, kst(9, 5), "diffStep", NoRetryExitListener.EXIT_CODE, "상품 급변 <a&b>");
        assertThat(CollectAlertMessages.afterRun(DAY, List.of(mass), null, null)).contains("""
                :octagonal_sign: *gongsi 수집 중단* · 2026-11-17 (1회째)
                • *단계*  diffStep
                • *사유*  상품 급변 &lt;a&amp;b&gt;
                • *조치*  재시도 안 함 - 원문 확인 필요""");
    }

    @Test
    void 실행_못_함은_사유를_싣는다() {
        assertThat(CollectAlertMessages.launchFailed(DAY, new IllegalStateException("restart blocked")))
                .isEqualTo(":x: *gongsi 수집 실행 못 함* · 2026-11-17\n• *사유*  restart blocked");
    }

    @Test
    void 고아_실행_정리() {
        assertThat(CollectAlertMessages.orphanCleaned(DAY, 42L, "diffStep")).isEqualTo("""
                :recycle: *gongsi 중단된 수집 정리* · 2026-11-17 (재기동)
                • *실행*  #42 diffStep 에서 끊김
                • *조치*  FAILED 로 정리 - 백오프 뒤 그 단계부터 자동 재시도""");
    }
}
