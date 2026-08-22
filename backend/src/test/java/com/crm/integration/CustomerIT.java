package com.crm.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.AbstractIntegrationTest;
import com.crm.repository.AuditLogMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** 客户 CRUD 集成测试（T018，US1）。 */
class CustomerIT extends AbstractIntegrationTest {

  @Autowired private AuditLogMapper auditLogMapper;

  private long createCustomer(String token, String name, String company) throws Exception {
    String body =
        String.format(
            "{\"name\": \"%s\", \"company\": \"%s\", \"phone\": \"13800000000\"}", name, company);
    String resp =
        mockMvc
            .perform(
                post("/api/v1/customers")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(resp).path("data").path("id").asLong();
  }

  @Test
  @DisplayName("客户全流程：创建→列表可见→详情→编辑→逻辑删除")
  void customerLifecycle() throws Exception {
    String token = loginAndGetToken();
    long id = createCustomer(token, "张三", "XX 科技");

    // 列表可见（FR-016：电话/邮箱脱敏）
    mockMvc
        .perform(get("/api/v1/customers").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1))
        .andExpect(jsonPath("$.data.items[0].phone").value("138****0000"));

    // FR-017：创建已写入审计日志
    org.assertj.core.api.Assertions.assertThat(auditLogMapper.selectCount(null))
        .isGreaterThanOrEqualTo(1L);

    // 详情（FR-016：详情返回完整值）
    mockMvc
        .perform(get("/api/v1/customers/{id}", id).header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.name").value("张三"))
        .andExpect(jsonPath("$.data.phone").value("13800000000"))
        .andExpect(jsonPath("$.data.opportunities").isArray())
        .andExpect(jsonPath("$.data.followUps").isArray());

    // 编辑
    String updateBody = """
        {"name": "张三", "company": "YY 科技", "version": 0}
        """;
    mockMvc
        .perform(
            put("/api/v1/customers/{id}", id)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.company").value("YY 科技"))
        .andExpect(jsonPath("$.data.version").value(1));

    // 删除（逻辑删除）
    mockMvc
        .perform(delete("/api/v1/customers/{id}", id).header("Authorization", bearer(token)))
        .andExpect(status().isOk());
    mockMvc
        .perform(get("/api/v1/customers").header("Authorization", bearer(token)))
        .andExpect(jsonPath("$.data.total").value(0));
    // 已删除客户详情 404
    mockMvc
        .perform(get("/api/v1/customers/{id}", id).header("Authorization", bearer(token)))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("相同名称与公司的客户重复创建返回 409")
  void duplicateCustomerReturns409() throws Exception {
    String token = loginAndGetToken();
    createCustomer(token, "李四", "ABC 公司");
    mockMvc
        .perform(
            post("/api/v1/customers")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"李四\", \"company\": \"ABC 公司\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("CUSTOMER_DUPLICATE"));
  }

  @Test
  @DisplayName("乐观锁版本冲突返回 409")
  void versionConflictReturns409() throws Exception {
    String token = loginAndGetToken();
    long id = createCustomer(token, "王五", "DEF 公司");
    // 两次基于 version=0 的更新，第二次应冲突
    String updateBody = """
        {"name": "王五", "company": "DEF 公司", "version": 0}
        """;
    mockMvc
        .perform(
            put("/api/v1/customers/{id}", id)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            put("/api/v1/customers/{id}", id)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("VERSION_CONFLICT"));
  }
}
