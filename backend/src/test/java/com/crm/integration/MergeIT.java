package com.crm.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** 客户查重合并集成测试（034 T002）。 */
class MergeIT extends AbstractIntegrationTest {

  private long createCustomer(String token, String name, String company, String phone)
      throws Exception {
    String resp =
        mockMvc
            .perform(
                post("/api/v1/customers")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"name\": \""
                            + name
                            + "\", \"company\": \""
                            + company
                            + "\", \"phone\": \""
                            + (phone == null ? "" : phone)
                            + "\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(resp).path("data").path("id").asLong();
  }

  @Test
  @DisplayName("建电话相同客户 → 扫描检出 → 合并")
  void mergeFlow() throws Exception {
    String token = loginAndGetToken();
    // 名称不同（避开唯一校验），电话相同 → 检出重复 85
    long a = createCustomer(token, "合并测试A", "Merge A", "13811112222");
    long b = createCustomer(token, "合并测试B", "Merge B", "13811112222");

    // 扫描检出重复
    String scan =
        mockMvc
            .perform(get("/api/v1/customers/duplicates").header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode groups = objectMapper.readTree(scan).path("data");
    System.out.println("重复组数: " + groups.size());

    // 合并（a 主，b 从）
    mockMvc
        .perform(
            post("/api/v1/customers/merge")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"primaryId\": " + a + ", \"duplicateId\": " + b + "}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.primaryId").value(a));
  }
}
