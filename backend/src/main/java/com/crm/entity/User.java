package com.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 用户（认证主体，角色：ADMIN/SALES/SUPPORT）。 */
@Getter
@Setter
@TableName("`user`")
public class User extends BaseEntity {

  private String username;
  private String passwordHash;
  private String displayName;
  private String role;
  private Boolean enabled;
  private LocalDateTime lastLoginAt;

  /** 密码变更/重置后递增，旧访问令牌据此失效（FR-005/FR-006）。 */
  private Integer tokenVersion;
}
