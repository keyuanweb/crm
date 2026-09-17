package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crm.dto.customer.ImportResult;
import com.crm.entity.Customer;
import com.crm.repository.CustomerMapper;
import com.crm.security.SecurityUtil;
import com.crm.support.BuiltinFieldRegistry;
import com.crm.support.FieldMaskPlanner;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

/** 客户 Excel 导入导出服务（research.md R6，FR-006）。 */
@Service
public class CustomerExcelService {

  private static final Logger log = LoggerFactory.getLogger(CustomerExcelService.class);
  private static final int BATCH_SIZE = 500;
  private static final String[] HEADERS = {"客户名称", "公司", "联系人", "电话", "邮箱", "地址", "备注"};

  private final CustomerMapper customerMapper;
  private final AuditService auditService;
  private final FieldMaskPlanner fieldMaskPlanner;

  public CustomerExcelService(
      CustomerMapper customerMapper, AuditService auditService, FieldMaskPlanner fieldMaskPlanner) {
    this.customerMapper = customerMapper;
    this.auditService = auditService;
    this.fieldMaskPlanner = fieldMaskPlanner;
  }

  /** 导入：逐行校验，按 500 行分批事务插入，返回成功/失败明细。 */
  @Transactional
  public ImportResult importCustomers(InputStream in) {
    ImportResult result = new ImportResult();
    List<Customer> batch = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    try (Workbook workbook = WorkbookFactory.create(in)) {
      Sheet sheet = workbook.getSheetAt(0);
      int lastRow = sheet.getLastRowNum();
      for (int i = 1; i <= lastRow; i++) {
        Row row = sheet.getRow(i);
        if (row == null || isEmptyRow(row)) {
          continue;
        }
        try {
          Customer customer = parseRow(row);
          String key = customer.getName() + "|" + customer.getCompany();
          if (!seen.add(key)) {
            throw new IllegalArgumentException("与文件内第 " + i + " 行重复");
          }
          if (existsInDb(customer.getName(), customer.getCompany())) {
            throw new IllegalArgumentException("客户已存在（相同名称与公司）");
          }
          customer.setStatus("ACTIVE");
          customer.setCreatedBy(SecurityUtil.currentUserId());
          batch.add(customer);
          result.setSuccessCount(result.getSuccessCount() + 1);
        } catch (Exception ex) {
          result.setFailureCount(result.getFailureCount() + 1);
          result.getFailures().add(new ImportResult.ImportFailure(i + 1, ex.getMessage()));
        }
      }
      insertInBatches(batch);
      auditService.record("IMPORT", "CUSTOMER", null, "批量导入客户：" + result.getSuccessCount() + " 成功");
    } catch (IOException ex) {
      log.error("Failed to parse import file", ex);
      throw new IllegalArgumentException("导入文件解析失败：" + ex.getMessage());
    }
    return result;
  }

  /** 按当前筛选条件导出客户为 .xlsx 字节流。 */
  public byte[] exportCustomers(String keyword, String status) {
    LambdaQueryWrapper<Customer> qw = new LambdaQueryWrapper<>();
    if (StringUtils.hasText(keyword)) {
      String kw = keyword.trim();
      qw.and(
          w ->
              w.like(Customer::getName, kw)
                  .or()
                  .like(Customer::getCompany, kw)
                  .or()
                  .like(Customer::getContactPerson, kw)
                  .or()
                  .like(Customer::getPhone, kw));
    }
    if (StringUtils.hasText(status)) {
      qw.eq(Customer::getStatus, status.trim());
    }
    qw.orderByDesc(Customer::getId);
    List<Customer> customers = customerMapper.selectList(qw);
    // 102：内置字段掩码走与出参收口点、ExportExecutor 同一个判据源（列在、格空）
    Set<String> hidden =
        fieldMaskPlanner.plan(fieldMaskPlanner.currentRole(), BuiltinFieldRegistry.ENTITY_CUSTOMER);
    try (Workbook workbook = new XSSFWorkbook();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      Sheet sheet = workbook.createSheet("客户");
      writeHeader(sheet);
      int rowIndex = 1;
      for (Customer c : customers) {
        Row row = sheet.createRow(rowIndex++);
        row.createCell(0).setCellValue(c.getName());
        row.createCell(1).setCellValue(c.getCompany());
        row.createCell(2).setCellValue(cell(hidden, "contactPerson", c.getContactPerson()));
        row.createCell(3).setCellValue(cell(hidden, "phone", c.getPhone()));
        row.createCell(4).setCellValue(cell(hidden, "email", c.getEmail()));
        row.createCell(5).setCellValue(cell(hidden, "address", c.getAddress()));
        row.createCell(6).setCellValue(cell(hidden, "remark", c.getRemark()));
      }
      workbook.write(out);
      auditService.record("EXPORT", "CUSTOMER", null, "导出客户：" + customers.size() + " 条");
      return out.toByteArray();
    } catch (IOException ex) {
      throw new IllegalStateException("导出失败", ex);
    }
  }

