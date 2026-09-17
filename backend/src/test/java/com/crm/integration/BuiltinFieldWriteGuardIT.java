package com.crm.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.crm.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

/**
 * T10–T13：内置字段的**写侧**（102）。本批的旗舰缺陷在这里。
 *
 * <h2>治的是「省略即销毁」</h2>
 *
 * <p>056 的权限语义要求客户端**先提交**才会被拦（提交 HIDDEN ⇒ 422、改 READ_ONLY ⇒ 422），但看不见的字段客户端 自然会**省略**，而 {@code
 * CustomerService.apply} 是无条件逐字段覆盖、{@code OpportunityService} 更把金额 {@code null} 强转成 {@code 0L}。⇒
 * 权限「生效」的那一瞬间正好是数据被销毁的那一瞬间：用户只是改了个备注，客户的电话就被静默清空。T10/T11 钉的就是它。
 *
 * <h2>为什么断言经 ADMIN 读库、而不是读 PUT 的响应</h2>
 *
 * <p>调用方（非 ADMIN）看到的响应里，被掩码的字段本来就看不见——**用它当判据等于用被遮住的眼睛去核对有没有被拆掉**。 ADMIN 在收口点走快路径，读到的就是库里的真值。
 *
 * <h2>每条用例都带正对照</h2>
 *
 * <p>「没被销毁」很容易被"这次 PUT 压根没生效"假绿（比如 422 了、或者库没更新）。故每条都同时改一个**未受保护**的字段（{@code remark}）并断言它确实变了。
 */
class BuiltinFieldWriteGuardIT extends AbstractIntegrationTest {

  private static final String CUSTOMER_NAME = "写守卫客户";
  private static final String CUSTOMER_COMPANY = "写守卫客户 公司";
  private static final String PHONE = "13900001111";
  private static final String OPP_NAME = "写守卫商机";
  private static final long MIN = 5000L;
  private static final long MAX = 20000L;

  private BuiltinFieldPermissionFixture fixture;
  private String token;
  private long customerId;
  private long opportunityId;

  @BeforeEach
  void setUp() throws Exception {
    fixture = new BuiltinFieldPermissionFixture(mockMvc, objectMapper, loginAndGetToken());
    fixture.ensureRole();
    token = fixture.createUser("fls_guard_").token();
    customerId = fixture.createCustomer(token, CUSTOMER_NAME, PHONE, "guard@example.com");
    opportunityId = fixture.createOpportunity(token, customerId, OPP_NAME, MIN, MAX);
  }

  /**
   * ⚠️ 本条的绿**不来自** {@code BuiltinWriteGuard.restore}——定向破坏 D4（去掉 {@code restore}）实测：本用例**仍绿**， 只有
   * T11 转红。机制已核实：{@code CustomerService.update} 走 MyBatis-Plus 的 {@code updateById}，其默认策略 {@code
   * NOT_NULL} **跳过 null 字段**（全仓无 {@code update-strategy} 覆盖），故装配把 phone 置 null 后这一列压根没进 UPDATE 语句。⇒
   * 客户路径上 {@code restore} 今天**没有可观察后果**（防御性代码）；「省略即销毁」在客户路径上要靠 实体字段的 null 语义变成真缺陷，而不是靠删掉回补。**能抓住
   * {@code restore} 缺失的判据只有 T11**（商机金额的 {@code null → 0L} 强转让 null
   * 语义消失，绕过了跳过策略）。本条仍留在原处：它钉的是**对外承诺**（编辑后电话还在）， 那是用户真正在意的性质。
   */
  @Test
  @DisplayName("T10 旗舰 1：客户 phone 配 HIDDEN，PUT 省略该字段 ⇒ 库中仍是原值，且本次编辑确实生效")
  void omittedHiddenCustomerPhoneSurvivesUpdate() throws Exception {
    fixture.configureBuiltin("CUSTOMER", "phone", "HIDDEN");

    // 省略 phone（看不见就会省略），同时改 remark 作为「这次 PUT 真的生效了」的正对照
    BuiltinFieldPermissionFixture.Res res =
        fixture.call(
            token, HttpMethod.PUT, "/api/v1/customers/" + customerId, customerPut(null, "改过的备注"));

    assertThat(res.status()).as("省略受保护字段的正常编辑不该被拒：%s", res.body()).isEqualTo(200);
    JsonNode stored = adminCustomer();
    assertThat(stored.path("phone").asText())
        .as("省略 HIDDEN 字段 ⇒ 必须回补库中原值，而不是清空（008cbb9 那类缺陷）")
        .isEqualTo(PHONE);
    assertThat(stored.path("remark").asText()).as("正对照：同一次 PUT 里未受保护的字段必须真的改了").isEqualTo("改过的备注");
  }

