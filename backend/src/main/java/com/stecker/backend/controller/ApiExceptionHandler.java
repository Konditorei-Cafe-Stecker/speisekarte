package com.stecker.backend.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;

/**
 * Turns every error of the API into an RFC 9457 problem detail
 * ({@code application/problem+json}) with a German {@code detail} text.
 *
 * Errors raised on purpose ({@code ResponseStatusException}) keep their
 * status and message. Invalid requests list every problem under
 * {@code errors}. Anything unexpected is logged and answered with a general
 * message, so no internal details reach the browser.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        List<String> errors = exception.getBindingResult()
                .getAllErrors()
                .stream()
                .map(error -> error.getDefaultMessage())
                .distinct()
                .toList();

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                status,
                errors.isEmpty() ? "Die Anfrage ist ungültig." : errors.getFirst()
        );
        problem.setTitle("Ungültige Anfrage");
        problem.setProperty("errors", errors);

        return handleExceptionInternal(exception, problem, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                status,
                "Die Anfrage konnte nicht gelesen werden."
        );
        problem.setTitle("Ungültige Anfrage");

        return handleExceptionInternal(exception, problem, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        // For example an order address with a malformed order number.
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, "Das gesuchte Element gibt es nicht.");
        problem.setTitle("Nicht gefunden");

        return handleExceptionInternal(exception, problem, headers, HttpStatus.NOT_FOUND, request);
    }

    /** Two people changed the same record at the same time. */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleConcurrentChange(OptimisticLockingFailureException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "Jemand anderes hat das gerade geändert. Bitte lade die Seite neu.");
        problem.setTitle("Gleichzeitige Änderung");

        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception exception) throws Exception {
        // Spring Security answers these itself (401, 403).
        if (exception instanceof AccessDeniedException
                || exception instanceof AuthenticationException) {
            throw exception;
        }

        log.error("Unerwarteter Fehler in der API", exception);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Es ist ein Fehler aufgetreten. Bitte versuche es gleich noch einmal."
        );
        problem.setTitle("Serverfehler");

        return problem;
    }
}
