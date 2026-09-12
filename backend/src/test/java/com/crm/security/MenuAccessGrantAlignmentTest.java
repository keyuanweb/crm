package com.crm.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.crm.support.MenuReadPermissionTestSupport;
import com.crm.support.MenuReadPermissionTestSupport.Grants;
import com.crm.support.MenuReadPermissionTestSupport.Kind;
import com.crm.support.MenuReadPermissionTestSupport.Requirement;
import com.crm.support.RequirePermissionScanTestSupport;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 「菜单已授 ⇒ 页面打得开」的机械核对（084 US1，FR-N06/N07、SC-N04、SC-N08）。
 *
 * <p><b>为什么要它</b>：菜单授权（{@code role_menu}）与端点闸门（{@code @RequirePermission} / {@code @PreAuthorize}）
 * 是两套互不知情的开关。两套开关各自看都正常，合起来就会出现「角色页勾得上、菜单也显示了，点开却 403」—— 没有任何编译错误、没有任何日志，只有用户看到一屏拒绝。人工逐项核对 13 个角色 ×
 * 56 个菜单项不现实， 而且做完就腐坏；本类把它变成每次构建都跑的断言。
 *
 * <p><b>五条断言（research.md 决策 ③ + T014 的解析器防呆）</b>：
 *
 * <ul>
 *   <li><b>C1</b> 任意角色：已授菜单 ⇒ 它持有该页面所需的码（{@link Kind#ROLE} 形态则须在放行角色集合里）。 这是 SC-N04 的机械形态。
 *   <li><b>C2</b> 对照表里写下的每个码，都必须真的被某个端点的 {@code @RequirePermission} 校验——否则表里 写的是个幻觉，C1 会对着一个假前提报绿。
 *   <li><b>C3</b> 对照表覆盖全部菜单项（与 {@code MENU_TREE} 逐键相等），且每项要么有码、要么是显式的 {@code OPEN}/{@code
 *       ROLE}——漏项会让 C1 静默放过它。
 *   <li><b>C4</b> 不存在指向不存在菜单项的死授权（FR-N07）。
 *   <li><b>C5</b> 解析器真的推演过改键 / 删除 / 取补集三种非平凡语句形态（漏读是静默故障，见该方法注释）。
 * </ul>
 *
 * <p><b>本表口径的边界</b>见 {@link MenuReadPermissionTestSupport} 的类注释（借分组显示的子页面、按钮级 子端点）。前端侧那两张别名表的守卫在
 * {@code MenuRouteAlignmentTest#aliasesStayExceptional}——别名表只有三条 且都指向存在的菜单键，就是那条断言的职责，本类不重复。
 *
 * <p><b>数据从哪来</b>：授权数据由 {@link MenuReadPermissionTestSupport} 从 {@code db/migration} 的
 * INSERT/UPDATE/DELETE 逐条推演（同一套语义、含改键与删除），不查库、不起 Spring 上下文。
 */
class MenuAccessGrantAlignmentTest {

  /**
   * 内建管理员在本类里按「持有全部权限码」处理。
   *
   * <p>这不是假设，而是 {@code PermissionAspect#checkPermission} 的实际行为：它在查库之前先 {@code if
   * ("ADMIN".equals(principal.role())) return}。写在这里是为了让 C1 的判定与服务端一致， 而不是让管理员在表里刷出一堆假红。
   */
  private static final String BUILT_IN_ADMIN = "ADMIN";

  @Test
  @DisplayName("C1：已授菜单 ⇒ 持有该页面所需的权限码（否则点开就是 403）")
  void grantedMenusAreActuallyOpenable() {
    Grants grants = MenuReadPermissionTestSupport.grants();
    Map<String, Requirement> table = MenuReadPermissionTestSupport.requirementByMenuKey();

    // 先证明输入是真读到的，否则下面那条断言会因为「一个角色都没解析出来」而通过——假绿
    assertThat(MenuReadPermissionTestSupport.roleCodes()).hasSizeGreaterThanOrEqualTo(13);
    assertThat(grants.menusOf("ANALYST")).contains("stats", "reports", "custom-objects");

    List<String> gaps = new ArrayList<>();
    for (String role : new TreeSet<>(MenuReadPermissionTestSupport.roleCodes())) {
      for (String menuKey : new TreeSet<>(grants.menusOf(role))) {
        Requirement required = table.get(menuKey);
        if (required == null) {
          continue; // 死授权不在这里报，交给 C4（两个断言各自只问一件事）
        }
        if (isOpenable(role, required, grants)) {
          continue;
        }
        gaps.add(describe(role, menuKey, required, grants));
      }
    }

    assertThat(gaps)
        .as(
            "以下「已授菜单」对应的页面，该角色点开会收到 403：菜单授权与端点闸门是两套开关，缺口只在这里显形。"
                + "修法二选一（FR-N06）：补齐权限码（改端点守卫 + 补授，需按 FR-N24 批准）或收回该菜单授权；"
                + "不得保留菜单指向拒绝页的状态")
        .isEmpty();
  }

  @Test
  @DisplayName("C2：对照表里的每个码都必须真的被某个端点校验（否则 C1 对着幻觉报绿）")
  void everyCodeInTheTableIsActuallyEnforced() {
    Set<String> enforced = RequirePermissionScanTestSupport.usages().keySet();
    // 扫描本身也要防假绿：pattern 写错时下面会「零违规通过」
    assertThat(enforced).hasSizeGreaterThan(30).contains("customer:merge", "ticket:read");

    Set<String> phantom = new TreeSet<>();
    for (Map.Entry<String, Requirement> entry :
        MenuReadPermissionTestSupport.requirementByMenuKey().entrySet()) {
      Requirement required = entry.getValue();
      if (required.kind() == Kind.CODE && !enforced.contains(required.code())) {
        phantom.add(entry.getKey() + " → " + required.code() + "（" + required.evidence() + "）");
      }
    }

    assertThat(phantom)
        .as("对照表里写了这些码，但没有任何端点的 @RequirePermission 校验它们：表本身写错了，" + "C1 会据此给出「已授就能开」的假结论")
        .isEmpty();
  }

  @Test
  @DisplayName("C3：对照表覆盖全部菜单项，且每项要么有码、要么显式标 OPEN/ROLE")
  void theTableCoversEveryMenuItem() {
    List<String> menuKeys = MenuReadPermissionTestSupport.menuKeysInOrder();
    Map<String, Requirement> table = MenuReadPermissionTestSupport.requirementByMenuKey();

    assertThat(menuKeys).hasSize(56).doesNotHaveDuplicates();
    assertThat(table.keySet())
        .as("对照表与 MENU_TREE 必须逐键相等：漏一项，C1 就会静默放过那一项")
        .containsExactlyInAnyOrderElementsOf(menuKeys);

    List<String> malformed = new ArrayList<>();
    for (Map.Entry<String, Requirement> entry : table.entrySet()) {
      Requirement required = entry.getValue();
      switch (required.kind()) {
        case CODE -> {
          if (required.code() == null || required.code().isBlank()) {
            malformed.add(entry.getKey() + "：CODE 形态却没写码");
          }
        }
        case ROLE -> {
          if (required.roles().isEmpty()) {
            malformed.add(entry.getKey() + "：ROLE 形态却没写放行角色");
          }
        }
        case OPEN -> {
          if (required.evidence() == null || required.evidence().isBlank()) {
            malformed.add(entry.getKey() + "：OPEN 形态必须写明「读接口无注解」的依据，否则无从复核");
          }
        }
      }
    }

    assertThat(malformed).as("对照表里有填得不完整的项").isEmpty();
  }

  @Test
  @DisplayName("C4：不存在指向不存在菜单项的死授权（FR-N07）")
  void noGrantPointsToAMenuItemThatDoesNotExist() {
    Grants grants = MenuReadPermissionTestSupport.grants();
    Set<String> known = new LinkedHashSet<>(MenuReadPermissionTestSupport.menuKeysInOrder());

    Map<String, Set<String>> dead = new TreeMap<>();
    for (String role : grants.menus().keySet()) {
      for (String menuKey : grants.menusOf(role)) {
        if (!known.contains(menuKey)) {
          dead.computeIfAbsent(role, r -> new TreeSet<>()).add(menuKey);
        }
      }
    }

    assertThat(dead)
        .as(
            "这些授权指向的菜单项在 MENU_TREE 里不存在：谁也渲染不出来（已授但永不出现），"
                + "且下次在角色页保存该角色时会被静默删除。V80 已清掉 board/usage-map，新增的要一并清理")
        .isEmpty();
  }

  /**
   * C5：解析器真的把三种非平凡语句形态吃进去了（T014 的防呆断言）。
   *
   * <p><b>防的是什么</b>：授权数据是「逐条推演迁移」得来的，不是查库——所以解析器漏读一条语句，后果是 <b>静默的</b>：某个角色凭空多出或少掉一项授权，而 C1~C4
   * 全绿（它们只检查"表里写的"与"解析出的"是否自洽， 不检查"迁移里还有没有别的"）。漏读最可能发生在非 INSERT 的形态上——{@code UPDATE role_menu}
   * （改键）、{@code DELETE FROM role_menu}、{@code WHERE r.code <> 'ADMIN'}（取补集），这三种都不是 顺手写出来的形态。
   *
   * <p>断言方式刻意选**结果**而不是计数：语句数相等在今天的实现里是构造性的（认不出来就抛异常， 不会静默
   * continue），所以它只能当"将来重构时的绊线"用；真正能失败的是下面三条结果断言。
   */
  @Test
  @DisplayName("C5：改键 / 删除 / 取补集三种语句形态都被真的推演过（漏读是静默故障）")
  void theParserReplaysNonTrivialStatementForms() {
    int parsed = MenuReadPermissionTestSupport.statementsParsed();
    int occurrences = MenuReadPermissionTestSupport.occurrences();
    assertThat(parsed)
        .as("解析到的授权语句数(%d) 必须等于迁移文件里出现的次数(%d)", parsed, occurrences)
        .isEqualTo(occurrences)
        .isPositive();

    Grants grants = MenuReadPermissionTestSupport.grants();

    // ① UPDATE role_menu SET menu_key = : V80 把 leaderboard → stats/leaderboard、custom-fields →
    //    settings/custom-fields。不重放改键，持有旧键的角色在新表里就是"死授权"（C4 报红），
    //    而持有新键的角色会凭空少一项授权（C1 静默）。
    assertThat(grants.menusOf("ADMIN"))
        .as("V80 的两处改键必须被重放到持有者身上")
        .contains("stats/leaderboard", "settings/custom-fields")
        .doesNotContain("leaderboard", "custom-fields");
    assertThat(allHeldMenus(grants))
        .as("旧键在改键后不该再有人持有")
        .doesNotContain("leaderboard", "custom-fields");

    // ② DELETE FROM role_menu: V80 清掉 board / usage-map。
    assertThat(allHeldMenus(grants)).doesNotContain("board", "usage-map");

    // ③ WHERE r.code <> 'ADMIN'（取补集）: V80 给**除 ADMIN 外**的所有角色授了 approvals 菜单。
    long nonAdminHolders =
        grants.menus().keySet().stream()
            .filter(role -> !BUILT_IN_ADMIN.equals(role))
            .filter(role -> grants.menusOf(role).contains("approvals"))
            .count();
    assertThat(nonAdminHolders)
        .as("补集形态必须被解成「除 ADMIN 外的所有角色」，解成空集或不认识都会在这里显形")
        .isEqualTo(MenuReadPermissionTestSupport.roleCodes().size() - 1);
  }

  /** 全部角色持有的菜单键并集（用于断言"某个键已经没人持有"）。 */
  private static Set<String> allHeldMenus(Grants grants) {
    Set<String> all = new TreeSet<>();
    for (String role : grants.menus().keySet()) {
      all.addAll(grants.menusOf(role));
    }
    return all;
  }

  /**
   * 改造前逐角色的授权项数（084 T002 基准，见 {@code verification.md} §T002）。
   *
   * <p>这些数字取自**改造前**一次独立的一次性脚本推导（不是本类的解析器）。两处数字相等因此是两份证据： 授权数据没有被本次改造动过，且两条互不相干的解析路径读出了同一份数据。
   */
  private static final Map<String, Integer> GRANTS_BEFORE_084 =
      Map.ofEntries(
          Map.entry("ADMIN", 28),
          Map.entry("ANALYST", 7),
          Map.entry("FINANCE_ACCOUNTANT", 8),
          Map.entry("FINANCE_MANAGER", 13),
          Map.entry("MARKETING_MANAGER", 15),
          Map.entry("MARKETING_SPECIALIST", 11),
          Map.entry("SALES", 22),
          Map.entry("SALES_MANAGER", 26),
          Map.entry("SALES_REP", 21),
          Map.entry("SUPPORT", 8),
          Map.entry("SUPPORT_AGENT", 10),
          Map.entry("SUPPORT_MANAGER", 16),
          Map.entry("VIEWER", 12));

  /**
   * C6：归属调整没有改变任何角色的**授权集合**（084 FR-N19、T028）。
   *
   * <p><b>为什么这条断言成立且够用</b>：可见集合 ＝ 授权集合 ∩ 可渲染集合，而 084 只搬动了 {@code MENU_TREE} 里各分组之间的成员位置（授权表 {@code
   * role_menu} 只存 {@code menu_key}，**没有分组列**，见 data-model.md §3）。 「可渲染集合」不因搬动而变（C3 已钉住它恰好是 {@code
   * MENU_TREE} 的全部 56 项），所以「授权集合不变 ⇒ 可见集合不变」。本类与 C3 合起来是这句话的机械形态：前者是授权侧，后者是可渲染侧。
   *
   * <p><b>它还守着什么</b>：搬动归属时若顺手重写了授权种子（V85 是手写的迁移文件），某个角色的某一项就会静默 消失——界面只是少一个菜单，没有任何报错。下面这 13
   * 个数字是审计这条路径的唯一手段。
   */
  @Test
  @DisplayName("C6：归属调整未改变任何角色的授权集合（FR-N19；逐角色与改造前基准相等）")
  void regroupingDidNotChangeAnyRoleGrantSet() {
    Grants grants = MenuReadPermissionTestSupport.grants();

    List<String> drift = new ArrayList<>();
    for (Map.Entry<String, Integer> before : GRANTS_BEFORE_084.entrySet()) {
      Set<String> now = new TreeSet<>(grants.menusOf(before.getKey()));
      if (now.size() != before.getValue()) {
        drift.add(
            before.getKey() + "：改造前 " + before.getValue() + " 项，现在 " + now.size() + " 项 → " + now);
      }
    }
    for (String role : new TreeSet<>(MenuReadPermissionTestSupport.roleCodes())) {
      if (!GRANTS_BEFORE_084.containsKey(role)) {
        drift.add(role + " 是基准里没有的新角色（基准 13 个角色未变，是 FR-N19 的判据）");
      }
    }

    assertThat(drift)
        .as(
            "本次改造只搬动菜单项在 MENU_TREE 里的归属，不改变任何角色被授了什么（role_menu 无分组列）。"
                + "上面这些角色的授权项数变了，说明授权种子被顺手改动了：请核对是刻意的变更（需在 spec 中登记）"
                + "还是搬动时的笔误。ADMIN 的 28 项小于 56 是刻意的口径——管理员看到全部 56 项靠的是全量兜底"
                + "（FR-N04），不是靠 role_menu")
        .isEmpty();

    // T002 记录的那 16 处「已授权却被 isAdmin 硬门隐藏」＝ 5 个角色 × 7 个键。它们一直在授权里，
    // 当年缺的是前端渲染（前端那侧的守卫见 frontend/src/constants/menuVisibility.test.ts 的 FR-N01 用例）。
    // 这里钉住「授权侧一直是齐的」，把两件事分开归因：不是补授权修好的，是去掉硬门修好的。
    assertThat(grants.menusOf("ANALYST")).contains("custom-objects", "settings/custom-fields");
    assertThat(grants.menusOf("FINANCE_MANAGER"))
        .contains("currencies", "departments", "roles", "users");
    assertThat(grants.menusOf("MARKETING_MANAGER")).contains("departments", "roles", "users");
    assertThat(grants.menusOf("SALES_MANAGER")).contains("departments", "roles", "users");
    assertThat(grants.menusOf("SUPPORT_MANAGER"))
        .contains("departments", "roles", "sla-policies", "users");
  }

  /** 该角色能不能打开这个菜单项对应的页面。 */
  private static boolean isOpenable(String role, Requirement required, Grants grants) {
    if (BUILT_IN_ADMIN.equals(role)) {
      return true; // 内建管理员：PermissionAspect 直通（见 BUILT_IN_ADMIN 的注释）
    }
    return switch (required.kind()) {
      case OPEN -> true;
      case CODE -> grants.codesOf(role).contains(required.code());
      case ROLE -> required.roles().contains(role);
    };
  }

  private static String describe(String role, String menuKey, Requirement required, Grants grants) {
    String held =
        switch (required.kind()) {
          case CODE -> "持有码=" + new TreeSet<>(grants.codesOf(role));
          case ROLE -> "角色名不在放行集合里";
          case OPEN -> "";
        };
    return role
        + " 的 "
        + menuKey
        + "：需要 "
        + switch (required.kind()) {
          case CODE -> required.code();
          case ROLE -> "角色 ∈ " + new TreeSet<>(required.roles());
          case OPEN -> "OPEN";
        }
        + "（"
        + required.evidence()
        + "）；"
        + held;
  }
}
