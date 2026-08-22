package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crm.dto.customer.ImportResult;
import com.crm.entity.Lead;
import com.crm.repository.LeadMapper;
import com.crm.security.SecurityUtil;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
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

/** 线索 Excel 导入导出服务（FR-L11，复用客户模块 Excel 模式）。 */
@Service
public class LeadExcelService {

  private static final Logger log = LoggerFactory.getLogger(LeadExcelService.class);
  private static final int BATCH_SIZE = 500;
  private static final String[] HEADERS = {"姓名", "公司", "职位", "电话", "邮箱", "来源", "评分", "备注"};
  private static final Set<String> SOURCES =
      Set.of("WEBSITE", "AD", "EXHIBITION", "REFERRAL", "COLD_CALL", "OTHER");

  private final LeadMapper leadMapper;
  private final AuditService auditService;

  public LeadExcelService(LeadMapper leadMapper, AuditService auditService) {
    this.leadMapper = leadMapper;
    this.auditService = auditService;
  }

  /** 导入：逐行校验（姓名/公司必填、电话邮箱格式、来源枚举、评分 0-100），按 500 行分批插入。 */
  @Transactional
  public ImportResult importLeads(InputStream in) {
    ImportResult result = new ImportResult();
    List<Lead> batch = new ArrayList<>();
    try (Workbook workbook = WorkbookFactory.create(in)) {
      Sheet sheet = workbook.getSheetAt(0);
      int lastRow = sheet.getLastRowNum();
      for (int i = 1; i <= lastRow; i++) {
        Row row = sheet.getRow(i);
        if (row == null || isEmptyRow(row)) {
          continue;
        }
        try {
          Lead lead = parseRow(row);
          lead.setStatus("NEW");
          lead.setScore(lead.getScore() == null ? 0 : lead.getScore());
          lead.setCreatedBy(SecurityUtil.currentUserId());
          batch.add(lead);
          result.setSuccessCount(result.getSuccessCount() + 1);
        } catch (Exception ex) {
          result.setFailureCount(result.getFailureCount() + 1);
          result.getFailures().add(new ImportResult.ImportFailure(i + 1, ex.getMessage()));
        }
      }
      insertInBatches(batch);
      auditService.record("IMPORT", "LEAD", null, "批量导入线索：" + result.getSuccessCount() + " 成功");
    } catch (IOException ex) {
      log.error("Failed to parse lead import file", ex);
      throw new IllegalArgumentException("导入文件解析失败：" + ex.getMessage());
    }
    return result;
  }

  /** 按筛选条件导出线索为 .xlsx 字节流。 */
  public byte[] exportLeads(String keyword, String status, String source) {
    LambdaQueryWrapper<Lead> qw = new LambdaQueryWrapper<>();
    if (StringUtils.hasText(keyword)) {
      String kw = keyword.trim();
      qw.and(
          w ->
              w.like(Lead::getName, kw)
                  .or()
                  .like(Lead::getCompany, kw)
                  .or()
                  .like(Lead::getTitle, kw)
                  .or()
                  .like(Lead::getPhone, kw)
                  .or()
                  .like(Lead::getEmail, kw));
    }
    if (StringUtils.hasText(status)) {
      qw.eq(Lead::getStatus, status.trim());
    }
    if (StringUtils.hasText(source)) {
      qw.eq(Lead::getSource, source.trim());
    }
    qw.orderByDesc(Lead::getId);
    List<Lead> leads = leadMapper.selectList(qw);
    try (Workbook workbook = new XSSFWorkbook();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      Sheet sheet = workbook.createSheet("线索");
      writeHeader(sheet);
      int rowIndex = 1;
      for (Lead lead : leads) {
        Row row = sheet.createRow(rowIndex++);
        row.createCell(0).setCellValue(nullToEmpty(lead.getName()));
        row.createCell(1).setCellValue(nullToEmpty(lead.getCompany()));
        row.createCell(2).setCellValue(nullToEmpty(lead.getTitle()));
        row.createCell(3).setCellValue(nullToEmpty(lead.getPhone()));
        row.createCell(4).setCellValue(nullToEmpty(lead.getEmail()));
        row.createCell(5).setCellValue(nullToEmpty(lead.getSource()));
        row.createCell(6).setCellValue(lead.getScore() == null ? 0 : lead.getScore());
        row.createCell(7).setCellValue(nullToEmpty(lead.getRemark()));
      }
      workbook.write(out);
      auditService.record("EXPORT", "LEAD", null, "导出线索：" + leads.size() + " 条");
      return out.toByteArray();
    } catch (IOException ex) {
      throw new IllegalStateException("导出失败", ex);
    }
  }

  /** 生成导入模板（表头 + 示例行）。 */
  public byte[] generateTemplate() {
    try (Workbook workbook = new XSSFWorkbook();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      Sheet sheet = workbook.createSheet("线索导入模板");
      writeHeader(sheet);
      Row example = sheet.createRow(1);
      example.createCell(0).setCellValue("张三");
      example.createCell(1).setCellValue("Acme 科技");
      example.createCell(2).setCellValue("采购经理");
      example.createCell(3).setCellValue("13800000000");
      example.createCell(4).setCellValue("zhangsan@example.com");
      example.createCell(5).setCellValue("EXHIBITION");
      example.createCell(6).setCellValue(80);
      example.createCell(7).setCellValue("展会收集，意向明确");
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

  private Lead parseRow(Row row) {
    String name = cellString(row, 0);
    String company = cellString(row, 1);
    if (!StringUtils.hasText(name)) {
      throw new IllegalArgumentException("姓名不能为空");
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
    String source = cellString(row, 5);
    if (StringUtils.hasText(source)) {
      String normalized = source.trim().toUpperCase();
      if (!SOURCES.contains(normalized)) {
        throw new IllegalArgumentException("来源不合法（WEBSITE/AD/EXHIBITION/REFERRAL/COLD_CALL/OTHER）");
      }
      source = normalized;
    } else {
      source = "OTHER";
    }
    String scoreStr = cellString(row, 6);
    Integer score = 0;
    if (StringUtils.hasText(scoreStr)) {
      try {
        score = Integer.parseInt(scoreStr.trim());
      } catch (NumberFormatException ex) {
        throw new IllegalArgumentException("评分须为 0-100 的整数");
      }
      if (score < 0 || score > 100) {
        throw new IllegalArgumentException("评分须为 0-100 的整数");
      }
    }
    Lead lead = new Lead();
    lead.setName(name.trim());
    lead.setCompany(company.trim());
    lead.setTitle(trimToNull(cellString(row, 2)));
    lead.setPhone(trimToNull(phone));
    lead.setEmail(trimToNull(email));
    lead.setSource(source);
    lead.setScore(score);
    lead.setRemark(trimToNull(cellString(row, 7)));
    return lead;
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

  private void insertInBatches(List<Lead> leads) {
    for (int i = 0; i < leads.size(); i += BATCH_SIZE) {
      int end = Math.min(i + BATCH_SIZE, leads.size());
      for (Lead lead : leads.subList(i, end)) {
        leadMapper.insert(lead);
      }
    }
  }

  private String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  private String trimToNull(String value) {
    return StringUtils.hasText(value) ? value.trim() : null;
  }
}
