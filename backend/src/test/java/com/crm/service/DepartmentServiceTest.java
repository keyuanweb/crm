package com.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
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
import com.crm.security.VisibleOwnerIdsCache;
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
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

/** DepartmentService 单元测试（012 T013）：树/子孙集合/删除防护。 */
@ExtendWith(MockitoExtension.class)
class DepartmentServiceTest {

  private DepartmentMapper departmentMapper;
  private UserMapper userMapper;
  private AuditService auditService;
  private DepartmentService service;
  private CacheManager cacheManager;
  private VisibleOwnerIdsCache visibleOwnerIdsCache;
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
    // 真实缓存容器（不是 mock）：本类要断言"写操作后缓存被清空"这一安全不变式，
    // 断言必须落在真实缓存状态上。每个测试方法新建，避免跨方法残留造成假命中。
    cacheManager = new ConcurrentMapCacheManager();
    visibleOwnerIdsCache = new VisibleOwnerIdsCache(cacheManager);
    service =
        new DepartmentService(departmentMapper, userMapper, auditService, visibleOwnerIdsCache);
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
    DataPermissionService dps = newDataPermissionService();
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
    DataPermissionService dps = newDataPermissionService();
    when(userMapper.selectById(1L)).thenReturn(user(1L, "ADMIN", null, "SELF"));

    var ids = dps.resolveVisibleOwnerIds(1L);

    assertThat(ids).isEmpty();
  }

  // ------------------------------------------------------------------
  // 083（FR-G27）：可见负责人集合缓存的读写与**全量失效**安全不变式
  // ------------------------------------------------------------------

  private DataPermissionService newDataPermissionService() {
    return new DataPermissionService(userMapper, service, departmentMapper, visibleOwnerIdsCache);
  }

  /** 直连缓存容器（不经 Service），用于断言缓存内的真实状态。 */
  private Cache visibleOwnerIdsCacheHandle() {
    return cacheManager.getCache(com.crm.config.CacheConfig.VISIBLE_OWNER_IDS_CACHE);
  }

  @Test
  @DisplayName("083：resolveVisibleOwnerIds 第二次调用不再查库（缓存命中）")
  void visibleOwnerIdsIsCached() {
    when(userMapper.selectById(1L)).thenReturn(user(1L, "SALES", 10L, "DEPT"));
    User m1 = new User();
    m1.setId(2L);
    when(userMapper.selectList(any())).thenReturn(List.of(m1));

    DataPermissionService dps = newDataPermissionService();
    List<Long> first = dps.resolveVisibleOwnerIds(1L);
    List<Long> second = dps.resolveVisibleOwnerIds(1L);

    assertThat(second).isEqualTo(first);
    // 用户表只被查了一次（第一次解析）；第二次完全命中缓存
    verify(userMapper, times(1)).selectList(any());
  }

  @Test
  @DisplayName("083 安全不变式：部门写操作必须全量失效（他人的缓存键也必须清掉）")
  void departmentWriteEvictsEveryKeyNotJustItsOwn() {
    // A（用户 1）与 B（用户 2）各自缓存了一份可见集合——A 的集合取决于"谁在部门 10 里"，
    // 会被 B 的写入改变。故写操作后 A 的键也必须消失，否则 A 会继续看到已调出同事的数据。
    when(userMapper.selectById(1L)).thenReturn(user(1L, "SALES", 10L, "DEPT"));
    when(userMapper.selectById(2L)).thenReturn(user(2L, "SALES", 10L, "DEPT"));
    User m1 = new User();
    m1.setId(2L);
    when(userMapper.selectList(any())).thenReturn(List.of(m1));

    DataPermissionService dps = newDataPermissionService();
    dps.resolveVisibleOwnerIds(1L);
    dps.resolveVisibleOwnerIds(2L);
    assertThat(visibleOwnerIdsCacheHandle().get(1L)).isNotNull();
    assertThat(visibleOwnerIdsCacheHandle().get(2L)).isNotNull();

    // 一次与用户 1、用户 2 都无关的部门写操作
    when(departmentMapper.insert(any(Department.class)))
        .thenAnswer(
            invocation -> {
              Department d = invocation.getArgument(0);
              d.setId(99L);
              return 1;
            });
    DepartmentRequest req = new DepartmentRequest();
    req.setName("新部门");
    service.create(req);

    // 断言"键 1 也消失了"，而不是"被改的那个键消失了"——后者正是越权读取的实现方式
    assertThat(visibleOwnerIdsCacheHandle().get(1L)).isNull();
    assertThat(visibleOwnerIdsCacheHandle().get(2L)).isNull();
  }

  @Test
  @DisplayName("083 安全不变式：机器主体（API Key）绕过缓存——既不读也不写")
  void machineSubjectBypassesCacheEntirely() {
    when(userMapper.selectById(7L)).thenReturn(user(7L, "ADMIN", null, "ALL"));

    // 先让"人工会话的 7 号用户"缓存下 ALL → 空集合（不过滤）
    DataPermissionService dps = newDataPermissionService();
    assertThat(dps.resolveVisibleOwnerIds(7L)).isEmpty();
    assertThat(visibleOwnerIdsCacheHandle().get(7L)).isNotNull();

    // 同一 userId 走机器主体：必须返回"仅本人"，且不得读到上面那份"不过滤"
    securityUtilMock.when(SecurityUtil::isMachineSubject).thenReturn(true);
    assertThat(dps.resolveVisibleOwnerIds(7L)).containsExactly(7L);

    // 反向也必须成立：机器主体不得把自己的结论写进人工会话的键
    assertThat(visibleOwnerIdsCacheHandle().get(7L)).isNotNull();
    securityUtilMock.when(SecurityUtil::isMachineSubject).thenReturn(false);
    assertThat(dps.resolveVisibleOwnerIds(7L)).isEmpty();
  }

  @Test
  @DisplayName("083：null 键不进缓存（避免把无主体当成一个可命中的键）")
  void nullKeyIsNeverStoredOrReturned() {
    // 这份 null 安全由 VisibleOwnerIdsCache 自己保证（读取方无需判空）：
    // 注意 ConcurrentHashMap 不允许 null 键，若不拦就会在缓存层抛 NPE。
    visibleOwnerIdsCache.put(null, List.of(1L));
    assertThat(visibleOwnerIdsCache.get(null)).isNull();
    assertThat(visibleOwnerIdsCacheHandle().get(1L)).isNull();
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
