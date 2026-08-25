package com.crm.dto.open;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/** API Key 创建请求。 */
@Data
public class ApiKeyRequest {

  @NotBlank(message = "名称不能为空")
  @Size(max = 100, message = "名称不能超过 100 字")
  private String name;

  /** 权限范围（如 customer:read / lead:write）。 */
  private List<String> scopes;

  /** 有效期（空=永不过期）。 */
  private LocalDateTime expiresAt;
}
