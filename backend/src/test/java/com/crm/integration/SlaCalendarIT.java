package com.crm.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** SLA 日历集成测试（054 T010）：配置/工单 SLA 计算。 */
class SlaCalendarIT extends AbstractIntegrationTest {

  @Test
  @DisplayName("配置 SLA 日历：保存生效")
  void configureCalendar() throws Exception {
    String token = loginAndGetToken();
    mockMvc
        .perform(
            put("/api/v1/sla-calendar")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"workSlots\":[{\"start\":\"09:00\",\"end\":\"18:00\"}],"
                        + "\"workDays\":[1,2,3,4,5],\"holidays\":[],\"enabled\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.enabled").value(true))
        .andExpect(jsonPath("$.data.workDays[0]").value(1));

    mockMvc
        .perform(get("/api/v1/sla-calendar").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.enabled").value(true));
  }

  @Test
  @DisplayName("非法配置：工作时间段不合法 → 422")
  void invalidSlotRejected() throws Exception {
    String token = loginAndGetToken();
    mockMvc
        .perform(
            put("/api/v1/sla-calendar")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"workSlots\":[{\"start\":\"18:00\",\"end\":\"09:00\"}],"
                        + "\"workDays\":[1,2,3,4,5],\"holidays\":[],\"enabled\":true}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("SLA_CALENDAR_SLOT_INVALID"));
  }

  @Test
  @DisplayName("非 ADMIN 不可配置 → 403")
  void nonAdminForbidden() throws Exception {
    String token = loginAndGetToken();
    // 创建 SUPPORT 用户
    mockMvc
        .perform(
            post("/api/v1/users")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"username\": \"slasupport\", \"password\": \"Passw0rd!\", \"displayName\": \"SLA客服\", \"role\": \"SUPPORT\"}"))
        .andExpect(status().isCreated());
    String supportToken = loginAndGetToken("slasupport", "Passw0rd!");

    mockMvc
        .perform(
            put("/api/v1/sla-calendar")
                .header("Authorization", bearer(supportToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"workSlots\":[{\"start\":\"09:00\",\"end\":\"18:00\"}],\"enabled\":true}"))
        .andExpect(status().isForbidden());
  }
}
