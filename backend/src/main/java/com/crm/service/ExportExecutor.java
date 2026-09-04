package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.entity.Customer;
import com.crm.entity.ExportJob;
import com.crm.entity.Lead;
import com.crm.entity.Opportunity;
import com.crm.entity.Ticket;
import com.crm.repository.CustomerMapper;
import com.crm.repository.ExportJobMapper;
import com.crm.repository.LeadMapper;
import com.crm.repository.OpportunityMapper;
import com.crm.repository.TicketMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

/** 导出执行器（016，FR-S08~S10）：后台生成 xlsx（含自定义字段列）。 */
@Service
public class ExportExecutor {

  private static final String[] LEAD_HEADERS = {"姓名", "公司", "职位", "电话", "邮箱", "来源", "评分", "状态"};
  private static final String[] CUSTOMER_HEADERS = {"客户名称", "公司", "联系人", "电话", "邮箱", "地址", "状态"};
  private static final String[] OPPORTUNITY_HEADERS = {"商机名称", "客户", "金额", "状态"};
  private static final String[] TICKET_HEADERS = {"标题", "客户", "优先级", "状态", "处理人", "创建时间"};

  private final ExportJobMapper exportJobMapper;
  private final LeadMapper leadMapper;
  private final CustomerMapper customerMapper;
  private final OpportunityMapper opportunityMapper;
  private final TicketMapper ticketMapper;
  private final CustomFieldService customFieldService;
  private final DataPermissionService dataPermissionService;
  private final String exportDir;

  public ExportExecutor(
      ExportJobMapper exportJobMapper,
      LeadMapper leadMapper,
      CustomerMapper customerMapper,
      OpportunityMapper opportunityMapper,
      TicketMapper ticketMapper,
      CustomFieldService customFieldService,
      DataPermissionService dataPermissionService) {
    this.exportJobMapper = exportJobMapper;
    this.leadMapper = leadMapper;
    this.customerMapper = customerMapper;
    this.opportunityMapper = opportunityMapper;
    this.ticketMapper = ticketMapper;
    this.customFieldService = customFieldService;
    this.dataPermissionService = dataPermissionService;
    this.exportDir = System.getProperty("user.dir") + "/backend/contract-files/exports";
  }

  /** 063(安全加固)：非 ADMIN 的可见 owner 集（null=不过滤）。 */
  private List<Long> visibleOwnersOrNull() {
    var principal = com.crm.security.SecurityUtil.currentPrincipal();
    if (principal == null || "ADMIN".equals(principal.role())) {
      return null;
    }
    return dataPermissionService.resolveVisibleOwnerIds(principal.userId());
  }

  /** 063(安全加固)：非 ADMIN 脱敏手机/邮箱。 */
  private String mask(String phoneOrEmail) {
    var principal = com.crm.security.SecurityUtil.currentPrincipal();
    if (principal != null && !"ADMIN".equals(principal.role())) {
      if (phoneOrEmail != null && phoneOrEmail.contains("@")) {
        return com.crm.common.MaskingUtil.maskEmail(phoneOrEmail);
      }
      return com.crm.common.MaskingUtil.maskPhone(phoneOrEmail);
    }
    return phoneOrEmail;
  }

