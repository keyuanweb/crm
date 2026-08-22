package com.crm.dto.lead;

import java.time.LocalDateTime;
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
  private Long convertedCustomerId;
  private LocalDateTime convertedAt;
  private String remark;
  private Integer version;
  private LocalDateTime createdAt;
}
