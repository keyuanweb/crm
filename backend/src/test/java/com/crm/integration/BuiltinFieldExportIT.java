package com.crm.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.crm.AbstractIntegrationTest;
import com.crm.integration.BuiltinFieldPermissionFixture.Res;
import com.crm.model.entity.ScheduledExport;
import com.crm.repository.ScheduledExportRepository;
import com.crm.security.SecurityUtil;
import com.crm.service.ScheduledExportService;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

/**
 * T15/T16：内置字段掩码在 **xlsx** 上的两处落点（102）。
 *
 * <p>这两条各守着一条**只有它能守**的链：
 *
 * <ul>
 *   <li>{@link #hiddenPhoneColumnIsEmptyInCustomerExport}：唯一覆盖 xlsx 的用例。只修 JSON 收口点、忘了导出，
 *       在页面上完全看不出来（导出的人只看到一份"正常"的文件），而文件一旦外发就收不回来。
 *   <li>{@link #scheduledExportMasksByOwnerRole}：唯一覆盖**无请求主体**路径的用例。定时导出跑在调度线程上， {@code
 *       FieldMaskPlanner.currentRole()} 在那里回落 ADMIN ⇒ 若实现改成"现取角色"，定时导出会成为一个稳定泄漏口，
 *       而它跟手动导出共用同一段代码、看不出差别。
 * </ul>
 *
 * <p>两条都读**真文件/真字节流**（POI 打开 xlsx），不读中间变量——「列在、格空」是本批对导出的完整承诺， 只断言"没有真值"会让"整张表是空的"也通过。
 */
class BuiltinFieldExportIT extends AbstractIntegrationTest {

  private static final String CUSTOMER_NAME = "导出客户甲";
  private static final String PHONE = "13900001111";
  private static final String EMAIL = "export1@example.com";
  private static final String ADMIN_CUSTOMER_NAME = "导出客户乙";
  private static final String ADMIN_PHONE = "13900002222";

  /** CUSTOMER 表的「电话」在第 3 列（0 基）——{@code CustomerExcelService} 与 {@code ExportExecutor} 同序。 */
  private static final int COL_PHONE = 3;

  private static final int COL_EMAIL = 4;

  @Autowired private ScheduledExportService scheduledExportService;
  @Autowired private ScheduledExportRepository scheduledExportRepository;

  private BuiltinFieldPermissionFixture fixture;
  private String token;

  @BeforeEach
  void setUp() throws Exception {
    fixture = new BuiltinFieldPermissionFixture(mockMvc, objectMapper, loginAndGetToken());
    fixture.ensureRole();
    token = fixture.createUser("fls_export_").token();
    // 归 IT 角色的客户（导出要过数据范围）+ 一个 ADMIN 建的客户（证明"行确实写进去了"而不是整表空）
    fixture.createCustomer(token, CUSTOMER_NAME, PHONE, EMAIL);
    fixture.createCustomer(
        fixture.adminToken(), ADMIN_CUSTOMER_NAME, ADMIN_PHONE, "export2@example.com");
  }

  @Test
  @DisplayName("T15 客户 xlsx 导出：HIDDEN 的 phone 列存在但格空，ADMIN 同一导出有值")
  void hiddenPhoneColumnIsEmptyInCustomerExport() throws Exception {
    fixture.configureBuiltin("CUSTOMER", "phone", "HIDDEN");

    SheetView asRole = read(fixture.callBytes(token, HttpMethod.GET, "/api/v1/customers/export"));
    assertThat(asRole.headers()).hasSize(7);
    assertThat(asRole.headers().get(COL_PHONE)).as("列在：表头仍是「电话」，不是整列消失").isEqualTo("电话");
    List<String> row = asRole.row(CUSTOMER_NAME);
    assertThat(row.get(COL_PHONE)).as("HIDDEN 的内置字段在导出里不得出现（照自定义字段的「格空」先例）").isEmpty();
    assertThat(row.get(COL_EMAIL)).as("正对照：同行的邮箱没配权限，必须仍是真值").isEqualTo(EMAIL);

    SheetView asAdmin =
        read(fixture.callBytes(fixture.adminToken(), HttpMethod.GET, "/api/v1/customers/export"));
    assertThat(asAdmin.row(CUSTOMER_NAME).get(COL_PHONE)).as("ADMIN 不受掩码影响").isEqualTo(PHONE);
    assertThat(asAdmin.row(ADMIN_CUSTOMER_NAME).get(COL_PHONE))
        .as("正对照：ADMIN 的导出里另一行也在，证明表里确实有数据")
        .isEqualTo(ADMIN_PHONE);
  }

