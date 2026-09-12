package com.crm.common;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 角色权限字典（081-role-permissions-update，与 design.md 一致）。 菜单 key 与前端路由对齐；权限码格式 实体:动作。单源定义，前端角色页树/勾选复用。
 */
public final class RoleConstants {

  private RoleConstants() {}

  /** 菜单分组 → 菜单项（menuKey, 中文名）。 */
  public static final List<Map<String, Object>> MENU_TREE =
      List.of(
          group("首页", item("stats", "首页")),
          group(
              "客户管理",
              item("leads", "线索"),
              item("customers", "客户"),
              item("contacts", "联系人"),
              item("customer-merge", "查重合并"),
              item("at-risk", "流失预警")),
          group(
              "销售管理",
              item("opportunities", "商机"),
              item("sales-opportunities", "销售机会"),
              item("quotes", "报价单"),
              item("visits", "外勤拜访"),
              item("products", "产品"),
              item("playbook", "销售 Playbook")),
          group(
              "交易管理",
              item("contracts", "合同"),
              item("contract-renewal", "合同续约"),
              item("orders", "订单"),
              item("invoices", "发票")),
          group(
              "营销管理",
              item("marketing", "营销活动"),
              item("marketing/email", "邮件营销"),
              item("email-unsubscribes", "邮件退订"),
              item("online-forms", "在线表单"),
              item("landing-pages", "落地页")),
          group(
              "客户服务",
              item("tickets", "工单管理"),
              item("knowledge", "知识库"),
              item("announcements", "公告管理"),
              item("approvals", "我的审批"),
              item("portal", "客户门户"),
              item("satisfaction", "满意度调查"),
              item("sla-calendar", "SLA 日历")),
          group(
              "工作台",
              item("tasks", "任务管理"),
              item("suggestions", "智能建议"),
              item("data-vision", "数据大屏"),
              item("call-records", "通话记录"),
              item("mail-sync", "邮件同步")),
          group(
              "数据分析",
              item("reports", "自定义报表"),
              item("stats/leaderboard", "团队排行"),
              item("exports", "导出中心"),
              item("quotas", "销售配额"),
              item("exports/scheduled", "定时导出"),
              item("data-retention", "数据保留策略")),
          group(
              "系统管理",
              item("users", "用户管理"),
              item("roles", "角色权限"),
              item("departments", "部门管理"),
              item("field-permissions", "字段权限"),
              item("currencies", "多币种")),
          group(
              "流程配置",
              item("workflows", "工作流"),
              item("approval-flows", "审批流"),
              item("sla-policies", "SLA 策略"),
              item("contract-templates", "合同模板"),
              item("settings/custom-fields", "自定义字段"),
              item("custom-objects", "自定义对象"),
              item("open-platform", "开放平台"),
              item("integration-hub", "集成中心")),
          group(
              "审计维护",
              item("tags", "标签与细分"),
              item("audit-logs", "审计日志"),
              item("recycle-bin", "回收站")));

