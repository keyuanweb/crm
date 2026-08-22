package com.crm.contract;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** 客户契约测试（T017）：请求/响应结构与契约 customers.md 一致。 */
class CustomerContractTest extends AbstractIntegrationTest {

  @Test
  @DisplayName("未认证请求返回 401（契约 README 状态码）")
  void unauthenticatedRequestReturns401() throws Exception {
    mockMvc.perform(get("/api/v1/customers")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("创建客户校验失败返回 400 与 fieldErrors")
  void validationErrorReturns400WithFieldErrors() throws Exception {
    String token = loginAndGetToken();
    String body = """
        {"name": "", "company": ""}
        """;
    mockMvc
        .perform(
            post("/api/v1/customers")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
        .andExpect(jsonPath("$.error.fieldErrors").isArray());
  }

  @Test
  @DisplayName("创建成功返回契约字段（id/name/company/status/version）")
  void createReturnsContractFields() throws Exception {
    String token = loginAndGetToken();
    String body =
        """
        {"name": "张三", "company": "XX 科技", "contactPerson": "张三",
         "phone": "13800000000", "email": "z@example.com", "address": "北京", "remark": ""}
        """;
    mockMvc
        .perform(
            post("/api/v1/customers")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.id").isNumber())
        .andExpect(jsonPath("$.data.name").value("张三"))
        .andExpect(jsonPath("$.data.company").value("XX 科技"))
        .andExpect(jsonPath("$.data.status").value("ACTIVE"))
        .andExpect(jsonPath("$.data.version").isNumber());
  }

  @Test
  @DisplayName("列表返回分页信封 items/total/page/pageSize")
  void listReturnsPaginationEnvelope() throws Exception {
    String token = loginAndGetToken();
    mockMvc
        .perform(get("/api/v1/customers").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items").isArray())
        .andExpect(jsonPath("$.data.total").isNumber())
        .andExpect(jsonPath("$.data.page").value(1))
        .andExpect(jsonPath("$.data.pageSize").value(20));
  }
}
