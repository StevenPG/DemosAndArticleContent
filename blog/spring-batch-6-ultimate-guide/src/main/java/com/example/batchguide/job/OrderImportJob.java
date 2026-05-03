package com.example.batchguide.job;

import com.example.batchguide.domain.Order;
import com.example.batchguide.domain.OrderRecord;
import com.example.batchguide.exception.ValidationException;
import com.example.batchguide.listener.JobTimingListener;
import com.example.batchguide.listener.OrderSkipListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.partition.PartitionHandler;
import org.springframework.batch.core.partition.support.TaskExecutorPartitionHandler;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Main Spring Batch job configuration for the Order Import pipeline.
 *
 * <p>The job consists of three sequential steps:
 * <ol>
 *   <li>{@code validateStep} — tasklet that confirms the input CSV file exists at the
 *       path supplied via the {@code filePath} job parameter.  Fails fast before chunk
 *       processing begins if the file is missing.</li>
 *   <li>{@code importStep} — chunk-oriented step (chunk size 5) that reads CSV rows,
 *       validates and enriches them, and writes the results to the {@code orders}
 *       table.  Configured with fault tolerance: skip up to 10
 *       {@link ValidationException}s and retry {@link TransientDataAccessException}
 *       up to 3 times per item.</li>
 *   <li>{@code reportStep} — tasklet that queries the {@code orders} and
 *       {@code skipped_orders} tables and logs a summary of the run.</li>
 * </ol>
 *
 * <p>A fourth step — {@code partitionedImportStep} — is defined as a separate bean
 * to demonstrate parallelism via {@link TaskExecutorPartitionHandler} with 4 threads
 * and {@link ColumnRangePartitioner}.  It is not wired into the main job flow so the
 * sequential job remains the canonical runnable example.
 *
 * <p>All steps use the {@code batchTransactionManager} (a
 * {@link org.springframework.jdbc.support.JdbcTransactionManager}) qualified
 * explicitly to avoid ambiguity with the {@code JpaTransactionManager} that Spring
 * Data JPA registers automatically.
 */
@Configuration
public class OrderImportJob {

    private static final Logger log = LoggerFactory.getLogger(OrderImportJob.class);

    // -------------------------------------------------------------------------
    // Step 1: Validate — confirm the source file exists
    // -------------------------------------------------------------------------

