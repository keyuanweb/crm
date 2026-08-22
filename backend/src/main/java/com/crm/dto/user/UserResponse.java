package com.crm.dto.user;

import java.time.LocalDateTime;
import lombok.Data;

/** 用户响应。 */
@Data
public class UserResponse {

  private Long id;
  private String username;
  private String displayName;
  private String role;
  private Boolean enabled;
  private LocalDateTime lastLoginAt;
  private Integer version;
  private LocalDateTime createdAt;
}
