package io.coreplatform.ai.api.exception;

import io.coreplatform.ai.application.exception.ProviderOperationException;
import io.coreplatform.ai.application.exception.ProviderProbeException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ProviderOperationException.class)
    ResponseEntity<ProblemDetail> handleProviderOperation(
            ProviderOperationException exception,
            HttpServletRequest request
    ) {
        return problem(
                HttpStatus.valueOf(exception.httpStatus()),
                exception.errorCode(),
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(ProviderProbeException.class)
    ResponseEntity<ProblemDetail> handleProviderProbe(
            ProviderProbeException exception,
            HttpServletRequest request
    ) {
        ProblemDetail detail = baseProblem(
                HttpStatus.UNPROCESSABLE_ENTITY,
                exception.errorCode(),
                exception.getMessage(),
                request
        );
        detail.setProperty("userMessage", exception.userMessage());
        detail.setProperty("providerStatus", exception.statusCode());
        return ResponseEntity.unprocessableEntity().body(detail);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        ProblemDetail detail = baseProblem(
                HttpStatus.BAD_REQUEST,
                "REQUEST_VALIDATION_FAILED",
                "Request validation failed",
                request
        );
        detail.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(detail);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class
    })
    ResponseEntity<ProblemDetail> handleInvalidRequest(Exception exception, HttpServletRequest request) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "REQUEST_INVALID",
                "Request payload or parameter is invalid",
                request
        );
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("Unhandled request failure for {}", request.getRequestURI(), exception);
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "CORE_AI_INTERNAL_ERROR",
                "An unexpected error occurred",
                request
        );
    }

    private ResponseEntity<ProblemDetail> problem(
            HttpStatus status,
            String errorCode,
            String message,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(status).body(baseProblem(status, errorCode, message, request));
    }

    private ProblemDetail baseProblem(
            HttpStatus status,
            String errorCode,
            String message,
            HttpServletRequest request
    ) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, message);
        detail.setType(URI.create("https://core-platform.dev/problems/" + errorCode.toLowerCase()));
        detail.setTitle(status.getReasonPhrase());
        detail.setProperty("errorCode", errorCode);
        detail.setProperty("traceId", MDC.get("traceId"));
        detail.setProperty("path", request.getRequestURI());
        return detail;
    }
}
