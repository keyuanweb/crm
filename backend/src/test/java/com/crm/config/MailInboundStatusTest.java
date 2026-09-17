package com.crm.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

/**
 * MailInboundStatus 单元测试（101，T8）：配置缺省即 {@code false}。
 *
 * <p><b>为什么这条必须存在、且必须在这里</b>：收信侧"默认不产生任何记录"这条性质有两个默认值在守——{@code application.yml} 的 {@code
 * ${CRM_MAIL_INBOUND_DEMO_ENABLED:false}} 与 Java 侧 {@code @Value} 的 {@code :false}。集成测试只走前者 （{@code
 * application-test.yml} 与 yml 都在环境里），把 Java 侧的 {@code :false} 改成 {@code :true} 后<b>它们一条都不会红</b>
 * ——本类是那条缺省值的唯一护栏。
 *
 * <p><b>为什么这里非要起容器</b>（与 {@code SecurityDefaultsGuardTest} 直接 {@code new} 的处置相反）：那条判据是纯函数，
 * 而本条的判据恰恰是"**所有属性源都没有这个键时**，注入进构造函数的值是什么"——只有占位符解析器能回答。手动 {@code new MailInboundStatus(false)}
 * 断言的是一个常量，不是缺省值，对上面那个破坏完全不敏感。
 *
 * <p>刻意不加载 {@code application.yml}：一旦加载，yml 里的 {@code :false} 会先命中，本测试就退化成上一条注释里说的那种失效护栏。
 */
class MailInboundStatusTest {

  private static final String KEY = "crm.mail.inbound.demo-enabled";

  /** 在给定的属性源（可为空 = 完全没有该键）下解析出的 Bean。 */
  private static MailInboundStatus beanUnder(Map<String, Object> properties) {
    try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
      if (properties != null) {
        ctx.getEnvironment()
            .getPropertySources()
            .addFirst(new MapPropertySource("test", properties));
      }
      ctx.register(MailInboundStatus.class);
      ctx.refresh();
      return ctx.getBean(MailInboundStatus.class);
    }
  }

  @Test
  @DisplayName("缺省：没有任何属性源定义该键 ⇒ false（默认部署不产生任何收信记录）")
  void demoDisabledByDefault() {
    assertThat(beanUnder(null).isDemoEnabled())
        .as("缺省必须是 false —— 改成 true 等于让每个未配置的部署都开始生成假的收信记录")
        .isFalse();
  }

  @Test
  @DisplayName("显式 true ⇒ 演示路径可用")
  void demoEnabledWhenExplicitlySet() {
    assertThat(beanUnder(Map.of(KEY, "true")).isDemoEnabled()).isTrue();
  }

  @Test
  @DisplayName("对外说明文案：非空白且点明未接入收信源")
  void notConfiguredMessageIsUsable() {
    assertThat(MailInboundStatus.NOT_CONFIGURED_MESSAGE).isNotBlank();
    assertThat(MailInboundStatus.NOT_CONFIGURED_MESSAGE).contains("未接入收信源");
  }
}
