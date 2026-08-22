package com.crm.dto.opportunity;

import com.crm.dto.customfield.CustomFieldValueDTO;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/** 商机列表项响应。 */
@Data
public class OpportunityResponse {

  private Long id;
  private String name;
  private Long customerId;
  private String customerName;
  private Long expectedAmountMin;
  private Long expectedAmountMax;
  private String remark;
  private String status;
  private Integer salesOpportunityCount;

  /** 自定义字段值（016）。 */
  private List<CustomFieldValueDTO> customFieldValues;

  private Integer version;
  private LocalDateTime createdAt;
}
