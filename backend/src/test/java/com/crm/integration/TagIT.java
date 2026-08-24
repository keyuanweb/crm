package com.crm.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** 标签与细分集成测试（031 T006）。 */
class TagIT extends AbstractIntegrationTest {

  private long createCustomer(String token, String name) throws Exception {
    String resp =
        mockMvc
            .perform(
                post("/api/v1/customers")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\": \"" + name + "\", \"company\": \"标签测试公司\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(resp).path("data").path("id").asLong();
  }

  private long createTag(String token, String name) throws Exception {
    String resp =
        mockMvc
            .perform(
                post("/api/v1/tags")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"name\": \""
                            + name
                            + "\", \"color\": \"red\", \"entityType\": \"CUSTOMER\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(resp).path("data").path("id").asLong();
  }

  @Test
  @DisplayName("建标签→打标→客户标签列表")
  void tagCustomerFlow() throws Exception {
    String token = loginAndGetToken();
    long customerId = createCustomer(token, "标签客户");
    long tagId = createTag(token, "VIP");

    // 打标
    mockMvc
        .perform(
            put("/api/v1/tags/customers/" + customerId + "/tags")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tagIds\":[" + tagId + "]}"))
        .andExpect(status().isOk());

    // 客户标签列表
    mockMvc
        .perform(
            get("/api/v1/tags/customers/" + customerId + "/tags")
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].name").value("VIP"));
  }

  @Test
  @DisplayName("建细分→成员数→成员列表")
  void segmentFlow() throws Exception {
    String token = loginAndGetToken();
    long customerId = createCustomer(token, "细分客户");
    long tagId = createTag(token, "重点");

    mockMvc
        .perform(
            put("/api/v1/tags/customers/" + customerId + "/tags")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tagIds\":[" + tagId + "]}"))
        .andExpect(status().isOk());

    String resp =
        mockMvc
            .perform(
                post("/api/v1/segments")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"name\":\"重点客户\",\"conditions\":\"{\\\"logic\\\":\\\"AND\\\",\\\"filters\\\":[{\\\"field\\\":\\\"tag\\\",\\\"op\\\":\\\"IN\\\",\\\"values\\\":[\\\"重点\\\"]}]}\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode seg = objectMapper.readTree(resp).path("data");
    long segId = seg.path("id").asLong();
    org.assertj.core.api.Assertions.assertThat(seg.path("memberCount").asLong())
        .isGreaterThanOrEqualTo(1);

    mockMvc
        .perform(
            get("/api/v1/segments/" + segId + "/members").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").isNumber());
  }
}
