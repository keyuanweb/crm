package com.crm.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** 合同续约集成测试（046 T013）：到期归类/续约创建/漏斗。 */
class ContractRenewalIT extends AbstractIntegrationTest {

  private long createCustomer(String token) throws Exception {
    String resp =
        mockMvc
            .perform(
                post("/api/v1/customers")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"name\": \"续约客户%d\", \"company\": \"续约公司\"}", System.nanoTime())))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(resp).path("data").path("id").asLong();
  }

  /** 创建合同并流转到生效。 */
  private long createEffectiveContract(String token, long customerId, String no, int daysFromNow)
      throws Exception {
    String createResp =
        mockMvc
            .perform(
                post("/api/v1/contracts")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"title\": \"续约合同%s\", \"customerId\": %d, \"amount\": 1000000, "
                                + "\"startDate\": \"2025-01-01\", \"endDate\": \"%s\"}",
                            no, customerId, java.time.LocalDate.now().plusDays(daysFromNow))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long id = objectMapper.readTree(createResp).path("data").path("id").asLong();
    mockMvc
        .perform(post("/api/v1/contracts/{id}/submit", id).header("Authorization", bearer(token)))
        .andExpect(status().isOk());
    mockMvc
        .perform(post("/api/v1/contracts/{id}/approve", id).header("Authorization", bearer(token)))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/contracts/{id}/effective", id).header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("EFFECTIVE"));
    return id;
  }

  @Test
  @DisplayName("续约流程：到期归类→续约创建→来源/去向→已续约漏斗")
  void renewalFlow() throws Exception {
    String token = loginAndGetToken();
    long customerId = createCustomer(token);

    // 即将到期合同 A（30 天后）
    long contractA = createEffectiveContract(token, customerId, "A", 30);

    // 即将到期视图包含 A
    mockMvc
        .perform(
            get("/api/v1/contracts/renewal-overview")
                .param("group", "EXPIRING_SOON")
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.data.items[?(@.id == " + contractA + ")]")
                .value(org.hamcrest.Matchers.hasSize(1)));

    // 续约合同 B（renewedFromId=A），远期内到期
    String renewalResp =
        mockMvc
            .perform(
                post("/api/v1/contracts")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"title\": \"续约合同B\", \"customerId\": %d, \"amount\": 1200000, "
                                + "\"startDate\": \"%s\", \"endDate\": \"%s\", \"renewedFromId\": %d}",
                            customerId,
                            java.time.LocalDate.now().plusDays(30),
                            java.time.LocalDate.now().plusDays(400),
                            contractA)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.renewedFromId").value(contractA))
            .andReturn()
            .getResponse()
            .getContentAsString();
    long contractB = objectMapper.readTree(renewalResp).path("data").path("id").asLong();
    mockMvc
        .perform(
            post("/api/v1/contracts/{id}/submit", contractB).header("Authorization", bearer(token)))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/contracts/{id}/approve", contractB)
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/contracts/{id}/effective", contractB)
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk());

    // A 详情展示续约去向
    mockMvc
        .perform(get("/api/v1/contracts/{id}", contractA).header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.renewedBy[0].id").value(contractB));

    // 已续约漏斗包含 A
    mockMvc
        .perform(
            get("/api/v1/contracts/renewal-overview")
                .param("group", "RENEWED")
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.data.items[?(@.id == " + contractA + ")]")
                .value(org.hamcrest.Matchers.hasSize(1)));
  }

  @Test
  @DisplayName("非法分组 → 422；续约来源不存在 → 404")
  void invalidInputs() throws Exception {
    String token = loginAndGetToken();
    mockMvc
        .perform(
            get("/api/v1/contracts/renewal-overview")
                .param("group", "BAD")
                .header("Authorization", bearer(token)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("CONTRACT_RENEWAL_GROUP_INVALID"));

    long customerId = createCustomer(token);
    mockMvc
        .perform(
            post("/api/v1/contracts")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    String.format(
                        "{\"title\": \"无效续约\", \"customerId\": %d, \"amount\": 1000, \"renewedFromId\": 99999}",
                        customerId)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("CONTRACT_NOT_FOUND"));
  }
}
