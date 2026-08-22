package com.crm.dto.customer;

import com.crm.dto.customfield.CustomFieldValueDTO;
import java.time.LocalDateTime;
import java.util.List;
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

  /** 自定义字段值（016）。 */
  private List<CustomFieldValueDTO> customFieldValues;

  private Integer version;
  private LocalDateTime createdAt;
}
