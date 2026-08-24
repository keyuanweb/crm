package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.crm.dto.task.TaskRequest;
import com.crm.entity.WorkflowExecutionLog;
import com.crm.entity.WorkflowRule;
import com.crm.repository.WorkflowExecutionLogMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/** 工作流引擎（013，FR-W04~W06）：事件触发 → 匹配启用规则 → 执行动作 → 记日志。 动作执行失败仅记日志，不影响主流程。 */
@Service
public class WorkflowEngine {

  private static final Logger log = LoggerFactory.getLogger(WorkflowEngine.class);

  public static final String EVENT_LEAD_CREATED = "LEAD_CREATED";
  public static final String EVENT_OPPORTUNITY_STAGE_CHANGED = "OPPORTUNITY_STAGE_CHANGED";
  public static final String EVENT_FOLLOW_UP_CREATED = "FOLLOW_UP_CREATED";
  public static final String EVENT_PAYMENT_RECORDED = "PAYMENT_RECORDED";
  // 049：营销自动化事件
  public static final String EVENT_LEAD_SCORE_THRESHOLD = "LEAD_SCORE_THRESHOLD";
  public static final String EVENT_TAG_CHANGED = "TAG_CHANGED";

  private final WorkflowRuleService ruleService;
  private final WorkflowExecutionLogMapper logMapper;
  private final WorkflowNotificationService notificationService;
  private final TaskService taskService;
  private final com.crm.repository.LeadMapper leadMapper;
  private final com.crm.repository.CustomerMapper customerMapper;
  private final ObjectProvider<EmailCampaignService> emailCampaignProvider;
  private final ObjectProvider<TagService> tagServiceProvider;
  private final ObjectMapper objectMapper;

  public WorkflowEngine(
      WorkflowRuleService ruleService,
      WorkflowExecutionLogMapper logMapper,
      WorkflowNotificationService notificationService,
      TaskService taskService,
      com.crm.repository.LeadMapper leadMapper,
      com.crm.repository.CustomerMapper customerMapper,
      ObjectProvider<EmailCampaignService> emailCampaignProvider,
      ObjectProvider<TagService> tagServiceProvider,
      ObjectMapper objectMapper) {
    this.ruleService = ruleService;
    this.logMapper = logMapper;
    this.notificationService = notificationService;
    this.taskService = taskService;
    this.leadMapper = leadMapper;
    this.customerMapper = customerMapper;
    this.emailCampaignProvider = emailCampaignProvider;
    this.tagServiceProvider = tagServiceProvider;
    this.objectMapper = objectMapper;
  }

  /**
   * 触发事件：匹配并执行全部启用规则（失败隔离）。
   *
   * @param entityType 业务实体类型（LEAD/CUSTOMER/...）
   * @param entityId 业务实体 id
   * @param context 条件匹配字段（如 stage/name）
   */
  public void fire(
      String eventType, String entityType, Long entityId, Map<String, String> context) {
    List<WorkflowRule> rules;
    try {
      rules = ruleService.enabledRulesFor(eventType);
    } catch (Exception ex) {
      log.warn("Workflow rule lookup failed for {}: {}", eventType, ex.getMessage());
      return;
    }
    if (rules.isEmpty()) {
      return;
    }
    for (WorkflowRule rule : rules) {
      try {
        boolean matched = matches(rule, context);
        if (!matched) {
          record(rule, eventType, entityType, entityId, false, null, true, null);
          continue;
        }
        String result = execute(rule, entityType, entityId, context);
        record(rule, eventType, entityType, entityId, true, result, true, null);
      } catch (Exception ex) {
        log.error("Workflow rule {} execution failed: {}", rule.getId(), ex.getMessage());
        record(rule, eventType, entityType, entityId, true, null, false, ex.getMessage());
      }
    }
  }

  /** 条件匹配：conditionJson 的 field/value 与 context 等值；数值字段（score）支持 >= 阈值。 */
  private boolean matches(WorkflowRule rule, Map<String, String> context) {
    if (rule.getConditionJson() == null || rule.getConditionJson().isBlank()) {
      return true;
    }
    try {
      Map<String, String> condition =
          objectMapper.readValue(
              rule.getConditionJson(), new TypeReference<Map<String, String>>() {});
      String field = condition.get("field");
      String value = condition.get("value");
      if (field == null || value == null || context == null) {
        return false;
      }
      String contextValue = context.get(field);
      if (contextValue == null) {
        return false;
      }
      // 049：数值字段（score）按 >= 比较（评分阈值）
      if ("score".equals(field)) {
        try {
          return Double.parseDouble(contextValue) >= Double.parseDouble(value);
        } catch (NumberFormatException ex) {
          log.warn("Numeric condition parse failed: {} vs {}", contextValue, value);
          return false;
        }
      }
      return value.equals(contextValue);
    } catch (Exception ex) {
      log.warn("Failed to parse rule condition: {}", ex.getMessage());
      return false;
    }
  }

