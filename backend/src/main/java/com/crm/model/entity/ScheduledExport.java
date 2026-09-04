/** 定时导出任务（079-scheduled-export）。 */
package com.crm.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("scheduled_export")
public class ScheduledExport {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long userId;

  /** 导出实体类型：CUSTOMER/OPPORTUNITY/CONTRACT/ORDER/INVOICE。 */
  private String entityType;

  /** 筛选条件（JSON）。 */
  private String filterConditions;

  /** CSV / XLSX。 */
  private String exportFormat;

  /** Cron 表达式。 */
  private String cronExpression;

  /** ACTIVE / SUSPENDED / DELETED。 */
  private String status;

  /** 下次执行时间。 */
  private LocalDateTime nextExecutionTime;

  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
