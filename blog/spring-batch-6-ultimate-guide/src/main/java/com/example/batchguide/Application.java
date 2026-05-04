package com.example.batchguide;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Entry point for the Spring Boot / Spring Batch Order Import application.
 *
 * <p>The application disables Spring Boot's automatic job execution on startup
 * ({@code spring.batch.job.enabled=false}) in favor of a custom {@link CommandLineRunner}
 * that triggers the job with a default or user-supplied file path.
 */
@SpringBootApplication
public class Application {

    private static final Logger log = LoggerFactory.getLogger(Application.class);

    private static final String DEFAULT_CSV_CONTENT = """
            id,customerId,productCode,amount,orderDate
            ORD001,C001,PROD-A,99.99,2024-01-01
            ORD002,C002,PROD-B,149.99,2024-01-02
            ORD003,C003,PROD-C,49.99,2024-01-03
            ORD004,C004,PROD-D,199.99,2024-01-04
            ORD005,C005,PROD-E,299.99,2024-01-05
            ORD006,C001,PROD-A,79.99,2024-01-06
            ORD007,C002,PROD-B,59.99,2024-01-07
            ORD008,C003,PROD-C,89.99,2024-01-08
            ORD009,C004,PROD-D,119.99,2024-01-09
            ORD010,C005,PROD-E,159.99,2024-01-10
            """;

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    /**
     * CLI runner that triggers the batch job on startup.
     *
     * <p>By default, it creates a temporary CSV file with sample data.
     * This can be overridden via command-line arguments to use an existing file.
     *
     * <p>Usage: {@code java -jar app.jar [filePath=/path/to/orders.csv]}
     *
     * @param jobLauncher       Spring Batch job launcher
     * @param configuredImportJob the primary order import job
     * @return a {@link CommandLineRunner} that executes the job
     */
    @Bean
    @SuppressWarnings("removal")
    public CommandLineRunner jobRunner(JobLauncher jobLauncher, Job configuredImportJob) {
        return args -> {
            String filePath = null;
            for (String arg : args) {
                if (arg.startsWith("filePath=")) {
                    filePath = arg.substring("filePath=".length());
                }
            }

            if (filePath == null) {
                filePath = createTempOrdersFile().toAbsolutePath().toString();
                log.info("No filePath provided. Created temporary demo file: {}", filePath);
            } else {
                log.info("Starting order import job. Using provided filePath: {}", filePath);
            }

            JobParameters params = new JobParametersBuilder()
                    .addString("filePath", filePath)
                    .addLong("runId", System.currentTimeMillis())
                    .toJobParameters();
            jobLauncher.run(configuredImportJob, params);
        };
    }

    private Path createTempOrdersFile() throws IOException {
        Path tempFile = Files.createTempFile("demo-orders", ".csv");
        Files.writeString(tempFile, DEFAULT_CSV_CONTENT);
        tempFile.toFile().deleteOnExit();
        return tempFile;
    }
}
