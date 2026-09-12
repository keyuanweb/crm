package com.crm.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.dto.ScheduledExportExecutionResponse;
import com.crm.dto.ScheduledExportRequest;
import com.crm.dto.ScheduledExportResponse;
import com.crm.model.entity.ScheduledExport;
import com.crm.model.entity.ScheduledExportExecution;
import com.crm.repository.ScheduledExportExecutionRepository;
import com.crm.repository.ScheduledExportRepository;
import com.crm.security.JwtAuthFilter.CrmPrincipal;
import com.crm.security.SecurityUtil;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/** 定时导出 Service 单元测试（079-scheduled-export）。 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class ScheduledExportServiceTest {

  @Mock private ScheduledExportRepository scheduledExportRepository;
  @Mock private ScheduledExportExecutionRepository scheduledExportExecutionRepository;
  @Mock private EmailService emailService;
  @Mock private ExportExecutor exportExecutor;
  @Mock private AuditService auditService;

  private ScheduledExportServiceImpl scheduledExportService;

  private ScheduledExport sampleTask;
  private MockedStatic<SecurityUtil> securityUtilMock;

  @BeforeEach
  void setUp() {
    sampleTask = new ScheduledExport();
    sampleTask.setId(1L);
    sampleTask.setUserId(1L);
    sampleTask.setEntityType("CUSTOMER");
    sampleTask.setExportFormat("CSV");
    sampleTask.setCronExpression("0 0 2 * * ?");
    sampleTask.setStatus("ACTIVE");
    sampleTask.setNextExecutionTime(LocalDateTime.now().minusHours(1));
    sampleTask.setCreatedAt(LocalDateTime.now());
    sampleTask.setUpdatedAt(LocalDateTime.now());

    // Mock SecurityUtil for currentUserId() calls
    securityUtilMock = Mockito.mockStatic(SecurityUtil.class);
    securityUtilMock
        .when(SecurityUtil::currentPrincipal)
        .thenReturn(new CrmPrincipal(1L, "admin", "ADMIN"));

    // Manually inject mocks to ensure proper injection
    scheduledExportService =
        new ScheduledExportServiceImpl(
            scheduledExportRepository,
            scheduledExportExecutionRepository,
            emailService,
            exportExecutor,
            auditService,
            null // UserMapper not needed for these tests
            );
  }

  @AfterEach
  void tearDown() {
    if (securityUtilMock != null) {
      securityUtilMock.close();
    }
  }

  @Test
  @DisplayName("T018: createScheduledExport 创建定时导出任务")
  void createScheduledExport_shouldCreateAndReturnResponse() {
    // Given
    ScheduledExportRequest request = new ScheduledExportRequest();
    request.setEntityType("CUSTOMER");
    request.setExportFormat("CSV");
    request.setCronExpression("0 0 2 * * ?");

    // MyBatis-Plus 在真实库上会把自增主键回填进实体，mock 必须模拟这一步：
    // 服务返回的 id 与审计记录的 id 都取自 entity.getId()，只桩返回值不会让 id 出现。
    when(scheduledExportRepository.insert(any(ScheduledExport.class)))
        .thenAnswer(
            inv -> {
              inv.getArgument(0, ScheduledExport.class).setId(1L);
              return 1;
            });

    // When
    ScheduledExportResponse response = scheduledExportService.createScheduledExport(request);

    // Then
    assertNotNull(response);
    assertEquals(1L, response.getId());
    assertEquals("CUSTOMER", response.getEntityType());
    assertEquals("CSV", response.getExportFormat());
    assertEquals("ACTIVE", response.getStatus());

    verify(scheduledExportRepository).insert(any(ScheduledExport.class));
    verify(auditService).record(eq("CREATE"), eq("SCHEDULED_EXPORT"), anyLong(), anyString());
  }

  @Test
  @DisplayName("T021: getScheduledExports 获取用户的活动任务")
  void getScheduledExports_shouldReturnActiveTasksForUser() {
    // Given
    when(scheduledExportRepository.findByUserIdAndStatus(1L, "ACTIVE"))
        .thenReturn(List.of(sampleTask));

    // When
    List<ScheduledExportResponse> responses = scheduledExportService.getScheduledExports(1L);

    // Then
    assertNotNull(responses);
    assertEquals(1, responses.size());
    assertEquals(1L, responses.get(0).getId());
  }

  @Test
  @DisplayName("T022: updateStatus 更新任务状态")
  void updateStatus_shouldUpdateAndRecalculateNextExecution() {
    // Given
    when(scheduledExportRepository.selectById(1L)).thenReturn(sampleTask);
    when(scheduledExportRepository.updateById(any(ScheduledExport.class))).thenReturn(1);

    // When
    scheduledExportService.updateStatus(1L, "ACTIVE");

    // Then
    verify(scheduledExportRepository).updateById(any(ScheduledExport.class));
  }

  @Test
  @DisplayName("T023: deleteScheduledExport 软删除任务")
  void deleteScheduledExport_shouldSoftDelete() {
    // Given
    when(scheduledExportRepository.selectById(1L)).thenReturn(sampleTask);
    when(scheduledExportRepository.updateById(any(ScheduledExport.class))).thenReturn(1);

    // When
    scheduledExportService.deleteScheduledExport(1L);

    // Then
    verify(scheduledExportRepository).updateById(any(ScheduledExport.class));
    verify(auditService).record(eq("DELETE"), eq("SCHEDULED_EXPORT"), anyLong(), anyString());
  }

  @Test
  @DisplayName("T026: executeNow 手动执行任务")
  void executeNow_shouldExecuteActiveTask() {
    // Given
    when(scheduledExportRepository.selectById(1L)).thenReturn(sampleTask);
    when(scheduledExportRepository.updateById(any(ScheduledExport.class))).thenReturn(1);
    // 该任务没有过滤条件（filterConditions 未设置 = null）。此前这一位用 anyString() 桩，
    // 而 anyString() 不匹配 null → 桩静默未命中、mock 返回 null → NPE 抛在服务内部，
    // 现象上像是服务缺陷。这里改为精确匹配，并在 Then 段显式 verify，
    // 使"桩没命中"不再可能被静默放过（本类为 LENIENT，未命中不会有任何提示）。
    when(exportExecutor.executeExportWithRowCount(eq("CUSTOMER"), isNull(), eq("CSV")))
        .thenReturn(new String[] {"/tmp/export.csv", "100"});

    // When
    scheduledExportService.executeNow(1L);

    // Then
    verify(exportExecutor).executeExportWithRowCount(eq("CUSTOMER"), isNull(), eq("CSV"));
    // 只有一处回写：executeExport 重算 nextExecutionTime 后 updateById（:244）。
    // executeNow 本身不改任务状态（执行结果记在 ScheduledExportExecution 表），故是 1 次而非 2 次。
    // 原断言写的是 times(2)，与实现不符却从未暴露——因为上面那个 NPE 每次都在到达本行前抛出，
    // 这条断言实际上从未被执行过。
    verify(scheduledExportRepository, times(1)).updateById(any(ScheduledExport.class));
    verify(scheduledExportExecutionRepository).insert(any(ScheduledExportExecution.class));
    // 回写的是重算后的下次执行时间（原值已被 setUp 置为过去时刻）
    assertTrue(sampleTask.getNextExecutionTime().isAfter(LocalDateTime.now()));
  }

  @Test
  @DisplayName("T027: executePendingTasks 执行到期待任务")
  void executePendingTasks_shouldExecuteAllPending() {
    // Given
    when(scheduledExportRepository.findByStatusAndNextExecutionTimeLessThanEqual(
            eq("ACTIVE"), any(LocalDateTime.class)))
        .thenReturn(List.of(sampleTask));
    when(scheduledExportRepository.updateById(any(ScheduledExport.class))).thenReturn(1);
    org.mockito.Mockito.when(
            exportExecutor.executeExportWithRowCount(anyString(), anyString(), anyString()))
        .thenReturn(new String[] {"/tmp/export.csv", "100"});

    // When
    scheduledExportService.executePendingTasks();

    // Then
    verify(scheduledExportExecutionRepository, times(1))
        .insert(any(ScheduledExportExecution.class));
  }

  @Test
  @DisplayName("T026: executeNow 非活动任务应抛出异常")
  void executeNow_shouldThrowWhenNotActive() {
    // Given
    sampleTask.setStatus("INACTIVE");
    when(scheduledExportRepository.selectById(1L)).thenReturn(sampleTask);

    // When & Then
    assertThrows(IllegalStateException.class, () -> scheduledExportService.executeNow(1L));
  }

  // ===== T063 归属校验（FR-G16）：写／执行端点必须与读端点同标准 =====
  // 以下四条先于修复编写。修复前，服务层对这三个端点只做"存在性"检查、不做归属检查，
  // 持 `export:scheduled` 权限码的用户可改状态、软删、触发**他人**任务——
  // 唯一拦截是"界面只展示本人任务"，正是章程原则三点名排除的那类伪访问控制。
  // 三条 *ShouldRejectOtherUsersTask 修复前全红。

  /** 构造"他人任务"：setUp 里当前登录用户是 1L，此处把任务属主改为 999L。 */
  private void givenTaskOwnedByAnotherUser() {
    sampleTask.setUserId(999L);
    when(scheduledExportRepository.selectById(1L)).thenReturn(sampleTask);
  }

  @Test
  @DisplayName("T063: updateStatus 不得修改他人任务的状态")
  void updateStatusShouldRejectOtherUsersTask() {
    givenTaskOwnedByAnotherUser();

    BusinessException ex =
        assertThrows(
            BusinessException.class, () -> scheduledExportService.updateStatus(1L, "INACTIVE"));

    assertEquals(ErrorCode.EXPORT_FORBIDDEN, ex.getErrorCode());
    // 只断言"抛了异常"不够——必须同时证明没有副作用：越权被拒但实体已被就地改写再回写，
    // 同样满足"抛异常"，而破坏已经发生。故这里同时钉住"未回写"与"内存对象未被改动"。
    verify(scheduledExportRepository, never()).updateById(any(ScheduledExport.class));
    assertEquals("ACTIVE", sampleTask.getStatus());
  }

  @Test
  @DisplayName("T063: deleteScheduledExport 不得软删他人任务")
  void deleteScheduledExportShouldRejectOtherUsersTask() {
    givenTaskOwnedByAnotherUser();

    BusinessException ex =
        assertThrows(
            BusinessException.class, () -> scheduledExportService.deleteScheduledExport(1L));

    assertEquals(ErrorCode.EXPORT_FORBIDDEN, ex.getErrorCode());
    verify(scheduledExportRepository, never()).updateById(any(ScheduledExport.class));
    // 审计记录也不得落下：它写的是"谁删了哪个任务"，越权被拒却留痕会污染审计
    verify(auditService, never()).record(anyString(), anyString(), anyLong(), anyString());
    assertEquals("ACTIVE", sampleTask.getStatus());
  }

  @Test
  @DisplayName("T063: executeNow 不得触发他人任务")
  void executeNowShouldRejectOtherUsersTask() {
    givenTaskOwnedByAnotherUser();

    BusinessException ex =
        assertThrows(BusinessException.class, () -> scheduledExportService.executeNow(1L));

    assertEquals(ErrorCode.EXPORT_FORBIDDEN, ex.getErrorCode());
    // 归属判定必须先于"是否 ACTIVE"判定，否则非属主可从错误码差异推断任务的当前状态
    verify(exportExecutor, never()).executeExportWithRowCount(anyString(), any(), anyString());
    verify(scheduledExportExecutionRepository, never()).insert(any(ScheduledExportExecution.class));
  }

  @Test
  @DisplayName("T063: 任务不存在时三个写端点统一返回 404 EXPORT_NOT_FOUND")
  void writeEndpointsReportNotFound() {
    // 本断言钉住一处**有意**的契约细化，而非实现细节：改动前这三个端点抛
    // IllegalArgumentException，被 GlobalExceptionHandler 映成 400 通用错误（无错误码）；
    // 同资源的读端点 getScheduledExport 早已是 404 EXPORT_NOT_FOUND。改用 requireOwned 后
    // 三者与读端点统一。若不显式钉住，日后有人把 404 改回 400 不会触发任何失败。
    when(scheduledExportRepository.selectById(404L)).thenReturn(null);

    assertEquals(
        ErrorCode.EXPORT_NOT_FOUND,
        assertThrows(
                BusinessException.class, () -> scheduledExportService.updateStatus(404L, "ACTIVE"))
            .getErrorCode());
    assertEquals(
        ErrorCode.EXPORT_NOT_FOUND,
        assertThrows(
                BusinessException.class, () -> scheduledExportService.deleteScheduledExport(404L))
            .getErrorCode());
    assertEquals(
        ErrorCode.EXPORT_NOT_FOUND,
        assertThrows(BusinessException.class, () -> scheduledExportService.executeNow(404L))
            .getErrorCode());
  }

  @Test
  @DisplayName("T028: getExecutions 获取执行历史")
  void getExecutions_shouldReturnExecutionHistory() {
    // Given
    // 归属判定（FR-G16）需要先定位任务本体，故这里必须桩上父任务，否则服务层无从判断请求者是否有权读该任务的执行记录
    when(scheduledExportRepository.selectById(1L)).thenReturn(sampleTask);

    ScheduledExportExecution execution = new ScheduledExportExecution();
    execution.setId(1L);
    execution.setScheduledExportId(1L);
    execution.setStatus("SUCCESS");
    execution.setExecutedAt(LocalDateTime.now());

    when(scheduledExportExecutionRepository.findByScheduledExportIdOrderByExecutedAtDesc(1L))
        .thenReturn(List.of(execution));

    // When
    List<ScheduledExportExecutionResponse> responses = scheduledExportService.getExecutions(1L);

    // Then
    assertNotNull(responses);
    assertEquals(1, responses.size());
    assertEquals("SUCCESS", responses.get(0).getStatus());
  }

  // ===== FR-G16：读取端点按当前登录用户限定范围 =====

  /**
   * 三个读取端点的越权拒绝（FR-G16）。
   *
   * <p>当事任务归属 {@code 2L}，而当前登录用户为 {@code 1L}（见 {@link #setUp}）。
   */
  private ScheduledExport foreignTask() {
    ScheduledExport foreign = new ScheduledExport();
    foreign.setId(2L);
    foreign.setUserId(2L);
    foreign.setEntityType("CUSTOMER");
    foreign.setExportFormat("CSV");
    foreign.setCronExpression("0 0 2 * * ?");
    foreign.setStatus("ACTIVE");
    when(scheduledExportRepository.selectById(2L)).thenReturn(foreign);
    return foreign;
  }

  @Test
  @DisplayName("FR-G16: 他人任务详情不可读")
  void getScheduledExport_shouldRejectForeignOwner() {
    foreignTask();
    assertThrows(BusinessException.class, () -> scheduledExportService.getScheduledExport(2L));
  }

  @Test
  @DisplayName("FR-G16: 他人任务执行记录不可读")
  void getExecutions_shouldRejectForeignOwner() {
    foreignTask();
    assertThrows(BusinessException.class, () -> scheduledExportService.getExecutions(2L));
    // 归属未通过就不该去查记录：只拒绝而不查库，才不会出现"先读了再报错"的时序泄漏
    verify(scheduledExportExecutionRepository, never())
        .findByScheduledExportIdOrderByExecutedAtDesc(anyLong());
  }

  @Test
  @DisplayName("FR-G16: 列表不接受任意用户标识参数")
  void getScheduledExports_shouldRejectForeignUserIdParam() {
    assertThrows(BusinessException.class, () -> scheduledExportService.getScheduledExports(2L));
    // 参数值不得参与过滤——一旦按传入标识查库，本方法就是越权读取的入口本身
    verify(scheduledExportRepository, never()).findByUserIdAndStatus(anyLong(), anyString());
  }
}
