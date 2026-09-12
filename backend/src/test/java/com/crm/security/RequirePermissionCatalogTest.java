package com.crm.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.crm.common.RoleConstants;
import com.crm.support.RequirePermissionScanTestSupport;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * 权限码字典与 {@code @RequirePermission} 的一致性护栏（1.5）。
 *
 * <p><b>防的是什么</b>：注解里写了一个 {@link RoleConstants#PERMISSION_DEFS} 里没有的码。这类码在角色配置页 根本勾不出来，于是 {@code
 * PermissionAspect} 对**所有非 ADMIN 角色恒判 403**——端点看上去只是「没给权限」， 实际是「授不了权」。1.5 开始时全库已有 6
 * 个这样的码（email:manage / form:manage / marketing:manage / visit:manage / invoice:manage /
 * announcement:manage），本测试就是把它们和将来新增的一起按住。
 *
 * <p>扫描本身在 {@link RequirePermissionScanTestSupport}（084 抽出：{@code MenuAccessGrantAlignmentTest} 的
 * C2 要用同一份「谁真的被校验」的答案，两份各写一遍会一起写错、一起假绿）。
 *
 * <p>反向（字典里有、注解没用）**刻意不断言**：字典同时承载菜单权限点与「将来要用」的码，未使用是常态。
 */
class RequirePermissionCatalogTest {

  @Test
  void everyAnnotatedCodeIsGrantableFromTheDictionary() {
    Map<String, Set<String>> usages = RequirePermissionScanTestSupport.usages();

    // 先证明扫描确实扫到了东西：资源 pattern 写错时，下面那条断言会因为「一个违规都没扫到」而通过——假绿
    assertThat(usages).containsKey("customer:merge");

    Set<String> dictionary = dictionaryCodes();

    Map<String, Set<String>> orphans = new TreeMap<>();
    usages.forEach(
        (code, where) -> {
          if (!dictionary.contains(code)) {
            orphans.put(code, where);
          }
        });

    assertThat(orphans)
        .as("@RequirePermission 用了字典里没有的权限码：这些端点对非 ADMIN 恒 403，且在角色页勾不出授权项")
        .isEmpty();
  }

  /** 展平 {@code PERMISSION_DEFS}（分组 → children[].code）。 */
  @SuppressWarnings("unchecked")
  private Set<String> dictionaryCodes() {
    Set<String> codes = new TreeSet<>();
    for (Map<String, Object> group : RoleConstants.PERMISSION_DEFS) {
      for (Map<String, Object> perm : (List<Map<String, Object>>) group.get("children")) {
        codes.add((String) perm.get("code"));
      }
    }
    return codes;
  }
}
