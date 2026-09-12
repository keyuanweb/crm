package com.crm.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.crm.common.RoleConstants;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.MethodMetadata;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;

/**
 * 权限码字典与 {@code @RequirePermission} 的一致性护栏（1.5）。
 *
 * <p><b>防的是什么</b>：注解里写了一个 {@link RoleConstants#PERMISSION_DEFS} 里没有的码。这类码在角色配置页 根本勾不出来，于是 {@code
 * PermissionAspect} 对**所有非 ADMIN 角色恒判 403**——端点看上去只是「没给权限」， 实际是「授不了权」。1.5 开始时全库已有 6
 * 个这样的码（email:manage / form:manage / marketing:manage / visit:manage / invoice:manage /
 * announcement:manage），本测试就是把它们和将来新增的一起按住。
 *
 * <p><b>为什么用字节码扫描而不是反射</b>：注解只标在方法上，散落在 60 余个 Controller 中。反射要先枚举所有类 再逐个 {@code
 * getDeclaredMethods}，而 Spring 的 {@link MetadataReader} 本来就是读注解元数据的工具， 一次遍历既能拿到类又能拿到被标注的方法。
 *
 * <p>反向（字典里有、注解没用）**刻意不断言**：字典同时承载菜单权限点与「将来要用」的码，未使用是常态。
 */
class RequirePermissionCatalogTest {

  private static final String ANNOTATION = RequirePermission.class.getName();

  @Test
  void everyAnnotatedCodeIsGrantableFromTheDictionary() throws IOException {
    Map<String, Set<String>> usages = scanAnnotationUsages();

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

  /** 注解码 → 用到它的位置（类名，或 类名#方法名）。仅用于失败时的定位信息。 */
  private Map<String, Set<String>> scanAnnotationUsages() throws IOException {
    MetadataReaderFactory factory = new CachingMetadataReaderFactory();
    Resource[] resources =
        new PathMatchingResourcePatternResolver().getResources("classpath*:com/crm/**/*.class");

    Map<String, Set<String>> usages = new TreeMap<>();
    for (Resource resource : resources) {
      MetadataReader reader = factory.getMetadataReader(resource);
      AnnotationMetadata metadata = reader.getAnnotationMetadata();
      String className = metadata.getClassName();
      for (MethodMetadata method : metadata.getAnnotatedMethods(ANNOTATION)) {
        collect(
            usages,
            method.getAnnotationAttributes(ANNOTATION),
            className + "#" + method.getMethodName());
      }
    }
    return usages;
  }

  private void collect(
      Map<String, Set<String>> usages, Map<String, Object> attributes, String where) {
    if (attributes == null) {
      return;
    }
    if (attributes.get("value") instanceof String code && !code.isBlank()) {
      usages.computeIfAbsent(code, key -> new TreeSet<>()).add(where);
    }
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
