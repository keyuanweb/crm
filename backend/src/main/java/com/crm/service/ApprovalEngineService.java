package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.dto.approval.TaskActionRequest;
import com.crm.entity.ApprovalFlow;
import com.crm.entity.ApprovalInstance;
import com.crm.entity.ApprovalLog;
import com.crm.entity.ApprovalTask;
import com.crm.entity.User;
import com.crm.repository.ApprovalInstanceMapper;
import com.crm.repository.ApprovalLogMapper;
import com.crm.repository.ApprovalTaskMapper;
import com.crm.repository.UserMapper;
import com.crm.security.SecurityUtil;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 审批引擎（033-approval-flow，FR-002/003/004/005）：发起/通过/驳回/转交/重提， 多级状态机 + 乐观并发校验 + 026 通知。 */
@Service
public class ApprovalEngineService {

  private static final Logger log = LoggerFactory.getLogger(ApprovalEngineService.class);
  public static final String STATUS_PENDING = "PENDING";
  public static final String STATUS_APPROVED = "APPROVED";
  public static final String STATUS_REJECTED = "REJECTED";
  public static final String STATUS_CANCELED = "CANCELED";
  public static final String STATUS_TRANSFERRED = "TRANSFERRED";

  private final ApprovalInstanceMapper instanceMapper;
  private final ApprovalTaskMapper taskMapper;
  private final ApprovalLogMapper logMapper;
  private final ApprovalFlowService flowService;
  private final UserMapper userMapper;
  private final NotificationService notificationService;
  private final com.crm.repository.ContractMapper contractMapper;
  private final com.crm.repository.QuoteMapper quoteMapper;
  private final org.springframework.beans.factory.ObjectProvider<IntegrationChannelService>
      integrationChannelProvider;

  public ApprovalEngineService(
      ApprovalInstanceMapper instanceMapper,
      ApprovalTaskMapper taskMapper,
      ApprovalLogMapper logMapper,
      ApprovalFlowService flowService,
      UserMapper userMapper,
      NotificationService notificationService,
      com.crm.repository.ContractMapper contractMapper,
      com.crm.repository.QuoteMapper quoteMapper,
      org.springframework.beans.factory.ObjectProvider<IntegrationChannelService>
          integrationChannelProvider) {
    this.instanceMapper = instanceMapper;
    this.taskMapper = taskMapper;
    this.logMapper = logMapper;
    this.flowService = flowService;
    this.userMapper = userMapper;
    this.notificationService = notificationService;
    this.contractMapper = contractMapper;
    this.quoteMapper = quoteMapper;
    this.integrationChannelProvider = integrationChannelProvider;
  }

