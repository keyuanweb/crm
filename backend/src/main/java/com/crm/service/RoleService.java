package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.common.PageResult;
import com.crm.common.RoleConstants;
import com.crm.dto.role.RoleOption;
import com.crm.dto.role.RoleRequest;
import com.crm.dto.role.RoleResponse;
import com.crm.entity.Role;
import com.crm.entity.RoleMenu;
import com.crm.entity.RolePermission;
import com.crm.entity.User;
import com.crm.repository.RoleMapper;
import com.crm.repository.RoleMenuMapper;
import com.crm.repository.RolePermissionMapper;
import com.crm.repository.UserMapper;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 角色权限服务（028-role-permissions，FR-001~008）：角色 CRUD + 菜单/权限配置 + 字典 + 权限查询。 */
@Service
public class RoleService {

  private static final Logger log = LoggerFactory.getLogger(RoleService.class);

  private final RoleMapper roleMapper;
  private final RoleMenuMapper roleMenuMapper;
  private final RolePermissionMapper rolePermissionMapper;
  private final UserMapper userMapper;
  private final AuditService auditService;

  public RoleService(
      RoleMapper roleMapper,
      RoleMenuMapper roleMenuMapper,
      RolePermissionMapper rolePermissionMapper,
      UserMapper userMapper,
      AuditService auditService) {
    this.roleMapper = roleMapper;
    this.roleMenuMapper = roleMenuMapper;
    this.rolePermissionMapper = rolePermissionMapper;
    this.userMapper = userMapper;
    this.auditService = auditService;
  }

  /** 角色列表（含菜单/权限码）。 */
  public PageResult<RoleResponse> list(long page, long pageSize) {
    List<Role> roles =
        roleMapper.selectList(
            new LambdaQueryWrapper<Role>()
                .orderByAsc(Role::getBuiltIn)
                .orderByAsc(Role::getId)
                .last("LIMIT " + pageSize + " OFFSET " + ((page - 1) * pageSize)));
    long total = roleMapper.selectCount(new LambdaQueryWrapper<Role>());
    return PageResult.of(roles.stream().map(this::toResponse).toList(), total, page, pageSize);
  }

  /** 创建角色（含菜单/权限配置）。 */
  @Transactional
  public RoleResponse create(RoleRequest req) {
    validate(req);
    if (codeExists(req.getCode())) {
      throw new BusinessException(ErrorCode.ROLE_DUPLICATE);
    }
    Role role = new Role();
    role.setCode(req.getCode().trim().toUpperCase());
    role.setName(req.getName().trim());
    role.setDescription(req.getDescription());
    role.setDataScope(StringUtils.hasText(req.getDataScope()) ? req.getDataScope() : "SELF");
    role.setEnabled(req.getEnabled() == null || req.getEnabled());
    role.setBuiltIn(false);
    roleMapper.insert(role);
    replaceMenus(role.getId(), req.getMenus());
    replacePermissions(role.getId(), req.getPermissions());
    auditService.record("CREATE", "ROLE", role.getId(), "创建角色：" + role.getName());
    return toResponse(role);
  }

  /** 编辑角色（含菜单/权限配置；内建 ADMIN 不可缩减）。 */
  @Transactional
  public RoleResponse update(Long id, RoleRequest req) {
    Role role = require(id);
    validate(req);
    if (role.getBuiltIn() != null && role.getBuiltIn()) {
      // 内建角色：名称/描述/数据范围可改，菜单/权限不可缩减（全量兜底）
      if (req.getMenus() != null && req.getMenus().size() < menusOf(role.getId()).size()) {
        throw new BusinessException(ErrorCode.ROLE_BUILT_IN);
      }
    }
    role.setName(req.getName().trim());
    role.setDescription(req.getDescription());
    if (StringUtils.hasText(req.getDataScope())) {
      role.setDataScope(req.getDataScope());
    }
    if (req.getEnabled() != null) {
      role.setEnabled(req.getEnabled());
    }
    roleMapper.updateById(role);
    if (req.getMenus() != null) {
      replaceMenus(id, req.getMenus());
    }
    if (req.getPermissions() != null) {
      replacePermissions(id, req.getPermissions());
    }
    auditService.record("UPDATE", "ROLE", id, "编辑角色：" + role.getName());
    return toResponse(role);
  }

