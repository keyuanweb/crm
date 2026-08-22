package com.crm.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.AbstractIntegrationTest;
import com.crm.entity.Customer;
import com.crm.repository.CustomerMapper;
import java.io.ByteArrayOutputStream;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

/** 客户导入导出集成测试（T044，US5）。 */
class CustomerImportExportIT extends AbstractIntegrationTest {

  @Autowired private CustomerMapper customerMapper;

  private MockMultipartFile buildWorkbook(boolean withInvalidRows) throws Exception {
    try (Workbook wb = new XSSFWorkbook();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      Sheet sheet = wb.createSheet("客户");
      Row header = sheet.createRow(0);
      String[] headers = {"客户名称", "公司", "联系人", "电话", "邮箱", "地址", "备注"};
      for (int i = 0; i < headers.length; i++) {
        header.createCell(i).setCellValue(headers[i]);
      }
      for (int i = 1; i <= 3; i++) {
        Row row = sheet.createRow(i);
        row.createCell(0).setCellValue("导入客户" + i);
        row.createCell(1).setCellValue("导入公司" + i);
        row.createCell(2).setCellValue("联系人" + i);
        row.createCell(3).setCellValue("1380000" + String.format("%04d", i));
      }
      if (withInvalidRows) {
        Row bad = sheet.createRow(4);
        bad.createCell(0).setCellValue(""); // 缺名称
        bad.createCell(1).setCellValue("坏公司");
        Row bad2 = sheet.createRow(5);
        bad2.createCell(0).setCellValue("坏电话");
        bad2.createCell(1).setCellValue("坏公司");
        bad2.createCell(3).setCellValue("abc"); // 非法电话
      }
      wb.write(out);
      return new MockMultipartFile(
          "file",
          "customers.xlsx",
          "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
          out.toByteArray());
    }
  }

  @Test
  @DisplayName("导入成功与失败明细正确返回（FR-006）")
  void importReportsSuccessAndFailures() throws Exception {
    String token = loginAndGetToken();
    MockMultipartFile file = buildWorkbook(true);

    mockMvc
        .perform(
            multipart("/api/v1/customers/import").file(file).header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.successCount").value(3))
        .andExpect(jsonPath("$.data.failureCount").value(2))
        .andExpect(jsonPath("$.data.failures.length()").value(2));

    Long count = customerMapper.selectCount(null);
    assertThat(count).isEqualTo(3);
  }

  @Test
  @DisplayName("导出文件可下载且包含数据")
  void exportReturnsWorkbook() throws Exception {
    String token = loginAndGetToken();
    for (int i = 1; i <= 2; i++) {
      Customer c = new Customer();
      c.setName("导出客户" + i);
      c.setCompany("导出公司");
      c.setStatus("ACTIVE");
      customerMapper.insert(c);
    }
    byte[] content =
        mockMvc
            .perform(get("/api/v1/customers/export").header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    assertThat(content).isNotEmpty();
    try (Workbook wb = new XSSFWorkbook(new java.io.ByteArrayInputStream(content))) {
      Sheet sheet = wb.getSheetAt(0);
      assertThat(sheet.getLastRowNum()).isGreaterThanOrEqualTo(2);
    }
  }

  @Test
  @DisplayName("模板接口返回可解析的 xlsx")
  void templateReturnsWorkbook() throws Exception {
    String token = loginAndGetToken();
    byte[] content =
        mockMvc
            .perform(
                get("/api/v1/customers/import-template").header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    try (Workbook wb = new XSSFWorkbook(new java.io.ByteArrayInputStream(content))) {
      assertThat(wb.getSheetAt(0).getRow(0).getCell(0).getStringCellValue()).isEqualTo("客户名称");
    }
  }
}
