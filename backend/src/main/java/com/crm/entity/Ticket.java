package com.crm.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 服务工单（015-customer-service）。 */
@Getter
@Setter
@TableName("ticket")
public class Ticket extends BaseEntity {

  /** 关联客户（必填）。 */
  private Long customerId;

  /** 关联联系人（可选）。 */
  private Long contactId;

  private String title;
  private String description;

  /** LOW / MEDIUM / HIGH / URGENT。 */
  private String priority;

  /** OPEN / IN_PROGRESS / RESOLVED / CLOSED。 */
  private String status;

  /** 处理人。 */
  private Long assigneeId;

  /** SLA 响应到期。 */
  private LocalDateTime slaRespondDeadline;

  /** SLA 解决到期。 */
  private LocalDateTime slaResolveDeadline;

  /** NORMAL / WARNING / OVERDUE。 */
  private String slaStatus;

  private String remark;

  @TableField(fill = FieldFill.INSERT)
  private Long createdBy;
}