  /** 删除角色（内建拒绝；被用户引用拒绝）。 */
  @Transactional
  public void delete(Long id) {
    Role role = require(id);
    if (Boolean.TRUE.equals(role.getBuiltIn())) {
      throw new BusinessException(ErrorCode.ROLE_BUILT_IN);
    }
    Long referenced =
        userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getRole, role.getCode()));
    if (referenced != null && referenced > 0) {
      throw new BusinessException(ErrorCode.ROLE_REFERENCED);
    }
    roleMapper.deleteById(id);
    roleMenuMapper.delete(new LambdaQueryWrapper<RoleMenu>().eq(RoleMenu::getRoleId, id));
    rolePermissionMapper.delete(
        new LambdaQueryWrapper<RolePermission>().eq(RolePermission::getRoleId, id));
    auditService.record("DELETE", "ROLE", id, "删除角色：" + role.getName());
  }

  /** 启用角色下拉选项（用户管理/创建用户用）。 */
  public List<RoleOption> options() {
    return roleMapper
        .selectList(new LambdaQueryWrapper<Role>().eq(Role::getEnabled, 1).orderByAsc(Role::getId))
        .stream()
        .map(r -> new RoleOption(r.getId(), r.getCode(), r.getName(), r.getDataScope()))
        .toList();
  }

  /** 菜单树字典（配置页勾选）。 */
  public List<Map<String, Object>> menuTree() {
    return RoleConstants.MENU_TREE;
  }

  /** 权限点字典（配置页勾选）。 */
  public List<Map<String, Object>> permissionDefs() {
    return RoleConstants.PERMISSION_DEFS;
  }

  /** 按角色编码查权限码列表（切面校验/me 用）；角色不存在返回空。 */
  public List<String> permissionsOf(String roleCode) {
    if (!StringUtils.hasText(roleCode)) {
      return List.of();
    }
    Role role =
        roleMapper.selectOne(
            new LambdaQueryWrapper<Role>().eq(Role::getCode, roleCode.trim()).last("LIMIT 1"));
    if (role == null) {
      return List.of();
    }
    return rolePermissionMapper
        .selectList(
            new LambdaQueryWrapper<RolePermission>().eq(RolePermission::getRoleId, role.getId()))
        .stream()
        .map(RolePermission::getPermissionCode)
        .toList();
  }

  /** 按角色编码查可见菜单 key 列表（me 用）。 */
  public List<String> menusOf(String roleCode) {
    if (!StringUtils.hasText(roleCode)) {
      return List.of();
    }
    Role role =
        roleMapper.selectOne(
            new LambdaQueryWrapper<Role>().eq(Role::getCode, roleCode.trim()).last("LIMIT 1"));
    if (role == null) {
      return List.of();
    }
    return menusOf(role.getId());
  }

  private List<String> menusOf(Long roleId) {
    return roleMenuMapper
        .selectList(new LambdaQueryWrapper<RoleMenu>().eq(RoleMenu::getRoleId, roleId))
        .stream()
        .map(RoleMenu::getMenuKey)
        .toList();
  }

  private void validate(RoleRequest req) {
    if (!StringUtils.hasText(req.getCode()) || !StringUtils.hasText(req.getName())) {
      throw new BusinessException(ErrorCode.BAD_REQUEST);
    }
    if (req.getDataScope() != null
        && !List.of("ALL", "DEPT", "SELF").contains(req.getDataScope())) {
      throw new BusinessException(ErrorCode.BAD_REQUEST);
    }
  }

  private boolean codeExists(String code) {
    Long count =
        roleMapper.selectCount(
            new LambdaQueryWrapper<Role>().eq(Role::getCode, code.trim().toUpperCase()));
    return count != null && count > 0;
  }

  private void replaceMenus(Long roleId, List<String> menus) {
    roleMenuMapper.delete(new LambdaQueryWrapper<RoleMenu>().eq(RoleMenu::getRoleId, roleId));
    if (menus != null) {
      for (String key : menus) {
        RoleMenu m = new RoleMenu();
        m.setRoleId(roleId);
        m.setMenuKey(key);
        roleMenuMapper.insert(m);
      }
    }
  }

  private void replacePermissions(Long roleId, List<String> permissions) {
    rolePermissionMapper.delete(
        new LambdaQueryWrapper<RolePermission>().eq(RolePermission::getRoleId, roleId));
    if (permissions != null) {
      for (String code : permissions) {
        RolePermission p = new RolePermission();
        p.setRoleId(roleId);
        p.setPermissionCode(code);
        rolePermissionMapper.insert(p);
      }
    }
  }

  private Role require(Long id) {
    Role role = roleMapper.selectById(id);
    if (role == null) {
      throw new BusinessException(ErrorCode.BAD_REQUEST);
    }
    return role;
  }

  private RoleResponse toResponse(Role role) {
    RoleResponse resp = new RoleResponse();
    resp.setId(role.getId());
    resp.setCode(role.getCode());
    resp.setName(role.getName());
    resp.setDescription(role.getDescription());
    resp.setDataScope(role.getDataScope());
    resp.setEnabled(role.getEnabled());
    resp.setBuiltIn(role.getBuiltIn());
    resp.setMenus(menusOf(role.getId()));
    resp.setPermissions(
        rolePermissionMapper
            .selectList(
                new LambdaQueryWrapper<RolePermission>()
                    .eq(RolePermission::getRoleId, role.getId()))
            .stream()
            .map(RolePermission::getPermissionCode)
            .toList());
    return resp;
  }
}
