package com.ecgcare.backend.exception;

import com.ecgcare.backend.dto.response.ApiResponse;
import com.ecgcare.backend.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

        @ExceptionHandler(BadRequestException.class)
        public ResponseEntity<ApiResponse<?>> handleBadRequest(BadRequestException e, HttpServletRequest request) {
                return ResponseEntity.badRequest()
                                .body(ApiResponse.error("BAD_REQUEST", e.getMessage())
                                                .toBuilder()
                                                .timestamp(OffsetDateTime.now().toString())
                                                .path(request.getRequestURI())
                                                .build());
        }

        @ExceptionHandler(UnauthorizedException.class)
        public ResponseEntity<ApiResponse<?>> handleUnauthorized(UnauthorizedException e, HttpServletRequest request) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                .body(ApiResponse.error("UNAUTHORIZED", e.getMessage())
                                                .toBuilder()
                                                .timestamp(OffsetDateTime.now().toString())
                                                .path(request.getRequestURI())
                                                .build());
        }

        @ExceptionHandler(ForbiddenException.class)
        public ResponseEntity<ApiResponse<?>> handleForbidden(ForbiddenException e, HttpServletRequest request) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .body(ApiResponse.error("FORBIDDEN", e.getMessage())
                                                .toBuilder()
                                                .timestamp(OffsetDateTime.now().toString())
                                                .path(request.getRequestURI())
                                                .build());
        }

        @ExceptionHandler(NotFoundException.class)
        public ResponseEntity<ApiResponse<?>> handleNotFound(NotFoundException e, HttpServletRequest request) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(ApiResponse.error("NOT_FOUND", e.getMessage())
                                                .toBuilder()
                                                .timestamp(OffsetDateTime.now().toString())
                                                .path(request.getRequestURI())
                                                .build());
        }

        @ExceptionHandler(MLServiceTimeoutException.class)
        public ResponseEntity<ApiResponse<?>> handleMLServiceTimeout(MLServiceTimeoutException e,
                        HttpServletRequest request) {
                log.error("ML service timeout", e);
                return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                                .body(ApiResponse
                                                .error("ML_SERVICE_TIMEOUT",
                                                                "ML service request timed out: " + e.getMessage())
                                                .toBuilder()
                                                .timestamp(OffsetDateTime.now().toString())
                                                .path(request.getRequestURI())
                                                .build());
        }

        @ExceptionHandler(MLServiceUnavailableException.class)
        public ResponseEntity<ApiResponse<?>> handleMLServiceUnavailable(MLServiceUnavailableException e,
                        HttpServletRequest request) {
                log.error("ML service unavailable", e);
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                                .body(ApiResponse
                                                .error("ML_SERVICE_UNAVAILABLE",
                                                                "ML service is currently unavailable: "
                                                                                + e.getMessage())
                                                .toBuilder()
                                                .timestamp(OffsetDateTime.now().toString())
                                                .path(request.getRequestURI())
                                                .build());
        }

        @ExceptionHandler(MLServiceException.class)
        public ResponseEntity<ApiResponse<?>> handleMLServiceException(MLServiceException e,
                        HttpServletRequest request) {
                log.error("ML service error", e);
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                                .body(ApiResponse.error("ML_SERVICE_ERROR", "ML service error: " + e.getMessage())
                                                .toBuilder()
                                                .timestamp(OffsetDateTime.now().toString())
                                                .path(request.getRequestURI())
                                                .build());
        }

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ApiResponse<?>> handleValidationExceptions(MethodArgumentNotValidException e,
                        HttpServletRequest request) {
                Map<String, String> errors = new HashMap<>();
                e.getBindingResult().getAllErrors().forEach((error) -> {
                        String fieldName = ((FieldError) error).getField();
                        String errorMessage = error.getDefaultMessage();
                        errors.put(fieldName, errorMessage);
                });

                return ResponseEntity.badRequest()
                                .body(ApiResponse.error("VALIDATION_ERROR", "Validation failed")
                                                .toBuilder()
                                                .error(ErrorResponse.builder()
                                                                .code("VALIDATION_ERROR")
                                                                .message("Validation failed")
                                                                .details(errors)
                                                                .build())
                                                .timestamp(OffsetDateTime.now().toString())
                                                .path(request.getRequestURI())
                                                .build());
        }

        @ExceptionHandler(RuntimeException.class)
        public ResponseEntity<ApiResponse<?>> handleRuntimeException(RuntimeException e, HttpServletRequest request) {
                log.error("Runtime exception: {}", e.getMessage(), e);
                String message = e.getMessage() != null ? e.getMessage() : "An unexpected error occurred";
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(ApiResponse.error("INTERNAL_ERROR", message)
                                                .toBuilder()
                                                .timestamp(OffsetDateTime.now().toString())
                                                .path(request.getRequestURI())
                                                .build());
        }

        @ExceptionHandler(Exception.class)
        public ResponseEntity<ApiResponse<?>> handleGenericException(Exception e, HttpServletRequest request) {
                log.error("Unexpected error: {}", e.getMessage(), e);
                String message = e.getMessage() != null ? e.getMessage() : "An unexpected error occurred";
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(ApiResponse.error("INTERNAL_ERROR", message)
                                                .toBuilder()
                                                .timestamp(OffsetDateTime.now().toString())
                                                .path(request.getRequestURI())
                                                .build());
        }
}
