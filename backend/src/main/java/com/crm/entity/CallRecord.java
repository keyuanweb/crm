package com.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 通话记录（061-call-center）。 */
@Getter
@Setter
@TableName("call_record")
public class CallRecord {

  private Long id;
  private Long customerId;
  private Long contactId;

  /** INBOUND / OUTBOUND。 */
  private String direction;

  private Integer durationSeconds;

  /** CONNECTED / NO_ANSWER / BUSY / FAILED。 */
  private String result;

  private String remark;
  private Long recordedBy;
  private LocalDateTime recordedAt;
}
