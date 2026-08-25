package com.crm.dto.call;

import java.time.LocalDateTime;
import lombok.Data;

/** 通话记录响应。 */
@Data
public class CallRecordResponse {

  private Long id;
  private Long customerId;
  private String customerName;
  private Long contactId;
  private String contactName;
  private String direction;
  private Integer durationSeconds;
  private String result;
  private String remark;
  private Long recordedBy;
  private LocalDateTime recordedAt;
}
