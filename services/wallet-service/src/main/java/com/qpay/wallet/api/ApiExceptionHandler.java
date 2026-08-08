package com.qpay.wallet.api;

import com.qpay.common.model.ApiError;
import com.qpay.wallet.application.LedgerPostingException;
import com.qpay.wallet.application.WalletNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(WalletNotFoundException.class)
    ResponseEntity<ApiError> notFound(WalletNotFoundException exception, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "WALLET_NOT_FOUND", exception.getMessage(), request, List.of());
    }

    @ExceptionHandler({LedgerPostingException.class, IllegalArgumentException.class})
    ResponseEntity<ApiError> businessRule(RuntimeException exception, HttpServletRequest request) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, "LEDGER_RULE_VIOLATION", exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> conflict(DataIntegrityViolationException exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "RESOURCE_CONFLICT",
                "The resource or business operation already exists.", request, List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<ApiError.Detail> details = exception.getBindingResult().getFieldErrors().stream()
                .map(field -> new ApiError.Detail(field.getField(), field.getDefaultMessage()))
                .toList();
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed.", request, details);
    }

    private ResponseEntity<ApiError> error(
            HttpStatus status, String code, String message, HttpServletRequest request, List<ApiError.Detail> details) {
        String requestId = request.getHeader("X-Request-Id");
        return ResponseEntity.status(status).body(new ApiError(code, message, requestId, Instant.now(), details));
    }
}

