package com.example.batchguide.test;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;

import com.example.batchguide.config.TestBatchConfig;

import java.io.File;
import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test that runs the {@code importStep} in isolation via
 * {@link JobLauncherTestUtils#launchStep(String, JobParameters)}.
 *
 * <p>Running a single step bypasses the {@code validateStep} (file existence check)
 * and {@code reportStep}, which allows focused testing of just the chunk-oriented
 * read/process/write logic.
 *
 * <p>Expected results for {@code test-orders.csv}:
 * <ul>
 *   <li>Read count: 13 (all data rows)</li>
 *   <li>Write count: 10 (valid orders with non-zero amounts)</li>
 *   <li>Skip count: 2 (ORD011: blank customerId, ORD012: blank productCode)</li>
 *   <li>Filter count: 1 (ORD013: amount = 0.00)</li>
 * </ul>
 *
 * <p>{@code @SpringBatchTest} auto-configures {@link JobLauncherTestUtils} and
 * {@link JobRepositoryTestUtils}.  The test profile activates H2 via
 * {@code application-test.yml}.
 */
@SpringBatchTest
@SpringBootTest
@ActiveProfiles("test")
@Import(TestBatchConfig.class)
class ImportStepTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private JobRepositoryTestUtils jobRepositoryTestUtils;

    @Autowired
    private JobLauncher jobLauncher;

    /**
     * Cleans up Spring Batch meta-data tables between test runs to avoid
     * "JobInstance already exists" exceptions when re-running with the same parameters.
     *
     * Also explicitly wires the JobLauncher into JobLauncherTestUtils — in Spring
     * Batch 6 the setter is no longer @Autowired so @SpringBatchTest no longer
     * injects it automatically.
     */
    @BeforeEach
    void cleanUp() {
        jobLauncherTestUtils.setJobLauncher(jobLauncher);
        jobRepositoryTestUtils.removeJobExecutions();
    }

    @Test
    @DisplayName("importStep reads 13, writes 10, skips 2, filters 1")
    void importStep_readsWritesSkipsAndFiltersCorrectly() throws Exception {
        // Resolve the test CSV to an absolute path so the FlatFileItemReader can find it
        File csvFile = new ClassPathResource("test-orders.csv").getFile();

        JobParameters params = new JobParametersBuilder()
                .addString("filePath", csvFile.getAbsolutePath())
                // Unique run ID prevents collisions across test runs
                .addLong("runId", System.currentTimeMillis())
                .toJobParameters();

        // Launch only the importStep — validateStep and reportStep are not executed
        JobExecution execution = jobLauncherTestUtils.launchStep("importStep", params);

        assertThat(execution.getStatus())
                .as("importStep should complete successfully")
                .isEqualTo(BatchStatus.COMPLETED);

        // Extract the step execution to inspect item counts
        Collection<StepExecution> stepExecutions = execution.getStepExecutions();
        assertThat(stepExecutions).hasSize(1);

        StepExecution stepExecution = stepExecutions.iterator().next();

        assertThat(stepExecution.getReadCount())
                .as("All 13 data rows should be read from the CSV")
                .isEqualTo(13);

        assertThat(stepExecution.getWriteCount())
                .as("10 valid, non-zero-amount orders should be written")
                .isEqualTo(10);

        assertThat(stepExecution.getProcessSkipCount())
                .as("2 orders should be skipped due to ValidationException (ORD011, ORD012)")
                .isEqualTo(2);

        assertThat(stepExecution.getFilterCount())
                .as("1 order should be filtered (ORD013 has amount=0.00)")
                .isEqualTo(1);
    }
}
