package com.crm.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

/**
 * 收信演示路径集成测试（101 T3/T4）：显式打开演示开关时的对外行为。
 *
 * <p>与 {@code EmailSyncIT} 的分工：那一类跑**默认**档（配置缺省 ⇒ 409 且零插入），本类跑**演示**档。两档的判据互不替代——默认档全绿
 * 而演示档坏掉（例如门被写死成永远拒绝）时，应用会从"诚实"退回"功能消失"，那是另一种缺陷。
 *
 * <p>⚠️ 只断言配置开关打开时的行为，<b>不</b>断言"真的收到了邮件"——演示记录不是收信，这正是本批要让人无法混淆的那点： 状态是 {@code SIMULATED}、主题与外部 id
 * 都带演示标记，界面另有一枚橙色「模拟」标签。
 *
 * <p>用 {@code @TestPropertySource} 而不是改 {@code application-test.yml}：后者会让**全仓每一个** {@code *IT}
 * 都跑在演示档下， 默认档的护栏（{@code EmailSyncIT} 那条 409）会被当场拆掉。
 */
@TestPropertySource(properties = "crm.mail.inbound.demo-enabled=true")
class MailInboundDemoIT extends AbstractIntegrationTest {

  @Test
  @DisplayName("演示档：同步返回 SIMULATED 且带演示标记，落库恰好 1 条")
  void demoModeWritesOneSimulatedRecord() throws Exception {
    String token = loginAndGetToken();
    String email = "demo" + (System.nanoTime() % 100000) + "@corp.com";

    String accountJson =
        mockMvc
            .perform(
                post("/api/v1/mail-accounts")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"email\": \"%s\", \"displayName\": \"演示\", \"smtpHost\":"
                                + " \"smtp.corp.com\", \"smtpPort\": 465, \"enabled\": true}",
                            email)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long accountId = objectMapper.readTree(accountJson).path("data").path("id").asLong();

    // T3：门开了 ⇒ 200 + SIMULATED（不是 SYNCED）
    mockMvc
        .perform(
            post("/api/v1/mail-accounts/{id}/sync", accountId)
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.direction").value("INBOUND"))
        .andExpect(jsonPath("$.data.syncStatus").value("SIMULATED"));

    // T4：状态之外的**数据层**标记——主题与外部 id。只改状态常量而文案照旧，等于换个名字继续冒充。
    mockMvc
        .perform(
            get("/api/v1/mail-accounts/{id}/records", accountId)
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1))
        .andExpect(jsonPath("$.data.items[0].syncStatus").value("SIMULATED"))
        .andExpect(
            jsonPath("$.data.items[0].subject").value(org.hamcrest.Matchers.containsString("演示")))
        .andExpect(
            jsonPath("$.data.items[0].subject")
                .value(org.hamcrest.Matchers.containsString("非真实收信")))
        .andExpect(
            jsonPath("$.data.items[0].externalId")
                .value(org.hamcrest.Matchers.startsWith("demo-")));
  }
}
