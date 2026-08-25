package com.crm.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** 邮件同步集成测试（062 T012）：账户 CRUD/默认唯一/模拟同步。 */
class EmailSyncIT extends AbstractIntegrationTest {

  @Test
  @DisplayName("邮件同步流程：配置账户→默认唯一→模拟同步→记录")
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

    // 模拟同步账户 B
    mockMvc
        .perform(
            post("/api/v1/mail-accounts/{id}/sync", accountB)
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.direction").value("INBOUND"))
        .andExpect(jsonPath("$.data.syncStatus").value("SYNCED"));

    // 同步记录列表
    mockMvc
        .perform(
            get("/api/v1/mail-accounts/{id}/records", accountB)
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1))
        .andExpect(jsonPath("$.data.items[0].subject").value("模拟同步邮件"));
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
