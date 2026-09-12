package com.crm.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.AbstractIntegrationTest;
import com.crm.dto.department.DataPermissionRequest;
import com.crm.dto.department.DepartmentRequest;
import com.crm.dto.role.RoleRequest;
import com.crm.dto.role.RoleResponse;
import com.crm.entity.Customer;
import com.crm.entity.Department;
import com.crm.entity.Product;
import com.crm.entity.User;
import com.crm.repository.CustomerMapper;
import com.crm.repository.DepartmentMapper;
import com.crm.repository.ProductMapper;
import com.crm.repository.UserMapper;
import com.crm.security.JwtAuthFilter;
import com.crm.service.DataPermissionService;
import com.crm.service.DepartmentService;
import com.crm.service.RoleService;
import com.crm.service.UserService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.Cache;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * US5 的性能与一致性回归网（FR-G22~G27，083-engineering-consolidation）。
 *
 * <p><b>本文件为什么必须先跑成红</b>：它断言的三件事——权限查询被缓存、操作人自动填充、报价单产品查询与明细行数解耦
 * ——在编写时**都还没有实现**。先写测试并确认失败，才能证明这些断言真的能观察到差异（章程原则四）。若一开始就绿， 说明断言根本没有落在缺陷上，那张"回归网"是假的。
 *
 * <p><b>为什么用查询计数而不是断言"返回值"</b>：缓存、N+1 这类损耗**不改变返回值**——改造前后 {@code permissionsOf}
 * 返回同一个列表，报价单算出同一个总额。差异只体现在"为此付了几次数据库往返"上， 所以断言必须落在往返次数上。计数器（{@link
 * QueryCountingInterceptor}）是测试基础设施，不是被测对象。
 *
 * <p><b>计数器的自校验</b>：断言"第二次调用 0 次查询"在计数器**根本没接上线**时会静默为真——正是本 spec 反复 处理的那类假绿。故每处"0
 * 次"断言之前都先断言"缓存未命中时的查询次数"，后者一旦为 0 就说明计数器没工作， 测试立刻失败而不是假装通过。
 *
 * <p>种子数据：本类**不依赖任何业务种子数据**，所需的用户/部门/客户/产品一律自建（用户与产品经 Mapper 直插， 见各用例内的说明）。角色与用户表在 H2
 * 测试库中确有种子，但那部分已由其他 IT 覆盖，此处不借用。
 */
class PerformanceRegressionIT extends AbstractIntegrationTest {

  @Autowired private RoleService roleService;
  @Autowired private DataPermissionService dataPermissionService;
  @Autowired private UserService userService;
  @Autowired private DepartmentService departmentService;
  @Autowired private UserMapper userMapper;
  @Autowired private DepartmentMapper departmentMapper;
  @Autowired private CustomerMapper customerMapper;
  @Autowired private ProductMapper productMapper;

  /** 清空全部进程内缓存：让每个用例都从"缓存未命中"这一确定的起点开始。 */
  private void clearAllCaches() {
    for (String name : cacheManager.getCacheNames()) {
      Cache cache = cacheManager.getCache(name);
      if (cache != null) {
        cache.clear();
      }
    }
  }

  /** 把查询计数器注册进 MyBatis（详见 {@link QueryCountingInterceptor}）。 */
  @TestConfiguration
  static class QueryCountingConfig {
    @Bean
    QueryCountingInterceptor queryCountingInterceptor() {
      return new QueryCountingInterceptor();
    }
  }

