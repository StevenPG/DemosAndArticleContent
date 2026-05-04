package com.example.batchguide.exception;

/**
 * Thrown by {@link com.example.batchguide.processor.OrderItemProcessor} when an
 * {@link com.example.batchguide.domain.OrderRecord} fails business-rule validation.
 *
 * <p>This is a <em>checked</em> exception so that callers are forced to decide
 * explicitly whether to propagate or handle it.  Spring Batch's skip mechanism is
 * configured to catch this type and route the offending item to the
 * {@link com.example.batchguide.listener.OrderSkipListener}.
 *
 * <p>Example usage:
 * <pre>{@code
 * if (record.customerId() == null || record.customerId().isBlank()) {
 *     throw new ValidationException("customerId is blank for order: " + record.id());
 * }
 * }</pre>
 */
public class ValidationException extends Exception {

    /**
     * Constructs a new {@code ValidationException} with the supplied detail message.
     *
     * @param message human-readable description of the validation failure
     */
    public ValidationException(String message) {
        super(message);
    }

    /**
     * Constructs a new {@code ValidationException} with a detail message and a
     * root cause.
     *
     * @param message human-readable description of the validation failure
     * @param cause   the underlying cause of this exception
     */
    public ValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
