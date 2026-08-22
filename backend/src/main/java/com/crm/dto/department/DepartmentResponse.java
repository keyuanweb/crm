package com.crm.dto.department;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** 部门响应（树节点）。 */
@Data
public class DepartmentResponse {

  private Long id;
  private String name;
  private Long parentId;
  private Integer version;
  private LocalDateTime createdAt;
  private List<DepartmentResponse> children = new ArrayList<>();
}