  /**
   * 按 {@code MappedStatement} id 统计查询次数的 MyBatis 拦截器。
   *
   * <p><b>为什么按 Mapper 前缀而不是精确语句 id 统计</b>：MyBatis-Plus 的 {@code selectOne} 内部可能落到 {@code
   * selectList} 语句上（实现细节），精确 id 会把这类无关紧要的内部差异变成测试失败。本类关心的是 "某个 Mapper 被往返了几次"，前缀匹配正好是这个粒度。
   *
   * <p><b>为什么只统计测试线程</b>：测试库是共享内存库，且 {@code DataRetentionScheduler}／ {@code
   * ScheduledExportScheduler} 等定时任务在测试上下文里同样活着。全局计数会把它们恰好落在计数窗口内的 查询算进来，形成偶发失败。请求链路（MockMvc
   * 调度与服务直调）都在测试线程上执行，按线程收窄既精确又消除了 这份噪声。
   */
  @Intercepts(
      @Signature(
          type = Executor.class,
          method = "query",
          args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}))
  static class QueryCountingInterceptor implements Interceptor {

    private static final Map<String, AtomicInteger> COUNTS = new ConcurrentHashMap<>();
    private static volatile Thread ownerThread;

    /** 清空计数并把"被统计的线程"钉在当前线程上。 */
    static void reset() {
      COUNTS.clear();
      ownerThread = Thread.currentThread();
    }

    /** 统计语句 id 含 {@code .<mapperSimpleName>.} 的查询次数。 */
    static int countOf(String mapperSimpleName) {
      String needle = "." + mapperSimpleName + ".";
      return COUNTS.entrySet().stream()
          .filter(e -> e.getKey().contains(needle))
          .mapToInt(e -> e.getValue().get())
          .sum();
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
      MappedStatement statement = (MappedStatement) invocation.getArgs()[0];
      if (Thread.currentThread() == ownerThread) {
        COUNTS.computeIfAbsent(statement.getId(), key -> new AtomicInteger()).incrementAndGet();
      }
      return invocation.proceed();
    }

    @Override
    public Object plugin(Object target) {
      return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
      // 无配置项
    }
  }

  // ------------------------------------------------------------------ 角色权限缓存

  @Test
  @DisplayName("同一角色的权限查询在缓存有效期内不再触库（FR-G23）")
  void rolePermissionsQueryIsCached() {
    RoleResponse role = createRole("CACHE_PROBE_ROLE", List.of("customer:read", "customer:write"));
    assertThat(role.getId()).isNotNull();

    clearAllCaches();
    QueryCountingInterceptor.reset();
    List<String> first = roleService.permissionsOf("CACHE_PROBE_ROLE");
    int missQueries =
        QueryCountingInterceptor.countOf("RoleMapper")
            + QueryCountingInterceptor.countOf("RolePermissionMapper");

    assertThat(first).containsExactlyInAnyOrder("customer:read", "customer:write");
    // 计数器自校验：这一条同时证明"计数器真的接上了"。若为 0，说明下面的 0 断言毫无意义。
    assertThat(missQueries).as("缓存未命中时应有的查询次数（角色 1 次 + 权限 1 次）；为 0 说明查询计数器没接上").isEqualTo(2);

    QueryCountingInterceptor.reset();
    List<String> second = roleService.permissionsOf("CACHE_PROBE_ROLE");

    assertThat(second).as("缓存命中时返回值必须与首次一致").isEqualTo(first);
    assertThat(QueryCountingInterceptor.countOf("RoleMapper")).as("缓存有效期内不得再查角色表").isZero();
    assertThat(QueryCountingInterceptor.countOf("RolePermissionMapper"))
        .as("缓存有效期内不得再查角色权限表")
        .isZero();
  }

  @Test
  @DisplayName("角色权限变更后缓存必须失效，不得把旧权限继续下发（不变式 §5.1）")
  void rolePermissionCacheIsInvalidatedOnWrite() {
    RoleResponse role = createRole("INVALIDATE_PROBE_ROLE", List.of("customer:read"));
    clearAllCaches();
    assertThat(roleService.permissionsOf("INVALIDATE_PROBE_ROLE")).containsExactly("customer:read");

    RoleRequest update = new RoleRequest();
    update.setCode("INVALIDATE_PROBE_ROLE");
    update.setName("失效探针角色");
    update.setPermissions(List.of("lead:read"));
    roleService.update(role.getId(), update);

    assertThat(roleService.permissionsOf("INVALIDATE_PROBE_ROLE"))
        .as("角色权限变更后仍下发旧权限 = 收权不生效")
        .containsExactly("lead:read");
  }

  // ------------------------------------------------------------------ 可见数据范围缓存

  @Test
  @DisplayName("同一用户的可见数据范围在缓存有效期内不再触库（FR-G27）")
  void visibleOwnerIdsQueryIsCached() {
    long deptId = insertDepartment("缓存探针部门");
    long memberA = insertUser("cache_probe_a", "SALES", DataPermissionService.SCOPE_DEPT, deptId);
    long memberB = insertUser("cache_probe_b", "SALES", DataPermissionService.SCOPE_DEPT, deptId);
    long owner = insertUser("cache_probe_owner", "SALES", DataPermissionService.SCOPE_DEPT, deptId);

    clearAllCaches();
    QueryCountingInterceptor.reset();
    List<Long> first = dataPermissionService.resolveVisibleOwnerIds(owner);
    int missQueries = QueryCountingInterceptor.countOf("UserMapper");

    assertThat(first).containsExactlyInAnyOrder(memberA, memberB, owner);
    assertThat(missQueries).as("缓存未命中时应有的查询次数（取参与者 1 次 + 取本部门成员 1 次）；为 0 说明计数器没接上").isEqualTo(2);

    QueryCountingInterceptor.reset();
    List<Long> second = dataPermissionService.resolveVisibleOwnerIds(owner);

    assertThat(second).isEqualTo(first);
    assertThat(QueryCountingInterceptor.countOf("UserMapper")).as("缓存有效期内不得再查用户表").isZero();
  }

  @Test
  @DisplayName("用户/部门写操作后可见数据范围缓存必须全量失效（FR-G27、不变式 §5.2）")
  void visibleOwnerIdsCacheIsFullyInvalidatedOnUserOrDeptWrite() {
    long deptId = insertDepartment("失效探针部门");
    long owner =
        insertUser("invalidate_probe_owner", "SALES", DataPermissionService.SCOPE_DEPT, deptId);

    clearAllCaches();
    List<Long> cached = dataPermissionService.resolveVisibleOwnerIds(owner);

    // 1) 证明缓存确实存在：绕过服务层直接改库，缓存期内看不到新成员。
    //    没有这一条，"写操作后必须失效"就无法与"根本没有缓存"区分——后者会以假绿的方式通过。
    long byDeptWrite =
        insertUser("invalidate_probe_c1", "SALES", DataPermissionService.SCOPE_DEPT, deptId);
    assertThat(dataPermissionService.resolveVisibleOwnerIds(owner))
        .as("缓存期内不应看到直接改库的新成员；此断言失败说明根本没有缓存")
        .isEqualTo(cached);

    // 2) 部门写操作 → 全量失效。部门成员集合的变动无法从 owner 自身的写操作推断，
    //    故失效范围不得是"谁被改就失效谁"。
    departmentService.create(departmentRequest("失效探针部门-旁支"));
    assertThat(dataPermissionService.resolveVisibleOwnerIds(owner))
        .as("部门写操作后缓存未失效：owner 看不到新增成员")
        .contains(byDeptWrite);

    // 3) 用户写操作 → 同样必须全量失效（此处被写的是**另一个**用户，不是 owner 本人）
    long byUserWrite =
        insertUser("invalidate_probe_c2", "SALES", DataPermissionService.SCOPE_DEPT, deptId);
    assertThat(dataPermissionService.resolveVisibleOwnerIds(owner))
        .as("刚直接插入的成员在缓存期内不应可见")
        .doesNotContain(byUserWrite);

    userService.setDataPermission(
        byUserWrite, dataPermissionRequest(deptId, DataPermissionService.SCOPE_DEPT));
    assertThat(dataPermissionService.resolveVisibleOwnerIds(owner))
        .as("其他用户的写操作后缓存未失效：owner 看不到新增成员")
        .contains(byUserWrite);
  }

  @Test
  @DisplayName("机器主体不得命中人工会话的可见范围缓存（FR-G11 与缓存的交互）")
  void machineSubjectDoesNotShareHumanSessionCache() {
    long deptId = insertDepartment("机器主体探针部门");
    long adminLike =
        insertUser("cache_probe_all", "SALES", DataPermissionService.SCOPE_ALL, deptId);

    // 人工会话：dataScope=ALL → 空集合（= 不过滤）。这个"不过滤"结论会被写进缓存。
    clearAllCaches();
    assertThat(dataPermissionService.resolveVisibleOwnerIds(adminLike)).isEmpty();

    // 机器主体（API Key）：数据边界恒为"主体本人名下"，**不继承**该主体的数据范围。
    // 若缓存只以 userId 为键，这里会命中上面那份"不过滤"——改造后的提权就由此复活。
    SecurityContextHolder.getContext().setAuthentication(machineAuth(adminLike));
    try {
      assertThat(dataPermissionService.resolveVisibleOwnerIds(adminLike))
          .as("机器主体命中了人工会话的全量缓存 = 全量数据泄漏")
          .containsExactly(adminLike);
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  // ------------------------------------------------------------------ 操作人自动填充

  @Test
  @DisplayName("新增实体时自动填充操作人（FR-G26）")
  void createdByIsFilledOnInsert() {
    long operatorId = 4242L;

    SecurityContextHolder.getContext().setAuthentication(humanAuth(operatorId));
    try {
      Product probe = new Product();
      probe.setCode("AUTOFILL-PROBE-1");
      probe.setName("操作人自动填充探针");
      probe.setStatus("ACTIVE");
      // 经 Mapper 直插：这条路径上**没有任何业务代码手写 createdBy**，故观测到的只能是公共字段填充的结果。
      // （走 Service 的路径大都已显式 setCreatedBy，无法区分"填充生效"与"业务代码写了"。）
      productMapper.insert(probe);

      assertThat(productMapper.selectById(probe.getId()).getCreatedBy())
          .as("新增时操作人应由公共字段填充写入")
          .isEqualTo(operatorId);
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  // ------------------------------------------------------------------ 报价单 N+1

  @Test
  @DisplayName("报价单创建的产品查询次数与明细行数解耦（FR-G24）")
  void quoteProductQueriesDoNotScaleTwicePerLine() throws Exception {
    // 断言的是"解耦"，故必须用两个**行数不同**的报价单互相比对：
    //   改造前 = 2N（4 行 8 次、8 行 16 次）——次数随行数增长；
    //   只做"两处合一" = N（4 次、8 次）——次数仍随行数增长，只是系数从 2 降到 1；
    //   正确做法 = 1（两次都是 1 次）——一次批量查询取齐全部产品，与行数无关。
    // 因此**不能**断言"次数 == 行数"：那会把"系数从 2 降到 1"这件半成品固化为验收标准，
    // 并让真正的解耦（1 次）反而判为失败。
    long customerId = insertCustomer("报价查询计数客户");
    String token = loginAndGetToken();

    List<Long> fourLines = insertProducts("4L", 4);
    QueryCountingInterceptor.reset();
    mockMvc
        .perform(
            post("/api/v1/quotes")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(quoteBody(customerId, fourLines)))
        .andExpect(status().isCreated());
    int queriesForFourLines = QueryCountingInterceptor.countOf("ProductMapper");

    List<Long> eightLines = insertProducts("8L", 8);
    QueryCountingInterceptor.reset();
    mockMvc
        .perform(
            post("/api/v1/quotes")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(quoteBody(customerId, eightLines)))
        .andExpect(status().isCreated());
    int queriesForEightLines = QueryCountingInterceptor.countOf("ProductMapper");

    assertThat(queriesForEightLines)
        .as("行数翻倍不得使产品查询次数变化——这才是解耦（4 行与 8 行各 1 次）")
        .isEqualTo(queriesForFourLines);
    assertThat(queriesForFourLines).as("每张报价单只应有一次产品查询（批量取齐），2N 与 N 都是随行数增长的旧缺陷形态").isEqualTo(1);
  }

  // ------------------------------------------------------------------ 夹具

  private RoleResponse createRole(String code, List<String> permissions) {
    RoleRequest req = new RoleRequest();
    req.setCode(code);
    req.setName("探针角色 " + code);
    req.setDataScope(DataPermissionService.SCOPE_SELF);
    req.setPermissions(permissions);
    return roleService.create(req);
  }

  private DepartmentRequest departmentRequest(String name) {
    DepartmentRequest req = new DepartmentRequest();
    req.setName(name);
    return req;
  }

  private DataPermissionRequest dataPermissionRequest(Long departmentId, String dataScope) {
    DataPermissionRequest req = new DataPermissionRequest();
    req.setDepartmentId(departmentId);
    req.setDataScope(dataScope);
    return req;
  }

  /** 直插部门（绕过服务层，避免夹具自身触发缓存失效而污染用例的观察窗口）。 */
  private long insertDepartment(String name) {
    Department dept = new Department();
    dept.setName(name);
    dept.setSortOrder(0);
    departmentMapper.insert(dept);
    return dept.getId();
  }

  /**
   * 直插用户。
   *
   * <p>不写 {@code enabled}：H2 建表脚本的默认值是 TRUE，而 Mapper 直插不走服务层的默认值补全。
   */
  private long insertUser(String username, String role, String dataScope, Long departmentId) {
    User user = new User();
    user.setUsername(username);
    user.setPasswordHash("$2a$10$probe-not-a-real-hash");
    user.setDisplayName(username);
    user.setRole(role);
    user.setDataScope(dataScope);
    user.setDepartmentId(departmentId);
    user.setEnabled(true);
    user.setTokenVersion(0);
    userMapper.insert(user);
    return user.getId();
  }

  private long insertCustomer(String name) {
    Customer customer = new Customer();
    customer.setName(name);
    customer.setCompany("报价查询计数公司");
    customer.setStatus("ACTIVE");
    customerMapper.insert(customer);
    return customer.getId();
  }

  /**
   * 插入 {@code count} 个产品并返回其 id。
   *
   * <p>{@code tag} 是必需的：{@code product.code} 有唯一约束，若编码只按序号生成（{@code PROBE-0..n}），
   * 同一测试方法里先后插入两批就会主键冲突。带上批次标签后，各批次编码互不相同。
   */
  private List<Long> insertProducts(String tag, int count) {
    List<Long> ids = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      Product product = new Product();
      product.setCode("QUERY-PROBE-" + tag + "-" + i);
      product.setName("查询计数产品 " + tag + "-" + i);
      product.setStandardPrice(1000L * (i + 1));
      product.setStatus("ACTIVE");
      productMapper.insert(product);
      ids.add(product.getId());
    }
    return ids;
  }

  private String quoteBody(long customerId, List<Long> productIds) {
    StringBuilder items = new StringBuilder();
    for (int i = 0; i < productIds.size(); i++) {
      if (i > 0) {
        items.append(", ");
      }
      items
          .append("{\"productId\": ")
          .append(productIds.get(i))
          .append(", \"quantity\": 2, \"discount\": 1}");
    }
    return "{\"customerId\": " + customerId + ", \"items\": [" + items + "]}";
  }

  private UsernamePasswordAuthenticationToken humanAuth(long userId) {
    return new UsernamePasswordAuthenticationToken(
        new JwtAuthFilter.CrmPrincipal(userId, "probe-user", "ADMIN"), null, List.of());
  }

  private UsernamePasswordAuthenticationToken machineAuth(long userId) {
    return new UsernamePasswordAuthenticationToken(
        new JwtAuthFilter.CrmPrincipal(userId, "open-api", "OPEN_API", true), null, List.of());
  }
}
