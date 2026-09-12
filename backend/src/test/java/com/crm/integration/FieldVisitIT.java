package com.crm.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** 外勤拜访集成测试（035 T005）：建计划 → 签到 → 统计。 */
class FieldVisitIT extends AbstractIntegrationTest {

  private long createCustomer(String token) throws Exception {
    String resp =
        mockMvc
            .perform(
                post("/api/v1/customers")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"name\": \"拜访IT客户\", \"company\": \"Visit IT\", \"phone\": \"13800009999\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(resp).path("data").path("id").asLong();
  }

  @Test
  @DisplayName("建拜访计划 → 签到（小结转跟进）→ 统计")
  void visitLifecycle() throws Exception {
    String token = loginAndGetToken();
    long customerId = createCustomer(token);

    // 建计划。visitTime 取"今天"，不写死日期：下方 /stats 默认统计**当月**（FieldVisitService.stats
    // 用 YearMonth.now()），原写死的 2026-08-25 一跨月就被排除在统计之外，表现为 totalDone=0。
    String visitTime = java.time.LocalDate.now() + "T10:00:00";
    String visitResp =
        mockMvc
            .perform(
                post("/api/v1/field-visits")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"customerId\": "
                            + customerId
                            + ", \"theme\": \"拜访IT\", \"visitTime\": \""
                            + visitTime
                            + "\", \"durationMinutes\": 60}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long visitId = objectMapper.readTree(visitResp).path("data").path("id").asLong();

    // 签到（小结）
    mockMvc
        .perform(
            post("/api/v1/field-visits/" + visitId + "/check-in")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"latitude\": 31.2304, \"longitude\": 121.4737, \"locationText\": \"上海市浦东新区\", \"summary\": \"客户确认意向\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("DONE"));

    // 重复签到拒绝
    mockMvc
        .perform(
            post("/api/v1/field-visits/" + visitId + "/check-in")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"summary\": \"重复\"}"))
        .andExpect(status().is4xxClientError());

    // 列表
    mockMvc
        .perform(get("/api/v1/field-visits").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].status").value("DONE"));

    // 统计
    mockMvc
        .perform(get("/api/v1/field-visits/stats").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalDone").value(1));
  }
}