  @Test
  @DisplayName("T16 定时导出：调度线程上没有请求主体，仍按**任务业主**的角色掩码（不是回落 ADMIN）")
  void scheduledExportMasksByOwnerRole() throws Exception {
    fixture.configureBuiltin("CUSTOMER", "phone", "HIDDEN");
    long taskId = createScheduledExport();
    forceDue(taskId);

    // 前提断言：本用例的全部意义在于"没有请求主体"。若将来有人在带主体的上下文里跑它，这条会先红，
    // 而不是让 T16 变成一条被架空的用例（那时它连 ADMIN 分支都覆盖不到）
    assertThat(SecurityUtil.currentPrincipal())
        .as("前提：调度路径上不得有请求主体（有主体时 currentRole() 就不是回落值了）")
        .isNull();

    scheduledExportService.executePendingTasks();

    JsonNode executions =
        fixture
            .call(
                token, HttpMethod.GET, "/api/v1/scheduled-exports/" + taskId + "/executions", null)
            .body();
    assertThat(executions).as("执行记录应恰好一条：%s", executions).hasSize(1);
    JsonNode execution = executions.get(0);
    assertThat(execution.path("status").asText())
        .as("导出本身应成功（失败时 filePath 为空、下面的断言会以误导性的形态红）：%s", execution)
        .isEqualTo("SUCCESS");

    Path file = Path.of(execution.path("filePath").asText());
    assertThat(Files.exists(file)).as("执行记录给的文件路径应真实存在：%s", file).isTrue();
    SheetView view = read(Files.readAllBytes(file));
    assertThat(view.headers().get(COL_PHONE)).isEqualTo("电话");
    List<String> row = view.row(CUSTOMER_NAME);
    assertThat(row.get(COL_PHONE))
        .as("业主是 IT 角色（HIDDEN phone）⇒ 定时导出必须同样格空；若这里出现真值，说明实现取的是环境主体（回落 ADMIN）")
        .isEmpty();
    assertThat(row.get(COL_EMAIL)).as("正对照：本行是真实数据行，不是空表").isEqualTo(EMAIL);
  }

  // ===== 定时导出 =====

  /** 以 **IT 角色用户**身份建任务 ⇒ 任务业主就是它（业主角色是掩码的唯一依据）。 */
  private long createScheduledExport() throws Exception {
    Res res =
        fixture.call(
            token,
            HttpMethod.POST,
            "/api/v1/scheduled-exports",
            "{\"entityType\":\"CUSTOMER\",\"exportFormat\":\"XLSX\",\"cronExpression\":\"0 0 1 * *\"}");
    assertThat(res.status())
        .as("建定时导出任务应 200（该端点用 ResponseEntity.ok）：%s", res.body())
        .isEqualTo(200);
    return res.body().path("id").asLong();
  }

  /**
   * 把下次执行时间挪到过去，让任务「到期」。
   *
   * <p>不经 HTTP：{@code PUT /{id}/status} 只改状态，没有任何端点能改 {@code nextExecutionTime}——而 {@code
   * executePendingTasks()} 的选取判据正是它。直改库是在**不改产品行为**的前提下复现"任务到期"的唯一办法。
   */
  private void forceDue(long taskId) {
    ScheduledExport task = scheduledExportRepository.selectById(taskId);
    assertThat(task).as("任务应已入库").isNotNull();
    assertThat(task.getUserId()).as("任务业主必须是被测角色那个用户（掩码按业主角色算）").isNotNull();
    task.setNextExecutionTime(LocalDateTime.now().minusMinutes(1));
    task.setUpdatedAt(LocalDateTime.now());
    scheduledExportRepository.updateById(task);
  }

  // ===== xlsx 读取 =====

  /** 一张表的可读视图：表头 + 「第 0 列的值 → 该行」映射。 */
  private record SheetView(List<String> headers, Map<String, List<String>> rows) {

    /** 取一行；取不到即失败（取不到多半是数据范围把行挡掉了，不能当成"没有泄漏"）。 */
    List<String> row(String firstCell) {
      List<String> row = rows.get(firstCell);
      if (row == null) {
        throw new AssertionError("导出里没有第一列为 " + firstCell + " 的行：" + rows.keySet());
      }
      return row;
    }
  }

  private static SheetView read(byte[] xlsx) throws Exception {
    List<String> headers = new ArrayList<>();
    Map<String, List<String>> rows = new LinkedHashMap<>();
    try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(xlsx))) {
      Sheet sheet = workbook.getSheetAt(0);
      Row header = sheet.getRow(0);
      for (int c = 0; c < header.getLastCellNum(); c++) {
        headers.add(text(header.getCell(c)));
      }
      for (int i = 1; i <= sheet.getLastRowNum(); i++) {
        Row row = sheet.getRow(i);
        if (row == null) {
          continue;
        }
        List<String> cells = new ArrayList<>();
        for (int c = 0; c < headers.size(); c++) {
          cells.add(text(row.getCell(c)));
        }
        rows.put(cells.get(0), cells);
      }
    }
    return new SheetView(headers, rows);
  }

  private static String text(Cell cell) {
    return cell == null ? "" : cell.getStringCellValue();
  }
}
