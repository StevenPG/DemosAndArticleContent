package com.example.batchguide.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Spring Batch {@link JobExecutionListener} that logs timing information for every
 * job execution.
 *
 * <p>Logged information includes:
 * <ul>
 *   <li>Job name</li>
 *   <li>Start time</li>
 *   <li>End time</li>
 *   <li>Elapsed wall-clock duration in milliseconds</li>
 * </ul>
 *
 * <p>This listener is registered on the {@code orderImportJob} bean in
 * {@link com.example.batchguide.job.OrderImportJob} and is called by the
 * {@link org.springframework.batch.core.launch.JobLauncher} infrastructure — not
 * application code.
 *
 * <p>Example log output:
 * <pre>
 * [JOB START ] orderImportJob started at 2024-01-01T10:00:00
 * [JOB END   ] orderImportJob finished at 2024-01-01T10:00:05 (elapsed: 4823 ms) status=COMPLETED
 * </pre>
 */
@Component
public class JobTimingListener implements JobExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(JobTimingListener.class);

    /**
     * Called by Spring Batch immediately before the first step of the job executes.
     *
     * <p>Logs the job name and start timestamp so operators can correlate log lines
     * with specific runs in a multi-job environment.
     *
     * @param jobExecution the current job execution context; never {@code null}
     */
    @Override
    public void beforeJob(JobExecution jobExecution) {
        log.info("[JOB START ] {} started at {}",
                jobExecution.getJobInstance().getJobName(),
                jobExecution.getStartTime());
    }

    /**
     * Called by Spring Batch after all steps have completed (or after a failure).
     *
     * <p>Logs the job name, end timestamp, elapsed duration, and final
     * {@link org.springframework.batch.core.BatchStatus} so that SLA monitoring
     * tools can parse structured log lines.
     *
     * <p>The elapsed time is computed as the wall-clock difference between the
     * start and end times stored in the {@link JobExecution}.  This includes time
     * spent in all steps plus any overhead from listeners and the job repository.
     *
     * @param jobExecution the completed/failed job execution context; never {@code null}
     */
    @Override
    public void afterJob(JobExecution jobExecution) {
        LocalDateTime start = jobExecution.getStartTime();
        LocalDateTime end = jobExecution.getEndTime();

        long elapsedMs = (start != null && end != null)
                ? Duration.between(start, end).toMillis()
                : -1L;

        log.info("[JOB END   ] {} finished at {} (elapsed: {} ms) status={}",
                jobExecution.getJobInstance().getJobName(),
                end,
                elapsedMs,
                jobExecution.getStatus());
    }
}
