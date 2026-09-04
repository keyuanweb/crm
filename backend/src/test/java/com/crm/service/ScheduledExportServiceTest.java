package com.crm.service;

import com.crm.dto.ScheduledExportExecutionResponse;
import com.crm.dto.ScheduledExportRequest;
import com.crm.dto.ScheduledExportResponse;
import com.crm.model.entity.ScheduledExport;
import com.crm.model.entity.ScheduledExportExecution;
import com.crm.repository.ScheduledExportExecutionRepository;
import com.crm.repository.ScheduledExportRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 定时导出 Service 单元测试（079-scheduled-export）。 */
@ExtendWith(MockitoExtension.class)
class ScheduledExportServiceTest {

  @Mock
  private ScheduledExportRepository scheduledExportRepository;

  @Mock
  private ScheduledExportExecutionRepository scheduledExportExecutionRepository;

  @Mock
  private EmailService emailService;

  @Mock
  private ExportExecutor exportExecutor;

  @Mock
  private AuditService auditService;

  @InjectMocks
  private ScheduledExportServiceImpl scheduledExportService;

  private ScheduledExport sampleTask;

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
  }

  @Test
  @DisplayName("T018: createScheduledExport 创建定时导出任务")
  void createScheduledExport_shouldCreateAndReturnResponse() {
    // Given
    ScheduledExportRequest request = new ScheduledExportRequest();
    request.setEntityType("CUSTOMER");
    request.setExportFormat("CSV");
    request.setCronExpression("0 0 2 * * ?");

    when(scheduledExportRepository.insert(any(ScheduledExport.class))).thenReturn(1);

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
    when(exportExecutor.executeExport(anyString(), anyString(), anyString())).thenReturn("/tmp/export.csv");

    // When
    scheduledExportService.executeNow(1L);

    // Then
    verify(scheduledExportRepository, times(1)).updateById(any(ScheduledExport.class));
    verify(scheduledExportExecutionRepository).insert(any(ScheduledExportExecution.class));
  }

  @Test
  @DisplayName("T027: executePendingTasks 执行到期待任务")
  void executePendingTasks_shouldExecuteAllPending() {
    // Given
    when(scheduledExportRepository.findByStatusAndNextExecutionTimeLessThanEqual(
        eq("ACTIVE"), any(LocalDateTime.class)))
        .thenReturn(List.of(sampleTask));
    when(scheduledExportRepository.updateById(any(ScheduledExport.class))).thenReturn(1);
    when(exportExecutor.executeExport(anyString(), anyString(), anyString())).thenReturn("/tmp/export.csv");

    // When
    scheduledExportService.executePendingTasks();

    // Then
    verify(scheduledExportRepository, times(1))
        .findByStatusAndNextExecutionTimeLessThanEqual(eq("ACTIVE"), any(LocalDateTime.class));
    verify(scheduledExportExecutionRepository, times(1)).insert(any(ScheduledExportExecution.class));
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

  @Test
  @DisplayName("T028: getExecutions 获取执行历史")
  void getExecutions_shouldReturnExecutionHistory() {
    // Given
    ScheduledExportExecution execution = new ScheduledExportExecution();
    execution.setId(1L);
    execution.setScheduledExportId(1L);
    execution.setStatus("SUCCESS");
    execution.setExecutedAt(LocalDateTime.now());

    when(scheduledExportExecutionRepository.findByScheduledExportIdOrderByExecutedAtDesc(1L))
        .thenReturn(List.of(execution));

    // When
    List<ScheduledExportExecutionResponse> responses =
        scheduledExportService.getExecutions(1L);

    // Then
    assertNotNull(responses);
    assertEquals(1, responses.size());
    assertEquals("SUCCESS", responses.get(0).getStatus());
  }
}
