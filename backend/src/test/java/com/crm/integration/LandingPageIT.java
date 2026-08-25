package com.crm.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** 托管落地页集成测试（053 T012）：配置/公开渲染/UTM 提交/统计。 */
class LandingPageIT extends AbstractIntegrationTest {

  private long createEnabledForm(String token) throws Exception {
    String resp =
        mockMvc
            .perform(
                post("/api/v1/forms")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"name\": \"线索表单\", \"fields\": [{\"field\":\"phone\",\"label\":\"手机号\",\"required\":true}], "
                            + "\"successMessage\": \"提交成功\", \"source\": \"WEBSITE\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(resp).path("data").path("id").asLong();
  }

  @Test
  @DisplayName("落地页流程：配置→公开渲染→UTM 提交→统计")
  void landingPageFlow() throws Exception {
    String token = loginAndGetToken();
    long formId = createEnabledForm(token);

    // 配置落地页
    String lpResp =
        mockMvc
            .perform(
                post("/api/v1/landing-pages")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"title\": \"夏季促销\", \"subtitle\": \"限时优惠\", \"formId\": %d, \"enabled\": true}",
                            formId)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long lpId = objectMapper.readTree(lpResp).path("data").path("id").asLong();

    // 公开渲染（无需 token）
    mockMvc
        .perform(get("/api/v1/public/lp/{id}", lpId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.title").value("夏季促销"))
        .andExpect(jsonPath("$.data.form.id").value(formId));

    // 带 UTM 提交表单（公开）
    mockMvc
        .perform(
            post("/api/v1/public/forms/{id}/submit?utm_source=facebook&utm_campaign=summer", formId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\": \"13800000000\"}"))
        .andExpect(status().isOk());

    // UTM 统计
    mockMvc
        .perform(
            get("/api/v1/landing-pages/{id}/stats", lpId)
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1))
        .andExpect(jsonPath("$.data.bySource[0].dimension").value("facebook"))
        .andExpect(jsonPath("$.data.byCampaign[0].dimension").value("summer"));
  }

  @Test
  @DisplayName("落地页停用 → 公开 404")
  void disabledLandingPageUnavailable() throws Exception {
    String token = loginAndGetToken();
    long formId = createEnabledForm(token);
    String lpResp =
        mockMvc
            .perform(
                post("/api/v1/landing-pages")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"title\": \"停用落地页\", \"formId\": %d, \"enabled\": false}",
                            formId)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long lpId = objectMapper.readTree(lpResp).path("data").path("id").asLong();

    mockMvc
        .perform(get("/api/v1/public/lp/{id}", lpId))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("LANDING_PAGE_UNAVAILABLE"));
  }
}
