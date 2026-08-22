package com.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 合同附件（008-contract-management，元数据，文件存本地磁盘）。 */
@Getter
@Setter
@TableName("contract_attachment")
public class ContractAttachment extends BaseEntity {

  private Long contractId;
  private String fileName;
  private String filePath;
  private Long fileSize;
  private String contentType;
  private Long uploadedBy;
}
