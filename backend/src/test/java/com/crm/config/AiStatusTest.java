package com.crm.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.crm.common.OutboundUrlValidator;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Parameter;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.Yaml;

/**
 * {@link AiStatus} 的配置判据（104-ai-content-generation，U1 / FR-002）。
 *
 * <p><b>U1：三种构造</b>——{@code enabled=false} / 已启用但 base-url 空 / 两者齐备 ⇒ 未配置 / 未配置 / 已配置。 这是 {@code
 * isConfigured()} 的正面判据，也是"未配置 ⇒ 零出站"那条结构性不变式的<b>上游</b>。
 *
 * <p><b>为什么还有第二条（yml 与 Java 兜底值逐字一致）</b>：{@code isConfigured()} 的取值随"有没有 {@code application.yml}
 * 覆盖"而变——IT 里两者都在环境里，所以 <b>IT 读的是 yml 那一侧</b>，把 Java 侧 {@code @Value} 的兜底值改掉后，<b>没有任何一条 IT
 * 会变红</b>（本仓的"假绿通道 2"）。本条就是那个缺口的判据。
 *
 * <p><b>为什么用反射读注解字面量、而不是起一个没有属性源的容器</b>：容器那条路会把 {@code System.getenv()} 一并接进来，于是开发机上若恰好导出了 {@code
 * CRM_AI_DAILY_TOKEN_BUDGET}， 本用例就会因为一个与被测代码无关的原因变红（本仓已有这种坑的先例：{@code application-test.yml} 里
 * {@code mfa.secret-key} 刻意写死字面量而不回落到环境变量，理由相同）。读注解则是<b>字面量对字面量</b>， 且读的正是 Spring
 * 自己读的那一处。代价是它证明的是"两处写的一致"，不证明"Spring 真把注解解析成了这个值"—— 后者由所有 IT（走 yml）与上面的三种构造（走构造器）共同覆盖，两半合起来才是
 * FR-002 的完整判据。
 */
class AiStatusTest {

  private static final String ANTHROPIC = "api.anthropic.com";

  /** 依赖只有出站白名单校验器一个；本类不测它，给一个放行目标主机的实例即可。 */
  private static AiStatus status(
      boolean enabled, String baseUrl, String apiKey, int dailyTokenBudget) {
    return new AiStatus(
        enabled,
        baseUrl,
        apiKey,
        "claude-opus-5",
        4096,
        60,
        dailyTokenBudget,
        new OutboundUrlValidator(ANTHROPIC));
  }

  // ===== U1：三种构造 =====

  @Test
  @DisplayName("U1-a 出厂档（enabled=false）⇒ 未配置")
  void disabledIsNotConfigured() {
    AiStatus status = status(false, "", "", 100_000);
    assertThat(status.isConfigured())
        .as("开关关着就是未配置——哪怕 base-url 与 api-key 都填好了（开关是唯一的总闸）")
        .isFalse();
  }

  @Test
  @DisplayName("U1-b 已启用但 base-url 空 ⇒ 未配置（fail closed，调用时 409）")
  void enabledWithoutBaseUrlIsNotConfigured() {
    AiStatus status = status(true, "", "sk-test", 100_000);
    assertThat(status.isConfigured()).isFalse();
    assertThat(status.configurationProblem())
        .as("未配置时 configurationProblem() 要给出可读的那一句，供启动期告警用")
        .isPresent();
  }

  @Test
  @DisplayName("U1-c 已启用且 base-url/api-key 齐备 ⇒ 已配置")
  void enabledWithBaseUrlAndKeyIsConfigured() {
    AiStatus status = status(true, "https://" + ANTHROPIC, "sk-test", 100_000);
    assertThat(status.isConfigured()).isTrue();
    assertThat(status.configurationProblem()).isEmpty();
  }

