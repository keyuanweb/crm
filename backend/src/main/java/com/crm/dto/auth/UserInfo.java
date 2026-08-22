package com.crm.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 当前用户信息。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserInfo {

  private Long id;
  private String username;
  private String displayName;
  private String role;
}
