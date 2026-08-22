package com.crm.dto.customer;

import java.time.LocalDateTime;
import lombok.Data;

/** 客户列表项/基本信息响应。 */
@Data
public class CustomerResponse {

  private Long id;
  private String name;
  private String company;
  private String contactPerson;
  private String phone;
  private String email;
  private String address;
  private String remark;
  private String status;

  /** 归属销售（011，空 = 公海）。 */
  private Long ownerId;

  private String ownerName;

  /** 营销活动归因（014）。 */
  private Long campaignId;

  private Integer version;
  private LocalDateTime createdAt;
}