    /**
     * Tasklet step that asserts the input CSV file exists before any chunk processing
     * begins.
     *
     * <p>Failing here produces a {@code FAILED} job status immediately, which is
     * preferable to discovering the missing file mid-way through a large run.
     *
     * @param jobRepository      Spring Batch job repository for step meta-data
     * @param transactionManager the batch-specific JDBC transaction manager
     * @return a configured {@link Step} that validates file existence
     */
    @Bean
    public Step validateStep(
            JobRepository jobRepository,
            @Qualifier("batchTransactionManager") PlatformTransactionManager transactionManager) {

        return new StepBuilder("validateStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    // Retrieve the filePath from the job parameters at runtime —
                    // this cannot be done via @Value because we're inside a lambda.
                    String filePath = (String) chunkContext
                            .getStepContext()
                            .getJobParameters()
                            .get("filePath");

                    if (filePath == null || filePath.isBlank()) {
                        throw new IllegalArgumentException(
                                "Job parameter 'filePath' is required but was not provided.");
                    }

                    FileSystemResource resource = new FileSystemResource(filePath);
                    if (!resource.exists()) {
                        throw new IllegalArgumentException(
                                "Input file does not exist: " + filePath);
                    }

                    log.info("[VALIDATE  ] Input file confirmed: {}", filePath);
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    // -------------------------------------------------------------------------
    // Step 2: Import — chunk-oriented read/process/write with fault tolerance
    // -------------------------------------------------------------------------

    /**
     * Chunk-oriented step that reads {@link OrderRecord} objects from CSV, processes
     * them, and writes the resulting {@link Order} entities to the database.
     *
     * <p>Fault-tolerance configuration:
     * <ul>
     *   <li>Skip up to 10 {@link ValidationException}s.</li>
     *   <li>Retry {@link TransientDataAccessException} up to 3 times per item.</li>
     * </ul>
     *
     * @param jobRepository      Spring Batch job repository
     * @param transactionManager the batch-specific JDBC transaction manager
     * @param reader             the CSV reader (step-scoped, injected as proxy)
     * @param processor          the validation/enrichment processor (step-scoped)
     * @param writer             the JDBC upsert writer
     * @param skipListener       records skipped items to the audit table
     * @return a fully configured chunk-oriented {@link Step}
     */
    @Bean
    public Step importStep(
            JobRepository jobRepository,
            @Qualifier("batchTransactionManager") PlatformTransactionManager transactionManager,
            ItemReader<OrderRecord> reader,
            ItemProcessor<OrderRecord, Order> processor,
            ItemWriter<Order> writer,
            OrderSkipListener skipListener) {

        return new StepBuilder("importStep", jobRepository)
                .<OrderRecord, Order>chunk(5, transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .faultTolerant()
                .skip(ValidationException.class)
                .skipLimit(10)
                .retry(TransientDataAccessException.class)
                .retryLimit(3)
                .noRetry(ValidationException.class)
                .listener(skipListener)
                .build();
    }

    // -------------------------------------------------------------------------
    // Step 3: Report — log summary counts
    // -------------------------------------------------------------------------

    /**
     * Tasklet step that queries the {@code orders} and {@code skipped_orders} tables
     * after the import step and logs a human-readable summary.
     *
     * @param jobRepository      Spring Batch job repository
     * @param transactionManager the batch-specific JDBC transaction manager
     * @param jdbcTemplate       JDBC template for executing count queries
     * @return a configured report {@link Step}
     */
    @Bean
    public Step reportStep(
            JobRepository jobRepository,
            @Qualifier("batchTransactionManager") PlatformTransactionManager transactionManager,
            JdbcTemplate jdbcTemplate) {

        return new StepBuilder("reportStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    Integer orderCount = jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM orders", Integer.class);
                    Integer skippedCount = jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM skipped_orders", Integer.class);

                    log.info("[REPORT    ] Run complete — orders written: {}, orders skipped: {}",
                            orderCount, skippedCount);

                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    // -------------------------------------------------------------------------
    // Partitioned step (demonstration — not wired into the main job flow)
    // -------------------------------------------------------------------------

    /**
     * {@link PartitionHandler} that distributes partition work across a thread pool.
     *
     * <p>Uses {@link SimpleAsyncTaskExecutor} with 4 concurrent threads.  For
     * production use, replace with a bounded {@code ThreadPoolTaskExecutor}.
     *
     * @param importStep the worker step executed for each partition
     * @return a configured {@link TaskExecutorPartitionHandler}
     */
    @Bean
    public PartitionHandler partitionHandler(Step importStep) {
        TaskExecutorPartitionHandler handler = new TaskExecutorPartitionHandler();
        handler.setStep(importStep);
        handler.setGridSize(4);
        handler.setTaskExecutor(new SimpleAsyncTaskExecutor("batch-partition-"));
        return handler;
    }

    /**
     * Partitioned wrapper step that delegates to {@code importStep} workers via the
     * {@link ColumnRangePartitioner}.
     *
     * <p>This step is defined as a bean to demonstrate the partitioning pattern but
     * is <em>not</em> included in the main job flow.  To use it, replace
     * {@code importStep} with {@code partitionedImportStep} in the job definition.
     *
     * @param jobRepository      Spring Batch job repository
     * @param transactionManager the batch-specific JDBC transaction manager
     * @param partitioner        divides the CSV file into line ranges (step-scoped)
     * @param partitionHandler   distributes work to worker threads
     * @return a configured partitioned {@link Step}
     */
    @Bean
    public Step partitionedImportStep(
            JobRepository jobRepository,
            @Qualifier("batchTransactionManager") PlatformTransactionManager transactionManager,
            ColumnRangePartitioner partitioner,
            PartitionHandler partitionHandler) {

        return new StepBuilder("partitionedImportStep", jobRepository)
                .partitioner("importStep", partitioner)
                .partitionHandler(partitionHandler)
                .build();
    }

    // -------------------------------------------------------------------------
    // Job assembly
    // -------------------------------------------------------------------------

    /**
     * Assembles the three-step {@code orderImportJob}.
     *
     * <p>Step flow: {@code validateStep} → {@code importStep} → {@code reportStep}.
     *
     * @param jobRepository  Spring Batch job repository
     * @param validateStep   file existence check tasklet
     * @param importStep     chunk-oriented CSV import step
     * @param reportStep     post-run summary tasklet
     * @param timingListener logs job start/end timing
     * @return the assembled {@link Job} bean
     */
    @Bean
    public Job configuredImportJob(
            JobRepository jobRepository,
            Step validateStep,
            Step importStep,
            Step reportStep,
            JobTimingListener timingListener) {

        return new JobBuilder("orderImportJob", jobRepository)
                .listener(timingListener)
                .start(validateStep)
                .next(importStep)
                .next(reportStep)
                .build();
    }
}
