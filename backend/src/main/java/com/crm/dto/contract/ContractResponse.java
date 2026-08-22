package com.crm.dto.contract;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/** 合同响应（含附件列表，contracts/contracts.md）。 */
@Data
public class ContractResponse {

  private Long id;
  private String contractNo;
  private String title;
  private Long customerId;
  private String customerName;
  private Long quoteId;
  private Long amount;
  private LocalDate startDate;
  private LocalDate endDate;
  private String content;
  private String status;
  private Long approverId;
  private LocalDateTime approvedAt;
  private String rejectReason;
  private LocalDateTime effectiveAt;
  private String terminatedReason;
  private String remark;
  private List<AttachmentResponse> attachments;
  private Integer version;
  private LocalDateTime createdAt;
}
