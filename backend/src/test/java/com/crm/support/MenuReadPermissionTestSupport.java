package com.crm.support;

import com.crm.common.RoleConstants;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 菜单项 ↔ 服务端闸门的对照表，以及预置角色授权的解析器（084 T014）。
 *
 * <p><b>这张表要回答的问题</b>：FR-N06 要求「每个已被授予的菜单项都必须打得开」——即角色的 {@code role_menu}
 * 里有这一项时，其页面首屏的读接口也必须对它放行。两侧过去是两套互不知情的开关 （菜单授权一份、端点守卫另一份），于是出现「接口放行、码已授、菜单永不出现」与「菜单出现了、 页面却
 * 403」两种反向断链。{@link com.crm.security.MenuAccessGrantAlignmentTest} 用这张表 + 迁移里
 * 的真实授权数据机械地算出缺口，而不是靠人眼看一遍。
 *
 * <p><b>为什么必须逐项写满 56 项</b>：表里漏一项，C3 变红（不是静默跳过）。这正是本规格要防的病—— 无声的缺口比报错的缺口危险得多。
 *
 * <p><b>口径（机械可复核，不靠印象）</b>：每项取「该菜单项对应页面首屏读接口」的服务端闸门， 三种形态之一：
 *
 * <ul>
 *   <li>{@link Kind#CODE}：读接口挂 {@code @RequirePermission("码")} ⇒ 角色必须持有该码；
 *   <li>{@link Kind#OPEN}：读接口不设任何权限码（仅要求已登录，配合行级数据范围）⇒ 无码可持；
 *   <li>{@link Kind#ROLE}：读接口按**角色名字面量**放行（字典里没有对应的码）⇒ 角色必须在放行集合里。
 *       这一形态是历史遗留的粗粒度门，本身就是「菜单授权与端点闸门两套开关」的残留—— 记下来才能量出它挡住谁。
 * </ul>
 *
 * <p><b>已知边界（刻意不覆盖）</b>：两点，都不在本表的机械范围内，以免表随前端长草：
 *
 * <ul>
 *   <li><b>借分组显示的子页面</b>（{@code App.tsx} 的 {@code SUB_PAGE_AFTER_MENU_KEY}，共 2 条： {@code
 *       /customers/at-risk}、{@code /marketing/roi}、{@code /workflows/logs} 中的后两条
 *       挂在别的菜单项之后）。它们没有自己的菜单项，故不是本表的行。
 *   <li><b>只影响局部功能的子端点</b>（如 {@code GET /customers/export} 挂 {@code customer:export}）：
 *       页面能打开、只有那个按钮点不动，属"按钮级"而非"页面级"，归权限矩阵而非本表。
 * </ul>
 *
 * <p><b>为什么自己解析迁移而不是查库</b>：本文件是纯单测夹具，跑在 surefire 里、不起 Spring 上下文 （与 {@link
 * PermissionDictionaryTestSupport} 同一取舍）。迁移文件是授权的**唯一**来源（V46 建表种子、 V75 的 10 个预置角色、V80–V84
 * 的对齐批次），从它们推演与真实部署一致，且不需要 H2/MySQL。
 */
public final class MenuReadPermissionTestSupport {

  /** 菜单项所需闸门的形态。 */
  public enum Kind {
    /** 读接口挂权限码 ⇒ 角色须持有该码。 */
    CODE,
    /** 读接口不设权限码（仅登录 + 行级数据范围）。 */
    OPEN,
    /** 读接口按角色名字面量放行。 */
    ROLE
  }

  /**
   * 一条闸门要求。
   *
   * @param kind 形态
   * @param code {@link Kind#CODE} 时的权限码，其余形态为 {@code null}
   * @param roles {@link Kind#ROLE} 时的放行角色集合，其余形态为空集
   * @param evidence 判定依据（Controller + 端点 + 实际注解），供复核时直接跳过去看
   */
  public record Requirement(Kind kind, String code, Set<String> roles, String evidence) {

    public Requirement {
      roles = Set.copyOf(roles);
    }
  }

  /** 一行对照表：菜单键 + 其闸门要求。 */
  public record MenuRequirement(String menuKey, Requirement requirement) {}

  private static Requirement code(String code, String evidence) {
    return new Requirement(Kind.CODE, code, Set.of(), evidence);
  }

  private static Requirement open(String evidence) {
    return new Requirement(Kind.OPEN, null, Set.of(), evidence);
  }

  private static Requirement role(String evidence, String... roles) {
    return new Requirement(Kind.ROLE, null, Set.of(roles), evidence);
  }

  private static MenuRequirement item(String menuKey, Requirement requirement) {
    return new MenuRequirement(menuKey, requirement);
  }

  /**
   * 全部 56 个菜单项 → 闸门。顺序与 {@link RoleConstants#MENU_TREE} 一致（便于与配置页逐行对照）。
   *
   * <p>每一项的 {@code evidence} 都指向实际注解所在处；改动端点守卫时，这里要同步改——C2 会拦住 「表里写了一个没人校验的码」，但拦不住「端点改了、表没改」，那要靠
   * code review。
   */
  public static final List<MenuRequirement> MENU_REQUIREMENTS =
      List.of(
          // ---------- 首页 ----------
          item("stats", open("StatsController#dashboard（GET /stats/dashboard 无注解；首页取的就是它）")),
          // ---------- 客户管理 ----------
          item(
              "leads",
              open(
                  "LeadController#page（GET /leads 无注解，LeadService.page 走 resolveVisibleOwnerIds；"
                      + "写与 GET /export 才挂码）")),
          item(
              "customers",
              open(
                  "CustomerController#page（GET /customers 无注解，applyDataScopeFilter + checkViewPermission）")),
          item("contacts", open("ContactController#page（GET /contacts 无注解）")),
          item(
              "customer-merge",
              code(
                  "customer:merge",
                  "CustomerMergeController#duplicates（GET /customers/duplicates）")),
          item("at-risk", open("CustomerController#atRisk（GET /customers/health/at-risk 无注解）")),
          item(
              "tags",
              open("TagController#list 与 SegmentController#list 均无注解——本页两个 Tab（标签/细分）都读得到")),
          // ---------- 销售管理 ----------
          item(
              "opportunities",
              code("opportunity:read", "OpportunityController（GET 全挂 opportunity:read）")),
          item(
              "sales-opportunities",
              code("opportunity:read", "SalesOpportunityController（GET 全挂 opportunity:read）")),
          item("quotes", code("quote:read", "QuoteController（GET 全挂 quote:read）")),
          item("visits", open("FieldVisitController#page（GET /field-visits 无注解；visit:manage 只挂写）")),
          item(
              "products",
              open("ProductController#page/#detail（GET /products、/{id} 无注解；写挂 hasRole('ADMIN')）")),
          item(
              "playbook",
              role(
                  "PlaybookController#stageActions（GET /stage-actions 为 @PreAuthorize(\"hasRole('ADMIN')\")；"
                      + "字典里没有 playbook 读码，而本页首屏就取它）",
                  "ADMIN")),
          item(
              "quotas",
              role(
                  "SalesQuotaController 类级 @PreAuthorize(\"hasAnyRole('ADMIN', 'SALES_MANAGER')\")",
                  "ADMIN",
                  "SALES_MANAGER")),
          // ---------- 交易管理 ----------
          item("contracts", code("contract:read", "ContractController（GET 全挂 contract:read）")),
          item(
              "contract-renewal",
              code(
                  "contract:renewal",
                  "ContractRenewalController#overview（GET /contracts/renewal-overview）")),
          item("orders", code("order:read", "OrderController（GET 全挂 order:read）")),
          item("invoices", code("invoice:read", "InvoiceController（GET 全挂 invoice:read）")),
          // ---------- 营销管理 ----------
          item(
              "marketing",
              open(
                  "MarketingController#page（GET /campaigns 无注解；/channel-roi 另挂 ADMIN/SALES，"
                      + "属 /marketing/roi 子页，不在本表口径内）")),
          item(
              "marketing/email",
              code(
                  "email:manage",
                  "EmailController（GET /email-templates、/email-campaigns 全挂 email:manage）")),
          item(
              "email-unsubscribes",
              code("email:manage", "EmailController#unsubscribes（GET /email/unsubscribes）")),
          item(
              "online-forms",
              code(
                  "form:manage",
                  "FormController（GET /forms、/forms/{id}/submissions 挂 form:manage）")),
          item(
              "landing-pages",
              code(
                  "marketing:manage",
                  "LandingPageController（GET /landing-pages、/{id}/stats 挂 marketing:manage）")),
          // ---------- 客户服务 ----------
          item("tickets", code("ticket:read", "TicketController（GET 全挂 ticket:read）")),
          item(
              "knowledge",
              code("knowledge:read", "KnowledgeArticleController（GET 挂 knowledge:read）")),
          item(
              "announcements",
              open("AnnouncementController#page（GET /announcements 无注解；announcement:manage 只挂写）")),
          item(
              "portal", open("CustomerPortalController（/api/v1/public/portal/** 为公开门户 API，全部无注解）")),
          item(
              "satisfaction",
              code(
                  "ticket:read", "TicketSurveyController#stats（GET /surveys/stats 挂 ticket:read）")),
          item("sla-calendar", code("sla:manage", "SlaCalendarController（GET 挂 sla:manage）")),
          // ---------- 工作台 ----------
          item("tasks", open("TaskController#page（GET /tasks 无注解；task:create/update/delete 只挂写）")),
          item("suggestions", open("SuggestionController#page（GET /suggestions 无注解）")),
          item(
              "call-records",
              code("call_record:read", "CallRecordController（GET 挂 call_record:read）")),
          item(
              "mail-sync",
              role(
                  "MailAccountController#page（GET /mail-accounts 为 hasRole('ADMIN')；记录子端点放 ADMIN/SALES）",
                  "ADMIN")),
          item(
              "approvals",
              open(
                  "ApprovalController#todos（GET /approvals/todos 无注解；approval-flows 两条才挂 workflow:manage）")),
          // ---------- 数据分析 ----------
          item("reports", code("report:view", "ReportController（GET 全挂 report:view）")),
          item(
              "stats/leaderboard", open("StatsController#leaderboard（GET /stats/leaderboard 无注解）")),
          item(
              "exports",
              open("ExportController#page（GET /exports 只返回本人任务，无注解；export:create 只挂 POST）")),
          item(
              "exports/scheduled",
              code("export:scheduled", "ScheduledExportController（GET 挂 export:scheduled）")),
          item(
              "data-vision",
              code("kpi:view", "StatsController#kpiBoard（GET /stats/kpi-board）；数据大屏页只调它")),
          // ---------- 系统管理 ----------
          item("users", code("user:manage", "UserController（含 GET，全挂 user:manage）")),
          item("roles", code("role:manage", "RoleController（含 GET，全挂 role:manage）")),
          item(
              "departments",
              code("department:manage", "DepartmentController（GET 挂 department:manage）")),
          item(
              "currencies",
              code(
                  "currency:read",
                  "CurrencyRateController#list（GET /currencies；084 T020 起挂新增读码 currency:read，"
                      + "此前是 hasAnyRole('ADMIN','SALES')）")),
          // ---------- 流程配置 ----------
          item(
              "workflows",
              role(
                  "WorkflowController（GET /workflows/rules、/workflows/logs 均为 hasRole('ADMIN')）",
                  "ADMIN")),
          item(
              "approval-flows",
              code("workflow:manage", "ApprovalController（/approval-flows 四条挂 workflow:manage）")),
          item("sla-policies", code("sla:manage", "SlaPolicyController（GET 挂 sla:manage）")),
          item(
              "opportunity-stages",
              open(
                  "OpportunityStageController#list（GET /opportunity-stages 无注解；stage:manage 只挂写）")),
          item(
              "contract-templates",
              open(
                  "ContractTemplateController#list（GET /contract-templates 无注解；写挂 hasRole('ADMIN')）")),
          item(
              "settings/custom-fields",
              code("custom_field:read", "CustomFieldController（GET 挂 custom_field:read）")),
          item(
              "field-permissions",
              code(
                  "field_permission:manage",
                  "FieldPermissionController（含 GET，挂 field_permission:manage）")),
          item(
              "custom-objects",
              code(
                  "custom_object:read",
                  "CustomObjectController#page（GET /custom-objects；084 前是 hasRole('ADMIN')，V85 起改挂 custom_object:read）")),
          item(
              "open-platform",
              role(
                  "OpenPlatformController（GET /platform/api-keys、/platform/webhooks 均为 hasRole('ADMIN')）",
                  "ADMIN")),
          item(
              "integration-hub", code("integration:manage", "IntegrationChannelController（含 GET）")),
          // ---------- 审计维护 ----------
          item("audit-logs", code("audit:view", "AuditLogController（GET 挂 audit:view）")),
          item("recycle-bin", code("recycle:view", "RecycleBinController（GET 挂 recycle:view）")),
          item(
              "data-retention",
              open(
                  "DataRetentionPolicyController（GET /policies、/{id}、/{id}/executions 无注解；retention:* 只挂写与 execute）")));

  /** 菜单键 → 要求。键重复时立刻失败，避免表中两项互相覆盖。 */
  public static Map<String, Requirement> requirementByMenuKey() {
    Map<String, Requirement> byKey = new LinkedHashMap<>();
    for (MenuRequirement row : MENU_REQUIREMENTS) {
      Requirement previous = byKey.put(row.menuKey(), row.requirement());
      if (previous != null) {
        throw new IllegalStateException("对照表里菜单键重复：" + row.menuKey());
      }
    }
    return byKey;
  }

  /** {@link RoleConstants#MENU_TREE} 里全部菜单键，**保持权威定义的顺序**（展平后不去重不排序）。 */
  @SuppressWarnings("unchecked")
  public static List<String> menuKeysInOrder() {
    List<String> keys = new ArrayList<>();
    for (Map<String, Object> group : RoleConstants.MENU_TREE) {
      for (Map<String, Object> child : (List<Map<String, Object>>) group.get("children")) {
        keys.add((String) child.get("key"));
      }
    }
    return keys;
  }

  // ------------------------------------------------------------------
  // 迁移解析：从 db/migration 的授权语句推演「谁被授了什么」
  // ------------------------------------------------------------------

  /** 一个角色持有的菜单键与权限码。 */
  public record Grants(Map<String, Set<String>> menus, Map<String, Set<String>> codes) {

    /** 该角色持有的菜单键（无授权时为不可变空集）。 */
    public Set<String> menusOf(String roleCode) {
      return menus.getOrDefault(roleCode, Set.of());
    }

    /** 该角色持有的权限码（无授权时为不可变空集）。 */
    public Set<String> codesOf(String roleCode) {
      return codes.getOrDefault(roleCode, Set.of());
    }
  }

  /** 解析结果 + 防呆计数。 */
  public record Parsed(
      Grants grants, Set<String> roleCodes, int statementsParsed, int occurrences) {}

  private static final Path MIGRATION_DIR =
      Paths.get("backend", "src", "main", "resources", "db", "migration");

  private static final String T_ROLE_MENU = "`role_menu`";
  private static final String T_ROLE_PERMISSION = "`role_permission`";
  private static final String T_ROLE = "`role`";

  private static final Pattern ROLE_EQ = Pattern.compile("r\\.code\\s*=\\s*'([A-Z][A-Z0-9_]*)'");
  private static final Pattern ROLE_IN = Pattern.compile("r\\.code\\s+IN\\s*\\(([^)]*)\\)");
  private static final Pattern ROLE_NE = Pattern.compile("r\\.code\\s*<>\\s*'([A-Z][A-Z0-9_]*)'");
  private static final Pattern JOIN_BLOCK =
      Pattern.compile("JOIN\\s*\\((.*?)\\)\\s*\\w", Pattern.DOTALL);
  private static final Pattern DIRECT_KEY = Pattern.compile("SELECT\\s+r\\.id\\s*,\\s*'([^']+)'");
  private static final Pattern LITERAL = Pattern.compile("'([^']+)'");
  private static final Pattern UPDATE_MENU_KEY =
      Pattern.compile(
          "SET\\s+`menu_key`\\s*=\\s*'([^']+)'\\s+WHERE\\s+`menu_key`\\s*=\\s*'([^']+)'");
  private static final Pattern DELETE_MENU_KEYS =
      Pattern.compile("DELETE FROM\\s+`role_menu`\\s+WHERE\\s+`menu_key`\\s+IN\\s*\\(([^)]*)\\)");
  private static final Pattern DELETE_CODES =
      Pattern.compile(
          "DELETE FROM\\s+`role_permission`\\s+WHERE\\s+`permission_code`\\s+IN\\s*\\(([^)]*)\\)");
  private static final Pattern ROLE_ROW = Pattern.compile("\\(\\s*'([A-Z][A-Z0-9_]*)'\\s*,\\s*'");

  private static final Parsed PARSED = parse();

  /** 全部角色的授权（菜单 + 权限码）。解析只做一次。 */
  public static Grants grants() {
    return PARSED.grants();
  }

  /** 迁移里建出的全部角色编码（3 个内建 + 10 个预置）。 */
  public static Set<String> roleCodes() {
    return PARSED.roleCodes();
  }

  /** 白名单解析到的语句数（防呆断言：必须等于 {@link #occurrences()}）。 */
  public static int statementsParsed() {
    return PARSED.statementsParsed();
  }

  /** 迁移文件里出现 `role_menu` / `role_permission` 的语句数（含 DDL）。 */
  public static int occurrences() {
    return PARSED.occurrences();
  }

  /**
   * 解析全部迁移里的角色与授权语句。
   *
   * <p><b>白名单式</b>：只认识 {@code INSERT INTO}/{@code UPDATE}/{@code DELETE FROM} 与建表语句； 任何其它形态（{@code
   * REPLACE INTO}、{@code MERGE}、{@code TRUNCATE}、后来新增的写法）都会**立刻
   * 带着该语句原文失败**，而不是被安静跳过。理由：解析器漏一条语句的后果是「一个角色凭空多出授权」或 「缺口被漏报」，两者都不会有任何症状——护栏必须在这种地方响。
   *
   * <p>{@code CREATE TABLE} 记为「建表，不产生授权」然后计入分子。这是必须显式处理的一类：`role_menu` 这个串在 {@code V46} 里出现 4 次，其中
   * 1 次就是建表——若只按「出现次数」计数而不认建表语句， 防呆断言会在 V46 上误报。
   */
  private static Parsed parse() {
    Map<String, Set<String>> menus = new LinkedHashMap<>();
    Map<String, Set<String>> codes = new LinkedHashMap<>();
    Set<String> roles = new LinkedHashSet<>();
    int parsed = 0;
    int occurrences = 0;

    for (Path file : sqlFiles()) {
      for (String statement : splitStatements(read(file))) {
        boolean touchesMenu = statement.contains(T_ROLE_MENU);
        boolean touchesCodes = statement.contains(T_ROLE_PERMISSION);
        // `role` 的建角色语句：这里只收角色编码，用来解 r.code <> 'X' 的补集形态
        if (!touchesMenu && !touchesCodes) {
          if (statement.contains("INSERT INTO " + T_ROLE)) {
            for (Matcher row = ROLE_ROW.matcher(statement); row.find(); ) {
              roles.add(row.group(1));
            }
          }
          continue;
        }

        occurrences++;
        String keyword = keywordOf(statement);
        switch (keyword) {
          case "CREATE TABLE" -> {
            // 只建表，不产生任何授权（V46 的 role_menu / role_permission 建表）
            parsed++;
          }
          case "ALTER TABLE", "DROP TABLE", "TRUNCATE TABLE" -> throw unrecognized(file, statement);
          case "INSERT INTO" -> {
            Set<String> targets = targets(statement, roles);
            Set<String> keys = keys(statement);
            Map<String, Set<String>> sink = touchesMenu ? menus : codes;
            for (String role : targets) {
              sink.computeIfAbsent(role, r -> new LinkedHashSet<>()).addAll(keys);
            }
            parsed++;
          }
          case "UPDATE" -> {
            Matcher rename = UPDATE_MENU_KEY.matcher(statement);
            if (!rename.find()) {
              throw unrecognized(file, statement);
            }
            // 改键：所有持有旧键的角色改持新键（V80 的 leaderboard → stats/leaderboard）
            for (Set<String> held : menus.values()) {
              if (held.remove(rename.group(2))) {
                held.add(rename.group(1));
              }
            }
            parsed++;
          }
          case "DELETE FROM" -> {
            boolean menuDelete = statement.contains(T_ROLE_MENU);
            Matcher removed = (menuDelete ? DELETE_MENU_KEYS : DELETE_CODES).matcher(statement);
            if (!removed.find()) {
              throw unrecognized(file, statement);
            }
            Set<String> doomed = literals(removed.group(1));
            Map<String, Set<String>> sink = menuDelete ? menus : codes;
            for (Set<String> held : sink.values()) {
              held.removeAll(doomed);
            }
            parsed++;
          }
          default -> throw unrecognized(file, statement);
        }
      }
    }

    if (roles.size() < 13) {
      throw new IllegalStateException(
          "只从迁移里解析出 " + roles.size() + " 个角色（内建 3 + 预置 10 应为 13）：" + roles);
    }
    return new Parsed(new Grants(menus, codes), roles, parsed, occurrences);
  }

  /** 语句的目标角色集合（{@code r.code = 'X'} / {@code IN (...)} / {@code <> 'X'}）。 */
  private static Set<String> targets(String statement, Set<String> allRoles) {
    Matcher eq = ROLE_EQ.matcher(statement);
    if (eq.find()) {
      return Set.of(eq.group(1));
    }
    Matcher in = ROLE_IN.matcher(statement);
    if (in.find()) {
      return literals(in.group(1));
    }
    Matcher ne = ROLE_NE.matcher(statement);
    if (ne.find()) {
      Set<String> complement = new LinkedHashSet<>(allRoles);
      complement.remove(ne.group(1));
      return complement;
    }
    throw new IllegalStateException(
        "这条授权语句的目标角色解析不出来（既不是 = 'X'，也不是 IN (...) / <> 'X'）：" + oneLine(statement));
  }

  /** 语句授予的键集合：子查询清单（JOIN + UNION）或单键直写。 */
  private static Set<String> keys(String statement) {
    Matcher join = JOIN_BLOCK.matcher(statement);
    if (join.find()) {
      Set<String> keys = literals(join.group(1));
      if (keys.isEmpty()) {
        throw new IllegalStateException("JOIN 子查询里一个键都没有：" + oneLine(statement));
      }
      return keys;
    }
    Matcher direct = DIRECT_KEY.matcher(statement);
    if (direct.find()) {
      return Set.of(direct.group(1));
    }
    throw new IllegalStateException("这条授权语句授予了什么解析不出来：" + oneLine(statement));
  }

  private static Set<String> literals(String text) {
    Set<String> values = new LinkedHashSet<>();
    Matcher matcher = LITERAL.matcher(text);
    while (matcher.find()) {
      values.add(matcher.group(1));
    }
    return values;
  }

  private static IllegalStateException unrecognized(Path file, String statement) {
    return new IllegalStateException(
        "白名单之外的授权语句形态（解析器不认识它，若放过去就会静默漏掉一条授权）：" + file.getFileName() + " -> " + oneLine(statement));
  }

  /**
   * 语句的首关键词。两词形态（{@code INSERT INTO} / {@code DELETE FROM} / 建表类）要按两词取， 只取首词会得到 {@code
   * INSERT}/{@code CREATE}，于是每条语句都掉进 {@code default} 分支。
   */
  private static String keywordOf(String statement) {
    String head = statement.stripLeading().toUpperCase();
    for (String twoWord :
        List.of(
            "INSERT INTO",
            "DELETE FROM",
            "CREATE TABLE",
            "ALTER TABLE",
            "DROP TABLE",
            "TRUNCATE TABLE")) {
      if (head.startsWith(twoWord)) {
        return twoWord;
      }
    }
    int space = head.indexOf(' ');
    return space < 0 ? head : head.substring(0, space);
  }

  private static String oneLine(String text) {
    String flat = text.replaceAll("\\s+", " ").strip();
    return flat.length() > 200 ? flat.substring(0, 200) + " …" : flat;
  }

  /** 按分号切分语句，先去掉注释（注释里出现的表名不该被当成语句）。 */
  private static List<String> splitStatements(String sql) {
    List<String> statements = new ArrayList<>();
    for (String raw : stripComments(sql).split(";")) {
      String statement = raw.strip();
      if (!statement.isEmpty()) {
        statements.add(statement);
      }
    }
    return statements;
  }

  /** 去行注释（双横线）与块注释（斜杠星号），跳过字符串字面量内部的假注释。 */
  private static String stripComments(String sql) {
    StringBuilder out = new StringBuilder(sql.length());
    boolean inSingle = false;
    boolean inDouble = false;
    boolean inBacktick = false;
    for (int i = 0; i < sql.length(); i++) {
      char c = sql.charAt(i);
      char next = i + 1 < sql.length() ? sql.charAt(i + 1) : '\0';
      if (!inSingle && !inDouble && !inBacktick) {
        if (c == '-' && next == '-') {
          while (i < sql.length() && sql.charAt(i) != '\n') {
            i++;
          }
          out.append('\n');
          continue;
        }
        if (c == '/' && next == '*') {
          i += 2;
          while (i + 1 < sql.length() && !(sql.charAt(i) == '*' && sql.charAt(i + 1) == '/')) {
            i++;
          }
          i++;
          out.append('\n');
          continue;
        }
        if (c == '\'') {
          inSingle = true;
        } else if (c == '"') {
          inDouble = true;
        } else if (c == '`') {
          inBacktick = true;
        }
      } else if (c == '\'' && inSingle) {
        inSingle = false;
      } else if (c == '"' && inDouble) {
        inDouble = false;
      } else if (c == '`' && inBacktick) {
        inBacktick = false;
      }
      out.append(c);
    }
    return out.toString();
  }

  private static List<Path> sqlFiles() {
    Path dir = resolveFromRepoRoot(MIGRATION_DIR);
    try (Stream<Path> files = Files.list(dir)) {
      List<Path> sql =
          files.filter(p -> p.getFileName().toString().endsWith(".sql")).sorted().toList();
      if (sql.isEmpty()) {
        throw new IllegalStateException("迁移目录里一个 .sql 都没有：" + dir);
      }
      return sql;
    } catch (IOException e) {
      throw new UncheckedIOException("读取迁移目录失败：" + dir, e);
    }
  }

  private static String read(Path file) {
    try {
      return Files.readString(file, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("读取迁移文件失败：" + file, e);
    }
  }

  /**
   * 从工作目录向上找仓库根（同时含 {@code frontend/} 与 {@code backend/} 的那一层）， 与 {@code MenuRouteAlignmentTest}
   * 同一做法，以免依赖 surefire 的 basedir。
   */
  private static Path resolveFromRepoRoot(Path repoRelative) {
    Path dir = Paths.get("").toAbsolutePath();
    for (int depth = 0; depth < 6 && dir != null; depth++) {
      if (Files.exists(dir.resolve("frontend")) && Files.exists(dir.resolve("backend"))) {
        return dir.resolve(repoRelative);
      }
      dir = dir.getParent();
    }
    throw new IllegalStateException(
        "找不到仓库根：从 " + Paths.get("").toAbsolutePath() + " 向上 6 层都没有同时含 frontend/ 与 backend/ 的目录。");
  }

  private MenuReadPermissionTestSupport() {}
}
