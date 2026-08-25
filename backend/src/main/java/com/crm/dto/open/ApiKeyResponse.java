package com.crm.dto.open;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/** API Key 响应（创建时含完整 key）。 */
@Data
public class ApiKeyResponse {

  private Long id;
  private String name;

  /** 完整 key（仅创建时返回）。 */
  private String key;

  private String keyPrefix;
  private List<String> scopes;
  private LocalDateTime expiresAt;
  private String status;
  private LocalDateTime lastUsedAt;
  private Long useCount;
  private LocalDateTime createdAt;
}
