/** 定时导出执行记录（079-scheduled-export）。 */
package com.crm.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("scheduled_export_execution")
public class ScheduledExportExecution {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long scheduledExportId;

  /** 执行时间。 */
  private LocalDateTime executedAt;

  /** SUCCESS / FAILED / EMAIL_SENT / EMAIL_FAILED。 */
  private String status;

  /** 导出文件路径。 */
  private String filePath;

  /** 文件大小（字节）。 */
  private Long fileSize;

  /** 导出行数。 */
  private Integer rowCount;

  /** 邮件发送状态。 */
  private String emailStatus;

  /** 错误信息。 */
  private String errorMessage;

  private LocalDateTime createdAt;
}
