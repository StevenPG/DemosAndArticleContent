package com.stevenpg.jackson3example.web;

import tools.jackson.core.JacksonException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps Jackson serialization failures to an HTTP response - same job as
 * jackson2-example's {@code JacksonErrorAdvice}.
 *
 * <p><b>Migration note:</b> this advice catches
 * {@code tools.jackson.core.JacksonException}, which is UNCHECKED (extends
 * {@code RuntimeException}). Confusingly, Jackson 2 ALSO has a class named
 * {@code JacksonException} - but at {@code com.fasterxml.jackson.core} and
 * extending {@code IOException} (checked) instead. Same simple name,
 * unrelated hierarchies; only the package tells them apart.
 *
 * <p>Because it's unchecked, nothing in {@code BlogPostController} is
 * FORCED to declare or catch it - which is exactly why this advice still
 * matters: an unchecked exception still needs somewhere to land before it
 * becomes a bare 500 with a stack trace leaked to the client. Spring MVC
 * routes it here the same way it would any other {@code RuntimeException}.
 */
@RestControllerAdvice
public class JacksonErrorAdvice {

    record JacksonErrorBody(String exceptionType, String message) {
    }

    @ExceptionHandler(JacksonException.class)
    ResponseEntity<JacksonErrorBody> handleJacksonFailure(JacksonException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new JacksonErrorBody(ex.getClass().getSimpleName(), ex.getOriginalMessage()));
    }
}
