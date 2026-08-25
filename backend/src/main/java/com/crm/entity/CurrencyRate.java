package com.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 币种汇率（057-multi-currency）。 */
@Getter
@Setter
@TableName("currency_rate")
public class CurrencyRate {

  private Long id;
  private String code;
  private String name;
  private BigDecimal rate;
  private Integer isBase;
  private Integer enabled;
  private Integer version;
  private Long createdBy;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
