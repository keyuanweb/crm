package com.crm.dto.department;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 部门创建/编辑请求（FR-DP01）。 */
@Data
public class DepartmentRequest {

  @NotBlank(message = "部门名称不能为空")
  @Size(max = 50, message = "名称不能超过 50 字")
  private String name;

  private Long parentId;

  private Integer version;
}
