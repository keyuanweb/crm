package com.crm.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** 联系人集成测试（T011）：CRUD/搜索筛选/唯一性 409/客户不存在 404/逻辑删除。 */
class ContactIT extends AbstractIntegrationTest {

  private long createCustomer(String token, String name) throws Exception {
    String resp =
        mockMvc
            .perform(
                post("/api/v1/customers")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\": \"" + name + "\", \"company\": \"联系人测试公司\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(resp).path("data").path("id").asLong();
  }

  private long createContact(String token, long customerId, String name, String phone)
      throws Exception {
    String body =
        String.format(
            "{\"customerId\": %d, \"name\": \"%s\", \"phone\": \"%s\", \"role\": \"DECISION_MAKER\"}",
            customerId, name, phone);
    String resp =
        mockMvc
            .perform(
                post("/api/v1/contacts")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.id").isNumber())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(resp).path("data").path("id").asLong();
  }

  @Test
  @DisplayName("联系人生命周期：创建→搜索筛选→详情→编辑→逻辑删除")
  void contactLifecycle() throws Exception {
    String token = loginAndGetToken();
    long customerId = createCustomer(token, "生命周期客户");

    // 1. 创建两个联系人（姓名全局唯一，避免同 JVM 内跨测试累计干扰断言）
    long c1 = createContact(token, customerId, "刘一", "13800138000");
    long c2 = createContact(token, customerId, "陈二", "13900139000");

    // 2. 关键字搜索
    mockMvc
        .perform(
            get("/api/v1/contacts").header("Authorization", bearer(token)).param("keyword", "刘一"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1))
        .andExpect(jsonPath("$.data.items[0].name").value("刘一"))
        .andExpect(jsonPath("$.data.items[0].customerName").value("生命周期客户"));

    // 3. 按客户筛选
    mockMvc
        .perform(
            get("/api/v1/contacts")
                .header("Authorization", bearer(token))
                .param("customerId", String.valueOf(customerId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(2));

    // 4. 详情
    mockMvc
        .perform(get("/api/v1/contacts/{id}", c1).header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.name").value("刘一"))
        .andExpect(jsonPath("$.data.role").value("DECISION_MAKER"));

    // 5. 编辑
    mockMvc
        .perform(
            put("/api/v1/contacts/{id}", c1)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    String.format(
                        "{\"customerId\": %d, \"name\": \"刘一丰\", \"phone\": \"13800138000\", \"role\": \"CHAMPION\", \"version\": 0}",
                        customerId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.name").value("刘一丰"))
        .andExpect(jsonPath("$.data.role").value("CHAMPION"));

    // 6. 逻辑删除后列表不可见
    mockMvc
        .perform(delete("/api/v1/contacts/{id}", c2).header("Authorization", bearer(token)))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            get("/api/v1/contacts").header("Authorization", bearer(token)).param("keyword", "陈二"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(0));
  }

  @Test
  @DisplayName("唯一性：同客户同名同电话创建返回 409 CONTACT_DUPLICATE")
  void duplicateThrows409() throws Exception {
    String token = loginAndGetToken();
    long customerId = createCustomer(token, "唯一性客户");
    createContact(token, customerId, "赵六", "13700137000");

    String body =
        String.format(
            "{\"customerId\": %d, \"name\": \"赵六\", \"phone\": \"13700137000\"}", customerId);
    mockMvc
        .perform(
            post("/api/v1/contacts")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("CONTACT_DUPLICATE"));
  }

  @Test
  @DisplayName("客户不存在：创建联系人返回 404 CUSTOMER_NOT_FOUND")
  void customerMissingThrows404() throws Exception {
    String token = loginAndGetToken();
    String body = "{\"customerId\": 999999, \"name\": \"孙七\", \"phone\": \"13600136000\"}";

    mockMvc
        .perform(
            post("/api/v1/contacts")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("CUSTOMER_NOT_FOUND"));
  }

  @Test
  @DisplayName("联系人不存在的详情返回 404 CONTACT_NOT_FOUND")
  void contactMissingThrows404() throws Exception {
    String token = loginAndGetToken();

    mockMvc
        .perform(get("/api/v1/contacts/999999").header("Authorization", bearer(token)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("CONTACT_NOT_FOUND"));
  }

  @Test
  @DisplayName("客户详情聚合返回该客户的联系人列表")
  void customerDetailIncludesContacts() throws Exception {
    String token = loginAndGetToken();
    long customerId = createCustomer(token, "聚合客户");
    createContact(token, customerId, "周八", "13500135000");
    createContact(token, customerId, "吴九", "13400134000");

    String resp =
        mockMvc
            .perform(
                get("/api/v1/customers/{id}", customerId).header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.contacts").isArray())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    JsonNode contacts = objectMapper.readTree(resp).path("data").path("contacts");
    assertThat(contacts.size()).isEqualTo(2);
    assertThat(contacts.get(0).path("name").asText()).isIn("周八", "吴九");
  }

  @Test
  @DisplayName("参数校验：姓名为空返回 400")
  void blankNameThrows400() throws Exception {
    String token = loginAndGetToken();
    long customerId = createCustomer(token, "校验客户");
    String body = String.format("{\"customerId\": %d, \"name\": \" \"}", customerId);

    mockMvc
        .perform(
            post("/api/v1/contacts")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
  }
}
