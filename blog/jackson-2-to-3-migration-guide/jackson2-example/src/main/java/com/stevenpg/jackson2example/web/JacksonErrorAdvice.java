package com.stevenpg.jackson2example.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps Jackson serialization failures to an HTTP response.
 *
 * <p><b>Migration note:</b> this advice exists to catch
 * {@code com.fasterxml.jackson.core.JsonProcessingException}, a CHECKED
 * exception. Every controller method that can throw it - see
 * {@code BlogPostController.sampleAsRawJson} and {@code .brokenAsRawJson} -
 * must say so in its {@code throws} clause, and this advice must be wired
 * up for Spring MVC to turn it into a clean response instead of a raw 500.
 *
 * <p>In Jackson 3, {@code tools.jackson.core.JacksonException} is UNCHECKED,
 * so nothing forces a controller method to declare it - which also means
 * it's easy to FORGET to handle it. The jackson3-example sibling advice
 * still exists and still catches it (unchecked exceptions still propagate
 * and still deserve a clean HTTP mapping) - the difference is the compiler
 * no longer reminds you to write it.
 */
@RestControllerAdvice
public class JacksonErrorAdvice {

    record JacksonErrorBody(String exceptionType, String message) {
    }

    @ExceptionHandler(JsonProcessingException.class)
    ResponseEntity<JacksonErrorBody> handleJacksonFailure(JsonProcessingException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new JacksonErrorBody(ex.getClass().getSimpleName(), ex.getOriginalMessage()));
    }
}
