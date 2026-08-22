package com.crm.dto.share;

import java.time.LocalDateTime;
import lombok.Data;

/** 共享给我的客户响应。 */
@Data
public class SharedCustomerResponse {

  private Long shareId;
  private Long customerId;
  private String customerName;
  private String company;
  private Long sharedBy;
  private LocalDateTime sharedAt;
}
