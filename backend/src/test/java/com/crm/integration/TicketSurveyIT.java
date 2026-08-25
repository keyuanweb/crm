package com.crm.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** 工单满意度集成测试（051 T011）：评分/重复 409/状态校验/统计。 */
class TicketSurveyIT extends AbstractIntegrationTest {

  private long createClosedTicket(String token) throws Exception {
    String custResp =
        mockMvc
            .perform(
                post("/api/v1/customers")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"name\": \"满意度客户%d\", \"company\": \"满意度公司\"}", System.nanoTime())))
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
                            "{\"customerId\": %d, \"title\": \"满意度工单\", \"priority\": \"MEDIUM\"}",
                            customerId)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long ticketId = objectMapper.readTree(ticketResp).path("data").path("id").asLong();

    // 流转到 CLOSED：OPEN→IN_PROGRESS→RESOLVED→CLOSED
    mockMvc
        .perform(
            post("/api/v1/tickets/{id}/transition", ticketId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetStatus\":\"IN_PROGRESS\"}"))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/tickets/{id}/transition", ticketId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetStatus\":\"RESOLVED\"}"))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/tickets/{id}/transition", ticketId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetStatus\":\"CLOSED\"}"))
        .andExpect(status().isOk());
    return ticketId;
  }

  @Test
  @DisplayName("满意度流程：关闭→评分→重复 409→记录可查→统计")
  void surveyFlow() throws Exception {
    String token = loginAndGetToken();
    long ticketId = createClosedTicket(token);

    // 评分
    mockMvc
        .perform(
            post("/api/v1/tickets/{id}/survey", ticketId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\": 5, \"comment\": \"处理及时\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.rating").value(5));

    // 重复 → 409
    mockMvc
        .perform(
            post("/api/v1/tickets/{id}/survey", ticketId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\": 3}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("SURVEY_ALREADY_SUBMITTED"));

    // 记录可查
    mockMvc
        .perform(
            get("/api/v1/tickets/{id}/survey", ticketId).header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.comment").value("处理及时"));

    // 统计
    mockMvc
        .perform(get("/api/v1/surveys/stats").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.sampleCount").value(1))
        .andExpect(jsonPath("$.data.npsScore").value(100)); // 5分→推荐者100%
  }

  @Test
  @DisplayName("未关闭工单评分 → 422")
  void openTicketRejected() throws Exception {
    String token = loginAndGetToken();
    String custResp =
        mockMvc
            .perform(
                post("/api/v1/customers")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"name\": \"满意度客户B%d\", \"company\": \"满意度公司B\"}",
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
                            "{\"customerId\": %d, \"title\": \"未关闭工单\", \"priority\": \"LOW\"}",
                            customerId)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long ticketId = objectMapper.readTree(ticketResp).path("data").path("id").asLong();

    mockMvc
        .perform(
            post("/api/v1/tickets/{id}/survey", ticketId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\": 5}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("SURVEY_STATE_INVALID"));
  }
}
