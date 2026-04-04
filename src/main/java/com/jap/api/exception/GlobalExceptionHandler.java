package com.jap.api.exception;

import com.jap.api.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(TaskNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTaskNotFound(TaskNotFoundException ex, HttpServletRequest request) {
        log.warn("Task not found: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.notFound("Task", extractId(ex.getMessage())));
    }

    @ExceptionHandler(ConcurrencyLimitReachedException.class)
    public ResponseEntity<ErrorResponse> handleConcurrencyLimit(ConcurrencyLimitReachedException ex) {
        log.warn("Concurrency limit reached: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ErrorResponse.concurrencyLimitReached(ex.getMaxConcurrent()));
    }

    @ExceptionHandler(SandboxViolationException.class)
    public ResponseEntity<ErrorResponse> handleSandboxViolation(SandboxViolationException ex) {
        log.error("Sandbox violation: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.sandboxViolation(ex.getMessage()));
    }

    @ExceptionHandler(HealingExhaustedException.class)
    public ResponseEntity<ErrorResponse> handleHealingExhausted(HealingExhaustedException ex) {
        log.warn("Healing exhausted: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.healingExhausted(ex.getErrorCategory(), ex.getRounds()));
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException ex) {
        log.error("API exception: {}", ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(
                    ex.getErrorCode(),
                    ex.getMessage(),
                    ex.getField(),
                    java.time.Instant.now(),
                    null,
                    null
                ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex) {
        String field = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField())
                .collect(Collectors.joining(", "));
        
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));

        log.warn("Validation failed: {}", message);
        
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.validation(field, message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));

        String field = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.joining(", "));

        log.warn("Constraint violation: {}", message);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.validation(field, message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        log.warn("Malformed request body: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.validation("body", "Malformed JSON request body"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = String.format("Parameter '%s' should be of type %s", 
                ex.getName(), 
                ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown");
        
        log.warn("Type mismatch: {}", message);
        
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.validation(ex.getName(), message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, HttpServletRequest request) {
        String traceId = java.util.UUID.randomUUID().toString().substring(0, 8);
        log.error("Unexpected error [traceId={}]: {}", traceId, ex.getMessage(), ex);
        
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.internal(
                    "An unexpected error occurred. Please try again later.",
                    traceId
                ));
    }

    private String extractId(String message) {
        if (message == null) return "unknown";
        int lastColon = message.lastIndexOf(": ");
        if (lastColon > 0 && lastColon + 2 < message.length()) {
            return message.substring(lastColon + 2);
        }
        return "unknown";
    }
}
