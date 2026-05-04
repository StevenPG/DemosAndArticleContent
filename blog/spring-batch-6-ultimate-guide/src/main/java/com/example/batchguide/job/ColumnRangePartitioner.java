package com.example.batchguide.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.partition.Partitioner;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/**
 * Spring Batch {@link Partitioner} that divides a flat CSV file into contiguous line
 * ranges for parallel processing.
 *
 * <p>This partitioner is used by the {@code partitionedImportStep} in
 * {@link OrderImportJob} to create independent {@link ExecutionContext} instances —
 * one per worker thread.  Each context carries {@code minLine} and {@code maxLine}
 * keys that tell a partitioned reader which lines to process.
 *
 * <p>Algorithm:
 * <ol>
 *   <li>Count the total number of data lines in the file (excluding the header).</li>
 *   <li>Divide evenly into {@code gridSize} partitions; the last partition absorbs
 *       any remainder lines.</li>
 *   <li>Emit one {@link ExecutionContext} per partition with {@code minLine} (1-based,
 *       inclusive) and {@code maxLine} (1-based, inclusive) set.</li>
 * </ol>
 *
 * <p>Example — 13 data lines, gridSize=4:
 * <pre>
 * Partition 0: minLine=1,  maxLine=3
 * Partition 1: minLine=4,  maxLine=6
 * Partition 2: minLine=7,  maxLine=9
 * Partition 3: minLine=10, maxLine=13   ← absorbs remainder
 * </pre>
 *
 * <p>{@code @StepScope} is required so that the {@code filePath} job parameter can
 * be late-bound at partition-step execution time.
 */
@Component
@StepScope
public class ColumnRangePartitioner implements Partitioner {

    private static final Logger log = LoggerFactory.getLogger(ColumnRangePartitioner.class);

    /** Key written to each partition's {@link ExecutionContext} for the start line (1-based, inclusive). */
    public static final String MIN_LINE_KEY = "minLine";

    /** Key written to each partition's {@link ExecutionContext} for the end line (1-based, inclusive). */
    public static final String MAX_LINE_KEY = "maxLine";

    /**
     * Absolute path to the CSV file, injected from the {@code filePath} job parameter.
     * Late-bound because of {@code @StepScope}.
     */
    private final String filePath;

    /**
     * Constructs the partitioner with the late-bound {@code filePath} job parameter.
     *
     * @param filePath absolute path to the source CSV file
     */
    public ColumnRangePartitioner(
            @Value("#{jobParameters['filePath']}") String filePath) {
        this.filePath = filePath;
    }

    /**
     * Creates {@code gridSize} partition contexts that together cover all data lines
     * of the CSV file.
     *
     * @param gridSize the desired number of partitions; must be &gt; 0
     * @return a map from partition name (e.g. {@code "partition0"}) to
     *         {@link ExecutionContext} containing {@code minLine}/{@code maxLine}
     * @throws IllegalStateException if the CSV file cannot be read to count lines
     */
    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {
        int totalLines = countDataLines();
        log.info("[PARTITION ] File '{}' has {} data lines, splitting into {} partitions",
                filePath, totalLines, gridSize);

        Map<String, ExecutionContext> partitions = new HashMap<>(gridSize);

        // Integer division gives the base size; remainder is added to the last partition.
        int baseSize = totalLines / gridSize;
        int remainder = totalLines % gridSize;

        int currentLine = 1; // 1-based line numbers relative to data rows (header excluded)

        for (int i = 0; i < gridSize; i++) {
            // Last partition absorbs any leftover lines from the division remainder
            int partitionSize = (i == gridSize - 1) ? baseSize + remainder : baseSize;

            // Guard: if partitionSize is 0 (more partitions than lines), emit an empty range
            if (partitionSize <= 0) {
                partitionSize = 0;
            }

            ExecutionContext context = new ExecutionContext();
            context.putInt(MIN_LINE_KEY, currentLine);
            context.putInt(MAX_LINE_KEY, currentLine + partitionSize - 1);
            context.putString("filePath", filePath); // pass file path to the partitioned reader

            String partitionName = "partition" + i;
            partitions.put(partitionName, context);

            log.debug("[PARTITION ] {} minLine={} maxLine={}",
                    partitionName, currentLine, currentLine + partitionSize - 1);

            currentLine += partitionSize;
        }

        return partitions;
    }

    /**
     * Counts the number of data lines in the CSV file (total lines minus the one
     * header line).
     *
     * <p>Uses a buffered reader for memory efficiency — only line counts are tracked,
     * not the full content of each line.
     *
     * @return number of data rows available for processing
     * @throws IllegalStateException if the file cannot be opened or read
     */
    private int countDataLines() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileSystemResource(filePath).getInputStream()))) {

            int count = 0;
            while (reader.readLine() != null) {
                count++;
            }
            // Subtract 1 for the header row
            return Math.max(0, count - 1);

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to count lines in file: " + filePath, e);
        }
    }
}
