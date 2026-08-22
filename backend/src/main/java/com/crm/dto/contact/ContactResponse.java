package com.crm.dto.contact;

import java.time.LocalDateTime;
import lombok.Data;

/** 联系人响应。 */
@Data
public class ContactResponse {

  private Long id;
  private Long customerId;
  private String customerName;
  private String name;
  private String title;
  private String phone;
  private String email;
  private String role;
  private String remark;
  private Integer version;
  private LocalDateTime createdAt;
}
