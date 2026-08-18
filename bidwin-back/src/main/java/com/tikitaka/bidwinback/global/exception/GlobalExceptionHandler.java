package com.tikitaka.bidwinback.global.exception;

import com.tikitaka.bidwinback.global.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.NoSuchElementException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(
            BusinessException exception
    ) {
        ErrorCode errorCode = exception.getErrorCode();

        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.error(errorCode, exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
            MethodArgumentNotValidException exception
    ) {
        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .findFirst()
                .orElse(ErrorCode.INVALID_INPUT_VALUE.getMessage());

        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ErrorCode.INVALID_INPUT_VALUE, message));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodValidationException(
            HandlerMethodValidationException exception
    ) {
        String message = exception.getParameterValidationResults()
                .stream()
                .flatMap(result -> result.getResolvableErrors().stream())
                .map(error -> error.getDefaultMessage())
                .filter(errorMessage ->
                        errorMessage != null && !errorMessage.isBlank()
                )
                .findFirst()
                .orElse(ErrorCode.INVALID_INPUT_VALUE.getMessage());

        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ErrorCode.INVALID_INPUT_VALUE, message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadable() {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ErrorCode.INVALID_INPUT_VALUE));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
            ConstraintViolationException exception
    ) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = ErrorCode.INVALID_INPUT_VALUE.getMessage();
        }

        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ErrorCode.INVALID_INPUT_VALUE, message));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoSuchElement(
            NoSuchElementException exception
    ) {
        return ResponseEntity.status(ErrorCode.ENTITY_NOT_FOUND.getStatus())
                .body(ApiResponse.error(ErrorCode.ENTITY_NOT_FOUND));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException exception
    ) {
        return ResponseEntity.status(ErrorCode.METHOD_NOT_ALLOWED.getStatus())
                .body(ApiResponse.error(ErrorCode.METHOD_NOT_ALLOWED));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(
            Exception exception,
            HttpServletRequest request
    ) {
        log.atError()
                .addKeyValue("event", "unhandled_request_failed")
                .addKeyValue("errorCode", ErrorCode.INTERNAL_SERVER_ERROR.getCode())
                .addKeyValue("method", request.getMethod())
                .addKeyValue("path", request.getRequestURI())
                .addKeyValue("failureType", exception.getClass().getSimpleName())
                .addKeyValue("failureLocation", failureLocation(exception))
                .log("처리하지 못한 요청 예외가 발생했습니다.");

        return ResponseEntity.status(ErrorCode.INTERNAL_SERVER_ERROR.getStatus())
                .body(ApiResponse.error(ErrorCode.INTERNAL_SERVER_ERROR));
    }

    private static String failureLocation(Exception exception) {
        StackTraceElement[] stackTrace = exception.getStackTrace();
        for (StackTraceElement element : stackTrace) {
            if (element.getClassName().startsWith("com.tikitaka.bidwinback")) {
                return element.toString();
            }
        }
        return stackTrace.length == 0 ? "unknown" : stackTrace[0].toString();
    }
}
