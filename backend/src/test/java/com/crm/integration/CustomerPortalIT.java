package com.crm.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** 客户门户集成测试（050 T011）：公开访问/提单/进度/不可见。 */
class CustomerPortalIT extends AbstractIntegrationTest {

  private long createCustomerAndContact(String token, String phone) throws Exception {
    String custResp =
        mockMvc
            .perform(
                post("/api/v1/customers")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"name\": \"门户客户%d\", \"company\": \"门户公司\"}",
                            System.nanoTime())))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long customerId = objectMapper.readTree(custResp).path("data").path("id").asLong();
    mockMvc
        .perform(
            post("/api/v1/contacts")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    String.format(
                        "{\"customerId\": %d, \"name\": \"门户联系人\", \"phone\": \"%s\", \"email\": \"%s@test.com\"}",
                        customerId, phone, phone)))
        .andExpect(status().isCreated());
    return customerId;
  }

  @Test
  @DisplayName("门户提单：公开访问（无需 token）→ 工单号 → 查进度")
  void portalTicketFlow() throws Exception {
    String token = loginAndGetToken();
    String phone = "139" + (System.nanoTime() % 100000000);
    createCustomerAndContact(token, phone);

    // 公开提单（不带 Authorization）
    String resp =
        mockMvc
            .perform(
                post("/api/v1/public/portal/tickets")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"phone\": \"%s\", \"title\": \"门户提单\", \"description\": \"无法导出\", \"priority\": \"MEDIUM\"}",
                            phone)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.status").value("OPEN"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    long ticketId = objectMapper.readTree(resp).path("data").path("ticketId").asLong();

    // 公开查进度（双验证）
    mockMvc
        .perform(
            post("/api/v1/public/portal/tickets/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    String.format(
                        "{\"ticketId\": %d, \"phone\": \"%s\"}", ticketId, phone)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("OPEN"));
  }

  @Test
  @DisplayName("门户提单：未匹配客户 → 422")
  void portalTicketCustomerNotFound() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/public/portal/tickets")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\": \"18800000000\", \"title\": \"未知客户提单\"}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("PORTAL_CUSTOMER_NOT_FOUND"));
  }

  @Test
  @DisplayName("门户进度：客户不匹配 → 404；手机邮箱都空 → 422")
  void portalTicketStatusGuard() throws Exception {
    String token = loginAndGetToken();
    String phone = "137" + (System.nanoTime() % 100000000);
    createCustomerAndContact(token, phone);
    String resp =
        mockMvc
            .perform(
                post("/api/v1/public/portal/tickets")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"phone\": \"%s\", \"title\": \"守卫测试\"}", phone)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long ticketId = objectMapper.readTree(resp).path("data").path("ticketId").asLong();

    // 错误手机 → 404
    mockMvc
        .perform(
            post("/api/v1/public/portal/tickets/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"ticketId\": %d, \"phone\": \"18000000000\"}", ticketId)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("PORTAL_TICKET_NOT_FOUND"));

    // 联系方式都空 → 422
    mockMvc
        .perform(
            post("/api/v1/public/portal/tickets/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"ticketId\": %d}", ticketId)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("PORTAL_CONTACT_REQUIRED"));
  }

  @Test
  @DisplayName("门户文章：仅已发布可见；草稿 400")
  void portalArticleVisibility() throws Exception {
    String token = loginAndGetToken();
    // 创建草稿文章
    String draftResp =
        mockMvc
            .perform(
                post("/api/v1/knowledge")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\": \"门户草稿\", \"content\": \"草稿内容\", \"category\": \"OTHER\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long draftId = objectMapper.readTree(draftResp).path("data").path("id").asLong();

    // 草稿门户不可见
    mockMvc
        .perform(get("/api/v1/public/portal/articles/{id}", draftId))
        .andExpect(status().isBadRequest());
  }
}
