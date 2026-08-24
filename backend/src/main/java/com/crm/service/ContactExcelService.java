package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crm.dto.customer.ImportResult;
import com.crm.entity.Contact;
import com.crm.entity.Customer;
import com.crm.repository.ContactMapper;
import com.crm.repository.CustomerMapper;
import com.crm.security.SecurityUtil;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 联系人 Excel 批量导入（024-bulk-import，FR-003/004/006）。按客户名称匹配关联。 */
@Service
public class ContactExcelService {

  private static final Logger log = LoggerFactory.getLogger(ContactExcelService.class);
  private static final String[] HEADERS = {"姓名", "客户名称", "职位", "电话", "邮箱", "角色", "备注"};

  private final ContactMapper contactMapper;
  private final CustomerMapper customerMapper;
  private final AuditService auditService;

  public ContactExcelService(
      ContactMapper contactMapper, CustomerMapper customerMapper, AuditService auditService) {
    this.contactMapper = contactMapper;
    this.customerMapper = customerMapper;
    this.auditService = auditService;
  }

  @Transactional
  public ImportResult importContacts(InputStream in) {
    ImportResult result = new ImportResult();
    List<Contact> batch = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    // 预加载全部客户名称→id 映射（精确匹配，不区分大小写）
    Map<String, Long> customerByName =
        customerMapper
            .selectList(
                new LambdaQueryWrapper<Customer>().select(Customer::getId, Customer::getName))
            .stream()
            .collect(
                Collectors.toMap(
                    c -> c.getName() == null ? "" : c.getName().trim().toLowerCase(),
                    Customer::getId,
                    (a, b) -> a));
    try (Workbook workbook = WorkbookFactory.create(in)) {
      Sheet sheet = workbook.getSheetAt(0);
      int lastRow = sheet.getLastRowNum();
      for (int i = 1; i <= lastRow; i++) {
        Row row = sheet.getRow(i);
        if (row == null || isEmptyRow(row)) {
          continue;
        }
        try {
          Contact contact = parseRow(row, customerByName);
          String key = contact.getName() + "|" + contact.getCustomerId();
          if (!seen.add(key)) {
            throw new IllegalArgumentException("与文件内第 " + i + " 行重复");
          }
          contact.setCreatedBy(SecurityUtil.currentUserId());
          batch.add(contact);
          result.setSuccessCount(result.getSuccessCount() + 1);
        } catch (Exception ex) {
          result.setFailureCount(result.getFailureCount() + 1);
          result.getFailures().add(new ImportResult.ImportFailure(i + 1, ex.getMessage()));
        }
      }
      for (Contact c : batch) {
        contactMapper.insert(c);
      }
      auditService.record("IMPORT", "CONTACT", null, "批量导入联系人：" + result.getSuccessCount() + " 成功");
    } catch (IOException | RuntimeException ex) {
      log.error("Failed to parse contact import file", ex);
      throw new IllegalArgumentException("导入文件解析失败：" + ex.getMessage());
    }
    return result;
  }

  /** 生成导入模板（表头 + 示例行）。 */
  public byte[] generateTemplate() {
    try (Workbook workbook = new XSSFWorkbook();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      Sheet sheet = workbook.createSheet("联系人导入模板");
      Row header = sheet.createRow(0);
      for (int i = 0; i < HEADERS.length; i++) {
        header.createCell(i).setCellValue(HEADERS[i]);
      }
      Row example = sheet.createRow(1);
      example.createCell(0).setCellValue("张三");
      example.createCell(1).setCellValue("XX 科技");
      example.createCell(2).setCellValue("CTO");
      example.createCell(3).setCellValue("13800000000");
      example.createCell(4).setCellValue("z@example.com");
      example.createCell(5).setCellValue("DECISION_MAKER");
      example.createCell(6).setCellValue("示例行，可删除");
      for (int i = 0; i < HEADERS.length; i++) {
        sheet.autoSizeColumn(i);
      }
      workbook.write(out);
      return out.toByteArray();
    } catch (IOException ex) {
      throw new IllegalStateException("模板生成失败", ex);
    }
  }

  private Contact parseRow(Row row, Map<String, Long> customerByName) {
    String name = cellString(row, 0);
    String customerName = cellString(row, 1);
    if (!StringUtils.hasText(name)) {
      throw new IllegalArgumentException("姓名不能为空");
    }
    if (!StringUtils.hasText(customerName)) {
      throw new IllegalArgumentException("客户名称不能为空");
    }
    Long customerId = customerByName.get(customerName.trim().toLowerCase());
    if (customerId == null) {
      throw new IllegalArgumentException("客户不存在：" + customerName.trim());
    }
    Contact contact = new Contact();
    contact.setName(name.trim());
    contact.setCustomerId(customerId);
    contact.setTitle(trimToNull(cellString(row, 2)));
    contact.setPhone(trimToNull(cellString(row, 3)));
    contact.setEmail(trimToNull(cellString(row, 4)));
    contact.setRole(trimToNull(cellString(row, 5)));
    contact.setRemark(trimToNull(cellString(row, 6)));
    return contact;
  }

  private String cellString(Row row, int index) {
    Cell cell = row.getCell(index);
    if (cell == null) {
      return null;
    }
    if (cell.getCellType() == CellType.STRING) {
      return cell.getStringCellValue();
    }
    if (cell.getCellType() == CellType.NUMERIC) {
      double v = cell.getNumericCellValue();
      if (v == Math.floor(v)) {
        return String.valueOf((long) v);
      }
      return String.valueOf(v);
    }
    return cell.toString();
  }

  private boolean isEmptyRow(Row row) {
    for (int i = 0; i < HEADERS.length; i++) {
      if (StringUtils.hasText(cellString(row, i))) {
        return false;
      }
    }
    return true;
  }

  private String trimToNull(String value) {
    return StringUtils.hasText(value) ? value.trim() : null;
  }
}
