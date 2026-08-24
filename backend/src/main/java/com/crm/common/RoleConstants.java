package com.crm.common;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 角色权限字典（028-role-permissions，与 contracts/role-permissions.md 一致）。 菜单 key 与前端路由对齐；权限码格式
 * 实体:动作。单源定义，前端角色页树/勾选复用。
 */
public final class RoleConstants {

  private RoleConstants() {}

  /** 菜单分组 → 菜单项（menuKey, 中文名）。 */
  public static final List<Map<String, Object>> MENU_TREE =
      List.of(
          group("首页", item("stats", "首页")),
          group("客户管理", item("leads", "线索"), item("customers", "客户"), item("contacts", "联系人")),
          group(
              "销售管理",
              item("opportunities", "商机"),
              item("sales-opportunities", "销售机会"),
              item("quotes", "报价单")),
          group("交易管理", item("contracts", "合同"), item("orders", "订单")),
          group("基础资料", item("tasks", "任务"), item("products", "产品")),
          group(
              "营销与服务", item("marketing", "营销"), item("tickets", "客户服务"), item("knowledge", "知识库")),
          group(
              "数据分析",
              item("exports", "导出中心"),
              item("at-risk", "流失预警"),
              item("leaderboard", "团队排行"),
              item("reports", "自定义报表"),
              item("suggestions", "智能建议"),
              item("board", "数据大屏")),
          group(
              "系统管理",
              item("users", "用户管理"),
              item("departments", "部门"),
              item("workflows", "工作流"),
              item("sla-policies", "SLA 策略"),
              item("custom-fields", "自定义字段"),
              item("contract-templates", "合同模板"),
              item("audit-logs", "审计日志"),
              item("recycle-bin", "回收站")));

  /** 权限点分组 → 权限码（code, 中文 label）。 */
  public static final List<Map<String, Object>> PERMISSION_DEFS =
      List.of(
          permGroup(
              "客户",
              perm("customer:create", "创建客户"),
              perm("customer:update", "编辑客户"),
              perm("customer:delete", "删除客户"),
              perm("customer:merge", "客户查重合并"),
              perm("customer:transfer", "转移客户"),
              perm("visit:manage", "外勤拜访"),
              perm("invoice:manage", "发票管理"),
              perm("customer:import", "导入客户")),
          permGroup(
              "线索",
              perm("lead:create", "创建线索"),
              perm("lead:update", "编辑线索"),
              perm("lead:delete", "删除线索"),
              perm("lead:convert", "转化为客户"),
              perm("lead:assign", "分配/认领")),
          permGroup(
              "商机",
              perm("opportunity:create", "创建商机"),
              perm("opportunity:update", "编辑商机"),
              perm("opportunity:delete", "删除商机")),
          permGroup(
              "订单",
              perm("order:create", "创建订单"),
              perm("order:update", "编辑订单"),
              perm("order:delete", "删除订单"),
              perm("order:payment", "登记回款")),
          permGroup(
              "合同",
              perm("contract:create", "创建合同"),
              perm("contract:update", "编辑合同"),
              perm("contract:delete", "删除合同"),
              perm("contract:approve", "审批合同")),
          permGroup(
              "报价单",
              perm("quote:create", "创建报价单"),
              perm("quote:update", "编辑报价单"),
              perm("quote:delete", "删除报价单"),
              perm("quote:approve", "审批报价单")),
          permGroup(
              "工单",
              perm("ticket:create", "创建工单"),
              perm("ticket:update", "编辑工单"),
              perm("ticket:delete", "删除工单"),
              perm("ticket:assign", "分配处理人"),
              perm("ticket:reply", "回复工单")),
          permGroup(
              "系统",
              perm("user:manage", "用户管理"),
              perm("role:manage", "角色管理"),
              perm("tag:manage", "标签与细分管理"),
              perm("email:manage", "邮件营销管理"),
              perm("form:manage", "在线表单管理"),
              perm("announcement:manage", "公告管理"),
              perm("workflow:manage", "工作流管理"),
              perm("report:manage", "报表管理"),
              perm("system:manage", "系统管理")));

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
