package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.dto.department.DepartmentRequest;
import com.crm.dto.department.DepartmentResponse;
import com.crm.dto.department.DepartmentTreeResponse;
import com.crm.entity.Department;
import com.crm.entity.User;
import com.crm.repository.DepartmentMapper;
import com.crm.repository.UserMapper;
import com.crm.security.SecurityUtil;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 部门服务（012，FR-DP01~DP03）：CRUD/树/子孙集合/删除防护。 */
@Service
public class DepartmentService {

  private final DepartmentMapper departmentMapper;
  private final UserMapper userMapper;
  private final AuditService auditService;

  public DepartmentService(
      DepartmentMapper departmentMapper, UserMapper userMapper, AuditService auditService) {
    this.departmentMapper = departmentMapper;
    this.userMapper = userMapper;
    this.auditService = auditService;
  }

  /** 部门树（全量加载构建）。 */
  public DepartmentTreeResponse tree() {
    List<Department> all =
        departmentMapper.selectList(
            new LambdaQueryWrapper<Department>().orderByAsc(Department::getId));
    return new DepartmentTreeResponse(buildTree(all, null));
  }

  @Transactional
  public DepartmentResponse create(DepartmentRequest req) {
    validateParent(req.getParentId());
    Department dept = new Department();
    dept.setName(req.getName().trim());
    dept.setParentId(req.getParentId());
    dept.setCreatedBy(SecurityUtil.currentUserId());
    departmentMapper.insert(dept);
    auditService.record("CREATE", "DEPARTMENT", dept.getId(), "创建部门：" + dept.getName());
    return toResponse(dept);
  }

  @Transactional
  public DepartmentResponse update(Long id, DepartmentRequest req) {
    Department existing = require(id);
    // 防环：parent 不能是自己的子孙
    if (req.getParentId() != null && req.getParentId().equals(id)) {
      throw new BusinessException(ErrorCode.BAD_REQUEST);
    }
    validateParent(req.getParentId());
    existing.setName(req.getName().trim());
    existing.setParentId(req.getParentId());
    existing.setVersion(req.getVersion());
    int rows = departmentMapper.updateById(existing);
    if (rows == 0) {
      throw new BusinessException(ErrorCode.VERSION_CONFLICT);
    }
    auditService.record("UPDATE", "DEPARTMENT", id, "编辑部门：" + existing.getName());
    return toResponse(departmentMapper.selectById(id));
  }

  @Transactional
  public void delete(Long id) {
    Department dept = require(id);
    Long children =
        departmentMapper.selectCount(
            new LambdaQueryWrapper<Department>().eq(Department::getParentId, id));
    if (children != null && children > 0) {
      throw new BusinessException(ErrorCode.DEPARTMENT_HAS_CHILDREN_OR_MEMBERS);
    }
    Long members =
        userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getDepartmentId, id));
    if (members != null && members > 0) {
      throw new BusinessException(ErrorCode.DEPARTMENT_HAS_CHILDREN_OR_MEMBERS);
    }
    departmentMapper.deleteById(id);
    auditService.record("DELETE", "DEPARTMENT", id, "删除部门：" + dept.getName());
  }

  /** 部门及全部子孙部门 id 集合（供数据权限解析）。 */
  public List<Long> deptAndChildrenIds(Long deptId) {
    List<Department> all =
        departmentMapper.selectList(
            new LambdaQueryWrapper<Department>()
                .select(Department::getId, Department::getParentId));
    Map<Long, List<Long>> childrenByParent = new LinkedHashMap<>();
    for (Department d : all) {
      if (d.getParentId() != null) {
        childrenByParent.computeIfAbsent(d.getParentId(), k -> new ArrayList<>()).add(d.getId());
      }
    }
    List<Long> result = new ArrayList<>();
    collect(deptId, childrenByParent, result);
    return result;
  }

  private void collect(Long deptId, Map<Long, List<Long>> childrenByParent, List<Long> result) {
    result.add(deptId);
    for (Long child : childrenByParent.getOrDefault(deptId, List.of())) {
      collect(child, childrenByParent, result);
    }
  }

  public Department require(Long id) {
    Department dept = departmentMapper.selectById(id);
    if (dept == null) {
      throw new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND);
    }
    return dept;
  }

  private void validateParent(Long parentId) {
    if (parentId != null) {
      require(parentId);
    }
  }

  private List<DepartmentResponse> buildTree(List<Department> all, Long parentId) {
    List<DepartmentResponse> nodes = new ArrayList<>();
    for (Department d : all) {
      if (parentId == null ? d.getParentId() == null : parentId.equals(d.getParentId())) {
        DepartmentResponse node = toResponse(d);
        node.setChildren(buildTree(all, d.getId()));
        nodes.add(node);
      }
    }
    return nodes;
  }

  private DepartmentResponse toResponse(Department dept) {
    DepartmentResponse resp = new DepartmentResponse();
    resp.setId(dept.getId());
    resp.setName(dept.getName());
    resp.setParentId(dept.getParentId());
    resp.setVersion(dept.getVersion());
    resp.setCreatedAt(dept.getCreatedAt());
    return resp;
  }
}
