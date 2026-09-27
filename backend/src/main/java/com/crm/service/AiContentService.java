package com.crm.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicRetryableException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.StopReason;
import com.crm.common.AiGenerationException;
import com.crm.common.AiNotConfiguredException;
import com.crm.config.AiClientFactory;
import com.crm.config.AiStatus;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * AI 文本生成的<b>唯一出网点</b>（104-ai-content-generation）。
 *
 * <p><b>判门在出站之前，且是这条不变式</b>：{@link #generate} 的第一件事就是查 {@link AiStatus#isConfigured()}，未配置即抛 {@link
 * AiNotConfiguredException}（409）。在那行之上<b>没有任何网络调用</b>，因此"未配置 ⇒ 零出站、零记录"（FR-003）是结构性成立的，
 * 不依赖调用方记得先判门。{@code AiContentIT} 的 I1 用出站计数为 0 断言它。
 *
 * <p><b>同步，不 {@code @Async}</b>（plan D3）：{@code @EnableAsync} 在本仓已开但<b>没有有界执行器</b>（无 {@code
 * AsyncConfigurer}、 无 {@code ThreadPoolTaskExecutor} bean）⇒ {@code @Async} 会跑在 Spring 默认的 {@code
 * SimpleAsyncTaskExecutor} 上， 每任务一线程、无上界。给一个 60
 * 秒量级的外部调用接上它，等于给了一个不受限的线程放大器。要放开并发前必须先有有界执行器，那是独立的一次变更。
 *
 * <p><b>密钥不经过本类</b>：客户端由 {@link AiClientFactory} 构造，本类只拿一个 {@link AnthropicClient}。 这不是洁癖——SC-004 用
 * grep 断言"密钥不落进日志／审计／异常"，把取密钥与写审计分居两个文件，那条断言才是结构性事实而非巧合。
 *
 * <p><b>审计 detail 只放元数据</b>（FR-016）：能力名／模型／输入输出 token／耗时。提示词原文与客户数据<b>不进</b> detail，也不进日志。
 */
@Service
public class AiContentService {

  private static final Logger log = LoggerFactory.getLogger(AiContentService.class);

  /** 审计动作名（沿用本仓大写动词体例：CREATE / UPDATE / DELETE / IMPORT / EXPORT …）。 */
  static final String AUDIT_ACTION = "GENERATE";

  private final AiStatus aiStatus;
  private final AiClientFactory clientFactory;
  private final AiTokenBudget tokenBudget;
  private final AuditService auditService;

  public AiContentService(
      AiStatus aiStatus,
      AiClientFactory clientFactory,
      AiTokenBudget tokenBudget,
      AuditService auditService) {
    this.aiStatus = aiStatus;
    this.clientFactory = clientFactory;
    this.tokenBudget = tokenBudget;
    this.auditService = auditService;
  }

  /**
   * 一次生成请求。
   *
   * <p>{@code systemPrompt} 是<b>稳定前缀</b>（提示词与输出契约），{@code userPrompt} 才含客户数据——两者不得混同（FR-009），
   * 故它们的拼接发生在<b>调用方</b>（{@code AiPromptCatalog}）而不是本类：本类不认识业务，只负责"把两段发出去、把结果与用量带回来"。
   *
   * @param capability 能力名，仅用于审计与日志（不进提示词）
   * @param userId <b>花钱的人</b>：日预算记在谁头上（FR-015）。恒非空——{@code AiTokenBudget} 拒绝 null
   * @param entityType 审计落点：本能力作用于哪类对象（如 {@code CUSTOMER}）
   * @param entityId 审计落点 id；可为 null（如邮件草稿在保存前没有实体）
   */
  public record AiRequest(
      String capability,
      Long userId,
      String entityType,
      Long entityId,
      String systemPrompt,
      String userPrompt) {}

  /**
   * 一次生成的产出。
   *
   * <p>{@code truncated} 为真表示上游因 {@code max_tokens} 截断——<b>这不是失败</b>：截断的草稿仍有价值，故按成功返回并置标志，
   * 由前端显示"截断态"（可续写或调大上限）。真正的失败走 {@link AiGenerationException}。
   */
  public record AiGeneration(
      String text, boolean truncated, long inputTokens, long outputTokens, long durationMillis) {}

