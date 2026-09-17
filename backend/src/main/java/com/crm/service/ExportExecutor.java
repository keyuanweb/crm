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
import com.crm.support.FieldMaskPlanner;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.poi.ss.usermodel.Cell;
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
  private final FieldMaskPlanner fieldMaskPlanner;
  private final String exportDir;

  public ExportExecutor(
      ExportJobMapper exportJobMapper,
      LeadMapper leadMapper,
      CustomerMapper customerMapper,
      OpportunityMapper opportunityMapper,
      TicketMapper ticketMapper,
      CustomFieldService customFieldService,
      DataPermissionService dataPermissionService,
      FieldMaskPlanner fieldMaskPlanner) {
    this.exportJobMapper = exportJobMapper;
    this.leadMapper = leadMapper;
    this.customerMapper = customerMapper;
    this.opportunityMapper = opportunityMapper;
    this.ticketMapper = ticketMapper;
    this.customFieldService = customFieldService;
    this.dataPermissionService = dataPermissionService;
    this.fieldMaskPlanner = fieldMaskPlanner;
    this.exportDir = System.getProperty("user.dir") + "/backend/contract-files/exports";
  }

  /**
   * 102：该实体在本角色下要掩码的内置字段（HIDDEN）。
   *
   * <p>与出参收口点共用 {@link FieldMaskPlanner} 这一个判据源——分开算就会出现「列表页不显示、导出里还在」。
   */
  private Set<String> hiddenKeys(String roleCode, String entityType) {
    return fieldMaskPlanner.plan(roleCode, entityType);
  }

  /**
   * 文本列：被掩码 ⇒ **空串**（列仍在）。这是 016 自定义字段 HIDDEN 的既有先例——过滤版 {@code readValuesBatch} 里根本没有那些字段，格子取
   * {@code getOrDefault(fieldId, "")} 即空串。不发明第二种读法。
   */
  private String cell(Set<String> hidden, String fieldKey, String value) {
    return hidden.contains(fieldKey) ? "" : nvl(value);
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
    // 102：本方法跑在异步线程里，但 ExportJobService 用的是 DelegatingSecurityContextExecutorService，
    // 主体被带进来了 ⇒ 这里的角色就是提交导出那个人的角色（不是 null 回落 ADMIN）
    String roleCode = fieldMaskPlanner.currentRole();
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

      byte[] content = buildContent(job, roleCode);
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

  private byte[] buildContent(ExportJob job, String roleCode) throws IOException {
    try (Workbook workbook = new XSSFWorkbook();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
      switch (job.getExportType()) {
        case "LEAD":
          writeLeads(workbook.createSheet("线索"), job);
          break;
        case "CUSTOMER":
          writeCustomers(workbook.createSheet("客户"), job, roleCode);
          break;
        case "OPPORTUNITY":
          writeOpportunities(workbook.createSheet("商机"), job, roleCode);
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

  private void writeCustomers(Sheet sheet, ExportJob job, String roleCode) {
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
    // 102：内置字段掩码。按 (角色, 实体) 算一次，不按行算——列表有 N 行就查 N 次库
    Set<String> hidden = hiddenKeys(roleCode, "CUSTOMER");
    int rowIndex = 1;
    for (Customer c : customers) {
      Row row = sheet.createRow(rowIndex++);
      row.createCell(0).setCellValue(nvl(c.getName()));
      row.createCell(1).setCellValue(nvl(c.getCompany()));
      row.createCell(2).setCellValue(cell(hidden, "contactPerson", c.getContactPerson()));
      row.createCell(3).setCellValue(cell(hidden, "phone", mask(c.getPhone())));
      row.createCell(4).setCellValue(cell(hidden, "email", mask(c.getEmail())));
      row.createCell(5).setCellValue(cell(hidden, "address", c.getAddress()));
      row.createCell(6).setCellValue(cell(hidden, "status", c.getStatus()));
      writeCustomFields(row, colCount, "CUSTOMER", c.getId(), cfDefs);
    }
  }

  private void writeOpportunities(Sheet sheet, ExportJob job, String roleCode) {
    List<Opportunity> opportunities =
        opportunityMapper.selectList(
            new LambdaQueryWrapper<Opportunity>()
                .orderByDesc(Opportunity::getId)
                .last("LIMIT 5000"));
    List<com.crm.dto.customfield.CustomFieldResponse> cfDefs =
        customFieldService.listByEntity("OPPORTUNITY");
    int colCount = writeHeader(sheet, OPPORTUNITY_HEADERS, cfDefs);
    // 102：⚠️ 「金额」列表头写的是 max（见 OPPORTUNITY_HEADERS 与下面第 2 列），故配 HIDDEN 的
    // `expectedAmountMin` 只影响 JSON 出参，**不影响本表**——这一点如实写在 102 的 research.md 里
    Set<String> hidden = hiddenKeys(roleCode, "OPPORTUNITY");
    int rowIndex = 1;
    for (Opportunity o : opportunities) {
      Row row = sheet.createRow(rowIndex++);
      row.createCell(0).setCellValue(nvl(o.getName()));
      row.createCell(1)
          .setCellValue(o.getCustomerId() == null ? "" : String.valueOf(o.getCustomerId()));
      Cell amount = row.createCell(2);
      if (hidden.contains("expectedAmountMax")) {
        amount.setCellValue(""); // 102：列在、格空
      } else {
        amount.setCellValue(o.getExpectedAmountMax() == null ? 0 : o.getExpectedAmountMax());
      }
      row.createCell(3).setCellValue(cell(hidden, "status", o.getStatus()));
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

  /** 统计导出行数（用于定时导出）。 */
  private int countRows(ExportJob job) {
    return estimateRowCount(job);
  }

  private String nvl(String s) {
    return s == null ? "" : s;
  }

  /** 定时导出复用：根据实体类型和格式生成导出文件。 */
  public String executeExport(String entityType, String filterConditions, String exportFormat) {
    String[] result =
        executeExportWithRowCount(
            entityType, filterConditions, exportFormat, fieldMaskPlanner.currentRole());
    return result[0];
  }

  /**
   * 定时导出复用：返回文件路径和行数。
   *
   * <p><b>102：掩码按调用方给的角色算。</b>调度线程**没有请求主体**（063 的 {@code mask()} 与行级过滤在这个入口
   * fail-open），故角色不能在这里现取——由 {@code ScheduledExportServiceImpl} 传**任务业主**的角色进来。这条路径上 字段掩码与 063
   * 的两处加固是**混合态**：前者按业主生效，后者仍 fail-open，如实登记在 102 的 research.md。
   */
  public String[] executeExportWithRowCount(
      String entityType, String filterConditions, String exportFormat, String roleCode) {
    try {
      // 创建临时 ExportJob 用于复用现有导出逻辑
      // （不再设置 exportFormat：ExportJob 已无该字段，见 entity/ExportJob.java 的说明。
      //  此处的 exportFormat 参数仍用于下方决定文件扩展名与内容格式。）
      ExportJob tempJob = new ExportJob();
      tempJob.setExportType(entityType.toUpperCase());
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

      byte[] content = buildContent(tempJob, roleCode);
      Files.write(file, content);

      int rowCount = countRows(tempJob);
      return new String[] {file.toString(), String.valueOf(rowCount)};
    } catch (IOException e) {
      throw new BusinessException(ErrorCode.EXPORT_FAILED, "Export failed: " + e.getMessage());
    }
  }
}