  @Test
  @DisplayName("T11 旗舰 2：商机 expectedAmountMin 配 HIDDEN，PUT 省略 ⇒ 保持 5000 而非被 null→0L 写成 0")
  void omittedHiddenOpportunityAmountSurvivesUpdate() throws Exception {
    fixture.configureBuiltin("OPPORTUNITY", "expectedAmountMin", "HIDDEN");

    // 省 min、给 max：若把 max 也省掉，max 会取默认 0 而 min 也是 0 ⇒ 仍通过区间校验，但断言就变成
    // 「0 是否被回补成 5000」，模糊了「省 min」这个唯一的自变量。故显式给 max 并顺带断言它没受影响
    BuiltinFieldPermissionFixture.Res res =
        fixture.call(
            token,
            HttpMethod.PUT,
            "/api/v1/opportunities/" + opportunityId,
            opportunityPut(null, "改过的商机备注"));

    assertThat(res.status()).as("省略受保护字段的正常编辑不该被拒：%s", res.body()).isEqualTo(200);
    JsonNode stored = adminOpportunity();
    assertThat(stored.path("expectedAmountMin").asLong())
        .as("回补必须排在 apply 之后——null→0L 发生在装配里，前置回补会被它盖掉")
        .isEqualTo(MIN);
    assertThat(stored.path("expectedAmountMax").asLong()).as("正对照：未受保护的金额上限不受影响").isEqualTo(MAX);
    assertThat(stored.path("remark").asText()).isEqualTo("改过的商机备注");
  }

  @Test
  @DisplayName("T12 提交 HIDDEN 内置字段 ⇒ 422 FIELD_HIDDEN（**原样回传也拒**，不是「改了才拒」）")
  void submittingHiddenBuiltinFieldIsRejected() throws Exception {
    fixture.configureBuiltin("CUSTOMER", "phone", "HIDDEN");

    // 刻意提交**与库中相同**的 phone：只拦「有变化」的实现会放它过去，而 HIDDEN 的语义是无条件拒绝。
    // remark 改成一个与库中不同的值，用来证明这次 PUT 整笔都被拦下了（而不是"先改库再 422"）
    BuiltinFieldPermissionFixture.Res res =
        fixture.call(
            token,
            HttpMethod.PUT,
            "/api/v1/customers/" + customerId,
            customerPut(PHONE, "不该落库的备注"));

    assertThat(res.status()).as("提交 HIDDEN 字段应 422：%s", res.body()).isEqualTo(422);
    assertThat(res.body().path("error").path("code").asText()).isEqualTo("FIELD_HIDDEN");
    // 判定发生在装配之前（capture 在 apply 之前）⇒ 库中不该有任何变化
    assertThat(adminCustomer().path("remark").asText())
        .as("422 之后库中不应留下半截修改（建客户时写的是「备注」）")
        .isEqualTo("备注");
  }

