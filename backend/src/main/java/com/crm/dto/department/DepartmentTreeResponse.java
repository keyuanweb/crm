package com.crm.dto.department;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 部门树响应。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentTreeResponse {

  private List<DepartmentResponse> departments;
}