  /** 权限点分组 → 权限码（code, 中文 label）。 */
  public static final List<Map<String, Object>> PERMISSION_DEFS =
      List.of(
          permGroup(
              "客户管理",
              perm("customer:create", "创建客户"),
              perm("customer:update", "编辑客户"),
              perm("customer:delete", "删除客户"),
              perm("customer:transfer", "转移客户"),
              perm("customer:import", "导入客户"),
              perm("customer:export", "导出客户"),
              perm("customer:merge", "查重合并")),
          permGroup(
              "线索管理",
              perm("lead:create", "创建线索"),
              perm("lead:update", "编辑线索"),
              perm("lead:delete", "删除线索"),
              perm("lead:convert", "转化为客户"),
              perm("lead:assign", "分配线索")),
          permGroup(
              "联系人管理",
              perm("contact:create", "创建联系人"),
              perm("contact:update", "编辑联系人"),
              perm("contact:delete", "删除联系人")),
          permGroup(
              "商机管理",
              perm("opportunity:create", "创建商机"),
              perm("opportunity:update", "编辑商机"),
              perm("opportunity:delete", "删除商机"),
              perm("opportunity:export", "导出商机")),
          permGroup(
              "销售配额",
              perm("quota:create", "创建配额"),
              perm("quota:update", "编辑配额"),
              perm("quota:delete", "删除配额"),
              perm("quota:breakdown", "配额分解"),
              perm("quota:achievement", "查看达成率")),
          permGroup(
              "合同管理",
              perm("contract:create", "创建合同"),
              perm("contract:update", "编辑合同"),
              perm("contract:delete", "删除合同"),
              perm("contract:approve", "审批合同"),
              perm("contract:renewal", "合同续约")),
          permGroup(
              "订单管理",
              perm("order:create", "创建订单"),
              perm("order:update", "编辑订单"),
              perm("order:delete", "删除订单"),
              perm("order:payment", "登记回款")),
          permGroup(
              "发票管理",
              perm("invoice:create", "创建发票"),
              perm("invoice:update", "编辑发票"),
              perm("invoice:delete", "删除发票")),
          permGroup(
              "报价单管理",
              perm("quote:create", "创建报价单"),
              perm("quote:update", "编辑报价单"),
              perm("quote:delete", "删除报价单"),
              perm("quote:approve", "审批报价单")),
          permGroup(
              "产品管理",
              perm("product:create", "创建产品"),
              perm("product:update", "编辑产品"),
              perm("product:delete", "删除产品")),
          permGroup(
              "营销活动",
              perm("campaign:create", "创建营销活动"),
              perm("campaign:update", "编辑营销活动"),
              perm("campaign:delete", "删除营销活动"),
              perm("campaign:export", "导出活动数据")),
          permGroup(
              "邮件营销",
              perm("email:create", "创建邮件"),
              perm("email:update", "编辑邮件"),
              perm("email:delete", "删除邮件"),
              perm("email:send", "发送邮件")),
          permGroup(
              "工单管理",
              perm("ticket:create", "创建工单"),
              perm("ticket:update", "编辑工单"),
              perm("ticket:delete", "删除工单"),
              perm("ticket:assign", "分配工单"),
              perm("ticket:reply", "回复工单"),
              perm("ticket:approve", "审批工单")),
          permGroup(
              "知识库",
              perm("knowledge:create", "创建文章"),
              perm("knowledge:update", "编辑文章"),
              perm("knowledge:delete", "删除文章")),
          permGroup(
              "任务管理",
              perm("task:create", "创建任务"),
              perm("task:update", "编辑任务"),
              perm("task:delete", "删除任务")),
          permGroup(
              "跟进管理",
              perm("follow_up:create", "创建跟进"),
              perm("follow_up:update", "编辑跟进"),
              perm("follow_up:delete", "删除跟进")),
          permGroup(
              "通话记录",
              perm("call_record:create", "创建通话记录"),
              perm("call_record:update", "编辑通话记录"),
              perm("call_record:delete", "删除通话记录")),
          permGroup(
              "工作流",
              perm("workflow:create", "创建工作流"),
              perm("workflow:update", "编辑工作流"),
              perm("workflow:delete", "删除工作流"),
              perm("workflow:manage", "管理工作流")),
          permGroup(
              "审批管理",
              perm("approval:create", "创建审批"),
              perm("approval:update", "编辑审批"),
              perm("approval:delete", "删除审批"),
              perm("approval:approve", "审批操作")),
          permGroup(
              "数据导出",
              perm("export:create", "创建导出"),
              perm("export:delete", "删除导出"),
              perm("export:scheduled", "定时导出"),
              perm("export:compliance", "合规导出")),
          permGroup(
              "数据保留",
              perm("retention:create", "创建策略"),
              perm("retention:update", "编辑策略"),
              perm("retention:delete", "删除策略"),
              perm("retention:execute", "执行归档")),
          permGroup(
              "系统管理",
              perm("user:manage", "用户管理"),
              perm("role:manage", "角色管理"),
              perm("department:manage", "部门管理"),
              perm("field_permission:manage", "字段权限管理"),
              perm("currency:manage", "多币种管理"),
              perm("workflow:manage", "工作流管理"),
              perm("report:manage", "报表管理"),
              perm("system:manage", "系统管理")),
          permGroup(
              "审计维护",
              perm("audit:view", "查看审计日志"),
              perm("recycle:restore", "恢复数据"),
              perm("recycle:purge", "清理数据"),
              perm("tag:manage", "标签管理"),
              perm("segment:manage", "细分管理")));

  private static Map<String, Object> group(String title, Map<String, Object>... items) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("title", title);
    m.put("children", List.of(items));
    return m;
  }

  private static Map<String, Object> item(String key, String title) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("key", key);
    m.put("title", title);
    return m;
  }

  private static Map<String, Object> permGroup(String title, Map<String, Object>... perms) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("title", title);
    m.put("children", List.of(perms));
    return m;
  }

  private static Map<String, Object> perm(String code, String label) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("code", code);
    m.put("label", label);
    return m;
  }
}