  @Test
  @DisplayName("T12 附带：create 上提交 HIDDEN ⇒ 同样 422（create 无原值可回补，等价于「不可设置」）")
  void submittingHiddenBuiltinFieldOnCreateIsRejected() throws Exception {
    fixture.configureBuiltin("CUSTOMER", "phone", "HIDDEN");

    BuiltinFieldPermissionFixture.Res res =
        fixture.call(
            token,
            HttpMethod.POST,
            "/api/v1/customers",
            "{\"name\":\"新建客户\",\"company\":\"新建公司\",\"phone\":\""
                + PHONE
                + "\",\"remark\":\"备注\"}");

    assertThat(res.status()).as("create 上提交 HIDDEN 字段应 422：%s", res.body()).isEqualTo(422);
    assertThat(res.body().path("error").path("code").asText()).isEqualTo("FIELD_HIDDEN");
  }

  @Test
  @DisplayName("T13 改 READ_ONLY 内置字段 ⇒ 422 FIELD_READ_ONLY；**原样回传 ⇒ 成功**（防三态被压成二态）")
  void changingReadOnlyBuiltinFieldIsRejectedButEchoingItBackIsNot() throws Exception {
    fixture.configureBuiltin("CUSTOMER", "phone", "READ_ONLY");

    // 改动只读字段 ⇒ 拒
    BuiltinFieldPermissionFixture.Res changed =
        fixture.call(
            token,
            HttpMethod.PUT,
            "/api/v1/customers/" + customerId,
            customerPut("13800009999", "备注"));
    assertThat(changed.status()).as("改 READ_ONLY 字段应 422：%s", changed.body()).isEqualTo(422);
    assertThat(changed.body().path("error").path("code").asText()).isEqualTo("FIELD_READ_ONLY");
    assertThat(adminCustomer().path("phone").asText()).as("被拒的请求不得改动库").isEqualTo(PHONE);

    // 正对照：原样回传（前端就是这么干的：把只读字段原值带回）必须成功。
    // 少了这一条，「READ_ONLY 一律拒绝」这种把三态压成二态的写法也会全绿
    BuiltinFieldPermissionFixture.Res echoed =
        fixture.call(
            token,
            HttpMethod.PUT,
            "/api/v1/customers/" + customerId,
            customerPut(PHONE, "只读回传后的备注"));
    assertThat(echoed.status()).as("原样回传 READ_ONLY 字段应成功：%s", echoed.body()).isEqualTo(200);
    JsonNode stored = adminCustomer();
    assertThat(stored.path("phone").asText()).isEqualTo(PHONE);
    assertThat(stored.path("remark").asText()).isEqualTo("只读回传后的备注");
  }

  // ===== 载体构造：受保护字段由参数决定「出现在请求里」还是「被省略」，让省略与否在调用点一眼可读 =====

  private static String customerPut(String phoneOrNull, String remark) {
    return "{\"name\":\""
        + CUSTOMER_NAME
        + "\",\"company\":\""
        + CUSTOMER_COMPANY
        + "\","
        + (phoneOrNull == null ? "" : "\"phone\":\"" + phoneOrNull + "\",")
        + "\"remark\":\""
        + remark
        + "\",\"version\":0}";
  }

  private String opportunityPut(String minOrNull, String remark) {
    return "{\"customerId\":"
        + customerId
        + ",\"name\":\""
        + OPP_NAME
        + "\","
        + (minOrNull == null ? "" : "\"expectedAmountMin\":" + minOrNull + ",")
        + "\"expectedAmountMax\":"
        + MAX
        + ",\"remark\":\""
        + remark
        + "\",\"version\":0}";
  }

  /** 经 ADMIN 读客户详情（ADMIN 在掩码收口点走快路径 ⇒ 读到的是库中真值）。 */
  private JsonNode adminCustomer() throws Exception {
    return fixture
        .call(fixture.adminToken(), HttpMethod.GET, "/api/v1/customers/" + customerId, null)
        .body()
        .path("data");
  }

  /** 经 ADMIN 读商机详情。 */
  private JsonNode adminOpportunity() throws Exception {
    return fixture
        .call(fixture.adminToken(), HttpMethod.GET, "/api/v1/opportunities/" + opportunityId, null)
        .body()
        .path("data");
  }
}
