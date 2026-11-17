package com.gongsi.collect.step;

import com.gongsi.collect.diff.MassChangeException;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;

/** 급변으로 실패한 Step 의 종료 코드를 FAILED_NO_RETRY 로 바꾸는 리스너 */
public class NoRetryExitListener implements StepExecutionListener {

    public static final String EXIT_CODE = "FAILED_NO_RETRY"; // 스케줄러가 재시도하지 않는 종료 코드

    // 급변 예외면 재시도 불가 코드로 (그 외는 그대로)
    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        ExitStatus current = stepExecution.getExitStatus();
        boolean massChange = stepExecution.getFailureExceptions().stream().anyMatch(NoRetryExitListener::isMassChange);
        return massChange ? new ExitStatus(EXIT_CODE, current.getExitDescription()) : current;
    }

    private static boolean isMassChange(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof MassChangeException) {
                return true;
            }
        }
        return false;
    }
}
