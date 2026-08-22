package com.crm.dto.lead;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LeadRequest {

  @NotBlank(message = "姓名不能为空")
  @Size(max = 50)
  private String name;

  @NotBlank(message = "公司不能为空")
  @Size(max = 100)
  private String company;

  @Size(max = 50)
  private String title;

  @Size(max = 30)
  private String phone;

  @Email(message = "邮箱格式不正确")
  @Size(max = 100)
  private String email;

  @Size(max = 20)
  private String source;

  @Size(max = 20)
  private String status;

  private Integer score;

  private Long ownerId;

  private Integer version;

  @Size(max = 500)
  private String remark;
}
