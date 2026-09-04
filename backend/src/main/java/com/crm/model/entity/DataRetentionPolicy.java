/** 数据保留策略（080-data-retention）。 */
package com.crm.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("data_retention_policy")
public class DataRetentionPolicy {

  @TableId(type = IdType.AUTO)
  private Long id;

  /** 实体类型：CUSTOMER/OPPORTUNITY/CONTRACT/ORDER/AUDIT_LOG。 */
  private String entityType;

  /** 保留期限（天）。 */
  private Integer retentionDays;

  /** ARCHIVE / DELETE。 */
  private String actionType;

  /** ACTIVE / INACTIVE。 */
  private String status;

  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
