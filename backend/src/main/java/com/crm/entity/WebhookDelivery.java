package com.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Webhook 推送记录（055）。 */
@Getter
@Setter
@TableName("webhook_delivery")
public class WebhookDelivery {

  private Long id;
  private Long subscriptionId;
  private String eventType;
  private String entityType;
  private Long entityId;
  private String payload;

  /**
   * PENDING（已派发、尚无结果）/ SUCCESS / FAILED。
   *
   * <p>085（FR-V05/V13）：记录在**派发**时即以 PENDING 落库，投递结束时原地更新为终态。读取方（投递记录列表接口 与前端页面）必须能识别 PENDING ——
   * 前端若不识别，会把"投递中"渲染成红色的"失败"，等于把"记录不反映真实" 原样搬到界面上。列本身是 VARCHAR(20)，故新增取值不涉及 schema 变更。
   */
  private String status;

  private Integer httpStatus;
  private String error;
  private Integer retryCount;
  private LocalDateTime createdAt;
}