  /** 执行动作。 */
  private String execute(
      WorkflowRule rule, String entityType, Long entityId, Map<String, String> context)
      throws Exception {
    Map<String, Object> action =
        objectMapper.readValue(rule.getActionJson(), new TypeReference<Map<String, Object>>() {});
    return switch (rule.getActionType()) {
      case "CREATE_TASK" -> executeCreateTask(action, context);
      case "ASSIGN" -> executeAssign(action, entityType, entityId);
      case "NOTIFY" -> executeNotify(action);
      case "SEND_EMAIL" -> executeSendEmail(action, context);
      case "ADD_TAG" -> executeAddTag(action, entityType, entityId);
      default -> throw new IllegalArgumentException("Unknown action type: " + rule.getActionType());
    };
  }

  /** 049：发送模板邮件给线索邮箱。 */
  private String executeSendEmail(Map<String, Object> action, Map<String, String> context)
      throws Exception {
    long templateId = Long.parseLong(String.valueOf(action.get("templateId")));
    String email = context == null ? null : context.get("email");
    if (email == null || email.isBlank()) {
      return "无收件人邮箱，跳过发送";
    }
    emailCampaignProvider.getObject().sendAutomationEmail(templateId, email);
    return "已发送模板邮件 " + templateId + " → " + email;
  }

  /** 049：为线索/客户添加标签。 */
  private String executeAddTag(Map<String, Object> action, String entityType, Long entityId) {
    String tagName = String.valueOf(action.get("tag"));
    com.crm.dto.tag.TagResponse tag =
        tagServiceProvider
            .getObject()
            .list(entityType.equals("CUSTOMER") ? "CUSTOMER" : "LEAD")
            .stream()
            .filter(t -> t.getName().equals(tagName))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("标签不存在: " + tagName));
    if ("CUSTOMER".equals(entityType)) {
      tagServiceProvider.getObject().setCustomerTags(entityId, java.util.List.of(tag.getId()));
      return "已为客户 " + entityId + " 添加标签 " + tagName;
    }
    return "标签动作当前仅支持客户实体";
  }

  /** 创建任务：title 模板替换（{name}），dueAt=now+dueDays。 */
  private String executeCreateTask(Map<String, Object> action, Map<String, String> context) {
    String titleTemplate = String.valueOf(action.get("titleTemplate"));
    String name = context == null ? "" : String.valueOf(context.getOrDefault("name", ""));
    String title = titleTemplate.replace("{name}", name == null ? "" : name);
    int dueDays =
        action.get("dueDays") == null ? 3 : Integer.parseInt(String.valueOf(action.get("dueDays")));

    TaskRequest req = new TaskRequest();
    req.setTitle(title);
    req.setDueAt(LocalDateTime.now().plusDays(dueDays));
    req.setPriority("MEDIUM");
    var task = taskService.create(req);
    return "已创建任务 #" + task.getId();
  }

  /** 自动分配：线索/客户 ownerId → 目标用户。 */
  private String executeAssign(Map<String, Object> action, String entityType, Long entityId) {
    Long targetUserId = Long.valueOf(String.valueOf(action.get("targetUserId")));
    if ("LEAD".equals(entityType)) {
      int rows =
          leadMapper.update(
              null,
              new LambdaUpdateWrapper<com.crm.entity.Lead>()
                  .eq(com.crm.entity.Lead::getId, entityId)
                  .set(com.crm.entity.Lead::getOwnerId, targetUserId));
      return rows > 0 ? "已分配线索给用户 " + targetUserId : "线索不存在";
    }
    if ("CUSTOMER".equals(entityType)) {
      int rows =
          customerMapper.update(
              null,
              new LambdaUpdateWrapper<com.crm.entity.Customer>()
                  .eq(com.crm.entity.Customer::getId, entityId)
                  .set(com.crm.entity.Customer::getOwnerId, targetUserId));
      return rows > 0 ? "已分配客户给用户 " + targetUserId : "客户不存在";
    }
    return "不支持的分配实体: " + entityType;
  }

  /** 站内通知。 */
  private String executeNotify(Map<String, Object> action) {
    String message = String.valueOf(action.get("message"));
    // 通知当前操作者（简化：无目标用户配置时通知管理员/操作者，v1 固定通知操作者）
    Long operator = com.crm.security.SecurityUtil.currentUserId();
    if (operator != null) {
      notificationService.notify(operator, message);
      return "已通知用户 " + operator;
    }
    return "无操作者，跳过通知";
  }

  private void record(
      WorkflowRule rule,
      String eventType,
      String entityType,
      Long entityId,
      boolean matched,
      String actionResult,
      boolean success,
      String errorMessage) {
    WorkflowExecutionLog logEntity = new WorkflowExecutionLog();
    logEntity.setRuleId(rule.getId());
    logEntity.setEventType(eventType);
    logEntity.setEntityType(entityType);
    logEntity.setEntityId(entityId);
    logEntity.setMatched(matched);
    logEntity.setActionResult(actionResult);
    logEntity.setSuccess(success);
    logEntity.setErrorMessage(errorMessage);
    try {
      logMapper.insert(logEntity);
    } catch (Exception ex) {
      log.warn("Failed to record workflow log: {}", ex.getMessage());
    }
  }
}
