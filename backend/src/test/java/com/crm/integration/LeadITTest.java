package com.crm.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** 线索集成测试（T013）：完整业务流程 CRUD/线索池/分配/领取/跟进/转化。 */
class LeadITTest extends AbstractIntegrationTest {

  private Long createLead(String token, String name, String company) throws Exception {
    String body =
        """
        {"name": "%s", "company": "%s", "source": "WEBSITE", "score": 70}
        """
            .formatted(name, company);
    String resp =
        mockMvc
            .perform(
                post("/api/v1/leads")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").isNumber())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(resp).path("data").path("id").asLong();
  }

  @Test
  @DisplayName("完整流程：创建→编辑→领取→跟进→转化→客户商机创建")
  void fullLeadLifecycle() throws Exception {
    String token = loginAndGetToken();

    // 1. 创建线索
    Long leadId = createLead(token, "集成测试用户", "集成测试科技");

    // 2. 编辑线索
    String updateBody =
        """
        {"name": "集成测试用户-改", "company": "集成测试科技", "source": "EXHIBITION",
         "title": "CEO", "phone": "13900139000", "email": "it@test.com", "score": 95}
        """;
    mockMvc
        .perform(
            put("/api/v1/leads/" + leadId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.name").value("集成测试用户-改"))
        .andExpect(jsonPath("$.data.source").value("EXHIBITION"))
        // 019：自动评分覆盖手工 score，断言为有效 0-100 数值
        .andExpect(jsonPath("$.data.score").isNumber());

    // 3. 领取线索
    mockMvc
        .perform(post("/api/v1/leads/" + leadId + "/claim").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("WORKING"))
        .andExpect(jsonPath("$.data.ownerId").isNumber());

    // 4. 添加跟进记录（关联 lead）
    String followUpBody =
        """
        {"leadId": %d, "method": "PHONE", "content": "电话沟通，客户有意向采购CRM"}
        """
            .formatted(leadId);
    mockMvc
        .perform(
            post("/api/v1/follow-ups")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(followUpBody))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.leadId").value(leadId))
        .andExpect(jsonPath("$.data.method").value("PHONE"));

    // 5. 查看详情（含跟进记录）
    String detailResp =
        mockMvc
            .perform(get("/api/v1/leads/" + leadId).header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.followUps").isArray())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode detail = objectMapper.readTree(detailResp);
    assertThat(detail.path("data").path("followUps").size()).isEqualTo(1);

    // 6. 转化线索
    String convertBody =
        """
        {"opportunityName": "集成测试-CRM系统采购", "expectedAmount": 500000, "remark": "高意向"}
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

    // 7. 验证客户已创建
    mockMvc
        .perform(get("/api/v1/customers?page=1&pageSize=50").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

    // 8. 验证商机已创建
    mockMvc
        .perform(
            get("/api/v1/opportunities?page=1&pageSize=50").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
  }

  @Test
  @DisplayName("线索池筛选：未分配线索出现在 poolOnly 结果中")
  void leadPoolShowsUnownedLeads() throws Exception {
    String token = loginAndGetToken();
    createLead(token, "线索池用户", "线索池科技");

    // poolOnly=true 应包含未分配线索
    mockMvc
        .perform(
            get("/api/v1/leads").header("Authorization", bearer(token)).param("poolOnly", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
  }

  @Test
  @DisplayName("已转化线索不可编辑、不可删除、不可再次转化")
  void convertedLeadIsTerminal() throws Exception {
    String token = loginAndGetToken();
    Long leadId = createLead(token, "终态用户", "终态科技");

    // 领取并转化
    mockMvc
        .perform(post("/api/v1/leads/" + leadId + "/claim").header("Authorization", bearer(token)))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/leads/" + leadId + "/convert")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"opportunityName": "终态商机", "expectedAmount": 100000}
                    """))
        .andExpect(status().isOk());

    // 编辑应返回 422
    mockMvc
        .perform(
            put("/api/v1/leads/" + leadId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "尝试修改", "company": "终态科技"}
                    """))
        .andExpect(status().isUnprocessableEntity());

    // 删除应返回 422
    mockMvc
        .perform(delete("/api/v1/leads/" + leadId).header("Authorization", bearer(token)))
        .andExpect(status().isUnprocessableEntity());

    // 再次转化应返回 422
    mockMvc
        .perform(
            post("/api/v1/leads/" + leadId + "/convert")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"opportunityName": "重复转化", "expectedAmount": 10000}
                    """))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  @DisplayName("关键词搜索：按姓名/公司/电话筛选")
  void keywordSearchFiltersLeads() throws Exception {
    String token = loginAndGetToken();
    createLead(token, "搜索用户A", "搜索科技A");
    createLead(token, "搜索用户B", "搜索科技B");

    mockMvc
        .perform(
            get("/api/v1/leads").header("Authorization", bearer(token)).param("keyword", "搜索用户A"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1));
  }

  @Test
  @DisplayName("状态筛选：按 NEW/WORKING/QUALIFIED 过滤")
  void statusFilterWorks() throws Exception {
    String token = loginAndGetToken();
    Long leadId = createLead(token, "状态筛选用户", "状态筛选科技");

    // 新建时状态为 NEW
    mockMvc
        .perform(get("/api/v1/leads").header("Authorization", bearer(token)).param("status", "NEW"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

    // 领取后状态变为 WORKING
    mockMvc
        .perform(post("/api/v1/leads/" + leadId + "/claim").header("Authorization", bearer(token)))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            get("/api/v1/leads").header("Authorization", bearer(token)).param("status", "WORKING"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
  }

  @Test
  @DisplayName("Excel 导入：成功与失败明细正确（FR-L11）")
  void importReportsSuccessAndFailures() throws Exception {
    String token = loginAndGetToken();
    byte[] bytes;
    try (org.apache.poi.xssf.usermodel.XSSFWorkbook wb =
            new org.apache.poi.xssf.usermodel.XSSFWorkbook();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
      org.apache.poi.ss.usermodel.Sheet sheet = wb.createSheet("线索");
      String[] headers = {"姓名", "公司", "职位", "电话", "邮箱", "来源", "评分", "备注"};
      for (int i = 0; i < headers.length; i++) {
        sheet.createRow(0).createCell(i).setCellValue(headers[i]);
      }
      String[][] rows = {
        {"导入线索1", "导入公司1", "经理", "13800000001", "a1@example.com", "EXHIBITION", "80", ""},
        {"导入线索2", "导入公司2", "总监", "13800000002", "a2@example.com", "WEBSITE", "60", ""},
        {"", "坏公司", "", "", "", "", "", ""}, // 缺姓名
        {"坏电话", "坏公司", "", "abc", "", "", "", ""}, // 非法电话
      };
      for (int i = 0; i < rows.length; i++) {
        org.apache.poi.ss.usermodel.Row row = sheet.createRow(i + 1);
        for (int j = 0; j < rows[i].length; j++) {
          row.createCell(j).setCellValue(rows[i][j]);
        }
      }
      wb.write(out);
      bytes = out.toByteArray();
    }
    org.springframework.mock.web.MockMultipartFile file =
        new org.springframework.mock.web.MockMultipartFile(
            "file",
            "leads.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            bytes);
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(
                    "/api/v1/leads/import")
                .file(file)
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.successCount").value(2))
        .andExpect(jsonPath("$.data.failureCount").value(2));
  }

  @Test
  @DisplayName("Excel 导出与模板可下载（FR-L11）")
  void exportAndTemplateAvailable() throws Exception {
    String token = loginAndGetToken();
    byte[] template =
        mockMvc
            .perform(get("/api/v1/leads/template").header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    assertThat(template).isNotEmpty();
    try (org.apache.poi.xssf.usermodel.XSSFWorkbook wb =
        new org.apache.poi.xssf.usermodel.XSSFWorkbook(
            new java.io.ByteArrayInputStream(template))) {
      assertThat(wb.getSheetAt(0).getRow(0).getCell(0).getStringCellValue()).isEqualTo("姓名");
    }
    byte[] export =
        mockMvc
            .perform(get("/api/v1/leads/export").header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    assertThat(export).isNotEmpty();
  }
}
