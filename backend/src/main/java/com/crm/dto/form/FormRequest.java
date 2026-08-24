package com.crm.dto.form;

import java.util.List;
import lombok.Data;

/** 表单定义请求（036）。 */
@Data
public class FormRequest {

  private String name;
  private List<FormField> fields;
  private String successMessage;
  private String source;
  private String status;

  @Data
  public static class FormField {
    private String field;
    private String label;

    /** TEXT / TEL / EMAIL / TEXTAREA。 */
    private String type;

    private Boolean required;
  }
}
