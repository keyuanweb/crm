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
  private String description;
  private Integer sortOrder;
  private Integer version;
  private LocalDateTime createdAt;
  private String createdBy;
  private Integer memberCount;
  private Integer childCount;
  private List<DepartmentResponse> children = new ArrayList<>();
}
