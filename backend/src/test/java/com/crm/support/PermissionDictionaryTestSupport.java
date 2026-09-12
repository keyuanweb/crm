package com.crm.support;

import com.crm.common.RoleConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 权限字典的展平工具（1.5）。
 *
 * <p>{@link RoleConstants#PERMISSION_DEFS} 与 {@link RoleConstants#MENU_TREE} 都是「分组 → children」的两层
 * 结构，而所有一致性断言要的都是展平后的集合。展平逻辑只写一处——两处各写一份的话，两份一起写错时 断言会同时通过，护栏就白加了。
 */
public final class PermissionDictionaryTestSupport {

  private PermissionDictionaryTestSupport() {}

  /** {@code PERMISSION_DEFS} 展平后的全部权限码。 */
  public static Set<String> codes() {
    return flatten(RoleConstants.PERMISSION_DEFS, "code");
  }

  /** {@code MENU_TREE} 展平后的全部菜单键。 */
  public static Set<String> menuKeys() {
    return flatten(RoleConstants.MENU_TREE, "key");
  }

  /**
   * {@code MENU_TREE} 的「分组 → 成员」视图，**保留分组顺序与组内顺序**（084 T022 用）。
   *
   * <p>{@link #menuKeys()} 只回答「有哪些项」，回答不了「哪一项属于哪个组、叫什么名字」。而 084
   * 要修的正是后者那两类故障：分组归属对不上（角色页勾了不生效）与两侧名称不一致。 两者都需要分组维度，故在此补一个访问器——解析权威处的地方只留一处，避免两处实现
   * 各自漂移（与类注释同一理由）。
   */
  @SuppressWarnings("unchecked")
  public static List<MenuGroup> menuGroups() {
    List<MenuGroup> groups = new ArrayList<>();
    for (Map<String, Object> group : RoleConstants.MENU_TREE) {
      List<MenuItem> items = new ArrayList<>();
      for (Map<String, Object> child : (List<Map<String, Object>>) group.get("children")) {
        items.add(new MenuItem((String) child.get("key"), (String) child.get("title")));
      }
      groups.add(new MenuGroup((String) group.get("title"), List.copyOf(items)));
    }
    return List.copyOf(groups);
  }

  /** {@code MENU_TREE} 的一个分组：权威标题 + 成员（有序）。 */
  public record MenuGroup(String title, List<MenuItem> items) {}

  /** {@code MENU_TREE} 的一个菜单项：菜单键 + 权威中文名。 */
  public record MenuItem(String key, String title) {}

  @SuppressWarnings("unchecked")
  private static Set<String> flatten(List<Map<String, Object>> groups, String childKey) {
    Set<String> values = new TreeSet<>();
    for (Map<String, Object> group : groups) {
      for (Map<String, Object> child : (List<Map<String, Object>>) group.get("children")) {
        values.add((String) child.get(childKey));
      }
    }
    return values;
  }
}