  /**
   * 生成。未配置 ⇒ 409 且零出站；上游失败 ⇒ 503；上游拒答／空输出 ⇒ 422。
   *
   * <p>⚠️ 上游异常<b>原文不进响应</b>：只进日志（含异常类名与状态码），响应里是我们自己写的固定文案。上游的异常文本可能带上响应体原文， 而响应体会带着请求侧内容。
   */
  public AiGeneration generate(AiRequest request) {
    // ⚠️ 此行**必须**是第一个动作：它之上任何一句网络调用都会让 FR-003 的"零出站"变成一句口号。
    if (!aiStatus.isConfigured()) {
      throw new AiNotConfiguredException();
    }
    // 日预算闸（FR-015/FR-017）：与判门**同侧**——在出站之前。耗尽即抛 RateLimitExceededException
    // （429 + Retry-After 到明天零点），此时还没有产生任何外部流量。
    tokenBudget.check(request.userId());
    AnthropicClient client = clientFactory.client().orElseThrow(AiNotConfiguredException::new);

    MessageCreateParams params =
        MessageCreateParams.builder()
            .model(aiStatus.model())
            .maxTokens(aiStatus.maxTokens())
            .system(request.systemPrompt())
            .messages(
                List.of(
                    MessageParam.builder()
                        .role(MessageParam.Role.USER)
                        .content(request.userPrompt())
                        .build()))
            .build();

    long startedNanos = System.nanoTime();
    Message message;
    try {
      message = client.messages().create(params);
    } catch (RateLimitException | AnthropicRetryableException | AnthropicIoException ex) {
      // 上游 429／可重试／IO。上游限流**不**映射成 RATE_LIMITED：那是"你请求太快"，这是"上游现在忙"。
      log.warn(
          "AI 生成失败（上游不可用）：capability={} type={}",
          request.capability(),
          ex.getClass().getSimpleName());
      throw AiGenerationException.upstreamUnavailable();
    } catch (AnthropicServiceException ex) {
      // 上游回了受控状态码。5xx 归"暂时不可用"，4xx 归"这次请求/配置有问题"——两者的正确反应不同。
      log.warn(
          "AI 生成失败（上游返回错误）：capability={} status={} type={}",
          request.capability(),
          ex.statusCode(),
          ex.getClass().getSimpleName());
      throw ex.statusCode() >= 500
          ? AiGenerationException.upstreamUnavailable()
          : AiGenerationException.rejected();
    }

    long durationMillis = (System.nanoTime() - startedNanos) / 1_000_000L;
    String text = extractText(message);
    boolean truncated = message.stopReason().map(StopReason.MAX_TOKENS::equals).orElse(false);

    if (text.isBlank() && !truncated) {
      // 空输出且不是截断 ⇒ 上游没给出可用内容（常见于拒答）。按 422 处理，而不是把空串当成功交给前端。
      log.warn(
          "AI 生成返回空内容：capability={} stopReason={}", request.capability(), message.stopReason());
      throw AiGenerationException.rejected();
    }

    long inputTokens = message.usage().inputTokens();
    long outputTokens = message.usage().outputTokens();
    // 记账在**成功之后**、审计之前：用 SDK 给的 usage，不估算（FR-016）。失败路径不记账（拿不到用量，
    // 而"估算一个数记进去"正是 FR-016 禁止的那件事）。
    tokenBudget.charge(request.userId(), inputTokens + outputTokens);
    auditService.record(
        AUDIT_ACTION,
        request.entityType(),
        request.entityId(),
        auditDetail(request.capability(), inputTokens, outputTokens, durationMillis));

    return new AiGeneration(text, truncated, inputTokens, outputTokens, durationMillis);
  }

  /**
   * 把 {@code content} 里的文本块拼成纯文本。
   *
   * <p>只取文本块：本项<b>不注册工具</b>（FR-012），故不会出现工具块；真出现了也应当忽略而不是把它字符串化塞给前端。 多个文本块之间用换行拼接。
   */
  private static String extractText(Message message) {
    return message.content().stream()
        .filter(ContentBlock::isText)
        .flatMap(block -> block.text().stream())
        .map(textBlock -> textBlock.text())
        .collect(Collectors.joining("\n"));
  }

  /**
   * 审计 detail：**只有元数据**（FR-016）。
   *
   * <p>包级可见是为了让用例能直接断言这条字符串——"detail 里不含提示词与客户数据"若只靠人工阅读， 下次有人顺手加一个 {@code + userPrompt}
   * 就没有任何东西拦得住。
   */
  static String auditDetail(
      String capability, long inputTokens, long outputTokens, long durationMillis) {
    return "capability="
        + capability
        + "; inputTokens="
        + inputTokens
        + "; outputTokens="
        + outputTokens
        + "; durationMs="
        + durationMillis;
  }
}
