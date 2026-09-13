package com.crm.exception;

import com.crm.common.ApiResponse;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.dto.opportunity.CloseRequest;
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
    // 085（FR-V03/FR-V15，澄清 Q1 裁决 B）：关闭销售机会时"未提供赢单/输单结果"这一类校验失败，
    // 契约约定的是 422 CLOSE_RESULT_REQUIRED，而本方法原先一律返回 400。于是**同一端点**的两条失败
    // 路径语义不一致：结果**非法**（非空但不是 WON/LOST）由 SalesOpportunityService 抛出 → 422；
    // 结果**缺失**却被 Bean Validation 在此拦下 → 400。契约（specs/001-crm-core/contracts/
    // sales-opportunities.md:81）只写了 422 —— 缺陷是"实现自相矛盾"，不是"契约比实现严格"。
    //
    // 这里**保留** CloseRequest 上的 @NotBlank（章程原则三"所有 DTO 必须声明 Jakarta Bean Validation
    // 约束"，不可协商），只把这一处边界校验失败**映射**为契约约定的状态码，两条不可协商原则由此同时满足。
    //
    // 分层（章程原则二）："某种校验失败该映射为何种 HTTP 状态码"是纯 HTTP 关注点，放在异常处理器是
    // 正确的；业务规则（结果必填且必须是 WON/LOST）仍在 SalesOpportunityService，且原样保留作为第二道
    // 防线。本分支只做**映射**，不做**判定**。
    //
    // 判定键用**绑定目标 DTO 类型 + 违规字段名**，而非 URI 或路径匹配：显式、不受路由改名影响，
    // 且天然只能命中这一个端点。
    // ⚠️ 绝不可扩张为"全局把 400 改成 422" —— 那会污染**所有**端点的失败语义，制造一个比原缺陷更大的
    // 问题。FR-V15 专门有一条测试钉住"其他端点的参数校验失败仍是 400"。
    if (isCloseResultInvalid(ex)) {
      ErrorCode code = ErrorCode.CLOSE_RESULT_REQUIRED;
      return build(code.getStatus(), code.getCode(), code.getMessage(), fieldErrors);
    }
    return build(400, ErrorCode.BAD_REQUEST.getCode(), "参数校验失败", fieldErrors);
  }

  /**
   * 085：绑定目标是 {@link CloseRequest} 且违规字段是 {@code closeResult} 时，判定为该端点专属的校验失败。
   *
   * <p>只按"类型 + 字段"判定。若 {@code closeResult} 因超长等其他约束失败，同样归入本分支 —— 这与域层一致：超长的值 经 {@code
   * trim().toUpperCase()} 后同样不是 WON/LOST，域层也会抛同一个错误码。
   */
  private boolean isCloseResultInvalid(MethodArgumentNotValidException ex) {
    if (!(ex.getBindingResult().getTarget() instanceof CloseRequest)) {
      return false;
    }
    return ex.getBindingResult().getFieldErrors().stream()
        .anyMatch(fe -> "closeResult".equals(fe.getField()));
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
    // 063(安全加固)：不把内部异常消息直接回传前端（防细节泄露），统一 400 文案
    return build(400, ErrorCode.BAD_REQUEST.getCode(), ErrorCode.BAD_REQUEST.getMessage(), null);
  }

  /** 063：请求体坏 JSON → 400。 */
  @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
  public ResponseEntity<ApiResponse<Void>> handleUnreadable(
      org.springframework.http.converter.HttpMessageNotReadableException ex) {
    return build(400, ErrorCode.BAD_REQUEST.getCode(), "请求体格式错误", null);
  }

  /** 063：路径/查询参数类型错误 → 400。 */
  @ExceptionHandler(
      org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(
      org.springframework.web.method.annotation.MethodArgumentTypeMismatchException ex) {
    return build(400, ErrorCode.BAD_REQUEST.getCode(), "参数类型错误", null);
  }

  /** 063：缺必需请求参数 → 400。 */
  @ExceptionHandler(org.springframework.web.bind.MissingServletRequestParameterException.class)
  public ResponseEntity<ApiResponse<Void>> handleMissingParam(
      org.springframework.web.bind.MissingServletRequestParameterException ex) {
    return build(400, ErrorCode.BAD_REQUEST.getCode(), "缺少必需参数", null);
  }

  /** 063：请求方法不支持 → 405。 */
  @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(
      org.springframework.web.HttpRequestMethodNotSupportedException ex) {
    return build(405, "METHOD_NOT_ALLOWED", "请求方法不支持", null);
  }

  /** 063：资源路径不存在 → 404。 */
  @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
  public ResponseEntity<ApiResponse<Void>> handleNoResource(
      org.springframework.web.servlet.resource.NoResourceFoundException ex) {
    return build(404, "NOT_FOUND", "资源不存在", null);
  }

  /** 063：唯一键冲突 → 409。 */
  @ExceptionHandler({
    org.springframework.dao.DuplicateKeyException.class,
    org.springframework.dao.DataIntegrityViolationException.class
  })
  public ResponseEntity<ApiResponse<Void>> handleDuplicate(
      org.springframework.dao.DataIntegrityViolationException ex) {
    return build(409, "DUPLICATE_KEY", "数据已存在或违反唯一约束", null);
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
