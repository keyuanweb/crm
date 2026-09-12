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

  /** 首次响应时刻（第一条回复写入）；NULL 表示尚未响应（1.3）。 */
  private LocalDateTime slaRespondedAt;

  /** 解决时刻（流转至 RESOLVED/CLOSED 时写入，1.3）。 */
  private LocalDateTime resolvedAt;

  /** SLA 升级级别：0 无 / 1 警告 / 2 超时 / 3+ 持续超时；单调递增不回退（1.3）。 */
  private Integer escalateLevel;

  /** 最近一次升级时刻，用于计算下一次升级间隔（1.3）。 */
  private LocalDateTime lastEscalatedAt;

  private String remark;

  @TableField(fill = FieldFill.INSERT)
  private Long createdBy;
}
