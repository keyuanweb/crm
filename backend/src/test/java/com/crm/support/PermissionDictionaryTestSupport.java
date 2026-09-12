package com.crm.support;

import com.crm.common.RoleConstants;
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
