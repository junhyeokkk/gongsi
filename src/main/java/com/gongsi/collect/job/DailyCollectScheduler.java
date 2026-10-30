package com.gongsi.collect.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionException;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/** 매일 서울 날짜를 targetDate 로 dailyCollectJob 을 띄우는 스케줄러. collect.schedule.enabled=true 일 때만 켜진다 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "collect.schedule.enabled", havingValue = "true")
public class DailyCollectScheduler {

    static final String ZONE = "Asia/Seoul";
    private static final Logger log = LoggerFactory.getLogger(DailyCollectScheduler.class);

    private final JobLauncher jobLauncher;
    private final Job dailyCollectJob;
    private final Clock clock;

    @Autowired
    public DailyCollectScheduler(JobLauncher jobLauncher, Job dailyCollectJob) {
        this(jobLauncher, dailyCollectJob, Clock.systemUTC());
    }

    DailyCollectScheduler(JobLauncher jobLauncher, Job dailyCollectJob, Clock clock) {
        this.jobLauncher = jobLauncher;
        this.dailyCollectJob = dailyCollectJob;
        this.clock = clock;
    }

    // targetDate 만 식별 파라미터 - 같은 날 다시 불리면 실패분은 재시작, 완료분은 건너뛴다
    @Scheduled(cron = "${collect.schedule.cron}", zone = ZONE)
    public void launch() {
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneId.of(ZONE));
        JobParameters params = new JobParametersBuilder()
                .addString("targetDate", today.toString())
                .toJobParameters();
        try {
            JobExecution execution = jobLauncher.run(dailyCollectJob, params);
            if (execution.getStatus() == BatchStatus.COMPLETED) {
                log.info("dailyCollectJob 완료 targetDate={}", today);
            } else {
                log.error("dailyCollectJob 비정상 종료 targetDate={} status={}", today, execution.getStatus());
            }
        } catch (JobInstanceAlreadyCompleteException e) {
            log.info("dailyCollectJob 이미 완료돼 건너뜀 targetDate={}", today);
        } catch (JobExecutionException e) {
            log.error("dailyCollectJob 실행 실패 targetDate={}", today, e);
        }
    }
}