  /** 生成导入模板（表头 + 示例行）。 */
  public byte[] generateTemplate() {
    try (Workbook workbook = new XSSFWorkbook();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      Sheet sheet = workbook.createSheet("客户导入模板");
      writeHeader(sheet);
      Row example = sheet.createRow(1);
      example.createCell(0).setCellValue("张三");
      example.createCell(1).setCellValue("XX 科技");
      example.createCell(2).setCellValue("张三");
      example.createCell(3).setCellValue("13800000000");
      example.createCell(4).setCellValue("z@example.com");
      example.createCell(5).setCellValue("北京");
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

  private void writeHeader(Sheet sheet) {
    Row header = sheet.createRow(0);
    for (int i = 0; i < HEADERS.length; i++) {
      header.createCell(i).setCellValue(HEADERS[i]);
    }
  }

  private Customer parseRow(Row row) {
    String name = cellString(row, 0);
    String company = cellString(row, 1);
    if (!StringUtils.hasText(name)) {
      throw new IllegalArgumentException("客户名称不能为空");
    }
    if (!StringUtils.hasText(company)) {
      throw new IllegalArgumentException("公司不能为空");
    }
    String phone = cellString(row, 3);
    if (StringUtils.hasText(phone) && !phone.matches("^[0-9+\\-() ]{5,30}$")) {
      throw new IllegalArgumentException("电话号码格式不正确");
    }
    String email = cellString(row, 4);
    if (StringUtils.hasText(email) && !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
      throw new IllegalArgumentException("邮箱格式不正确");
    }
    Customer customer = new Customer();
    customer.setName(name.trim());
    customer.setCompany(company.trim());
    customer.setContactPerson(trimToNull(cellString(row, 2)));
    customer.setPhone(trimToNull(phone));
    customer.setEmail(trimToNull(email));
    customer.setAddress(trimToNull(cellString(row, 5)));
    customer.setRemark(trimToNull(cellString(row, 6)));
    return customer;
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

  private boolean existsInDb(String name, String company) {
    Long count =
        customerMapper.selectCount(
            new LambdaQueryWrapper<Customer>()
                .eq(Customer::getName, name.trim())
                .eq(Customer::getCompany, company.trim()));
    return count != null && count > 0;
  }

  private void insertInBatches(List<Customer> customers) {
    for (int i = 0; i < customers.size(); i += BATCH_SIZE) {
      int end = Math.min(i + BATCH_SIZE, customers.size());
      List<Customer> sub = customers.subList(i, end);
      for (Customer c : sub) {
        customerMapper.insert(c);
      }
    }
  }

  private String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  /** 102：内置字段列——被掩码 ⇒ 空串（列在、格空，照自定义字段的既有先例）。 */
  private String cell(Set<String> hidden, String fieldKey, String value) {
    return hidden.contains(fieldKey) ? "" : nullToEmpty(value);
  }

  private String trimToNull(String value) {
    return StringUtils.hasText(value) ? value.trim() : null;
  }
}
