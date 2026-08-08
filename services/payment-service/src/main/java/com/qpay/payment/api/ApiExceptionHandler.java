package com.qpay.payment.api;

import com.qpay.common.model.ApiError;
import com.qpay.payment.application.PaymentException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(SecurityException.class)
    ResponseEntity<ApiError> unauthorized(SecurityException exception, HttpServletRequest request) {
        return response(HttpStatus.UNAUTHORIZED, "INVALID_WEBHOOK_SIGNATURE", exception.getMessage(), request, List.of());
    }

    @ExceptionHandler({PaymentException.class, IllegalStateException.class, IllegalArgumentException.class})
    ResponseEntity<ApiError> paymentError(RuntimeException exception, HttpServletRequest request) {
        return response(HttpStatus.UNPROCESSABLE_ENTITY, "PAYMENT_RULE_VIOLATION", exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<ApiError.Detail> details = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiError.Detail(error.getField(), error.getDefaultMessage())).toList();
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed.", request, details);
    }

    private ResponseEntity<ApiError> response(
            HttpStatus status, String code, String message, HttpServletRequest request, List<ApiError.Detail> details) {
        return ResponseEntity.status(status).body(new ApiError(
                code, message, request.getHeader("X-Request-Id"), Instant.now(), details));
    }
}

