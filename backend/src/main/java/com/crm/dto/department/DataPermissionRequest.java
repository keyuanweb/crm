package com.crm.dto.department;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** 用户部门与数据权限设置请求（FR-DP03/DP04）。 */
@Data
public class DataPermissionRequest {

  private Long departmentId;

  @Pattern(regexp = "^(SELF|DEPT|DEPT_AND_CHILD|ALL)$", message = "数据权限范围不合法")
  private String dataScope;
}
