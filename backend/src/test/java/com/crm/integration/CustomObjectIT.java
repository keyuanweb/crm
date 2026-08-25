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

/** 自定义对象集成测试（059 T012）：对象定义/记录 CRUD/校验。 */
class CustomObjectIT extends AbstractIntegrationTest {

  private long createObject(String token) throws Exception {
    String code = "OBJ" + (System.nanoTime() % 100000);
    String resp =
        mockMvc
            .perform(
                post("/api/v1/custom-objects")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"name\": \"项目\", \"code\": \"%s\", \"fields\": ["
                                + "{\"field\": \"name\", \"label\": \"项目名称\", \"type\": \"TEXT\", \"required\": true},"
                                + "{\"field\": \"status\", \"label\": \"状态\", \"type\": \"SELECT\", \"options\": \"进行中,已完成\"}"
                                + "], \"enabled\": true}",
                            code)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(resp).path("data").path("id").asLong();
  }

  @Test
  @DisplayName("对象流程：定义→记录 CRUD→搜索")
  void customObjectFlow() throws Exception {
    String token = loginAndGetToken();
    long objectId = createObject(token);

    // 创建记录
    String recordResp =
        mockMvc
            .perform(
                post("/api/v1/custom-objects/{id}/records", objectId)
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"values\": {\"name\": \"CRM 重构\", \"status\": \"进行中\"}}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.values.name").value("CRM 重构"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    long recordId = objectMapper.readTree(recordResp).path("data").path("id").asLong();

    // 记录列表（搜索命中）
    mockMvc
        .perform(
            get("/api/v1/custom-objects/{id}/records", objectId)
                .param("keyword", "CRM")
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1));

    // 编辑记录
    mockMvc
        .perform(
            put("/api/v1/custom-objects/{id}/records/{rid}", objectId, recordId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"values\": {\"name\": \"CRM 重构2\", \"status\": \"已完成\"}}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.values.name").value("CRM 重构2"));

    // 删除记录
    mockMvc
        .perform(
            delete("/api/v1/custom-objects/{id}/records/{rid}", objectId, recordId)
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("必填缺失 → 422；停用对象 → 422")
  void validations() throws Exception {
    String token = loginAndGetToken();
    long objectId = createObject(token);

    // 必填缺失
    mockMvc
        .perform(
            post("/api/v1/custom-objects/{id}/records", objectId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"values\": {\"status\": \"进行中\"}}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("OBJECT_FIELD_REQUIRED"));

    // 停用对象
    mockMvc
        .perform(
            post("/api/v1/custom-objects/{id}/toggle", objectId)
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/custom-objects/{id}/records", objectId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"values\": {\"name\": \"x\"}}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("OBJECT_DISABLED"));
  }

  @Test
  @DisplayName("编码重复 → 409；字段非法 → 422")
  void objectValidations() throws Exception {
    String token = loginAndGetToken();
    String code = "DUP" + (System.nanoTime() % 100000);
    mockMvc
        .perform(
            post("/api/v1/custom-objects")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    String.format(
                        "{\"name\": \"项目A\", \"code\": \"%s\", \"fields\": ["
                            + "{\"field\": \"name\", \"label\": \"名称\", \"type\": \"TEXT\"}]}",
                        code)))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/v1/custom-objects")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    String.format(
                        "{\"name\": \"项目B\", \"code\": \"%s\", \"fields\": ["
                            + "{\"field\": \"name\", \"label\": \"名称\", \"type\": \"TEXT\"}]}",
                        code)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("OBJECT_CODE_DUPLICATE"));

    mockMvc
        .perform(
            post("/api/v1/custom-objects")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\": \"项目3\", \"code\": \"P3\", \"fields\": ["
                        + "{\"field\": \"x\", \"label\": \"X\", \"type\": \"BOGUS\"}]}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("OBJECT_FIELD_INVALID"));
  }
}
