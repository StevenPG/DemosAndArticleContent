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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full end-to-end integration test for the {@code orderImportJob}.
 *
 * <p>This test launches the complete three-step job:
 * <ol>
 *   <li>{@code validateStep} — verifies the test CSV file exists (it does)</li>
 *   <li>{@code importStep} — reads, processes, and writes order records</li>
 *   <li>{@code reportStep} — logs summary counts</li>
 * </ol>
 *
 * <p>Expected results for {@code test-orders.csv}:
 * <ul>
 *   <li>Job status: {@link BatchStatus#COMPLETED}</li>
 *   <li>importStep read count: 13</li>
 *   <li>importStep write count: 10</li>
 *   <li>importStep skip count: 2</li>
 *   <li>importStep filter count: 1</li>
 * </ul>
 *
 * <p>The test CSV is resolved to an absolute path via {@link ClassPathResource} so the
 * {@code validateStep}'s file-existence check passes cleanly.
 *
 * <p>Uses H2 in PostgreSQL-compatibility mode (see {@code application-test.yml}) to
 * avoid requiring a running PostgreSQL instance.
 */
@SpringBatchTest
@SpringBootTest
@ActiveProfiles("test")
@Import(TestBatchConfig.class)
class FullImportJobTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private JobRepositoryTestUtils jobRepositoryTestUtils;

    @Autowired
    private JobLauncher jobLauncher;

    /**
     * Removes all previous job executions from the meta-data tables before each test
     * to prevent "JobInstance already exists" errors on repeated runs.
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
    @DisplayName("Full job completes with correct read/write/skip/filter counts")
    void fullJob_completesSuccessfully() throws Exception {
        // Resolve the classpath resource to an absolute filesystem path.
        // The validateStep checks File.exists(), so we need a real path, not a
        // classpath URL.
        File csvFile = new ClassPathResource("test-orders.csv").getFile();

        JobParameters params = new JobParametersBuilder()
                .addString("filePath", csvFile.getAbsolutePath())
                .addLong("runId", System.currentTimeMillis())
                .toJobParameters();

        JobExecution execution = jobLauncherTestUtils.launchJob(params);

        // ---- Overall job status ----
        assertThat(execution.getStatus())
                .as("Job should complete without errors")
                .isEqualTo(BatchStatus.COMPLETED);

        // ---- importStep counts ----
        // Find the importStep execution among all steps in this job execution
        Optional<StepExecution> importStepExec = execution.getStepExecutions()
                .stream()
                .filter(se -> "importStep".equals(se.getStepName()))
                .findFirst();

        assertThat(importStepExec)
                .as("importStep should have been executed")
                .isPresent();

        StepExecution step = importStepExec.get();

        assertThat(step.getReadCount())
                .as("All 13 data rows should be read from the CSV")
                .isEqualTo(13);

        assertThat(step.getWriteCount())
                .as("10 valid, non-zero-amount orders should be written")
                .isEqualTo(10);

        assertThat(step.getProcessSkipCount())
                .as("2 orders should be skipped (ORD011 blank customerId, ORD012 blank productCode)")
                .isEqualTo(2);

        assertThat(step.getFilterCount())
                .as("1 order should be filtered (ORD013 amount=0.00)")
                .isEqualTo(1);

        // ---- Verify all three steps executed ----
        // getStepExecutions() returns a Collection with no guaranteed order, so
        // use containsExactlyInAnyOrder rather than containsExactly.
        assertThat(execution.getStepExecutions())
                .extracting(StepExecution::getStepName)
                .as("All three steps should have been executed")
                .containsExactlyInAnyOrder("validateStep", "importStep", "reportStep");
    }
}
