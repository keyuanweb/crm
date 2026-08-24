package com.crm.exception;

import com.crm.common.ApiResponse;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 全局异常处理：将各类异常映射为契约错误格式（contracts/README.md）。 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
    ErrorCode code = ex.getErrorCode();
    return build(code.getStatus(), code.getCode(), ex.getMessage(), null);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
    List<ApiResponse.FieldError> fieldErrors = new ArrayList<>();
    for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
      fieldErrors.add(new ApiResponse.FieldError(fe.getField(), fe.getDefaultMessage()));
    }
    return build(400, ErrorCode.BAD_REQUEST.getCode(), "参数校验失败", fieldErrors);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ApiResponse<Void>> handleConstraint(ConstraintViolationException ex) {
    return build(400, ErrorCode.BAD_REQUEST.getCode(), ex.getMessage(), null);
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
    return build(403, ErrorCode.FORBIDDEN.getCode(), ErrorCode.FORBIDDEN.getMessage(), null);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
    return build(400, ErrorCode.BAD_REQUEST.getCode(), ex.getMessage(), null);
  }

  @ExceptionHandler(com.crm.controller.EmailTrackController.RateLimitedException.class)
  public ResponseEntity<ApiResponse<Void>> handleRateLimited(
      com.crm.controller.EmailTrackController.RateLimitedException ex) {
    return build(429, "TOO_MANY_REQUESTS", ex.getMessage(), null);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleOther(Exception ex) {
    log.error("Unhandled exception", ex);
    return build(
        500, ErrorCode.INTERNAL_ERROR.getCode(), ErrorCode.INTERNAL_ERROR.getMessage(), null);
  }

  private ResponseEntity<ApiResponse<Void>> build(
      int status, String code, String message, List<ApiResponse.FieldError> fieldErrors) {
    return ResponseEntity.status(HttpStatus.valueOf(status))
        .body(ApiResponse.fail(code, message, fieldErrors));
  }
}
