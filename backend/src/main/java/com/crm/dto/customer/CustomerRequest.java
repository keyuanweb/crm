package com.crm.dto.customer;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 客户创建/编辑请求（data-model.md §2 校验规则）。 */
@Data
public class CustomerRequest {

  @NotBlank(message = "客户名称不能为空")
  @Size(max = 100, message = "客户名称不能超过 100 字")
  private String name;

  @NotBlank(message = "公司不能为空")
  @Size(max = 100, message = "公司不能超过 100 字")
  private String company;

  @Size(max = 50, message = "联系人不能超过 50 字")
  private String contactPerson;

  @Pattern(regexp = "^[0-9+\\-() ]{5,30}$", message = "电话号码格式不正确")
  private String phone;

  @Email(message = "邮箱格式不正确")
  @Size(max = 100)
  private String email;

  @Size(max = 255, message = "地址不能超过 255 字")
  private String address;

  @Size(max = 500, message = "备注不能超过 500 字")
  private String remark;

  private String status;

  /** 编辑时用于乐观锁校验。 */
  private Integer version;
}
