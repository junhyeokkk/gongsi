package com.gongsi.collect.job;

import com.gongsi.collect.job.RetryPolicy.Decision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecutionException;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/** 깨어날 때마다 오늘 실행 기록을 보고 dailyCollectJob 을 띄우거나 기다리는 스케줄러. collect.schedule.enabled=true 일 때만 켜진다 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "collect.schedule.enabled", havingValue = "true")
public class DailyCollectScheduler {

    static final String ZONE = "Asia/Seoul";
    private static final ZoneId SEOUL = ZoneId.of(ZONE);
    private static final Logger log = LoggerFactory.getLogger(DailyCollectScheduler.class);

    private final JobLauncher jobLauncher;
    private final Job dailyCollectJob;
    private final AttemptHistory history;
    private final CollectAlert alert;
    private final CronExpression cron;
    private final Clock clock;

    @Autowired
    public DailyCollectScheduler(JobLauncher jobLauncher, Job dailyCollectJob, AttemptHistory history,
                                 CollectAlert alert, @Value("${collect.schedule.cron}") String cron) {
        this(jobLauncher, dailyCollectJob, history, alert, cron, Clock.systemUTC());
    }

    DailyCollectScheduler(JobLauncher jobLauncher, Job dailyCollectJob, AttemptHistory history,
                          CollectAlert alert, String cron, Clock clock) {
        this.jobLauncher = jobLauncher;
        this.dailyCollectJob = dailyCollectJob;
        this.history = history;
        this.alert = alert;
        this.cron = CronExpression.parse(cron);
        this.clock = clock;
    }

    // 1. 오늘 실행 기록으로 판단  2. 실행  3. 결과 알림 (실패·복구·마감)
    @Scheduled(cron = "${collect.schedule.cron}", zone = ZONE)
    public void launch() {
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, SEOUL);
        Decision decision = RetryPolicy.decide(history.of(today), now);
        if (decision.action() != RetryPolicy.Action.RUN) {
            log.info("dailyCollectJob 건너뜀 targetDate={} action={} retryAfter={}",
                    today, decision.action(), decision.retryAfter());
            return;
        }
        try {
            jobLauncher.run(dailyCollectJob, DailyCollectJobConfig.params(today));
        } catch (JobExecutionException e) {
            log.error("dailyCollectJob 실행 못 함 targetDate={}", today, e);
            alert.send(CollectAlertMessages.launchFailed(today, e));
            return;
        }
        List<CollectAttempt> after = history.of(today);
        log.info("dailyCollectJob 종료 targetDate={} result={}", today, after.isEmpty() ? null : after.get(after.size() - 1));
        CollectAlertMessages.afterRun(today, after, RetryPolicy.nextRun(after, cron, today, SEOUL).orElse(null))
                .ifPresent(alert::send);
    }
}
