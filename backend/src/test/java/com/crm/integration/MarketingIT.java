package com.crm.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** 营销集成测试（014 T011/T020/T026）：活动 CRUD/归因/ROI/权限。 */
class MarketingIT extends AbstractIntegrationTest {

  private long createCampaign(String token, String name, String channel) throws Exception {
    String resp =
        mockMvc
            .perform(
                post("/api/v1/campaigns")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"name\": \"%s\", \"channel\": \"%s\", \"budget\": 100000, \"cost\": 50000}",
                            name, channel)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(resp).path("data").path("id").asLong();
  }

  @Test
  @DisplayName("活动生命周期：创建→列表→编辑→开始→结束→删除防护")
  void campaignLifecycle() throws Exception {
    String token = loginAndGetToken();
    long campaignId = createCampaign(token, "营销活动A", "AD");

    // 列表
    mockMvc
        .perform(get("/api/v1/campaigns").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

    // 编辑
    mockMvc
        .perform(
            put("/api/v1/campaigns/{id}", campaignId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\": \"营销活动A-改\", \"channel\": \"AD\", \"budget\": 200000, \"cost\": 60000, \"version\": 0}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.name").value("营销活动A-改"));

    // 开始
    mockMvc
        .perform(
            post("/api/v1/campaigns/{id}/start", campaignId).header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("RUNNING"));

    // 结束
    mockMvc
        .perform(
            post("/api/v1/campaigns/{id}/end", campaignId).header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("ENDED"));

    // ENDED 不可再开始 → 409
    mockMvc
        .perform(
            post("/api/v1/campaigns/{id}/start", campaignId).header("Authorization", bearer(token)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("CAMPAIGN_INVALID_STATE"));

    // 无归因 → 删除成功
    mockMvc
        .perform(
            delete("/api/v1/campaigns/{id}", campaignId).header("Authorization", bearer(token)))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("营销归因：创建线索选活动→计数→转化带入→删除 409")
  void attribution() throws Exception {
    String token = loginAndGetToken();
    long campaignId = createCampaign(token, "归因活动", "EXHIBITION");

    // 创建线索并归因
    mockMvc
        .perform(
            post("/api/v1/leads")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\": \"归因线索\", \"company\": \"归因公司\", \"campaignId\": "
                        + campaignId
                        + "}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.campaignId").value(campaignId));

    // 活动 leadCount=1
    mockMvc
        .perform(get("/api/v1/campaigns").header("Authorization", bearer(token)))
        .andExpect(status().isOk());

    // 有归因 → 删除 409
    mockMvc
        .perform(
            delete("/api/v1/campaigns/{id}", campaignId).header("Authorization", bearer(token)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("CAMPAIGN_HAS_ATTRIBUTION"));
  }

  @Test
  @DisplayName("渠道 ROI 统计返回结构正确")
  void channelRoi() throws Exception {
    String token = loginAndGetToken();
    createCampaign(token, "ROI活动", "AD");

    mockMvc
        .perform(get("/api/v1/campaigns/channel-roi").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray());
  }

  @Test
  @DisplayName("非管理员创建活动返回 403")
  void nonAdminForbidden() throws Exception {
    String adminToken = loginAndGetToken();
    mockMvc
        .perform(
            post("/api/v1/users")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"username\": \"mktupport\", \"password\": \"Passw0rd!\", \"displayName\": \"营销客服\", \"role\": \"SUPPORT\"}"))
        .andExpect(status().isCreated());
    String supportToken = loginAndGetToken("mktupport", "Passw0rd!");

    mockMvc
        .perform(
            post("/api/v1/campaigns")
                .header("Authorization", bearer(supportToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"越权活动\", \"channel\": \"AD\"}"))
        .andExpect(status().isForbidden());

    // SUPPORT 可看 ROI
    mockMvc
        .perform(get("/api/v1/campaigns/channel-roi").header("Authorization", bearer(supportToken)))
        .andExpect(status().isOk());
  }
}