  @Test
  @DisplayName("U1-d 对外说明文案：非空白且点明未执行（FR-003 的文案落点）")
  void notConfiguredMessageIsUsable() {
    assertThat(AiStatus.NOT_CONFIGURED_MESSAGE).isNotBlank();
    assertThat(AiStatus.NOT_CONFIGURED_MESSAGE).contains("未执行");
  }

  @Test
  @DisplayName("U1-e 第 7 项：dailyTokenBudget 的取值经访问器原样取回（<=0 = 闸关闭）")
  void dailyTokenBudgetIsExposedVerbatim() {
    assertThat(status(true, "https://" + ANTHROPIC, "sk-test", 100_000).dailyTokenBudget())
        .as("默认 100000 是闸的默认档；改它会静默改变每个部署的成本上界")
        .isEqualTo(100_000);
    assertThat(status(true, "https://" + ANTHROPIC, "sk-test", 0).dailyTokenBudget())
        .as("0（及负数）是「关掉这个闸」的表达，必须原样传出去而不是被夹成默认值")
        .isZero();
  }

  // ===== FR-002：application.yml 的默认值与 @Value 兜底值逐字一致 =====

  /** 配置键 → 该键上 Spring 会解析到的字符串值（由访问器给出）。 */
  private static Map<String, String> resolvedValues(AiStatus status) {
    Map<String, String> values = new LinkedHashMap<>();
    values.put("enabled", String.valueOf(status.isEnabled()));
    values.put("base-url", status.baseUrl());
    values.put("api-key", status.apiKey());
    values.put("model", status.model());
    values.put("max-tokens", String.valueOf(status.maxTokens()));
    values.put("timeout-seconds", String.valueOf(status.timeoutSeconds()));
    values.put("daily-token-budget", String.valueOf(status.dailyTokenBudget()));
    return values;
  }

  /**
   * 把注解/yml 里的 {@code ${ENV:default}} 取成 {@code default}；占位符里没有冒号则视为没有默认值（空串）。
   *
   * <p>⚠️ <b>不是占位符的字面量要原样返回</b>：yml 里某一项直接写死字面量（而不是 {@code ${ENV:...}}）是允许的，
   * 而那正是"两处逐字一致"这条断言最该看着的形态。把字面量读成空串，会让"yml 写了字面量、注解写了占位符"
   * 这种真实的不一致<b>以两边都非空的方式</b>继续比，或者让一次合法写法变红——两种结果都是假的。
   */
  private static String defaultOf(String raw) {
    String body = raw.trim();
    if (!body.startsWith("${") || !body.endsWith("}")) {
      return body;
    }
    body = body.substring(2, body.length() - 1);
    int colon = body.indexOf(':');
    return colon < 0 ? "" : body.substring(colon + 1);
  }

  /** {@code application.yml} 里 {@code crm.ai} 那一节（键 → 该键的原始字面量）。 */
  @SuppressWarnings("unchecked")
  private static Map<String, Object> aiSectionOfApplicationYml() throws Exception {
    try (InputStream in = new ClassPathResource("application.yml").getInputStream()) {
      Map<String, Object> root = new Yaml().load(in);
      Map<String, Object> crm = (Map<String, Object>) root.get("crm");
      return (Map<String, Object>) crm.get("ai");
    }
  }

