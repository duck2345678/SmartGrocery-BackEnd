package com.smartgrocery.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartgrocery.backend.dto.ApiResponse;
import com.smartgrocery.backend.exception.OrderAssignmentConflictException;
import com.smartgrocery.backend.exception.ResourceOwnershipException;
import com.smartgrocery.backend.service.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @Value("${app.debug.errors:true}")
    private boolean debugErrors;

    public GlobalExceptionHandler(AuditService auditService, ObjectMapper objectMapper) {
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @ExceptionHandler(ResourceOwnershipException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceOwnershipException(
            ResourceOwnershipException ex,
            HttpServletRequest request
    ) {
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(Map.of(
                    "actorUserId", ex.getActorUserId(),
                    "targetUserId", ex.getTargetUserId(),
                    "resourceType", ex.getResourceType(),
                    "resourceId", ex.getResourceId(),
                    "method", request.getMethod(),
                    "path", request.getRequestURI(),
                    "ipAddress", request.getRemoteAddr()
            ));
        } catch (Exception ignored) {
            payloadJson = null;
        }

        auditService.logEvent(
                ex.getActorUserId(),
                "SECURITY_IDOR_ATTEMPT",
                ex.getResourceType(),
                ex.getResourceId(),
                payloadJson
        );

        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(HttpStatus.FORBIDDEN.value(), "Forbidden"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(HttpStatus.FORBIDDEN.value(), "Forbidden"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .orElse("Invalid input");
        return ResponseEntity.badRequest().body(ApiResponse.error(HttpStatus.BAD_REQUEST.value(), message));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleRuntimeException(RuntimeException ex) {
        log.error("Runtime exception", ex);
        String message = ex.getMessage() != null ? ex.getMessage() : "Unexpected error";

        HttpStatus status;
        if (message.toLowerCase().contains("not found")) {
            status = HttpStatus.NOT_FOUND;
        } else if (message.toLowerCase().contains("already") || message.toLowerCase().contains("invalid")) {
            status = HttpStatus.BAD_REQUEST;
        } else if (message.toLowerCase().contains("unauthorized")) {
            status = HttpStatus.UNAUTHORIZED;
        } else {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        return ResponseEntity.status(status).body(ApiResponse.error(status.value(), message));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        String message = ex.getMessage() != null ? ex.getMessage() : "Invalid input";
        return ResponseEntity.badRequest().body(ApiResponse.error(400, message));
    }

    @ExceptionHandler(OrderAssignmentConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleOrderAssignmentConflict(OrderAssignmentConflictException ex) {
        String message = ex.getMessage() != null ? ex.getMessage() : "Order already assigned";
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(409, message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneralException(Exception ex) {
        log.error("Unhandled exception", ex);
        if (debugErrors) {
            String msg = ex.getMessage() != null && !ex.getMessage().isBlank()
                    ? ex.getClass().getSimpleName() + ": " + ex.getMessage()
                    : ex.getClass().getSimpleName();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(500, msg));
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "An unexpected system error occurred"));
    }
}
