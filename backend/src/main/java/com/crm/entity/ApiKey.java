package com.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 开放平台 API Key（055）。 */
@Getter
@Setter
@TableName("api_key")
public class ApiKey {

  private Long id;
  private String name;
  private String keyHash;
  private String keyPrefix;
  private String scopes;
  private LocalDateTime expiresAt;

  /** ACTIVE / REVOKED。 */
  private String status;

  private LocalDateTime lastUsedAt;
  private Long useCount;
  private Long createdBy;
  private LocalDateTime createdAt;
}
