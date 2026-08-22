package com.crm.dto.lead;

import com.crm.dto.customfield.CustomFieldValueDTO;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class LeadResponse {

  private Long id;
  private String name;
  private String company;
  private String title;
  private String phone;
  private String email;
  private String source;
  private String status;
  private Integer score;
  private Long ownerId;
  private String ownerName;

  /** 营销活动归因（014）。 */
  private Long campaignId;

  /** 自定义字段值（016）。 */
  private List<CustomFieldValueDTO> customFieldValues;

  private Long convertedCustomerId;
  private LocalDateTime convertedAt;
  private String remark;
  private Integer version;
  private LocalDateTime createdAt;
}
