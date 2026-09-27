package com.crm.config;

import com.crm.common.ErrorCode;
import com.crm.common.OutboundUrlValidator;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * AI 文本生成配置状态（104-ai-content-generation）：本部署有没有可用的模型通道。
 *
 * <p><b>本类是"AI 能否生成"的唯一判据</b>；接入生成时改的也是这里，不要在别处重复判断配置（照 {@link MailInboundStatus} 的体例）。消费方两处：{@code
 * AiContentService} 在**每次调用前**查 {@link #isConfigured()}，未配置即抛 {@code AiNotConfiguredException}（回
 * 409 且**零出站、零记录**，FR-003）；本类同时是 {@link ApplicationRunner}，在**启动期**做配置自洽性检查。
 *
 * <p><b>为什么是"启动即失败"而不是只告警（FR-005）</b>：官方 SDK 内部走 OkHttp，**绕过**本仓的 {@link OutboundUrlValidator}
 * 逐跳校验（FR-007 的已知架构偏离）。因此白名单检查<b>只能在启动期做一次</b>，它是本项<b>唯一的出站准入控制</b>。
 * 若只告警，白名单就成了建议性的——一个读起来像护栏、实际不拦的东西，比没有更坏。 之所以不伤及默认部署：{@code crm.ai.enabled} 默认 {@code
 * false}，本检查在开关打开前**整段不执行**。
 *
 * <p><b>两种错法，两种处置</b>（判据在 {@link #configurationProblem()} 里，动作在本类；照 {@code
 * MfaSecretEncryptionService} + {@code SecurityDefaultsGuard} 的判例）：
 *
 * <table border="1">
 *   <caption>配置状态与后果</caption>
 *   <tr><th>状态</th><th>启动时</th><th>调用时</th></tr>
 *   <tr><td>{@code enabled=false}（默认）</td><td>整段不检查</td><td>{@code AI_NOT_CONFIGURED}（409）</td></tr>
 *   <tr><td>已启用但 base-url／api-key 空白</td><td>告警，继续启动</td><td>{@code AI_NOT_CONFIGURED}（409）</td></tr>
 *   <tr><td>已启用且 base-url 畸形／主机不在白名单</td><td><b>抛出，拒绝启动</b></td><td>（到不了：启动即失败）</td></tr>
 *   <tr><td>合法</td><td>无</td><td>正常</td></tr>
 * </table>
 *
 * <p><b>为什么"空白"放行而"畸形"拒绝</b>：与 MFA 密钥同一判例——畸形是误配，它在启动时没有任何症状，要等某个人真的点"生成"
 * 才炸，而那一刻的错误现场是"某个人生成不了"，排查得从配置查起；把症状提前到启动时，代价是一次拒绝启动，收益是错误现场指向正确的地方。
 * 空白则是"没配"的正常表达，且已启用却没配的部署在调用时会拿到 409——<b>fail closed，不会静默降级</b>。
 *
 * <p><b>密钥不回显</b>（FR-004）：本类的告警与异常里<b>只报"有没有配"与长度</b>，从不回显 api-key 的内容或其片段——那个值可能是运维误粘的别的东西，
 * 且本类会把它写进日志。同一考虑见 {@code MfaSecretEncryptionService} 的类 javadoc。
 *
 * <p><b>为什么用构造器注入而不是字段 {@code @Value}</b>：{@code AiStatusTest} 要直构本类来断言"Java 兜底值有没有被改"（U1），
 * 字段注入会让那条用例只能靠反射设值——测的就变成了反射而不是兜底值。同理 {@link #configurationProblem()} 与 {@link
 * #checkConfiguration()} 是包级可见而非 {@code private}，供测试直接驱动。
 */
@Component
public class AiStatus implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(AiStatus.class);

  /** 未启用或未配置时对外返回的统一说明（照 {@link MailInboundStatus#NOT_CONFIGURED_MESSAGE} 体例）。 */
  public static final String NOT_CONFIGURED_MESSAGE = "未启用 AI 文本生成，未执行";

  private final boolean enabled;
  private final String baseUrl;
  private final String apiKey;
  private final String model;
  private final int maxTokens;
  private final int timeoutSeconds;
  private final OutboundUrlValidator outboundUrlValidator;

  public AiStatus(
      @Value("${crm.ai.enabled:false}") boolean enabled,
      @Value("${crm.ai.base-url:}") String baseUrl,
      @Value("${crm.ai.api-key:}") String apiKey,
      @Value("${crm.ai.model:claude-opus-5}") String model,
      @Value("${crm.ai.max-tokens:4096}") int maxTokens,
      @Value("${crm.ai.timeout-seconds:60}") int timeoutSeconds,
      OutboundUrlValidator outboundUrlValidator) {
    this.enabled = enabled;
    this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
    this.apiKey = apiKey == null ? "" : apiKey.trim();
    this.model = model == null ? "" : model.trim();
    this.maxTokens = maxTokens;
    this.timeoutSeconds = timeoutSeconds;
    this.outboundUrlValidator = outboundUrlValidator;
  }

  /** 开关本身。{@code false} 时本项**零出站、零成本、零行为变化**（出厂状态）。 */
  public boolean isEnabled() {
    return enabled;
  }

  /**
   * 现在能不能生成——{@link #isEnabled()} 且 base-url 与 api-key 都非空白。
   *
   * <p><b>这是 {@code AiContentService} 出站前必须查的那一个判据</b>。刻意回答"现在能不能"，而不是"配置写得对不对"——后者见 {@link
   * #configurationProblem()}。两者分开，是因为它们的消费方不同：调用方要一个布尔（能不能继续）， 启动检查要一句可读的话（哪里写错了）。
   */
  public boolean isConfigured() {
    return enabled && !baseUrl.isEmpty() && !apiKey.isEmpty();
  }

  /** 模型名（已启用且调用时才被读到；未配置的部署不需要它）。 */
  public String model() {
    return model;
  }

  /** 单次生成的最大输出 token 数。 */
  public int maxTokens() {
    return maxTokens;
  }

  /** 单次生成的超时秒数（FR-008：服务端超时由此控制，不受前端全局 30s 约束）。 */
  public int timeoutSeconds() {
    return timeoutSeconds;
  }

  /** 出站基地址；**不含**任何凭据（凭据只经 {@code apiKey()} 单独取用，见 FR-004）。 */
  public String baseUrl() {
    return baseUrl;
  }

  /**
   * API 密钥 <b>原样</b>返回，仅供 SDK 客户端构造时使用。
   *
   * <p>⚠️ <b>调用方不得把它写进日志、响应、审计 detail 或异常消息</b>（FR-004，须以 grep 断言，SC-004）。 本方法刻意命名为 {@code
   * apiKey()} 而不是 {@code toString()} 里的字段，是为了让每次取用都在 code review 里显式可见。
   */
  public String apiKey() {
    return apiKey;
  }

  /**
   * 配置问题的可读描述；{@link Optional#empty()} 表示配置可用或本项未启用。
   *
   * <p>返回描述而不是抛异常，是因为同一个判据有<b>两种动作</b>（启动时抛、调用时转业务异常），而<b>判据只该有一处</b>（照 {@code
   * MfaSecretEncryptionService#configurationProblem()} 的判例：若本方法抛业务异常、再由启动检查去 catch 它， 就会出现"启动检查依赖一个
   * HTTP 语义的异常类型"这种耦合）。
   *
   * <p>描述里<b>不含</b>配置内容，只报形态与长度。
   */
  public Optional<String> configurationProblem() {
    if (!enabled) {
      // 没打开开关的部署没有"配置问题"可言——这正是出厂状态。
      return Optional.empty();
    }
    if (baseUrl.isEmpty()) {
      return Optional.of("crm.ai.enabled=true 但未配置 crm.ai.base-url（环境变量 CRM_AI_BASE_URL）");
    }
    if (apiKey.isEmpty()) {
      return Optional.of("crm.ai.enabled=true 但未配置 crm.ai.api-key（环境变量 CRM_AI_API_KEY）");
    }
    try {
      outboundUrlValidator.validate(baseUrl, ErrorCode.AI_NOT_CONFIGURED);
    } catch (RuntimeException ex) {
      // 该 ErrorCode 在此只是 OutboundUrlValidator 的"拒绝载体"：启动期抛出的是 IllegalStateException，
      // 不会作为 HTTP 响应外泄（本方法只在 AiStatus.checkConfiguration() 里被调用）。
      return Optional.of(ex.getMessage());
    }
    return Optional.empty();
  }

  /**
   * 启动期检查：配置畸形即拒绝启动，未启用则整段不动（FR-005）。
   *
   * <p><b>包级可见而不是 {@code private}</b>：这是一步可以独立验证的检查（{@code AiStatusTest} 直构本类后调它）， 而"它在 {@code
   * run()} 里被调到"是另一条性质——两者分开测，合成一个会让"判据错了"与"位置错了"给出同一条失败信息。
   */
  void checkConfiguration() {
    if (!enabled) {
      return;
    }
    if (baseUrl.isEmpty() || apiKey.isEmpty()) {
      // "没配"是合法的失败姿态（调用时 409），但在启动时说一声——否则运维要到第一次点生成才知道。
      log.warn(
          "AI 文本生成已启用（crm.ai.enabled=true）但配置不完整：{}。" + "生成端点会回 409 且不发起任何出站请求；补齐配置后重启即可。",
          configurationProblem().orElse("配置不完整"));
      return;
    }
    configurationProblem()
        .ifPresent(
            problem -> {
              throw new IllegalStateException(
                  "AI 文本生成的出站配置不可用："
                      + problem
                      + "\n本项唯一的出站准入控制是启动期的这一次校验（SDK 内部走 OkHttp，绕过逐跳校验）。"
                      + "请把模型主机加入 crm.outbound.allowed-hosts（环境变量 CRM_OUTBOUND_ALLOWED_HOSTS），"
                      + "见 .env.example 的出站白名单段落。");
            });
  }

  @Override
  public void run(ApplicationArguments args) {
    checkConfiguration();
  }
}
