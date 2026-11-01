package com.ledger.wallet.controller;

import com.ledger.wallet.dto.response.ErrorResponse;
import com.ledger.wallet.exception.ErrorCode;
import com.ledger.wallet.exception.WalletLedgerException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Translates everything thrown below the web layer into one error shape.
 *
 * <p>Business failures already know their status and code, so they need a single handler.
 * The rest are mapped here, and anything unrecognised becomes a 500 with a generic message:
 * stack traces and SQL never reach a client.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(WalletLedgerException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(WalletLedgerException ex, HttpServletRequest request) {
        log.warn("{} on {} {}: {}", ex.getErrorCode(), request.getMethod(), request.getRequestURI(), ex.getMessage());
        return build(ex.getHttpStatus(), ex.getErrorCode(), ex.getMessage(), request, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                          HttpServletRequest request) {
        List<ErrorResponse.FieldError> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ErrorResponse.FieldError(error.getField(), error.getDefaultMessage()))
                .sorted(Comparator.comparing(ErrorResponse.FieldError::field))
                .toList();
        return build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                "Request validation failed", request, fields);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException ex,
                                                             HttpServletRequest request) {
        String code = TransactionController.IDEMPOTENCY_HEADER.equalsIgnoreCase(ex.getHeaderName())
                ? ErrorCode.IDEMPOTENCY_KEY_ERROR
                : ErrorCode.VALIDATION_ERROR;
        return build(HttpStatus.BAD_REQUEST, code,
                "Missing required header: " + ex.getHeaderName(), request, null);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> handleMalformed(Exception ex, HttpServletRequest request) {
        String message = ex instanceof MethodArgumentTypeMismatchException mismatch
                ? "Invalid value for '" + mismatch.getName() + "'"
                : "Request body is missing or malformed";
        return build(HttpStatus.BAD_REQUEST, ErrorCode.MALFORMED_REQUEST, message, request, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethod(HttpRequestMethodNotSupportedException ex,
                                                      HttpServletRequest request) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED,
                ex.getMethod() + " is not supported here", request, null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex,
                                                          HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND,
                "No endpoint " + request.getMethod() + " " + request.getRequestURI(), request, null);
    }

    /**
     * The row lock could not be taken within its timeout, or an optimistic version check
     * failed. Both mean someone else is working on this wallet; the client may retry with
     * the same Idempotency-Key, which is safe by construction.
     */
    @ExceptionHandler({PessimisticLockingFailureException.class, ObjectOptimisticLockingFailureException.class})
    public ResponseEntity<ErrorResponse> handleLockContention(Exception ex, HttpServletRequest request) {
        log.warn("Lock contention on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.CONFLICT, ErrorCode.CONCURRENT_MODIFICATION,
                "The wallet is busy. Retry with the same Idempotency-Key.", request, null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleIntegrity(DataIntegrityViolationException ex,
                                                         HttpServletRequest request) {
        log.warn("Integrity violation on {} {}: {}", request.getMethod(), request.getRequestURI(),
                ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT, ErrorCode.DATA_CONFLICT,
                "The request conflicts with existing data", request, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR,
                "An unexpected error occurred", request, null);
    }

    private static ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String message,
                                                       HttpServletRequest request,
                                                       List<ErrorResponse.FieldError> fieldErrors) {
        return ResponseEntity.status(status).body(new ErrorResponse(
                code, message, status.value(), status.getReasonPhrase(),
                request.getRequestURI(), fieldErrors, Instant.now()));
    }
}
