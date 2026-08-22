package com.crm.dto.contact;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 联系人创建/编辑请求（FR-C03/C04）。 */
@Data
public class ContactRequest {

  @NotNull(message = "必须关联客户")
  private Long customerId;

  @NotBlank(message = "姓名不能为空")
  @Size(max = 50, message = "姓名不能超过 50 字")
  private String name;

  @Size(max = 50, message = "职位不能超过 50 字")
  private String title;

  @Pattern(regexp = "^[0-9+\\-() ]{5,30}$|^$", message = "电话号码格式不正确")
  private String phone;

  @Email(message = "邮箱格式不正确")
  @Size(max = 100)
  private String email;

  @Pattern(regexp = "^(DECISION_MAKER|INFLUENCER|EVALUATOR|CHAMPION|OTHER)$", message = "角色不合法")
  private String role;

  @Size(max = 500, message = "备注不能超过 500 字")
  private String remark;

  private Integer version;
}
