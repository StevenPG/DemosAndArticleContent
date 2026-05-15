package com.stevenpg.fuzzingdemo.exception;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Converts validation failures and unexpected exceptions into structured JSON error responses.
 *
 * Extends {@link ResponseEntityExceptionHandler} so that all standard Spring MVC infrastructure
 * exceptions ({@code HttpMessageNotReadableException}, {@code NoHandlerFoundException},
 * {@code HandlerMethodValidationException}, etc.) are automatically converted to appropriate
 * 4xx responses. This is critical for correct fuzzing behaviour: the fuzz tests assert that
 * no 5xx responses occur, so every expected failure path must return a 4xx, not a 500.
 *
 * Without this inheritance, a bare {@code @ExceptionHandler(Exception.class)} catch-all would
 * intercept Spring's own infrastructure exceptions and incorrectly return 500 for malformed
 * requests, empty request bodies, unknown routes, and other valid rejection scenarios — causing
 * every fuzz test to fail even when there is no planted bug.
 *
 * From a fuzzing perspective this class enforces the invariant:
 *   400 / 404 / 405 / 415 → expected, the fuzzer should keep exploring
 *   500                    → unexpected, the fuzzer records the crashing input
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    // -------------------------------------------------------------------------
    // Bean Validation on @RequestBody — triggered by @Valid
    // -------------------------------------------------------------------------

    /**
     * Overrides the parent's default handler to return our own JSON error shape
     * instead of Spring's default Problem Details format.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        List<String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .toList();

        return ResponseEntity.badRequest()
                .body(errorBody(HttpStatus.BAD_REQUEST, "Validation failed", errors));
    }

    // -------------------------------------------------------------------------
    // Bean Validation on @RequestParam / @PathVariable — triggered by @Validated
    // -------------------------------------------------------------------------

    /**
     * Handles {@link ConstraintViolationException} thrown when a method-level constraint
     * (e.g. {@code @Pattern} on a path variable) is violated.
     *
     * In Spring Framework 7, path variable and query param validation also raises
     * {@code HandlerMethodValidationException}, which is handled by the parent class
     * and returns 400 automatically. This handler covers the remaining cases where a
     * {@link ConstraintViolationException} bubbles up from service-layer validation.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolations(
            ConstraintViolationException ex) {

        List<String> errors = ex.getConstraintViolations()
                .stream()
                .map(cv -> cv.getPropertyPath() + ": " + cv.getMessage())
                .toList();

        return ResponseEntity.badRequest()
                .body(errorBody(HttpStatus.BAD_REQUEST, "Constraint violation", errors));
    }

    // -------------------------------------------------------------------------
    // Catch-all — surfaces truly unexpected exceptions as 500s
    // -------------------------------------------------------------------------

    /**
     * Last-resort handler for any exception not covered by a more specific handler.
     * The fuzz tests assert that no 500 responses occur, so this handler being triggered
     * means the fuzzer has found a code path that should be fixed.
     *
     * Note: this handler is NOT called for standard Spring MVC exceptions because those
     * are handled by the parent class ({@link ResponseEntityExceptionHandler}) first.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
        return ResponseEntity.internalServerError()
                .body(errorBody(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Unexpected error: " + ex.getClass().getSimpleName() + " — " + ex.getMessage(),
                        List.of()));
    }

    // -------------------------------------------------------------------------

    private Map<String, Object> errorBody(HttpStatus status, String message, List<String> details) {
        return Map.of(
                "timestamp", Instant.now().toString(),
                "status",    status.value(),
                "error",     status.getReasonPhrase(),
                "message",   message,
                "details",   details
        );
    }
}
