package com.crm.dto.personal;

import java.time.LocalDateTime;
import lombok.Data;

/** 个人信息响应。 */
@Data
public class PersonalInfoResponse {

  private Long id;
  private String username;
  private String displayName;
  private String role;

  /** 所属部门 ID。 */
  private Long departmentId;

  /** 所属部门名称。 */
  private String departmentName;

  /** 数据权限范围。 */
  private String dataScope;

  private Boolean enabled;
  private LocalDateTime lastLoginAt;
  private LocalDateTime createdAt;

  /** 密码最后修改时间（从 tokenVersion 推算，暂无直接字段，使用 updated_at 近似）。 */
  private LocalDateTime passwordUpdatedAt;
}
