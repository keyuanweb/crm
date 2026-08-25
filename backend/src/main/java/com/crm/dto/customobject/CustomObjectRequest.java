package com.crm.dto.customobject;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

/** 自定义对象请求。 */
@Data
public class CustomObjectRequest {

  @NotBlank(message = "对象名称不能为空")
  @Size(max = 100, message = "名称不能超过 100 字")
  private String name;

  @NotBlank(message = "对象编码不能为空")
  @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "编码须大写字母开头，含数字/下划线")
  @Size(max = 50, message = "编码不能超过 50 字符")
  private String code;

  /** 字段集。 */
  private List<FieldDef> fields;

  private Boolean enabled;

  private Integer version;

  @Data
  public static class FieldDef {
    private String field;
    private String label;
    private String type;
    private Boolean required;
    private String options;
  }
}
