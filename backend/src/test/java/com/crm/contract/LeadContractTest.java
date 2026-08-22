package com.crm.contract;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** 线索契约测试（T017）：请求/响应结构与契约一致。 */
class LeadContractTest extends AbstractIntegrationTest {

  @Test
  @DisplayName("未认证请求返回 401")
  void unauthenticatedRequestReturns401() throws Exception {
    mockMvc.perform(get("/api/v1/leads")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("创建线索校验失败返回 400 与 fieldErrors")
  void validationErrorReturns400() throws Exception {
    String token = loginAndGetToken();
    String body = """
        {"name": "", "company": ""}
        """;
    mockMvc
        .perform(
            post("/api/v1/leads")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
        .andExpect(jsonPath("$.error.fieldErrors").isArray());
  }

  @Test
  @DisplayName("创建成功返回契约字段（id/name/company/status/source/score/version）")
  void createReturnsContractFields() throws Exception {
    String token = loginAndGetToken();
    String body =
        """
        {"name": "张三", "company": "测试科技", "title": "CTO",
         "phone": "13800138000", "email": "zhangsan@test.com",
         "source": "WEBSITE", "score": 85}
        """;
    mockMvc
        .perform(
            post("/api/v1/leads")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").isNumber())
        .andExpect(jsonPath("$.data.name").value("张三"))
        .andExpect(jsonPath("$.data.company").value("测试科技"))
        .andExpect(jsonPath("$.data.status").value("NEW"))
        .andExpect(jsonPath("$.data.source").value("WEBSITE"))
        .andExpect(jsonPath("$.data.score").value(85))
        .andExpect(jsonPath("$.data.version").isNumber());
  }

  @Test
  @DisplayName("列表返回分页信封 items/total/page/pageSize")
  void listReturnsPaginationEnvelope() throws Exception {
    String token = loginAndGetToken();
    mockMvc
        .perform(get("/api/v1/leads").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items").isArray())
        .andExpect(jsonPath("$.data.total").isNumber())
        .andExpect(jsonPath("$.data.page").value(1))
        .andExpect(jsonPath("$.data.pageSize").value(20));
  }

  @Test
  @DisplayName("线索池筛选 poolOnly=true 返回未分配线索")
  void poolOnlyReturnsUnowned() throws Exception {
    String token = loginAndGetToken();
    mockMvc
        .perform(
            get("/api/v1/leads").header("Authorization", bearer(token)).param("poolOnly", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items").isArray())
        .andExpect(jsonPath("$.data.total").isNumber());
  }

  @Test
  @DisplayName("领取线索后状态变为 WORKING")
  void claimChangesStatusToWorking() throws Exception {
    String token = loginAndGetToken();
    String createBody =
        """
        {"name": "李四", "company": "领取测试", "source": "EXHIBITION"}
        """;
    String createResp =
        mockMvc
            .perform(
                post("/api/v1/leads")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createBody))
            .andReturn()
            .getResponse()
            .getContentAsString();
    Long leadId = objectMapper.readTree(createResp).path("data").path("id").asLong();

    mockMvc
        .perform(post("/api/v1/leads/" + leadId + "/claim").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("WORKING"))
        .andExpect(jsonPath("$.data.ownerId").isNumber());
  }

  @Test
  @DisplayName("转化线索成功返回 QUALIFIED 状态与 convertedCustomerId")
  void convertReturnsQualified() throws Exception {
    String token = loginAndGetToken();
    String createBody =
        """
        {"name": "王五", "company": "转化测试科技", "source": "REFERRAL", "score": 90}
        """;
    String createResp =
        mockMvc
            .perform(
                post("/api/v1/leads")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createBody))
            .andReturn()
            .getResponse()
            .getContentAsString();
    Long leadId = objectMapper.readTree(createResp).path("data").path("id").asLong();

    mockMvc
        .perform(post("/api/v1/leads/" + leadId + "/claim").header("Authorization", bearer(token)))
        .andExpect(status().isOk());

    String convertBody =
        """
        {"opportunityName": "转化测试-CRM采购", "expectedAmount": 300000, "remark": "高意向"}
        """;
    mockMvc
        .perform(
            post("/api/v1/leads/" + leadId + "/convert")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(convertBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("QUALIFIED"))
        .andExpect(jsonPath("$.data.convertedCustomerId").isNumber())
        .andExpect(jsonPath("$.data.convertedAt").isNotEmpty());
  }

  @Test
  @DisplayName("重复转化已转化线索返回 422")
  void convertConvertedReturns422() throws Exception {
    String token = loginAndGetToken();
    String createBody =
        """
        {"name": "赵六", "company": "重复转化测试", "source": "COLD_CALL"}
        """;
    String createResp =
        mockMvc
            .perform(
                post("/api/v1/leads")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createBody))
            .andReturn()
            .getResponse()
            .getContentAsString();
    Long leadId = objectMapper.readTree(createResp).path("data").path("id").asLong();

    mockMvc
        .perform(post("/api/v1/leads/" + leadId + "/claim").header("Authorization", bearer(token)))
        .andExpect(status().isOk());

    String convertBody =
        """
        {"opportunityName": "商机", "expectedAmount": 100000}
        """;
    mockMvc
        .perform(
            post("/api/v1/leads/" + leadId + "/convert")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(convertBody))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/leads/" + leadId + "/convert")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(convertBody))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("LEAD_INVALID_STATE"));
  }
}
