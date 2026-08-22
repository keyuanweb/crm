package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.common.PageResult;
import com.crm.dto.task.CalendarResponse;
import com.crm.dto.task.TaskRequest;
import com.crm.dto.task.TaskResponse;
import com.crm.entity.TaskItem;
import com.crm.repository.TaskItemMapper;
import com.crm.security.SecurityUtil;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 任务服务（010，FR-T01~T09）：CRUD/完成重开/提醒标识/汇总/日历。 */
@Service
public class TaskService {

  public static final String STATUS_TODO = "TODO";
  public static final String STATUS_DONE = "DONE";

  private final TaskItemMapper taskMapper;
  private final AuditService auditService;

  public TaskService(TaskItemMapper taskMapper, AuditService auditService) {
    this.taskMapper = taskMapper;
    this.auditService = auditService;
  }

  public PageResult<TaskResponse> page(
      String keyword, String status, String priority, String linkedType, long page, long pageSize) {
    Long ownerId = SecurityUtil.currentUserId();
    LambdaQueryWrapper<TaskItem> qw = new LambdaQueryWrapper<>();
    qw.eq(TaskItem::getOwnerId, ownerId);
    if (StringUtils.hasText(keyword)) {
      qw.like(TaskItem::getTitle, keyword.trim());
    }
    if (StringUtils.hasText(status)) {
      qw.eq(TaskItem::getStatus, status.trim());
    }
    if (StringUtils.hasText(priority)) {
      qw.eq(TaskItem::getPriority, priority.trim());
    }
    if (StringUtils.hasText(linkedType)) {
      qw.eq(TaskItem::getLinkedType, linkedType.trim());
    }
    qw.orderByDesc(TaskItem::getId);
    Page<TaskItem> p = taskMapper.selectPage(new Page<>(page, pageSize), qw);
    return PageResult.of(
        p.getRecords().stream().map(this::toResponse).toList(), p.getTotal(), page, pageSize);
  }

  @Transactional
  public TaskResponse create(TaskRequest req) {
    TaskItem task = new TaskItem();
    apply(req, task);
    task.setStatus(STATUS_TODO);
    Long userId = SecurityUtil.currentUserId();
    task.setOwnerId(userId);
    task.setCreatedBy(userId);
    taskMapper.insert(task);
    auditService.record("CREATE", "TASK", task.getId(), "创建任务：" + task.getTitle());
    return toResponse(task);
  }

  @Transactional
  public TaskResponse update(Long id, TaskRequest req) {
    TaskItem existing = requireOwned(id);
    apply(req, existing);
    existing.setVersion(req.getVersion());
    int rows = taskMapper.updateById(existing);
    if (rows == 0) {
      throw new BusinessException(ErrorCode.VERSION_CONFLICT);
    }
    auditService.record("UPDATE", "TASK", id, "编辑任务：" + existing.getTitle());
    return toResponse(taskMapper.selectById(id));
  }

  /** 完成/重开（TODO↔DONE）。 */
  @Transactional
  public TaskResponse toggle(Long id) {
    TaskItem task = requireOwned(id);
    task.setStatus(STATUS_DONE.equals(task.getStatus()) ? STATUS_TODO : STATUS_DONE);
    taskMapper.updateById(task);
    auditService.record("TOGGLE", "TASK", id, "任务状态切换：" + task.getStatus());
    return toResponse(taskMapper.selectById(id));
  }

  @Transactional
  public void delete(Long id) {
    TaskItem task = requireOwned(id);
    taskMapper.deleteById(id);
    auditService.record("DELETE", "TASK", id, "逻辑删除任务：" + task.getTitle());
  }

  /** 提醒汇总：逾期数/今日到期数（仅当前用户）。 */
  public Map<String, Long> reminderSummary() {
    Long ownerId = SecurityUtil.currentUserId();
    List<TaskItem> todos =
        taskMapper.selectList(
            new LambdaQueryWrapper<TaskItem>()
                .eq(TaskItem::getOwnerId, ownerId)
                .eq(TaskItem::getStatus, STATUS_TODO)
                .isNotNull(TaskItem::getDueAt));
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime todayStart = LocalDate.now().atStartOfDay();
    LocalDateTime tomorrowStart = LocalDate.now().plusDays(1).atStartOfDay();
    long overdue = 0;
    long today = 0;
    for (TaskItem t : todos) {
      if (t.getDueAt().isBefore(now)) {
        overdue++;
      } else if (!t.getDueAt().isBefore(todayStart) && t.getDueAt().isBefore(tomorrowStart)) {
        today++;
      }
    }
    return Map.of("overdueCount", overdue, "todayCount", today);
  }

