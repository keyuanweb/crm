package com.crm.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** 回收站集成测试（025 T003）：删除→回收站→恢复。 */
class RecycleBinIT extends AbstractIntegrationTest {

  private long createCustomer(String token, String name) throws Exception {
    String resp =
        mockMvc
            .perform(
                post("/api/v1/customers")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\": \"" + name + "\", \"company\": \"回收站测试公司\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(resp).path("data").path("id").asLong();
  }

  @Test
  @DisplayName("删除客户→回收站可见→恢复→列表重现")
  void recycleFlow() throws Exception {
    String token = loginAndGetToken();
    long customerId = createCustomer(token, "回收站客户");

    // 删除（逻辑删除）
    mockMvc
        .perform(delete("/api/v1/customers/" + customerId).header("Authorization", bearer(token)))
        .andExpect(status().isOk());

    // 回收站可见（用 jsonPath 断言中文，与 CustomerIT 同方式）
    mockMvc
        .perform(
            get("/api/v1/recycle-bin")
                .header("Authorization", bearer(token))
                .param("type", "CUSTOMER"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].type").value("CUSTOMER"))
        .andExpect(jsonPath("$.data.items[0].id").value(customerId))
        .andExpect(jsonPath("$.data.items[0].name").value("回收站客户"));

    // 恢复
    mockMvc
        .perform(
            post("/api/v1/recycle-bin/restore")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"type\":\"CUSTOMER\",\"id\":" + customerId + "}]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.restoredCount").value(1));

    // 客户列表重现
    mockMvc
        .perform(get("/api/v1/customers/" + customerId).header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.name").value("回收站客户"));
  }

  @Test
  @DisplayName("彻底删除后回收站与列表均不可见")
  void purgeRemoves() throws Exception {
    String token = loginAndGetToken();
    long customerId = createCustomer(token, "彻底删除客户");

    mockMvc
        .perform(delete("/api/v1/customers/" + customerId).header("Authorization", bearer(token)))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/recycle-bin/purge")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"type\":\"CUSTOMER\",\"id\":" + customerId + "}]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.purgedCount").value(1));

    mockMvc
        .perform(get("/api/v1/customers/" + customerId).header("Authorization", bearer(token)))
        .andExpect(status().isNotFound());
  }
}
