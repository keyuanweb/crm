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

/** 集成中心集成测试（058 T014）：通道 CRUD/事件推送/非法 URL。 */
class IntegrationHubIT extends AbstractIntegrationTest {

  @Test
  @DisplayName("集成中心流程：配置通道→触发事件→推送记录")
  void integrationFlow() throws Exception {
    String token = loginAndGetToken();

    // 配置通道（URL 不可达 → 推送记录 FAILED，验证记录链路）
    String resp =
        mockMvc
            .perform(
                post("/api/v1/integration-channels")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"channelType\": \"CUSTOM\", \"name\": \"测试通道\", \"webhookUrl\": \"http://localhost:9999/hook\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long channelId = objectMapper.readTree(resp).path("data").path("id").asLong();

    // 触发工单分配事件（创建客户+工单+分配）
    String custResp =
        mockMvc
            .perform(
                post("/api/v1/customers")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"name\": \"集成客户%d\", \"company\": \"集成公司\"}",
                            System.nanoTime())))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long customerId = objectMapper.readTree(custResp).path("data").path("id").asLong();
    String ticketResp =
        mockMvc
            .perform(
                post("/api/v1/tickets")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"customerId\": %d, \"title\": \"集成工单\", \"priority\": \"MEDIUM\"}",
                            customerId)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long ticketId = objectMapper.readTree(ticketResp).path("data").path("id").asLong();
    mockMvc
        .perform(
            post("/api/v1/tickets/{id}/assign", ticketId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"assigneeId\": 1}"))
        .andExpect(status().isOk());

    // 推送记录（异步，稍等后查询）
    Thread.sleep(1500);
    mockMvc
        .perform(
            get("/api/v1/integration-channels/{id}/deliveries", channelId)
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1))
        .andExpect(jsonPath("$.data.items[0].eventType").value("TICKET_ASSIGNED"));
  }

  @Test
  @DisplayName("非法 URL → 422；停用通道列表")
  void invalidUrlAndToggle() throws Exception {
    String token = loginAndGetToken();

    mockMvc
        .perform(
            post("/api/v1/integration-channels")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"channelType\": \"DINGTALK\", \"name\": \"钉钉群\", \"webhookUrl\": \"ftp://bad\"}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("INTEGRATION_URL_INVALID"));

    String resp =
        mockMvc
            .perform(
                post("/api/v1/integration-channels")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"channelType\": \"WECHAT_WORK\", \"name\": \"企微群\", \"webhookUrl\": \"https://qyapi.weixin.qq.com/x\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long channelId = objectMapper.readTree(resp).path("data").path("id").asLong();

    // 启停
    mockMvc
        .perform(
            post("/api/v1/integration-channels/{id}/toggle", channelId)
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.enabled").value(false));
  }
}
