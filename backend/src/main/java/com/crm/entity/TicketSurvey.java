package com.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 工单满意度（051-csat-nps）。 */
@Getter
@Setter
@TableName("ticket_survey")
public class TicketSurvey {

  private Long id;
  private Long ticketId;

  /** 评分 1-5。 */
  private Integer rating;

  private String comment;
  private Long createdBy;
  private LocalDateTime createdAt;
}
