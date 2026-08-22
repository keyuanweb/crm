package com.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 工单回复（015-customer-service，工单时间线）。 */
@Getter
@Setter
@TableName("ticket_reply")
public class TicketReply {

  private Long id;
  private Long ticketId;
  private Long replierId;
  private String content;
  private LocalDateTime createdAt;
}
