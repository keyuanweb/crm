package com.crm.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.crm.AbstractIntegrationTest;
import com.crm.support.PermissionDictionaryTestSupport;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 权限数据的完整性护栏（1.5）：种子数据授予的权限码与菜单键，必须都能在字典里查到。
 *
 * <p><b>为什么这条断言值得单独写一个 IT</b>：{@code RoleService.replacePermissions} 是先删后插，而角色页的
 * 勾选项完全由字典渲染。于是「授予了字典里没有的码」的真实后果不是「无效」，而是——管理员看不见这条授权， 打开角色点一次保存，它就**静默消失**。1.5 开始时全库有 14 个这样的权限码与
 * 4 个这样的菜单键 （含 8 个角色的「团队排行」）。
 *
 * <p>与 {@code RequirePermissionCatalogTest}（注解 ⊄ 字典）合起来，三处漂移都有了护栏：注解、授予、菜单。
 * 本类跑的是真实迁移种子，因此它同时也是「迁移改完没漏授权」的回归测试。
 */
class PermissionMatrixIT extends AbstractIntegrationTest {

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("role_permission 里的每个权限码都能在字典里查到")
  void everyGrantedCodeExistsInDictionary() {
    Set<String> granted = queryDistinct("SELECT DISTINCT permission_code FROM role_permission");
    Set<String> dictionary = PermissionDictionaryTestSupport.codes();

    // 先证明前提成立：查询真返回了数据、字典真被展平了。否则下面那条断言会「零违规通过」
    assertThat(granted).isNotEmpty();
    assertThat(dictionary).contains("customer:delete");

    assertThat(orphans(granted, dictionary))
        .as("以下权限码已授予角色，但不在 PERMISSION_DEFS 里：角色页勾不出它们，管理员保存角色时会被静默删除")
        .isEmpty();
  }

  @Test
  @DisplayName("role_menu 里的每个菜单键都能在菜单树里查到")
  void everyGrantedMenuExistsInMenuTree() {
    Set<String> granted = queryDistinct("SELECT DISTINCT menu_key FROM role_menu");
    Set<String> menuTree = PermissionDictionaryTestSupport.menuKeys();

    assertThat(granted).isNotEmpty();
    assertThat(menuTree).contains("customers");

    assertThat(orphans(granted, menuTree))
        .as("以下菜单键已授予角色，但不在 MENU_TREE 里：前端 menuKeyOf 认不出它们，且保存角色时会被静默删除")
        .isEmpty();
  }

  /**
   * FR-G14 的六个权限码：预置矩阵对<b>任何</b>角色都零授予——这是**已作出的决定**（默认仅 ADMIN），不是遗漏。
   *
   * <p><b>为什么要把一个"没有"写成用例</b>：T071 的问题不是"没人补授"，而是"没人能看见这个后果"。这六个码 在字典里（管理员能在角色页勾选）、权限注解也在（{@code
   * RequirePermissionCatalogTest} 绿）， 但全部迁移中一条授予都没有——于是升级后除 ADMIN 外一律 403。判据③（既无闸门也无菜单 → 一个码都不补）
   * 给出的是"保持仅 ADMIN"，那就必须留下一条可执行的痕迹：谁哪天把 {@code export:scheduled} 授给 SALES_MANAGER，
   * 本用例即转红，逼他把决定与记录一起改掉，而不是让它悄悄变成另一种现状。
   *
   * <p><b>为什么另一条同类用例盖不住</b>：{@code SecurityHardeningIT} 用 {@code ensureRole} 自造了一个持码角色，
   * 证明的是"机制在"；若预置矩阵其实授过这些码，那条用例照样绿。视角换了，缺口才现形。
   *
   * <p>行为面的那一半在 {@code PermissionEnforcementIT.frG14ModulesDenyEveryPresetRole}（预置角色真打端点 → 403）。
   */
  private static final Set<String> ADMIN_ONLY_BY_DEFAULT =
      Set.of(
          "export:scheduled",
          "export:compliance",
          "retention:create",
          "retention:update",
          "retention:delete",
          "retention:execute");

  @Test
  @DisplayName("FR-G14 六码：预置矩阵对任何角色都零授予（默认仅 ADMIN 的可执行记录）")
  void frG14CodesAreGrantedToNoPresetRole() {
    // 正对照：先证明这条查询真查得出授予——联表或列名写坏时，下面六个"空"会一起假绿
    assertThat(rolesHolding("customer:claim"))
        .as("正对照落空：查不出任何角色持有 customer:claim，说明查询本身写坏了")
        .isNotEmpty();

    assertThat(PermissionDictionaryTestSupport.codes())
        .as("六个码必须都在字典里；某码不在，则它的「零授予」是句废话——字典里没有的码本来就授不出去")
        .containsAll(ADMIN_ONLY_BY_DEFAULT);

    for (String code : ADMIN_ONLY_BY_DEFAULT) {
      assertThat(rolesHolding(code))
          .as(
              "预置角色持有 %s。若这是有意的，请同时改掉本用例与 spec.md 的「FR-G14 默认后果」记录："
                  + "这六个码的默认后果是一份**记录在案的决定**，不是一份没人知道的现状",
              code)
          .isEmpty();
    }
  }

  /** 哪些角色持有该码——用角色 code 而非 id，失败信息才读得懂。 */
  private List<String> rolesHolding(String permissionCode) {
    return jdbcTemplate.queryForList(
        "SELECT r.code FROM role_permission rp JOIN role r ON r.id = rp.role_id"
            + " WHERE rp.permission_code = ? ORDER BY r.code",
        String.class,
        permissionCode);
  }

  /** granted 里不在 dictionary 中的那些。 */
  private Set<String> orphans(Set<String> granted, Set<String> dictionary) {
    Set<String> orphans = new TreeSet<>(granted);
    orphans.removeAll(dictionary);
    return orphans;
  }

  private Set<String> queryDistinct(String sql) {
    List<String> rows = jdbcTemplate.queryForList(sql, String.class);
    return new TreeSet<>(rows);
  }
}
