package com.example.batchguide.reader;

import com.example.batchguide.domain.OrderRecord;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;

import java.math.BigDecimal;

/**
 * Spring Batch item reader configuration for the CSV order import pipeline.
 *
 * <p>This class produces a {@link FlatFileItemReader} that:
 * <ul>
 *   <li>Reads a flat CSV file whose path is supplied as a late-bound job parameter
 *       ({@code filePath}).</li>
 *   <li>Skips the first (header) line.</li>
 *   <li>Maps each subsequent line to an {@link OrderRecord} using a fixed column
 *       mapping (no annotation mapping is needed because the CSV schema is stable).</li>
 * </ul>
 *
 * <p>The {@code @StepScope} annotation ensures a fresh reader instance is created per
 * step execution, which is required for late-binding of job/step parameters and also
 * enables restart from the last committed chunk position.
 */
@Configuration
public class OrderItemReader {

    /**
     * Produces the {@link FlatFileItemReader} for {@link OrderRecord} objects.
     *
     * <p>Column mapping (0-based):
     * <pre>
     * 0 → id
     * 1 → customerId
     * 2 → productCode
     * 3 → amount   (parsed to BigDecimal)
     * 4 → orderDate
     * </pre>
     *
     * <p>The reader is {@code @StepScope} so that the {@code filePath} job parameter
     * is resolved at step-execution time, not at application-context startup.
     *
     * @param filePath absolute path to the CSV file, injected from the {@code filePath}
     *                 job parameter at step-execution time
     * @return a fully configured, thread-safe-for-single-thread {@link FlatFileItemReader}
     */
    @Bean
    @StepScope
    public FlatFileItemReader<OrderRecord> csvOrderReader(
            @Value("#{jobParameters['filePath']}") String filePath) {

        return new FlatFileItemReaderBuilder<OrderRecord>()
                .name("orderItemReader")
                // FileSystemResource accepts absolute paths from job parameters
                .resource(new FileSystemResource(filePath))
                // Skip the header row — "id,customerId,productCode,amount,orderDate"
                .linesToSkip(1)
                .delimited()
                .delimiter(",")
                .names("id", "customerId", "productCode", "amount", "orderDate")
                // Custom FieldSetMapper converts raw string fields to the record type
                .fieldSetMapper(fieldSet -> new OrderRecord(
                        fieldSet.readString("id"),
                        fieldSet.readString("customerId"),
                        fieldSet.readString("productCode"),
                        // Use BigDecimal.valueOf to avoid floating-point precision loss
                        parseBigDecimal(fieldSet.readString("amount")),
                        fieldSet.readString("orderDate")
                ))
                .build();
    }

    /**
     * Safely parses a string to {@link BigDecimal}, returning {@link BigDecimal#ZERO}
     * for blank or null input.
     *
     * <p>This guards against CSV rows where the amount column is accidentally empty,
     * which would otherwise cause a {@link NumberFormatException} during field mapping.
     *
     * @param raw the raw string value from the CSV field
     * @return parsed {@link BigDecimal}, or {@link BigDecimal#ZERO} if input is blank
     */
    private BigDecimal parseBigDecimal(String raw) {
        if (raw == null || raw.isBlank()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(raw.trim());
    }
}
