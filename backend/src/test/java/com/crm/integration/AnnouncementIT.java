package com.crm.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** 公告与评论集成测试（037 T006）。 */
class AnnouncementIT extends AbstractIntegrationTest {

  private long createAnnouncement(String token) throws Exception {
    String resp =
        mockMvc
            .perform(
                post("/api/v1/announcements")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"季度目标\",\"content\":\"<p>冲刺</p>\",\"pinned\":true}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(resp).path("data").path("id").asLong();
  }

  @Test
  @DisplayName("发公告 → 列表 → 已读 → 未读计数")
  void announcementFlow() throws Exception {
    String token = loginAndGetToken();
    long id = createAnnouncement(token);

    // 列表（未读）
    mockMvc
        .perform(get("/api/v1/announcements").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].read").value(false));

    // 未读计数
    mockMvc
        .perform(get("/api/v1/announcements/unread-count").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").value(1));

    // 标记已读
    mockMvc
        .perform(
            post("/api/v1/announcements/" + id + "/read").header("Authorization", bearer(token)))
        .andExpect(status().isOk());

    // 未读归零
    mockMvc
        .perform(get("/api/v1/announcements/unread-count").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").value(0));
  }

  @Test
  @DisplayName("评论：发表 + 列表 + @提及")
  void commentFlow() throws Exception {
    String token = loginAndGetToken();

    mockMvc
        .perform(
            post("/api/v1/comments")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"entityType\":\"CUSTOMER\",\"entityId\":1,\"content\":\"@admin 请跟进\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content").value("@admin 请跟进"));

    mockMvc
        .perform(
            get("/api/v1/comments")
                .param("entityType", "CUSTOMER")
                .param("entityId", "1")
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1));
  }
}
