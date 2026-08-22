package com.crm.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 统一响应信封。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

  private boolean success;
  private T data;
  private ErrorBody error;

  public static <T> ApiResponse<T> ok(T data) {
    return new ApiResponse<>(true, data, null);
  }

  public static <T> ApiResponse<T> ok() {
    return new ApiResponse<>(true, null, null);
  }

  public static <T> ApiResponse<T> fail(
      String code, String message, java.util.List<FieldError> fieldErrors) {
    return new ApiResponse<>(false, null, new ErrorBody(code, message, fieldErrors));
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ErrorBody {
    private String code;
    private String message;
    private java.util.List<FieldError> fieldErrors;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class FieldError {
    private String field;
    private String message;
  }
}
