package com.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 发票（038-invoice）。 */
@Getter
@Setter
@TableName("invoice")
public class Invoice extends BaseEntity {

  private Long orderId;
  private String invoiceNo;
  private String title;
  private String taxNo;

  /** 金额（分）。 */
  private Long amount;

  /** GENERAL / SPECIAL。 */
  private String invoiceType;

  /** DRAFT / ISSUED / VOID。 */
  private String status;

  private String voidReason;
  private LocalDateTime issuedAt;
  private LocalDateTime voidedAt;
  private Long createdBy;
}
