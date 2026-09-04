/** 合规导出 Service（080-data-retention，DSAR）。

支持 GDPR/个人信息保护法的数据主体导出请求。
 */
package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crm.entity.Customer;
import com.crm.entity.Lead;
import com.crm.entity.Contact;
import com.crm.entity.Opportunity;
import com.crm.entity.Contract;
import com.crm.entity.FollowUp;
import com.crm.repository.CustomerMapper;
import com.crm.repository.LeadMapper;
import com.crm.repository.ContactMapper;
import com.crm.repository.OpportunityMapper;
import com.crm.repository.ContractMapper;
import com.crm.repository.FollowUpMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ComplianceExportService {

  private static final Logger log = LoggerFactory.getLogger(ComplianceExportService.class);
  private static final String EXPORT_DIR = System.getProperty("user.dir") + "/backend/contract-files/compliance";

  private final CustomerMapper customerMapper;
  private final LeadMapper leadMapper;
  private final ContactMapper contactMapper;
  private final OpportunityMapper opportunityMapper;
  private final ContractMapper contractMapper;
  private final FollowUpMapper followUpMapper;

  public ComplianceExportService(
      CustomerMapper customerMapper,
      LeadMapper leadMapper,
      ContactMapper contactMapper,
      OpportunityMapper opportunityMapper,
      ContractMapper contractMapper,
      FollowUpMapper followUpMapper) {
    this.customerMapper = customerMapper;
    this.leadMapper = leadMapper;
    this.contactMapper = contactMapper;
    this.opportunityMapper = opportunityMapper;
    this.contractMapper = contractMapper;
    this.followUpMapper = followUpMapper;
  }

  /** 执行合规导出：根据实体类型导出用户数据。 */
  public String executeExport(String entityType, String userId, String exportFormat) {
    try {
      Path dir = Paths.get(EXPORT_DIR);
      Files.createDirectories(dir);
      String fileName =
          "compliance_"
              + entityType.toLowerCase()
              + "_user_"
              + userId
              + "_"
              + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
              + "."
              + (exportFormat.equals("CSV") ? "csv" : "xlsx");
      Path file = dir.resolve(fileName);

      List<Map<String, Object>> data = queryUserData(entityType, userId);
      byte[] content = generateExportFile(data, exportFormat);
      Files.write(file, content);

      log.info("Compliance export completed: entityType={}, userId={}, file={}", entityType, userId, file);
      return file.toString();
    } catch (IOException e) {
      throw new RuntimeException("Compliance export failed: " + e.getMessage(), e);
    }
  }

  /** 查询用户数据。 */
  private List<Map<String, Object>> queryUserData(String entityType, String userId) {
    List<Map<String, Object>> data = new ArrayList<>();
    Long userIdLong = null;
    try {
      userIdLong = Long.parseLong(userId);
    } catch (NumberFormatException e) {
      // 用户 ID 可能是邮箱或其他标识，当前实现仅支持 Long 类型
      log.warn("Invalid userId format: {}", userId);
      return data;
    }

    switch (entityType.toUpperCase()) {
      case "CUSTOMER":
        customerMapper.selectList(new LambdaQueryWrapper<Customer>()).forEach(c -> {
          Map<String, Object> row = Map.of(
              "id", c.getId(),
              "name", c.getName(),
              "company", c.getCompany(),
              "contactPerson", c.getContactPerson(),
              "phone", c.getPhone(),
              "email", c.getEmail(),
              "createdAt", c.getCreatedAt()
          );
          data.add(row);
        });
        break;
      case "LEAD":
        leadMapper.selectList(new LambdaQueryWrapper<Lead>()).forEach(l -> {
          Map<String, Object> row = Map.of(
              "id", l.getId(),
              "name", l.getName(),
              "company", l.getCompany(),
              "phone", l.getPhone(),
              "email", l.getEmail(),
              "status", l.getStatus(),
              "createdAt", l.getCreatedAt()
          );
          data.add(row);
        });
        break;
      case "CONTACT":
        contactMapper.selectList(new LambdaQueryWrapper<Contact>()).forEach(c -> {
          Map<String, Object> row = Map.of(
              "id", c.getId(),
              "name", c.getName(),
              "phone", c.getPhone(),
              "email", c.getEmail(),
              "createdAt", c.getCreatedAt()
          );
          data.add(row);
        });
        break;
      case "OPPORTUNITY":
        opportunityMapper.selectList(new LambdaQueryWrapper<Opportunity>()).forEach(o -> {
          Map<String, Object> row = Map.of(
              "id", o.getId(),
              "name", o.getName(),
              "amount", o.getAmount(),
              "status", o.getStatus(),
              "createdAt", o.getCreatedAt()
          );
          data.add(row);
        });
        break;
      case "CONTRACT":
        contractMapper.selectList(new LambdaQueryWrapper<Contract>()).forEach(c -> {
          Map<String, Object> row = Map.of(
              "id", c.getId(),
              "title", c.getTitle(),
              "status", c.getStatus(),
              "startDate", c.getStartDate(),
              "endDate", c.getEndDate(),
              "createdAt", c.getCreatedAt()
          );
          data.add(row);
        });
        break;
      case "FOLLOW_UP":
        followUpMapper.selectList(new LambdaQueryWrapper<FollowUp>()).forEach(f -> {
          Map<String, Object> row = Map.of(
              "id", f.getId(),
              "method", f.getMethod(),
              "content", f.getContent(),
              "createdAt", f.getCreatedAt()
          );
          data.add(row);
        });
        break;
      default:
        log.warn("Unsupported entity type for compliance export: {}", entityType);
    }

    return data;
  }

  /** 生成导出文件。 */
  private byte[] generateExportFile(List<Map<String, Object>> data, String format) throws IOException {
    if (format.equals("CSV")) {
      return generateCsv(data);
    } else {
      return generateXlsx(data);
    }
  }

  /** 生成 CSV 文件。 */
  private byte[] generateCsv(List<Map<String, Object>> data) throws IOException {
    StringBuilder sb = new StringBuilder();
    if (!data.isEmpty()) {
      // 表头
      sb.append(String.join(",", data.get(0).keySet()));
      sb.append("\n");
      // 数据行
      for (Map<String, Object> row : data) {
        sb.append(String.join(",", row.values().stream().map(Object::toString).toArray(String[]::new)));
        sb.append("\n");
      }
    }
    return sb.toString().getBytes("UTF-8");
  }

  /** 生成 XLSX 文件。 */
  private byte[] generateXlsx(List<Map<String, Object>> data) throws IOException {
    try (XSSFWorkbook workbook = new XSSFWorkbook()) {
      Sheet sheet = workbook.createSheet("Data");
      if (!data.isEmpty()) {
        // 表头
        Row headerRow = sheet.createRow(0);
        int colIdx = 0;
        for (String key : data.get(0).keySet()) {
          headerRow.createCell(colIdx++).setCellValue(key);
        }
        // 数据行
        int rowIdx = 1;
        for (Map<String, Object> rowData : data) {
          Row row = sheet.createRow(rowIdx++);
          colIdx = 0;
          for (Object value : rowData.values()) {
            row.createCell(colIdx++).setCellValue(value != null ? value.toString() : "");
          }
        }
      }
      java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
      workbook.write(out);
      return out.toByteArray();
    }
  }
}
