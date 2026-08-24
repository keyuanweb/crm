package com.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 电子签署记录（047-e-signature）。 */
@Getter
@Setter
@TableName("signature_record")
public class SignatureRecord {

  private Long id;

  /** QUOTE / CONTRACT。 */
  private String businessType;

  private Long businessId;
  private Long signerId;
  private String signerName;

  /** 签名图 base64。 */
  private String signatureImage;

  private LocalDateTime createdAt;
}
