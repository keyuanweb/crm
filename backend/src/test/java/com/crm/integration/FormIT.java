package com.crm.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** 在线表单集成测试（036 T005）：建表单 → 公开提交 → 防重复。 */
class FormIT extends AbstractIntegrationTest {

  private long createForm(String token) throws Exception {
    String resp =
        mockMvc
            .perform(
                post("/api/v1/forms")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"name\":\"试用申请\",\"fields\":[{\"field\":\"name\",\"label\":\"姓名\",\"type\":\"TEXT\",\"required\":true},{\"field\":\"phone\",\"label\":\"手机\",\"type\":\"TEL\",\"required\":true}],\"successMessage\":\"已收到您的申请\",\"source\":\"WEBSITE\",\"status\":\"ENABLED\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(resp).path("data").path("id").asLong();
  }

  @Test
  @DisplayName("建表单 → 公开提交 → 线索生成 → 重复提交拦截")
  void formSubmitFlow() throws Exception {
    String token = loginAndGetToken();
    long formId = createForm(token);
    String phone = "138" + String.valueOf(System.currentTimeMillis()).substring(4, 11);

    // 公开提交（匿名，无 token）
    mockMvc
        .perform(
            post("/api/v1/public/forms/" + formId + "/submit")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"表单测试\",\"phone\":\"" + phone + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.message").value("已收到您的申请"));

    // 重复提交拦截（同 phone）
    mockMvc
        .perform(
            post("/api/v1/public/forms/" + formId + "/submit")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"表单测试2\",\"phone\":\"" + phone + "\"}"))
        .andExpect(status().isConflict());

    // 提交记录
    mockMvc
        .perform(
            get("/api/v1/forms/" + formId + "/submissions").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1));
  }
}
