package com.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.dto.task.TaskRequest;
import com.crm.entity.TaskItem;
import com.crm.repository.TaskItemMapper;
import com.crm.security.SecurityUtil;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/** TaskService 单元测试（010 T008/T014）：CRUD/归属隔离/提醒标识/汇总。 */
@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

  private TaskItemMapper taskMapper;
  private AuditService auditService;
  private TaskService service;
  private MockedStatic<SecurityUtil> securityUtilMock;

  /** 纯 Mockito 测试无 Spring 上下文：注册实体 TableInfo，供 LambdaQueryWrapper 解析列名。 */
  @BeforeAll
  static void initTableInfo() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
    TableInfoHelper.initTableInfo(assistant, TaskItem.class);
  }

  @BeforeEach
  void setUp() {
    taskMapper = mock(TaskItemMapper.class);
    auditService = mock(AuditService.class);
    service = new TaskService(taskMapper, auditService);
    securityUtilMock = Mockito.mockStatic(SecurityUtil.class);
    securityUtilMock.when(SecurityUtil::currentUserId).thenReturn(1L);
  }

  @AfterEach
  void tearDown() {
    securityUtilMock.close();
  }

  private TaskRequest request(String title) {
    TaskRequest req = new TaskRequest();
    req.setTitle(title);
    req.setPriority("HIGH");
    return req;
  }

  private TaskItem task(Long id, Long ownerId, String status, LocalDateTime dueAt) {
    TaskItem t = new TaskItem();
    t.setId(id);
    t.setTitle("测试任务");
    t.setOwnerId(ownerId);
    t.setStatus(status);
    t.setDueAt(dueAt);
    t.setPriority("MEDIUM");
    t.setVersion(0);
    return t;
  }

  @Test
  @DisplayName("创建任务：归属当前用户，默认 TODO，审计记录")
  void createSucceeds() {
    when(taskMapper.insert(any(TaskItem.class)))
        .thenAnswer(
            invocation -> {
              TaskItem t = invocation.getArgument(0);
              t.setId(1L);
              return 1;
            });

    var resp = service.create(request("跟进客户"));

    assertThat(resp.getId()).isEqualTo(1L);
    assertThat(resp.getStatus()).isEqualTo("TODO");
    verify(taskMapper).insert(any(TaskItem.class));
    verify(auditService).record("CREATE", "TASK", 1L, "创建任务：跟进客户");
  }

  @Test
  @DisplayName("非本人任务编辑抛出 FORBIDDEN")
  void updateOthersTaskThrows() {
    when(taskMapper.selectById(1L)).thenReturn(task(1L, 999L, "TODO", null));

    assertThatThrownBy(() -> service.update(1L, request("x")))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.FORBIDDEN);
    verify(taskMapper, never()).updateById(any());
  }

  @Test
  @DisplayName("任务不存在抛出 TASK_NOT_FOUND")
  void missingTaskThrows() {
    when(taskMapper.selectById(99L)).thenReturn(null);

    assertThatThrownBy(() -> service.toggle(99L))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.TASK_NOT_FOUND);
  }

  @Test
  @DisplayName("完成/重开：TODO→DONE→TODO")
  void toggleSwitches() {
    TaskItem todo = task(1L, 1L, "TODO", null);
    TaskItem done = task(1L, 1L, "DONE", null);
    when(taskMapper.selectById(1L)).thenReturn(todo, done);

    var first = service.toggle(1L);
    assertThat(first.getStatus()).isEqualTo("DONE");
    var second = service.toggle(1L);
    assertThat(second.getStatus()).isEqualTo("TODO");
  }

  @Test
  @DisplayName("提醒标识：逾期含天数/今日/正常/完成")
  void reminderStatus() {
    LocalDateTime now = LocalDateTime.now();
    TaskItem overdue = task(1L, 1L, "TODO", now.minusDays(2));
    TaskItem today = task(2L, 1L, "TODO", now.plusHours(2));
    TaskItem normal = task(3L, 1L, "TODO", now.plusDays(5));
    TaskItem done = task(4L, 1L, "DONE", now.minusDays(1));

    var r1 = service.toResponse(overdue);
    var r2 = service.toResponse(today);
    var r3 = service.toResponse(normal);
    var r4 = service.toResponse(done);

    assertThat(r1.getReminderStatus()).isEqualTo("OVERDUE");
    assertThat(r1.getOverdueDays()).isPositive();
    assertThat(r2.getReminderStatus()).isEqualTo("TODAY");
    assertThat(r3.getReminderStatus()).isEqualTo("NORMAL");
    assertThat(r4.getReminderStatus()).isEqualTo("DONE");
  }

  @Test
  @DisplayName("提醒汇总：逾期与今日计数")
  void reminderSummaryCounts() {
    // 固定时间避免跨日边界：昨天中午（必逾期）、今天 23:59（必今日）
    // 模拟 SQL 的 status=TODO 过滤：仅返回 TODO 任务
    LocalDateTime overdueDue = LocalDate.now().minusDays(1).atTime(12, 0);
    LocalDateTime todayDue = LocalDate.now().atTime(23, 59, 59);
    when(taskMapper.selectList(any()))
        .thenReturn(List.of(task(1L, 1L, "TODO", overdueDue), task(2L, 1L, "TODO", todayDue)));

    var summary = service.reminderSummary();

    assertThat(summary.get("overdueCount")).isEqualTo(1L);
    assertThat(summary.get("todayCount")).isEqualTo(1L);
  }
}
