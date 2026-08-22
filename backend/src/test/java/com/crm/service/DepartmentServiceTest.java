package com.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.dto.department.DepartmentRequest;
import com.crm.entity.Department;
import com.crm.entity.User;
import com.crm.repository.DepartmentMapper;
import com.crm.repository.UserMapper;
import com.crm.security.SecurityUtil;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/** DepartmentService 单元测试（012 T013）：树/子孙集合/删除防护。 */
@ExtendWith(MockitoExtension.class)
class DepartmentServiceTest {

  private DepartmentMapper departmentMapper;
  private UserMapper userMapper;
  private AuditService auditService;
  private DepartmentService service;
  private MockedStatic<SecurityUtil> securityUtilMock;

  /** 纯 Mockito 测试无 Spring 上下文：注册实体 TableInfo，供 LambdaQueryWrapper 解析列名。 */
  @BeforeAll
  static void initTableInfo() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
    TableInfoHelper.initTableInfo(assistant, Department.class);
    TableInfoHelper.initTableInfo(assistant, User.class);
  }

  @BeforeEach
  void setUp() {
    departmentMapper = mock(DepartmentMapper.class);
    userMapper = mock(UserMapper.class);
    auditService = mock(AuditService.class);
    service = new DepartmentService(departmentMapper, userMapper, auditService);
    securityUtilMock = Mockito.mockStatic(SecurityUtil.class);
    securityUtilMock.when(SecurityUtil::currentUserId).thenReturn(1L);
  }

  @AfterEach
  void tearDown() {
    securityUtilMock.close();
  }

  private Department dept(Long id, String name, Long parentId) {
    Department d = new Department();
    d.setId(id);
    d.setName(name);
    d.setParentId(parentId);
    return d;
  }

  @Test
  @DisplayName("部门树：层级构建正确")
  void treeBuildsHierarchy() {
    when(departmentMapper.selectList(any()))
        .thenReturn(List.of(dept(1L, "销售一部", null), dept(2L, "华东组", 1L), dept(3L, "其他部", null)));

    var tree = service.tree();

    assertThat(tree.getDepartments()).hasSize(2);
    assertThat(tree.getDepartments().get(0).getChildren()).hasSize(1);
    assertThat(tree.getDepartments().get(0).getChildren().get(0).getName()).isEqualTo("华东组");
  }

  @Test
  @DisplayName("部门及下级：递归收集子孙 id")
  void deptAndChildrenCollects() {
    when(departmentMapper.selectList(any()))
        .thenReturn(
            List.of(dept(1L, "A", null), dept(2L, "B", 1L), dept(3L, "C", 2L), dept(4L, "D", 1L)));

    var ids = service.deptAndChildrenIds(1L);

    assertThat(ids).containsExactlyInAnyOrder(1L, 2L, 3L, 4L);
  }

  @Test
  @DisplayName("删除有子部门的部门抛出 DEPARTMENT_HAS_CHILDREN_OR_MEMBERS")
  void deleteWithChildrenThrows() {
    when(departmentMapper.selectById(1L)).thenReturn(dept(1L, "A", null));
    when(departmentMapper.selectCount(any())).thenReturn(2L);

    assertThatThrownBy(() -> service.delete(1L))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.DEPARTMENT_HAS_CHILDREN_OR_MEMBERS);
  }

  @Test
  @DisplayName("删除有成员的部门抛出 DEPARTMENT_HAS_CHILDREN_OR_MEMBERS")
  void deleteWithMembersThrows() {
    when(departmentMapper.selectById(1L)).thenReturn(dept(1L, "A", null));
    when(departmentMapper.selectCount(any())).thenReturn(0L);
    when(userMapper.selectCount(any())).thenReturn(3L);

    assertThatThrownBy(() -> service.delete(1L))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.DEPARTMENT_HAS_CHILDREN_OR_MEMBERS);
  }

  @Test
  @DisplayName("创建部门成功：审计记录")
  void createSucceeds() {
    // 上级部门存在
    when(departmentMapper.selectById(1L)).thenReturn(dept(1L, "销售一部", null));
    when(departmentMapper.insert(any(Department.class)))
        .thenAnswer(
            invocation -> {
              Department d = invocation.getArgument(0);
              d.setId(2L);
              return 1;
            });
    DepartmentRequest req = new DepartmentRequest();
    req.setName("华东组");
    req.setParentId(1L);

    var resp = service.create(req);

    assertThat(resp.getId()).isEqualTo(2L);
    assertThat(resp.getParentId()).isEqualTo(1L);
    verify(departmentMapper).insert(any(Department.class));
    verify(auditService).record("CREATE", "DEPARTMENT", 2L, "创建部门：华东组");
  }

  @Test
  @DisplayName("创建部门：上级不存在抛出 DEPARTMENT_NOT_FOUND")
  void createWithMissingParentThrows() {
    when(departmentMapper.selectById(99L)).thenReturn(null);
    DepartmentRequest req = new DepartmentRequest();
    req.setName("华东组");
    req.setParentId(99L);

    assertThatThrownBy(() -> service.create(req))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.DEPARTMENT_NOT_FOUND);
  }

  @Test
  @DisplayName("User 数据权限：DEPT 返回本部门成员")
  void deptScopeResolvesMembers() {
    DataPermissionService dps = new DataPermissionService(userMapper, service, departmentMapper);
    when(userMapper.selectById(1L)).thenReturn(user(1L, "SALES", 10L, "DEPT"));
    User m1 = new User();
    m1.setId(2L);
    User m2 = new User();
    m2.setId(3L);
    when(userMapper.selectList(any())).thenReturn(List.of(m1, m2));

    var ids = dps.resolveVisibleOwnerIds(1L);

    assertThat(ids).containsExactlyInAnyOrder(2L, 3L);
  }

  @Test
  @DisplayName("User 数据权限：ADMIN 强制 ALL（空集合不过滤）")
  void adminScopeIsAll() {
    DataPermissionService dps = new DataPermissionService(userMapper, service, departmentMapper);
    when(userMapper.selectById(1L)).thenReturn(user(1L, "ADMIN", null, "SELF"));

    var ids = dps.resolveVisibleOwnerIds(1L);

    assertThat(ids).isEmpty();
  }

  private User user(Long id, String role, Long deptId, String scope) {
    User u = new User();
    u.setId(id);
    u.setRole(role);
    u.setDepartmentId(deptId);
    u.setDataScope(scope);
    return u;
  }
}
