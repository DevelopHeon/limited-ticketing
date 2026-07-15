package com.example.limitedticketing.presentation.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgumentException(
        IllegalArgumentException exception,
        HttpServletRequest request
    ) {
        log.warn("Invalid request: {}", exception.getMessage());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ApiErrorResponse(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                "INVALID_REQUEST",
                exception.getMessage(),
                request.getRequestURI()
            ));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFoundException(
        NoSuchElementException exception,
        HttpServletRequest request
    ) {
        log.warn("Resource not found: {}", exception.getMessage());

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ApiErrorResponse(
                Instant.now(),
                HttpStatus.NOT_FOUND.value(),
                "RESOURCE_NOT_FOUND",
                exception.getMessage(),
                request.getRequestURI()
            ));
    }

    @ExceptionHandler({IllegalStateException.class, DataIntegrityViolationException.class})
    public ResponseEntity<ApiErrorResponse> handleBusinessConflict(
        RuntimeException exception,
        HttpServletRequest request
    ) {
        log.warn("Business conflict: {}", exception.getMessage());

        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ApiErrorResponse(
                Instant.now(),
                HttpStatus.CONFLICT.value(),
                "BUSINESS_CONFLICT",
                exception.getMessage(),
                request.getRequestURI()
            ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleException(
        Exception exception,
        HttpServletRequest request
    ) {
        log.error("Unexpected server error", exception);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ApiErrorResponse(
                Instant.now(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "INTERNAL_SERVER_ERROR",
                "Unexpected server error",
                request.getRequestURI()
            ));
    }
}
