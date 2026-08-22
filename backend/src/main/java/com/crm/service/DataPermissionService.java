package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crm.entity.User;
import com.crm.repository.DepartmentMapper;
import com.crm.repository.UserMapper;
import java.util.List;
import org.springframework.stereotype.Service;

/** 行级数据权限解析（012，FR-DP04~DP06）。 将当前用户 data_scope 解析为可见 owner 集合；ALL 返回空集合表示不过滤。 */
@Service
public class DataPermissionService {

  public static final String SCOPE_SELF = "SELF";
  public static final String SCOPE_DEPT = "DEPT";
  public static final String SCOPE_DEPT_AND_CHILD = "DEPT_AND_CHILD";
  public static final String SCOPE_ALL = "ALL";

  private final UserMapper userMapper;
  private final DepartmentService departmentService;
  private final DepartmentMapper departmentMapper;

  public DataPermissionService(
      UserMapper userMapper,
      DepartmentService departmentService,
      DepartmentMapper departmentMapper) {
    this.userMapper = userMapper;
    this.departmentService = departmentService;
    this.departmentMapper = departmentMapper;
  }

  /**
   * 解析当前用户可见的 owner id 集合。
   *
   * @return 非空集合时表示 owner 必须在此集合内；空集合表示不过滤（ALL）。
   */
  public List<Long> resolveVisibleOwnerIds(Long userId) {
    User user = userMapper.selectById(userId);
    if (user == null) {
      return List.of(userId);
    }
    // ADMIN 强制 ALL
    if ("ADMIN".equals(user.getRole()) || SCOPE_ALL.equals(user.getDataScope())) {
      return List.of();
    }
    String scope = user.getDataScope() == null ? SCOPE_SELF : user.getDataScope();
    return switch (scope) {
      case SCOPE_DEPT -> membersOfDept(user.getDepartmentId());
      case SCOPE_DEPT_AND_CHILD -> membersOfDeptAndChildren(user.getDepartmentId());
      default -> List.of(userId);
    };
  }

  /** 本部门成员 id。 */
  private List<Long> membersOfDept(Long deptId) {
    if (deptId == null) {
      return List.of();
    }
    return userMapper
        .selectList(
            new LambdaQueryWrapper<User>().select(User::getId).eq(User::getDepartmentId, deptId))
        .stream()
        .map(User::getId)
        .toList();
  }

  /** 本部门及全部子孙部门成员 id。 */
  private List<Long> membersOfDeptAndChildren(Long deptId) {
    if (deptId == null) {
      return List.of();
    }
    List<Long> deptIds = departmentService.deptAndChildrenIds(deptId);
    if (deptIds.isEmpty()) {
      return List.of();
    }
    return userMapper
        .selectList(
            new LambdaQueryWrapper<User>().select(User::getId).in(User::getDepartmentId, deptIds))
        .stream()
        .map(User::getId)
        .distinct()
        .toList();
  }
}
