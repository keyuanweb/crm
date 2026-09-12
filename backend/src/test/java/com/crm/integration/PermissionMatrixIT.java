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
