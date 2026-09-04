/** 数据保留执行记录（080-data-retention）。 */
package com.crm.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("data_retention_execution")
public class DataRetentionExecution {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long policyId;

  /** 执行时间。 */
  private LocalDateTime executedAt;

  /** SUCCESS / FAILED / PARTIAL。 */
  private String status;

  /** 处理数量。 */
  private Integer processedCount;

  /** 错误信息。 */
  private String errorMessage;

  private LocalDateTime createdAt;
}