  /**
   * {@code application.yml} 的 {@code crm.ai} 默认值 ↔ {@code AiStatus} 构造器上的 {@code @Value} 兜底值。
   *
   * <p>三件事一起断言，少一件都留一个藏身处：① 两处逐字相等；② 键的<b>条数</b>相等（只比对交集的话，
   * 一边多出一项不会被发现——而"多出来的那一项没有兜底值"正是这项最危险的形态）；③ 每个键都有注解 （缺注解 ⇒ 那一项连兜底值都没有，Spring 起不来或注入空值，而 ①
   * 会以"缺键"的形式含糊地红）。
   */
  @Test
  @DisplayName("FR-002：application.yml 的七项默认值与 AiStatus 的 @Value 兜底值逐字一致")
  void javaFallbacksMatchApplicationYmlDefaults() throws Exception {
    Map<String, Object> yml = aiSectionOfApplicationYml();
    Map<String, String> fromYml = new LinkedHashMap<>();
    yml.forEach((key, raw) -> fromYml.put(key, defaultOf(String.valueOf(raw))));

    Constructor<?> ctor = AiStatus.class.getDeclaredConstructors()[0];
    Map<String, String> fromAnnotations = new LinkedHashMap<>();
    for (Parameter parameter : ctor.getParameters()) {
      Value value = parameter.getAnnotation(Value.class);
      if (value == null) {
        continue;
      }
      String placeholder = value.value();
      assertThat(placeholder).as("本项的配置项一律挂在 crm.ai 下；出现别的键说明有配置被写到了别处").startsWith("${crm.ai.");
      String key = placeholder.substring("${crm.ai.".length(), placeholder.indexOf(':'));
      fromAnnotations.put(key, defaultOf(placeholder));
    }

    assertThat(fromAnnotations.keySet())
        .as("配置项条数：yml 注释里写死的是「七项」，两处必须一起改（一个数字住在好几个地方）")
        .containsExactlyInAnyOrderElementsOf(fromYml.keySet())
        .hasSize(7);
    assertThat(fromAnnotations)
        .as("yml 的默认值与 @Value 兜底值必须逐字一致：不一致时 isConfigured() 会随「有没有 yml 覆盖」而变")
        .isEqualTo(fromYml);

    // 正对照：这条用例真读到了 yml（文件路径写坏时上面那个"相等"会以两边都空的形式假绿）。
    assertThat(fromYml.get("model"))
        .as("读不到 crm.ai.model 说明 yml 解析路径已失效，上面的一致性断言不再看着任何东西")
        .isEqualTo("claude-opus-5");
    assertThat(resolvedValues(status(false, "", "", 100_000)).get("daily-token-budget"))
        .as("条数断言证明「七项」，这条证明第 7 项确实有访问器把它取出来")
        .isEqualTo("100000");
  }

  /**
   * 取值器自检：三种形态各一条。
   *
   * <p>⚠️ 第三条（字面量原样返回）不是凑数：取值器初版把"不含冒号"一律读成空串，于是 {@code defaultOf("claude-opus-5")} 返回 {@code ""}。而
   * {@code crm.ai} 里某一项<b>直接写死字面量</b> （不用 {@code ${ENV:...}}）是合法写法——那时上面那条一致性断言会以"两边都非空但都不是真值"的方式继续
   * 比较，或者把一次合法写法判红。这条自检就是那个缺口的判据，它当时确实红过。
   */
  @Test
  @DisplayName("FR-002 取值器自检：占位符取默认值、空默认值为空串、非占位符字面量原样返回")
  void placeholderReaderTakesTheDefault() {
    assertThat(defaultOf("${CRM_AI_MODEL:claude-opus-5}")).isEqualTo("claude-opus-5");
    assertThat(defaultOf("${CRM_AI_BASE_URL:}")).isEmpty();
    assertThat(defaultOf("claude-opus-5")).isEqualTo("claude-opus-5");
    assertThat(defaultOf("${CRM_AI_MAX_TOKENS}")).as("占位符没写默认值 ⇒ 空串").isEmpty();
  }

  @Test
  @DisplayName("启动期检查：未启用时整段不动（出厂部署不因本项拒绝启动）")
  void startupCheckDoesNothingWhenDisabled() {
    // 畸形 base-url（内网地址，白名单里没有）+ 关着的开关：若检查在开关之前就动手，这里会抛
    // IllegalStateException 而整个应用起不来。判据由「不抛异常」承担，故本方法没有断言。
    status(false, "http://127.0.0.1:1/internal", "", 100_000).checkConfiguration();
  }
}
