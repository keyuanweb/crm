package com.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.crm.common.BusinessException;
import com.crm.dto.role.RoleOption;
import com.crm.dto.role.RoleRequest;
import com.crm.entity.Role;
import com.crm.entity.RoleMenu;
import com.crm.entity.RolePermission;
import com.crm.repository.RoleMapper;
import com.crm.repository.RoleMenuMapper;
import com.crm.repository.RolePermissionMapper;
import com.crm.repository.UserMapper;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** RoleService 单元测试（028 T004）：CRUD/配置/内建保护/字典。 */
class RoleServiceTest {

  @BeforeAll
  static void initTableInfo() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
    TableInfoHelper.initTableInfo(assistant, Role.class);
    TableInfoHelper.initTableInfo(assistant, RoleMenu.class);
    TableInfoHelper.initTableInfo(assistant, RolePermission.class);
  }

  private RoleMapper roleMapper;
  private RoleMenuMapper roleMenuMapper;
  private RolePermissionMapper rolePermissionMapper;
  private UserMapper userMapper;
  private AuditService auditService;
  private RoleService service;

  @BeforeEach
  void setUp() {
    roleMapper = mock(RoleMapper.class);
    roleMenuMapper = mock(RoleMenuMapper.class);
    rolePermissionMapper = mock(RolePermissionMapper.class);
    userMapper = mock(UserMapper.class);
    auditService = mock(AuditService.class);
    service =
        new RoleService(roleMapper, roleMenuMapper, rolePermissionMapper, userMapper, auditService);
  }

  private Role role(Long id, String code, String name, boolean builtIn) {
    Role r = new Role();
    r.setId(id);
    r.setCode(code);
    r.setName(name);
    r.setDataScope("SELF");
    r.setEnabled(true);
    r.setBuiltIn(builtIn);
    return r;
  }

  @Test
  @DisplayName("创建角色：插入并配置菜单/权限")
  void createRoleConfigures() {
    when(roleMapper.selectCount(any())).thenReturn(0L);
    RoleRequest req = new RoleRequest();
    req.setCode("REGIONAL_MGR");
    req.setName("区域经理");
    req.setDataScope("DEPT");
    req.setMenus(List.of("customers", "orders"));
    req.setPermissions(List.of("customer:create"));

    service.create(req);

    verify(roleMapper).insert(any(Role.class));
    // menus 2 个 + permissions 1 个
    verify(roleMenuMapper, Mockito.times(2)).insert(any(RoleMenu.class));
    verify(rolePermissionMapper, Mockito.times(1)).insert(any(RolePermission.class));
  }

  @Test
  @DisplayName("编码重复创建拒绝 409")
  void createDuplicateCodeRejected() {
    when(roleMapper.selectCount(any())).thenReturn(1L);
    RoleRequest req = new RoleRequest();
    req.setCode("ADMIN");
    req.setName("重复");

    assertThatThrownBy(() -> service.create(req)).isInstanceOf(BusinessException.class);
    verify(roleMapper, never()).insert(any());
  }

  @Test
  @DisplayName("内建角色删除拒绝")
  void deleteBuiltInRejected() {
    when(roleMapper.selectById(1L)).thenReturn(role(1L, "ADMIN", "系统管理员", true));

    assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(BusinessException.class);
  }

  @Test
  @DisplayName("被用户引用的角色删除拒绝")
  void deleteReferencedRejected() {
    when(roleMapper.selectById(2L)).thenReturn(role(2L, "SALES", "销售", true));
    when(userMapper.selectCount(any())).thenReturn(1L);

    assertThatThrownBy(() -> service.delete(2L)).isInstanceOf(BusinessException.class);
    verify(roleMapper, never()).deleteById(anyLong());
  }

  @Test
  @DisplayName("配置保存：删除重建关联（事务内）")
  void configureReplacesRelations() {
    RoleRequest req = new RoleRequest();
    req.setCode("SALES");
    req.setName("销售");
    req.setDataScope("SELF");
    req.setMenus(List.of("customers"));
    req.setPermissions(List.of("customer:create", "customer:delete"));
    when(roleMapper.selectById(1L)).thenReturn(role(1L, "SALES", "销售", true));

    service.update(1L, req);

    verify(roleMenuMapper).delete(any());
    verify(rolePermissionMapper).delete(any());
    // menus 1 个 + permissions 2 个
    verify(roleMenuMapper, Mockito.times(1)).insert(any(RoleMenu.class));
    verify(rolePermissionMapper, Mockito.times(2)).insert(any(RolePermission.class));
  }

  @Test
  @DisplayName("options 返回启用角色下拉")
  void optionsReturnEnabledRoles() {
    Role r1 = role(1L, "SALES", "销售", true);
    Role r2 = role(2L, "CUSTOM", "自定义", false);
    when(roleMapper.selectList(any())).thenReturn(List.of(r1, r2));

    List<RoleOption> options = service.options();

    assertThat(options).hasSize(2);
    assertThat(options.get(0).getCode()).isEqualTo("SALES");
  }

  @Test
  @DisplayName("permissionsOf 返回角色权限码（切面/me 用）")
  void permissionsOfReturnsCodes() {
    Role salesRole = role(1L, "SALES", "销售", true);
    when(roleMapper.selectOne(any())).thenReturn(salesRole);
    RolePermission p1 = new RolePermission();
    p1.setRoleId(1L);
    p1.setPermissionCode("customer:create");
    RolePermission p2 = new RolePermission();
    p2.setRoleId(1L);
    p2.setPermissionCode("order:payment");
    when(rolePermissionMapper.selectList(any())).thenReturn(List.of(p1, p2));

    List<String> codes = service.permissionsOf("SALES");

    assertThat(codes).containsExactlyInAnyOrder("customer:create", "order:payment");
  }

  @Test
  @DisplayName("字典：menu-tree 与 permission-defs 非空")
  void dictionariesNotEmpty() {
    assertThat(service.menuTree()).isNotEmpty();
    assertThat(service.permissionDefs()).isNotEmpty();
  }
}
