package com.crm;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.dto.auth.AuthResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 集成测试基类：H2 + MockMvc + 模拟 Redis（research.md R9）。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

  /** 与 `application-test.yml` 的 `spring.sql.init.schema-locations` 指向同一份脚本。 */
  private static final String TEST_SCHEMA_SCRIPT = "schema-h2.sql";

  @Autowired protected MockMvc mockMvc;

  @Autowired protected ObjectMapper objectMapper;

  @Autowired protected CacheManager cacheManager;

  @Autowired protected DataSource dataSource;

  /** 全部启动期播种 Bean。实测有 {@code DataInitializer}（写入 admin 账号）与 {@code SecurityDefaultsGuard}（纯检查）。 */
  @Autowired private List<ApplicationRunner> applicationRunners;

  @MockBean protected RedisTemplate<String, Object> redisTemplate;

  @BeforeEach
  void stubRedis() {
    when(redisTemplate.opsForValue()).thenReturn(mock(ValueOperations.class));
    clearInProcessCaches();
  }

  /**
   * 每个测试方法前重建测试库（083-engineering-consolidation）。
   *
   * <p><b>为什么必须逐方法重置</b>：测试库是<b>共享</b>的具名内存库（{@code
   * jdbc:h2:mem:crm;DB_CLOSE_DELAY=-1}），跨测试类存活。建库脚本只在上下文首次创建时执行一次，此后所有测试类共用同一份数据——
   * 于是"本类新建的那条记录"之外还躺着前面几十个类留下的数据。实测症状是 {@code $.data.total expected:<1> but was:<27>}、{@code
   * Status expected:<201> but was:<409>}（唯一键已被前一个类占用）等。
   *
   * <p><b>为什么是逐方法而非逐类</b>：实测全部 66 个 IT 类均无
   * {@code @TestMethodOrder}／{@code @Order}，也无静态可变状态，即设计上各方法相互独立。**该计数与结论会随新增 IT
   * 类失效**——新增类一旦声明方法序或带来静态可变状态，本重置策略须重估；逐方法重置因此是安全的，且比逐类更省——无需重建 {@code
   * ApplicationContext}，只重跑一次脚本。
   *
   * <p><b>只重跑脚本是不够的——必须重放启动期播种</b>：admin 账号<b>不在建库脚本里</b>，而由 {@code DataInitializer} 这个 {@code
   * ApplicationRunner} 在上下文启动时写入。只重跑脚本会删掉 admin 且永不恢复，实测表现为 <b>178 个用例全部以 {@code Status
   * expected:<200> but was:<401} 失败</b>——症状与"没有清理"同样具有误导性，只是方向相反。
   *
   * <p>重放的对象是<b>全部</b> {@code ApplicationRunner}（而非直接依赖 {@code
   * DataInitializer}）：这样以后新增的启动期播种会被自动涵盖，本重置不会悄悄落后于上下文真正的启动序列。
   *
   * <p>复用的是 {@code schema-h2.sql} 本身，未另建清理脚本：该脚本自带完整 DROP 块（每张表都有对应 DROP），本就为"可重复执行"而设计。其幂等性由
   * {@code SchemaIdempotencyIT} 单独守卫——若哪天 DROP 块再被漏维护，那个守卫会直接失败，而不是让这里以 22 个用例级联失败的形式暴露。
   */
  @BeforeEach
  void resetDatabase() throws Exception {
    try (Connection connection = dataSource.getConnection()) {
      ScriptUtils.executeSqlScript(connection, new ClassPathResource(TEST_SCHEMA_SCRIPT));
    }
    for (ApplicationRunner runner : applicationRunners) {
      runner.run(null);
    }
  }

  /**
   * 清空全部进程内缓存（083-engineering-consolidation）。
   *
   * <p>Spring TestContext 会跨测试类复用同一 ApplicationContext，缓存 Bean 因此在测试类之间存活。不清理会使用例结果
   * 依赖执行顺序——前一个类写入的缓存使后一个类看到陈旧数据，产生极难归因的偶发失败（research.md §5）。
   */
  private void clearInProcessCaches() {
    for (String name : cacheManager.getCacheNames()) {
      Cache cache = cacheManager.getCache(name);
      if (cache != null) {
        cache.clear();
      }
    }
  }

  /** 使用默认种子账号登录并返回 access token。 */
  protected String loginAndGetToken() throws Exception {
    return loginAndGetToken("admin", "admin123");
  }

  protected String loginAndGetToken(String username, String password) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new LoginBody(username, password))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
            .andReturn();
    String json = result.getResponse().getContentAsString();
    return objectMapper.readValue(json, ApiEnvelopeAuth.class).data().getAccessToken();
  }

  protected String bearer(String token) {
    return "Bearer " + token;
  }

  private record LoginBody(String username, String password) {}

  private record ApiEnvelopeAuth(boolean success, AuthResponse data, Object error) {}
}