  /** 执行导出任务（异步线程池调用）。 */
  public void execute(Long jobId) {
    ExportJob job = exportJobMapper.selectById(jobId);
    if (job == null) {
      return;
    }
    job.setStatus(ExportJobService.STATUS_RUNNING);
    exportJobMapper.updateById(job);
    try {
      Path dir = Paths.get(exportDir);
      Files.createDirectories(dir);
      String fileName =
          job.getExportType().toLowerCase()
              + "_"
              + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
              + ".xlsx";
      Path file = dir.resolve(fileName);

      byte[] content = buildContent(job);
      Files.write(file, content);

      job.setStatus(ExportJobService.STATUS_DONE);
      job.setFilePath(file.toString());
      job.setRowCount((long) estimateRowCount(job));
      job.setCompletedAt(LocalDateTime.now());
      exportJobMapper.updateById(job);
    } catch (Exception ex) {
      job.setStatus(ExportJobService.STATUS_FAILED);
      job.setErrorMessage(
          ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
      job.setCompletedAt(LocalDateTime.now());
      exportJobMapper.updateById(job);
    }
  }

  private byte[] buildContent(ExportJob job) throws IOException {
    try (Workbook workbook = new XSSFWorkbook();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
      switch (job.getExportType()) {
        case "LEAD":
          writeLeads(workbook.createSheet("线索"), job);
          break;
        case "CUSTOMER":
          writeCustomers(workbook.createSheet("客户"), job);
          break;
        case "OPPORTUNITY":
          writeOpportunities(workbook.createSheet("商机"), job);
          break;
        case "TICKET":
          writeTickets(workbook.createSheet("工单"), job);
          break;
        default:
          throw new BusinessException(ErrorCode.EXPORT_TYPE_INVALID);
      }
      workbook.write(out);
      return out.toByteArray();
    }
  }

  private void writeLeads(Sheet sheet, ExportJob job) {
    LambdaQueryWrapper<Lead> qw =
        new LambdaQueryWrapper<Lead>().orderByDesc(Lead::getId).last("LIMIT 5000");
    List<Long> visibleOwners = visibleOwnersOrNull();
    if (visibleOwners != null) {
      if (visibleOwners.isEmpty()) {
        return; // 无可见数据
      }
      qw.in(Lead::getOwnerId, visibleOwners);
    }
    List<Lead> leads = leadMapper.selectList(qw);
    List<com.crm.dto.customfield.CustomFieldResponse> cfDefs =
        customFieldService.listByEntity("LEAD");
    int colCount = writeHeader(sheet, LEAD_HEADERS, cfDefs);
    int rowIndex = 1;
    for (Lead lead : leads) {
      Row row = sheet.createRow(rowIndex++);
      row.createCell(0).setCellValue(nvl(lead.getName()));
      row.createCell(1).setCellValue(nvl(lead.getCompany()));
      row.createCell(2).setCellValue(nvl(lead.getTitle()));
      row.createCell(3).setCellValue(nvl(mask(lead.getPhone())));
      row.createCell(4).setCellValue(nvl(mask(lead.getEmail())));
      row.createCell(5).setCellValue(nvl(lead.getSource()));
      row.createCell(6).setCellValue(lead.getScore() == null ? 0 : lead.getScore());
      row.createCell(7).setCellValue(nvl(lead.getStatus()));
      writeCustomFields(row, colCount, "LEAD", lead.getId(), cfDefs);
    }
  }

  private void writeCustomers(Sheet sheet, ExportJob job) {
    LambdaQueryWrapper<Customer> qw =
        new LambdaQueryWrapper<Customer>().orderByDesc(Customer::getId).last("LIMIT 5000");
    List<Long> visibleOwners = visibleOwnersOrNull();
    if (visibleOwners != null) {
      if (visibleOwners.isEmpty()) {
        return;
      }
      qw.in(Customer::getOwnerId, visibleOwners);
    }
    List<Customer> customers = customerMapper.selectList(qw);
    List<com.crm.dto.customfield.CustomFieldResponse> cfDefs =
        customFieldService.listByEntity("CUSTOMER");
    int colCount = writeHeader(sheet, CUSTOMER_HEADERS, cfDefs);
    int rowIndex = 1;
    for (Customer c : customers) {
      Row row = sheet.createRow(rowIndex++);
      row.createCell(0).setCellValue(nvl(c.getName()));
      row.createCell(1).setCellValue(nvl(c.getCompany()));
      row.createCell(2).setCellValue(nvl(c.getContactPerson()));
      row.createCell(3).setCellValue(nvl(mask(c.getPhone())));
      row.createCell(4).setCellValue(nvl(mask(c.getEmail())));
      row.createCell(5).setCellValue(nvl(c.getAddress()));
      row.createCell(6).setCellValue(nvl(c.getStatus()));
      writeCustomFields(row, colCount, "CUSTOMER", c.getId(), cfDefs);
    }
  }

  private void writeOpportunities(Sheet sheet, ExportJob job) {
    List<Opportunity> opportunities =
        opportunityMapper.selectList(
            new LambdaQueryWrapper<Opportunity>()
                .orderByDesc(Opportunity::getId)
                .last("LIMIT 5000"));
    List<com.crm.dto.customfield.CustomFieldResponse> cfDefs =
        customFieldService.listByEntity("OPPORTUNITY");
    int colCount = writeHeader(sheet, OPPORTUNITY_HEADERS, cfDefs);
    int rowIndex = 1;
    for (Opportunity o : opportunities) {
      Row row = sheet.createRow(rowIndex++);
      row.createCell(0).setCellValue(nvl(o.getName()));
      row.createCell(1)
          .setCellValue(o.getCustomerId() == null ? "" : String.valueOf(o.getCustomerId()));
      row.createCell(2)
          .setCellValue(o.getExpectedAmountMax() == null ? 0 : o.getExpectedAmountMax());
      row.createCell(3).setCellValue(nvl(o.getStatus()));
      writeCustomFields(row, colCount, "OPPORTUNITY", o.getId(), cfDefs);
    }
  }

  private void writeTickets(Sheet sheet, ExportJob job) {
    List<Ticket> tickets =
        ticketMapper.selectList(
            new LambdaQueryWrapper<Ticket>().orderByDesc(Ticket::getId).last("LIMIT 5000"));
    List<com.crm.dto.customfield.CustomFieldResponse> cfDefs =
        customFieldService.listByEntity("TICKET");
    int colCount = writeHeader(sheet, TICKET_HEADERS, cfDefs);
    int rowIndex = 1;
    for (Ticket t : tickets) {
      Row row = sheet.createRow(rowIndex++);
      row.createCell(0).setCellValue(nvl(t.getTitle()));
      row.createCell(1)
          .setCellValue(t.getCustomerId() == null ? "" : String.valueOf(t.getCustomerId()));
      row.createCell(2).setCellValue(nvl(t.getPriority()));
      row.createCell(3).setCellValue(nvl(t.getStatus()));
      row.createCell(4)
          .setCellValue(t.getAssigneeId() == null ? "" : String.valueOf(t.getAssigneeId()));
      row.createCell(5).setCellValue(t.getCreatedAt() == null ? "" : t.getCreatedAt().toString());
      writeCustomFields(row, colCount, "TICKET", t.getId(), cfDefs);
    }
  }

  /** 追加自定义字段列：定义名作为列头，值按 entityId 匹配。 */
  private void writeCustomFields(
      Row row,
      int colCount,
      String entityType,
      Long entityId,
      List<com.crm.dto.customfield.CustomFieldResponse> cfDefs) {
    if (cfDefs.isEmpty()) {
      return;
    }
    Map<Long, List<com.crm.dto.customfield.CustomFieldValueDTO>> values =
        customFieldService.readValuesBatch(entityType, List.of(entityId));
    List<com.crm.dto.customfield.CustomFieldValueDTO> rowValues =
        values.getOrDefault(entityId, List.of());
    Map<Long, String> valueByField =
        rowValues.stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    com.crm.dto.customfield.CustomFieldValueDTO::getFieldId,
                    v -> v.getValue() == null ? "" : v.getValue(),
                    (a, b) -> a));
    for (int i = 0; i < cfDefs.size(); i++) {
      Long fieldId = cfDefs.get(i).getId();
      row.createCell(colCount + i).setCellValue(valueByField.getOrDefault(fieldId, ""));
    }
  }

  private int writeHeader(
      Sheet sheet, String[] headers, List<com.crm.dto.customfield.CustomFieldResponse> cfDefs) {
    Row header = sheet.createRow(0);
    int col = 0;
    for (String h : headers) {
      header.createCell(col++).setCellValue(h);
    }
    for (com.crm.dto.customfield.CustomFieldResponse cf : cfDefs) {
      header.createCell(col++).setCellValue(cf.getName());
    }
    return col;
  }

  private int estimateRowCount(ExportJob job) {
    switch (job.getExportType()) {
      case "LEAD":
        return leadMapper.selectCount(new LambdaQueryWrapper<Lead>()).intValue();
      case "CUSTOMER":
        return customerMapper.selectCount(new LambdaQueryWrapper<Customer>()).intValue();
      case "OPPORTUNITY":
        return opportunityMapper.selectCount(new LambdaQueryWrapper<Opportunity>()).intValue();
      case "TICKET":
        return ticketMapper.selectCount(new LambdaQueryWrapper<Ticket>()).intValue();
      default:
        return 0;
    }
  }

  private String nvl(String s) {
    return s == null ? "" : s;
  }

  /** 定时导出复用：根据实体类型和格式生成导出文件。 */
  public String executeExport(String entityType, String filterConditions, String exportFormat) {
    String[] result = executeExportWithRowCount(entityType, filterConditions, exportFormat);
    return result[0];
  }

  /** 定时导出复用：返回文件路径和行数。 */
  public String[] executeExportWithRowCount(String entityType, String filterConditions, String exportFormat) {
    try {
      // 创建临时 ExportJob 用于复用现有导出逻辑
      ExportJob tempJob = new ExportJob();
      tempJob.setExportType(entityType.toUpperCase());
      tempJob.setExportFormat(exportFormat.toUpperCase());
      tempJob.setStatus(ExportJobService.STATUS_RUNNING);
      tempJob.setCreatedAt(LocalDateTime.now());

      Path dir = Paths.get(exportDir);
      Files.createDirectories(dir);
      String fileName =
          entityType.toLowerCase()
              + "_scheduled_"
              + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
              + "."
              + (exportFormat.toUpperCase().equals("CSV") ? "csv" : "xlsx");
      Path file = dir.resolve(fileName);

      byte[] content = buildContent(tempJob);
      Files.write(file, content);

      int rowCount = countRows(tempJob);
      return new String[] { file.toString(), String.valueOf(rowCount) };
    } catch (IOException e) {
      throw new BusinessException(ErrorCode.EXPORT_FAILED, "Export failed: " + e.getMessage());
    }
  }
}
