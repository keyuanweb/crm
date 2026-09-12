package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crm.entity.User;
import com.crm.repository.DepartmentMapper;
import com.crm.repository.UserMapper;
import com.crm.security.SecurityUtil;
import com.crm.security.VisibleOwnerIdsCache;
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
  private final VisibleOwnerIdsCache visibleOwnerIdsCache;

  public DataPermissionService(
      UserMapper userMapper,
      DepartmentService departmentService,
      DepartmentMapper departmentMapper,
      VisibleOwnerIdsCache visibleOwnerIdsCache) {
    this.userMapper = userMapper;
    this.departmentService = departmentService;
    this.departmentMapper = departmentMapper;
    this.visibleOwnerIdsCache = visibleOwnerIdsCache;
  }

  /**
   * 解析当前用户可见的 owner id 集合。
   *
   * <p><b>缓存（FR-G27）</b>：命中直接返回；未命中查库后回写。失效**不在这里**——缓存值取决于部门成员集合，会被<b>他人</b>
   * 的写操作改变，故失效点是用户与部门的写入方（{@code UserService}／{@code DepartmentService}）， 且必须经 {@link
   * VisibleOwnerIdsCache#evictAll()} 全量失效（原因见该类说明）。
   *
   * @return 非空集合时表示 owner 必须在此集合内；空集合表示不过滤（ALL）。
   */
  public List<Long> resolveVisibleOwnerIds(Long userId) {
    // 机器主体（API Key，FR-G11）：数据边界恒为"主体本人名下"，**不继承**该主体的数据范围。
    // 此判定必须先于任何库内查表：密钥只能由管理员创建，若按库中角色／data_scope 判定，
    // 管理员密钥会得到"无限制"（空集合 = 不过滤）而读到全量——即"去 ADMIN 化"在此处反转成新的全量泄漏。
    // 也就是说本方法的"无限制"结论只能来自人工会话主体，不能来自 userId 指向的那条用户记录。
    //
    // 该分支**必须整体绕过缓存**：缓存键是 userId，而机器主体的 userId 是其所属主体（管理员）。
    // 若读写同一个键，人工会话那份"ALL → 空集合（不过滤）"会被机器主体读到（全量泄漏），
    // 反之机器主体那份"仅本人"也会污染人工会话（该看的看不到）。两种方向都不允许，故不读也不写。
    if (SecurityUtil.isMachineSubject()) {
      return userId == null ? List.of() : List.of(userId);
    }
    List<Long> cached = visibleOwnerIdsCache.get(userId);
    if (cached != null) {
      return cached;
    }
    List<Long> resolved = resolveFromDatabase(userId);
    visibleOwnerIdsCache.put(userId, resolved);
    return resolved;
  }

  /** 查库解析（缓存未命中时）。 */
  private List<Long> resolveFromDatabase(Long userId) {
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