  /** 发起审批：取启用流程 → 解析节点 → 建实例 + 首任务 → 通知审批人。 */
  @Transactional
  public ApprovalInstance start(String businessType, Long businessId, String title, double amount) {
    ApprovalFlow flow = flowService.enabledFlow(businessType);
    if (flow == null) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "未配置启用的审批流");
    }
    Long initiator = SecurityUtil.currentUserId();
    List<Map<String, Object>> nodes = flowService.resolveNodes(flow, amount);
    if (nodes.isEmpty()) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "审批流无节点");
    }

    ApprovalInstance instance = new ApprovalInstance();
    instance.setFlowId(flow.getId());
    instance.setBusinessType(businessType);
    instance.setBusinessId(businessId);
    instance.setTitle(title);
    instance.setStatus(STATUS_PENDING);
    instance.setInitiator(initiator);
    instanceMapper.insert(instance);

    ApprovalTask first = null;
    int seq = 0;
    for (Map<String, Object> node : nodes) {
      ApprovalTask task = new ApprovalTask();
      task.setInstanceId(instance.getId());
      task.setNodeName(String.valueOf(node.getOrDefault("name", "审批")));
      task.setApproverType(String.valueOf(node.getOrDefault("approverType", "ROLE")));
      task.setApproverValue(
          node.get("approverValue") == null ? null : String.valueOf(node.get("approverValue")));
      task.setStatus(STATUS_PENDING);
      task.setSeq(seq++);
      task.setCreatedAt(LocalDateTime.now());
      task.setUpdatedAt(LocalDateTime.now());
      taskMapper.insert(task);
      if (first == null) {
        first = task;
      }
    }
    instance.setCurrentTaskId(first.getId());
    instanceMapper.updateById(instance);

    // 通知首审批人
    Long approver = resolveApprover(first, initiator);
    first.setApproverId(approver);
    taskMapper.updateById(first);
    notifyApprover(approver, instance);

    recordLog(instance.getId(), first.getId(), "SUBMIT", initiator, "提交审批");
    // 058：集成通道推送（审批待办）
    IntegrationChannelService channelService = integrationChannelProvider.getIfAvailable();
    if (channelService != null) {
      channelService.publish("APPROVAL_PENDING", title + "（审批 #" + instance.getId() + "）");
    }
    return instance;
  }

  /** 通过：当前任务 APPROVED → 推进下一节点 → 无则实例 APPROVED。 */
  @Transactional
  public void approve(Long taskId, TaskActionRequest req) {
    ApprovalTask task = requirePendingTask(taskId);
    checkApprover(task);
    task.setStatus(STATUS_APPROVED);
    task.setApproverId(SecurityUtil.currentUserId());
    task.setComment(req.getComment());
    task.setUpdatedAt(LocalDateTime.now());
    taskMapper.updateById(task);

    ApprovalInstance instance = requireInstance(task.getInstanceId());
    // 找下一 PENDING 任务
    ApprovalTask next =
        taskMapper.selectOne(
            new LambdaQueryWrapper<ApprovalTask>()
                .eq(ApprovalTask::getInstanceId, instance.getId())
                .eq(ApprovalTask::getStatus, STATUS_PENDING)
                .orderByAsc(ApprovalTask::getSeq)
                .last("LIMIT 1"));
    if (next != null) {
      instance.setCurrentTaskId(next.getId());
      instanceMapper.updateById(instance);
      Long approver = resolveApprover(next, instance.getInitiator());
      next.setApproverId(approver);
      taskMapper.updateById(next);
      notifyApprover(approver, instance);
    } else {
      instance.setStatus(STATUS_APPROVED);
      instanceMapper.updateById(instance);
      // 通知发起人
      notifyInitiator(instance, "审批已全部通过");
    }
    recordLog(
        instance.getId(), task.getId(), "APPROVE", SecurityUtil.currentUserId(), req.getComment());
  }

  /** 驳回：实例 REJECTED → 通知发起人。 */
  @Transactional
  public void reject(Long taskId, TaskActionRequest req) {
    ApprovalTask task = requirePendingTask(taskId);
    checkApprover(task);
    task.setStatus(STATUS_REJECTED);
    task.setApproverId(SecurityUtil.currentUserId());
    task.setComment(req.getComment());
    task.setUpdatedAt(LocalDateTime.now());
    taskMapper.updateById(task);

    ApprovalInstance instance = requireInstance(task.getInstanceId());
    instance.setStatus(STATUS_REJECTED);
    instanceMapper.updateById(instance);
    notifyInitiator(instance, "审批被驳回：" + (req.getComment() == null ? "" : req.getComment()));
    recordLog(
        instance.getId(), task.getId(), "REJECT", SecurityUtil.currentUserId(), req.getComment());
  }

  /** 转交：当前任务转给他人。 */
  @Transactional
  public void transfer(Long taskId, TaskActionRequest req) {
    ApprovalTask task = requirePendingTask(taskId);
    checkApprover(task);
    if (req.getToUserId() == null) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择转交人");
    }
    task.setStatus(STATUS_TRANSFERRED);
    task.setUpdatedAt(LocalDateTime.now());
    taskMapper.updateById(task);

    // 新任务（同节点，审批人 = 转交人）
    ApprovalTask newTask = new ApprovalTask();
    newTask.setInstanceId(task.getInstanceId());
    newTask.setNodeName(task.getNodeName());
    newTask.setApproverType("USER");
    newTask.setApproverValue(String.valueOf(req.getToUserId()));
    newTask.setApproverId(req.getToUserId());
    newTask.setStatus(STATUS_PENDING);
    newTask.setSeq(task.getSeq());
    newTask.setCreatedAt(LocalDateTime.now());
    newTask.setUpdatedAt(LocalDateTime.now());
    taskMapper.insert(newTask);

    ApprovalInstance instance = requireInstance(task.getInstanceId());
    instance.setCurrentTaskId(newTask.getId());
    instanceMapper.updateById(instance);
    notifyApprover(req.getToUserId(), instance);
    recordLog(
        instance.getId(),
        task.getId(),
        "TRANSFER",
        SecurityUtil.currentUserId(),
        "转交给用户#" + req.getToUserId());
  }

  /** 重提（发起人，驳回后新建实例）。 */
  @Transactional
  public ApprovalInstance renew(Long instanceId) {
    ApprovalInstance old = requireInstance(instanceId);
    Long current = SecurityUtil.currentUserId();
    if (!old.getInitiator().equals(current)) {
      throw new BusinessException(ErrorCode.FORBIDDEN);
    }
    if (!STATUS_REJECTED.equals(old.getStatus())) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "仅驳回状态可重提");
    }
    old.setStatus(STATUS_CANCELED);
    instanceMapper.updateById(old);
    recordLog(old.getId(), null, "RENEW", current, "重新发起");
    // 按业务取真实金额 → 条件分支（金额阈值追加节点）正确生效
    double amount = businessAmount(old.getBusinessType(), old.getBusinessId());
    return start(old.getBusinessType(), old.getBusinessId(), old.getTitle(), amount);
  }

  /** 业务金额（CONTRACT → 合同金额 / QUOTE → 报价金额）。 */
  private double businessAmount(String businessType, Long businessId) {
    try {
      if ("CONTRACT".equals(businessType)) {
        com.crm.entity.Contract c = contractMapper.selectById(businessId);
        return c == null || c.getAmount() == null ? 0 : c.getAmount().doubleValue();
      }
      if ("QUOTE".equals(businessType)) {
        com.crm.entity.Quote q = quoteMapper.selectById(businessId);
        return q == null || q.getTotalAmount() == null ? 0 : q.getTotalAmount().doubleValue();
      }
    } catch (Exception ex) {
      log.warn("Resolve business amount failed: {} {}", businessType, businessId);
    }
    return 0;
  }

  /** 按业务查审批实例。 */
  public List<ApprovalInstance> instancesOf(String businessType, Long businessId) {
    return instanceMapper.selectList(
        new LambdaQueryWrapper<ApprovalInstance>()
            .eq(ApprovalInstance::getBusinessType, businessType)
            .eq(ApprovalInstance::getBusinessId, businessId)
            .orderByDesc(ApprovalInstance::getId));
  }

  /** 我的待办。 */
  public List<ApprovalTask> todos(Long userId) {
    return taskMapper.selectList(
        new LambdaQueryWrapper<ApprovalTask>()
            .eq(ApprovalTask::getApproverId, userId)
            .eq(ApprovalTask::getStatus, STATUS_PENDING)
            .orderByDesc(ApprovalTask::getId));
  }

  /** 我的已办。 */
  public List<ApprovalTask> done(Long userId) {
    return taskMapper.selectList(
        new LambdaQueryWrapper<ApprovalTask>()
            .eq(ApprovalTask::getApproverId, userId)
            .in(
                ApprovalTask::getStatus,
                List.of(STATUS_APPROVED, STATUS_REJECTED, STATUS_TRANSFERRED))
            .orderByDesc(ApprovalTask::getId)
            .last("LIMIT 100"));
  }

  /** 实例详情（含任务列表 + 日志）。 */
  public ApprovalInstance detail(Long id) {
    return requireInstance(id);
  }

  public List<ApprovalTask> tasksOf(Long instanceId) {
    return taskMapper.selectList(
        new LambdaQueryWrapper<ApprovalTask>()
            .eq(ApprovalTask::getInstanceId, instanceId)
            .orderByAsc(ApprovalTask::getSeq));
  }

  public List<ApprovalLog> logsOf(Long instanceId) {
    return logMapper.selectList(
        new LambdaQueryWrapper<ApprovalLog>()
            .eq(ApprovalLog::getInstanceId, instanceId)
            .orderByAsc(ApprovalLog::getId));
  }

  /** 解析审批人：ROLE 按角色码查用户 / USER 按 value / MANAGER 简化按 value。 */
  private Long resolveApprover(ApprovalTask task, Long initiator) {
    if ("USER".equals(task.getApproverType()) || "MANAGER".equals(task.getApproverType())) {
      return Long.valueOf(task.getApproverValue());
    }
    // ROLE
    User user =
        userMapper.selectOne(
            new LambdaQueryWrapper<User>()
                .eq(User::getRole, task.getApproverValue())
                .eq(User::getEnabled, 1)
                .last("LIMIT 1"));
    if (user == null) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "审批人角色无可用用户：" + task.getApproverValue());
    }
    return user.getId();
  }

  private void checkApprover(ApprovalTask task) {
    Long current = SecurityUtil.currentUserId();
    if (task.getApproverId() != null && !task.getApproverId().equals(current)) {
      throw new BusinessException(ErrorCode.FORBIDDEN);
    }
  }

  private ApprovalTask requirePendingTask(Long taskId) {
    ApprovalTask task = taskMapper.selectById(taskId);
    if (task == null || !STATUS_PENDING.equals(task.getStatus())) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "任务不存在或已处理");
    }
    return task;
  }

  private ApprovalInstance requireInstance(Long id) {
    ApprovalInstance instance = instanceMapper.selectById(id);
    if (instance == null) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "审批实例不存在");
    }
    return instance;
  }

  private void recordLog(
      Long instanceId, Long taskId, String action, Long operator, String comment) {
    ApprovalLog l = new ApprovalLog();
    l.setInstanceId(instanceId);
    l.setTaskId(taskId);
    l.setAction(action);
    l.setOperator(operator);
    l.setComment(comment);
    l.setCreatedAt(LocalDateTime.now());
    logMapper.insert(l);
  }

  private void notifyApprover(Long approverId, ApprovalInstance instance) {
    try {
      notificationService.notify(
          approverId,
          NotificationService.TYPE_WORKFLOW,
          "你有新的审批任务：「" + instance.getTitle() + "」",
          "APPROVAL",
          instance.getId());
    } catch (Exception ex) {
      log.warn("Notify approver failed: {}", ex.getMessage());
    }
  }

  private void notifyInitiator(ApprovalInstance instance, String msg) {
    try {
      notificationService.notify(
          instance.getInitiator(),
          NotificationService.TYPE_WORKFLOW,
          "审批「" + instance.getTitle() + "」：" + msg,
          "APPROVAL",
          instance.getId());
    } catch (Exception ex) {
      log.warn("Notify initiator failed: {}", ex.getMessage());
    }
  }
}
