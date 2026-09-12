package com.crm.support;

import com.crm.security.RequirePermission;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.MethodMetadata;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;

/**
 * {@code @RequirePermission} 的字节码扫描（084 T014 抽出）。
 *
 * <p><b>为什么抽出来</b>：这条扫描现在有两个断言要用——{@code RequirePermissionCatalogTest} 问「注解里的码在字典里 有没有」，{@code
 * MenuAccessGrantAlignmentTest} 的 C2 问「菜单对照表里写的码有没有人真的校验」。两份各写一遍是 {@link
 * PermissionDictionaryTestSupport} 里已经写明要避免的形态：两份一起写错时，两个断言会同时给出假绿。
 *
 * <p>用字节码而不是反射：注解只标在方法上、散落在 60 余个 Controller 中，{@link MetadataReader} 一次遍历既能拿到 类又能拿到被标注的方法。扫描范围是
 * classpath 上 {@code com/crm} 包下的全部 {@code .class}，即主代码类 （测试类不在该包下，不会被算进来）。
 */
public final class RequirePermissionScanTestSupport {

  private static final String ANNOTATION = RequirePermission.class.getName();

  /** 注解码 → 用到它的位置（{@code 类名#方法名}），仅用于失败时的定位信息。 */
  public static Map<String, Set<String>> usages() {
    MetadataReaderFactory factory = new CachingMetadataReaderFactory();
    Map<String, Set<String>> usages = new TreeMap<>();
    try {
      Resource[] resources =
          new PathMatchingResourcePatternResolver().getResources("classpath*:com/crm/**/*.class");
      for (Resource resource : resources) {
        MetadataReader reader = factory.getMetadataReader(resource);
        AnnotationMetadata metadata = reader.getAnnotationMetadata();
        String className = metadata.getClassName();
        for (MethodMetadata method : metadata.getAnnotatedMethods(ANNOTATION)) {
          Map<String, Object> attributes = method.getAnnotationAttributes(ANNOTATION);
          if (attributes == null) {
            continue;
          }
          if (attributes.get("value") instanceof String code && !code.isBlank()) {
            usages
                .computeIfAbsent(code, key -> new TreeSet<>())
                .add(className + "#" + method.getMethodName());
          }
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException("扫描 @RequirePermission 失败", e);
    }
    return usages;
  }

  private RequirePermissionScanTestSupport() {}
}
