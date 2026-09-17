package com.crm.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * 邮件同步集成测试（062 T012；同步段 101 T2 重写）：账户 CRUD / 默认唯一 / **默认部署下拒绝收信同步**。
 *
 * <p>062 的同步段断言的是 200 + {@code syncStatus=SYNCED} + 一条记录——那正是本批要关掉的行为，故重写。账户 CRUD 与默认发件人唯一
 * 两段一字未改（101 不碰它们）。演示路另起 {@code MailInboundDemoIT}（它要改配置，与 本类的上下文不同）。
 *
 * <p>⚠️ 断言的是 {@code $.error.code} 而非 message：本仓有"无参 {@code getContentAsString()} 走 ISO-8859-1，
 * 中文文案断言以不匹配红掉、指向的却是唯一正确的那段代码"的先例。
 */
class EmailSyncIT extends AbstractIntegrationTest {

  @Test
  @DisplayName("邮件同步流程：配置账户→默认唯一→收信同步被拒且不留记录")
  void emailSyncFlow() throws Exception {
    String token = loginAndGetToken();
    String email = "sales" + (System.nanoTime() % 100000) + "@corp.com";

    // 创建账户 A（默认发件）
    String respA =
        mockMvc
            .perform(
                post("/api/v1/mail-accounts")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"email\": \"%s\", \"displayName\": \"销售部\", \"imapHost\": \"imap.corp.com\", "
                                + "\"imapPort\": 993, \"smtpHost\": \"smtp.corp.com\", \"smtpPort\": 465, "
                                + "\"enabled\": true, \"isDefaultSender\": true}",
                            email)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.isDefaultSender").value(true))
            .andReturn()
            .getResponse()
            .getContentAsString();
    long accountA = objectMapper.readTree(respA).path("data").path("id").asLong();

    // 创建账户 B（默认发件 → A 自动取消）
    String emailB = "support" + (System.nanoTime() % 100000) + "@corp.com";
    String respB =
        mockMvc
            .perform(
                post("/api/v1/mail-accounts")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"email\": \"%s\", \"displayName\": \"服务部\", \"isDefaultSender\": true}",
                            emailB)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long accountB = objectMapper.readTree(respB).path("data").path("id").asLong();

    // A 不再是默认
    mockMvc
        .perform(get("/api/v1/mail-accounts").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.data[?(@.id == " + accountA + ")].isDefaultSender")
                .value(org.hamcrest.Matchers.hasSize(1)));

    // 收信同步账户 B：本部署没有收信源 ⇒ 拒绝（062 在这里回 200 并插一条假的 SYNCED 记录）
    mockMvc
        .perform(
            post("/api/v1/mail-accounts/{id}/sync", accountB)
                .header("Authorization", bearer(token)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("MAIL_INBOUND_NOT_CONFIGURED"));

    // 再点一次：仍是 409，且**一次都没落库**（连点不累积）
    mockMvc
        .perform(
            post("/api/v1/mail-accounts/{id}/sync", accountB)
                .header("Authorization", bearer(token)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("MAIL_INBOUND_NOT_CONFIGURED"));

    // 同步记录列表：0 条。
    // ⚠️ 这条 `total=0` 是**正对照**，不是"顺带看一眼"——上面两次调用都抛了异常，光凭"抛了异常"无法排除
    // "先 insert 再抛"（方法上有 @Transactional，看着像会回滚，而那是推理不是行为）。只有这里能证明副作用没发生。
    mockMvc
        .perform(
            get("/api/v1/mail-accounts/{id}/records", accountB)
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(0));
  }

  @Test
  @DisplayName("邮箱格式非法 → 422")
  void invalidEmailThrows() throws Exception {
    String token = loginAndGetToken();
    mockMvc
        .perform(
            post("/api/v1/mail-accounts")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"not-an-email\", \"displayName\": \"坏\"}"))
        .andExpect(status().isBadRequest());
  }
}
