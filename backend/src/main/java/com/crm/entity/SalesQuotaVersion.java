package com.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 配额版本历史（078-sales-quota，记录配额调整历史）。 */
@Getter
@Setter
@TableName("sales_quota_version")
public class SalesQuotaVersion extends BaseEntity {

  /** 配额 ID。 */
  private Long quotaId;

  /** 调整前金额（万元）。 */
  private BigDecimal oldAmount;

  /** 调整后金额（万元）。 */
  private BigDecimal newAmount;

  /** 调整人 ID。 */
  private Long changedBy;

  /** 调整时间。 */
  private LocalDateTime changedAt;

  /** 调整原因。 */
  private String changeReason;

  /** 版本号（从 1 开始递增）。 */
  private Integer versionNumber;
}
