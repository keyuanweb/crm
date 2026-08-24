package com.crm.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.AbstractIntegrationTest;
import java.io.ByteArrayOutputStream;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

/** 批量导入集成测试（024 T002）：联系人导入 + 模板下载。 */
class BulkImportIT extends AbstractIntegrationTest {

  private byte[] buildContactXlsx(String[][] rows) throws Exception {
    try (Workbook wb = new XSSFWorkbook();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      Sheet sheet = wb.createSheet("联系人");
      String[] headers = {"姓名", "客户名称", "职位", "电话", "邮箱", "角色", "备注"};
      Row h = sheet.createRow(0);
      for (int i = 0; i < headers.length; i++) {
        h.createCell(i).setCellValue(headers[i]);
      }
      int idx = 1;
      for (String[] r : rows) {
        Row row = sheet.createRow(idx++);
        for (int i = 0; i < r.length; i++) {
          row.createCell(i).setCellValue(r[i]);
        }
      }
      wb.write(out);
      return out.toByteArray();
    }
  }

  private long createCustomer(String token, String name) throws Exception {
    String resp =
        mockMvc
            .perform(
                post("/api/v1/customers")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\": \"" + name + "\", \"company\": \"导入测试公司\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(resp).path("data").path("id").asLong();
  }

  @Test
  @DisplayName("联系人导入：合法行成功、客户不存在失败")
  void contactImport() throws Exception {
    String token = loginAndGetToken();
    createCustomer(token, "导入客户A");

    byte[] bytes =
        buildContactXlsx(
            new String[][] {
              {"张三", "导入客户A", "CTO", "138", "", "DECISION_MAKER", ""},
              {"李四", "不存在的客户", "", "", "", "", ""},
            });
    MockMultipartFile file =
        new MockMultipartFile("file", "contacts.xlsx", "application/vnd.ms-excel", bytes);

    mockMvc
        .perform(
            multipart("/api/v1/contacts/import").file(file).header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.successCount").value(1))
        .andExpect(jsonPath("$.data.failureCount").value(1))
        .andExpect(jsonPath("$.data.failures[0].message").value("客户不存在：不存在的客户"));
  }

  @Test
  @DisplayName("联系人导入模板下载返回 xlsx")
  void contactTemplate() throws Exception {
    String token = loginAndGetToken();

    mockMvc
        .perform(get("/api/v1/contacts/import-template").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .contentTypeCompatibleWith(
                    MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")));
  }

  @Test
  @DisplayName("线索导入模板下载返回 xlsx")
  void leadTemplate() throws Exception {
    String token = loginAndGetToken();

    mockMvc
        .perform(get("/api/v1/leads/template").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .contentTypeCompatibleWith(
                    MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")));
  }
}