  /** 按月日历数据：date → tasks（仅当前用户，含逾期标记）。 */
  public CalendarResponse calendar(String month) {
    YearMonth ym = YearMonth.parse(month);
    LocalDateTime start = ym.atDay(1).atStartOfDay();
    LocalDateTime end = ym.plusMonths(1).atDay(1).atStartOfDay();
    Long ownerId = SecurityUtil.currentUserId();
    List<TaskItem> tasks =
        taskMapper.selectList(
            new LambdaQueryWrapper<TaskItem>()
                .eq(TaskItem::getOwnerId, ownerId)
                .ge(TaskItem::getDueAt, start)
                .lt(TaskItem::getDueAt, end)
                .orderByAsc(TaskItem::getDueAt));
    Map<LocalDate, List<TaskResponse>> byDay = new LinkedHashMap<>();
    for (TaskItem t : tasks) {
      if (t.getDueAt() == null) {
        continue;
      }
      byDay.computeIfAbsent(t.getDueAt().toLocalDate(), k -> new ArrayList<>()).add(toResponse(t));
    }
    List<CalendarResponse.CalendarDay> days =
        byDay.entrySet().stream()
            .map(e -> new CalendarResponse.CalendarDay(e.getKey().toString(), e.getValue()))
            .toList();
    return new CalendarResponse(month, days);
  }

  /** 校验任务存在且归属当前用户（数据隔离）。 */
  public TaskItem requireOwned(Long id) {
    TaskItem task = taskMapper.selectById(id);
    if (task == null) {
      throw new BusinessException(ErrorCode.TASK_NOT_FOUND);
    }
    Long userId = SecurityUtil.currentUserId();
    if (!userId.equals(task.getOwnerId())) {
      throw new BusinessException(ErrorCode.FORBIDDEN);
    }
    return task;
  }

  private void apply(TaskRequest req, TaskItem task) {
    task.setTitle(req.getTitle().trim());
    task.setDueAt(req.getDueAt());
    task.setPriority(StringUtils.hasText(req.getPriority()) ? req.getPriority().trim() : "MEDIUM");
    task.setLinkedType(trimToNull(req.getLinkedType()));
    task.setLinkedId(req.getLinkedId());
    task.setRemark(trimToNull(req.getRemark()));
  }

  /** 包级可见供单元测试直接调用。 */
  TaskResponse toResponse(TaskItem task) {
    TaskResponse resp = new TaskResponse();
    resp.setId(task.getId());
    resp.setTitle(task.getTitle());
    resp.setDueAt(task.getDueAt());
    resp.setPriority(task.getPriority());
    resp.setStatus(task.getStatus());
    resp.setLinkedType(task.getLinkedType());
    resp.setLinkedId(task.getLinkedId());
    resp.setRemark(task.getRemark());
    resp.setVersion(task.getVersion());
    resp.setCreatedAt(task.getCreatedAt());
    fillReminder(task, resp);
    return resp;
  }

  private void fillReminder(TaskItem task, TaskResponse resp) {
    if (STATUS_DONE.equals(task.getStatus())) {
      resp.setReminderStatus("DONE");
      return;
    }
    if (task.getDueAt() == null) {
      resp.setReminderStatus("NORMAL");
      return;
    }
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime todayStart = LocalDate.now().atStartOfDay();
    LocalDateTime tomorrowStart = LocalDate.now().plusDays(1).atStartOfDay();
    if (task.getDueAt().isBefore(now)) {
      resp.setReminderStatus("OVERDUE");
      long days = Duration.between(task.getDueAt(), now).toDays() + 1;
      resp.setOverdueDays(Math.max(days, 1L));
    } else if (!task.getDueAt().isBefore(todayStart) && task.getDueAt().isBefore(tomorrowStart)) {
      resp.setReminderStatus("TODAY");
    } else {
      resp.setReminderStatus("NORMAL");
    }
  }

  private String trimToNull(String value) {
    return StringUtils.hasText(value) ? value.trim() : null;
  }
}
