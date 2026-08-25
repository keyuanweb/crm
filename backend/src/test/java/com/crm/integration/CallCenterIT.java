package com.crm.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** 通话记录集成测试（061 T011）：录入/列表/统计/归属校验。 */
class CallCenterIT extends AbstractIntegrationTest {

  private long createCustomerAndContact(String token) throws Exception {
    String custResp =
        mockMvc
            .perform(
                post("/api/v1/customers")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"name\": \"通话客户%d\", \"company\": \"通话公司\"}",
                            System.nanoTime())))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long customerId = objectMapper.readTree(custResp).path("data").path("id").asLong();
    String contactResp =
        mockMvc
            .perform(
                post("/api/v1/contacts")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"customerId\": %d, \"name\": \"通话联系人\"}",
                            customerId)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long contactId = objectMapper.readTree(contactResp).path("data").path("id").asLong();
    return customerId * 100000 + contactId; // 编码返回
  }

  @Test
  @DisplayName("通话记录流程：录入→列表→统计→归属校验")
  void callRecordFlow() throws Exception {
    String token = loginAndGetToken();
    long combo = createCustomerAndContact(token);
    long customerId = combo / 100000;
    long contactId = combo % 100000;

    // 录入呼出记录
    mockMvc
        .perform(
            post("/api/v1/call-records")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    String.format(
                        "{\"customerId\": %d, \"contactId\": %d, \"direction\": \"OUTBOUND\", "
                            + "\"durationSeconds\": 300, \"result\": \"CONNECTED\", \"remark\": \"确认续约\"}",
                        customerId, contactId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.direction").value("OUTBOUND"))
        .andExpect(jsonPath("$.data.durationSeconds").value(300));

    // 列表
    mockMvc
        .perform(
            get("/api/v1/call-records")
                .param("keyword", "通话")
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1));

    // 统计
    mockMvc
        .perform(get("/api/v1/call-records/stats").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalCount").value(1))
        .andExpect(jsonPath("$.data.totalDurationSeconds").value(300));

    // 归属校验：联系人属其他客户 → 422
    String otherCustResp =
        mockMvc
            .perform(
                post("/api/v1/customers")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"name\": \"其他客户%d\", \"company\": \"其他公司\"}",
                            System.nanoTime())))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long otherCustomerId = objectMapper.readTree(otherCustResp).path("data").path("id").asLong();
    mockMvc
        .perform(
            post("/api/v1/call-records")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    String.format(
                        "{\"customerId\": %d, \"contactId\": %d, \"direction\": \"INBOUND\", "
                            + "\"durationSeconds\": 60, \"result\": \"CONNECTED\"}",
                        otherCustomerId, contactId)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("CALL_CONTACT_MISMATCH"));
  }

  @Test
  @DisplayName("枚举校验：非法方向 → 422")
  void invalidEnumThrows() throws Exception {
    String token = loginAndGetToken();
    mockMvc
        .perform(
            post("/api/v1/call-records")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"direction\": \"SMS\", \"result\": \"CONNECTED\"}"))
        .andExpect(status().isBadRequest());
  }
}
