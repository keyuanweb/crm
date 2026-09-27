package com.crm.service;

import com.crm.common.AiNotConfiguredException;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.config.AiStatus;
import com.crm.entity.Customer;
import com.crm.security.SecurityUtil;
import org.springframework.stereotype.Service;

/**
 * P3 跟进记录润色 / 总结的<b>入参校验与上下文装配</b>（104-ai-content-generation）。
 *
 * <p><b>顺序即判据：判门 → 校验 → 取数（含可见性）→ 装配 → 出站</b>。任何一步失败都在出站之前——这是 FR-012 的 I5 /
 * D5（"非法输入不得产生任何出站请求"）在本能力上的落点。
 *
 * <p><b>本类是四项能力里依赖最少的一个</b>：它<b>不注入任何 Mapper、也不注入</b> {@code FollowUpService} 等实体服务——
 * 输入就是调用方给的一段文本（{@code P3_CONTENT_FIELDS} 只有一个 {@code content}）。唯一的一次取数发生在调用方 给了 {@code customerId}
 * 时（判可见性 + 取客户名）。这不是巧合："能做的事少"在这里是安全面：少一个依赖就少一条 "顺手查一下"的路径（022 的缺陷形态，见 {@link AiEmailDraftService}
 * 的类注释）。
 *
 * <p><b>为什么校验是 400 而不是契约 §3 原表写的 422</b>：全仓<b>没有通用校验类 422 码</b>（那 49 处 422 全是实体专属的）， 而
 * {@code @Valid} 那条路在本仓一律映射成 400。为一个入参枚举值新造通用 422 码是与本项无关的全局口径变更 ⇒ 走 400 + 既有 {@code
 * BAD_REQUEST}，在服务层判。逐条理由在 {@code contracts/} §3 的 C3 订正块里（本类不重复整段）。
 *
 * <p><b>审计为什么仍锚在 {@code CUSTOMER}</b>：本能力的作用对象是"跟进文本"，而那段文本住在<b>请求体里</b>——
 * 用户还没保存（本项零新增表，草稿不落库），因此<b>没有 id 可锚</b>。此时有两种写法：① 新造一个 {@code entity_type} 词条（如 {@code
 * FOLLOW_UP}）配一个 null id；② 沿用 P1/P2 的落点 {@code CUSTOMER} + {@code customerId}（可为 null）。 选
 * ②：新词条要在审计页的标签表、筛选器与用例里各加一处，而它带来的信息量是<b>零</b>（同样没有 id 可指，还多一个 无人认识的词条）；{@code customerId}
 * 为空时该行就是"这次生成没有绑定任何实体"，语义如实。
 */
@Service
public class AiFollowUpPolishService {

  private final CustomerService customerService;
  private final EntityAccessService entityAccessService;
  private final AiContentService aiContentService;
  private final AiStatus aiStatus;

  public AiFollowUpPolishService(
      CustomerService customerService,
      EntityAccessService entityAccessService,
      AiContentService aiContentService,
      AiStatus aiStatus) {
    this.customerService = customerService;
    this.entityAccessService = entityAccessService;
    this.aiContentService = aiContentService;
    this.aiStatus = aiStatus;
  }

  /** 一次润色/总结请求（HTTP 层已把 body 映射成本记录）。 */
  public record PolishCommand(String content, String mode, Long customerId) {}

  /** 结果。{@code model} 是<b>实际使用</b>的模型名（来自配置，不是调用方给的）；{@code truncated} 见契约 §2.4。 */
  public record PolishResult(String text, String model, boolean truncated) {}

  /** 生成一段整理后的跟进记录。 */
  public PolishResult generate(PolishCommand cmd) {
    // 未配置：连校验都不做之外的事——"零出站"的结构性保证在 AiContentService 里（本行省掉的是无谓的工作）。
    if (!aiStatus.isConfigured()) {
      throw new AiNotConfiguredException();
    }
    String mode = validate(cmd);

    Long userId = SecurityUtil.currentUserId();
    String customerName = null;
    if (cmd.customerId() != null) {
      // 房规：缺失 404（require 自己抛）、不可见 403。契约原表的"不区分 404/403"已被 C3 订正作废。
      Customer customer = customerService.require(cmd.customerId());
      if (!entityAccessService.canViewCustomer(userId, customer.getId())) {
        throw new BusinessException(ErrorCode.FORBIDDEN);
      }
      // §5.4：客户名只在 POLISH 时作上下文（SUMMARIZE 的输入是用户自己贴的多条记录，客户名不是它的用途）。
      // ⚠️ 可见性判定**不**跟着 mode 走：契约 §2.4 写"customerId 提供则**须在可见范围内**"，是无条件的
      // ——"提供了 id 但那条记录不归我"这件事不该因模式不同而变成 200。
      if (AiPromptCatalog.P3_MODE_POLISH.equals(mode)) {
        customerName = blankToNull(customer.getName());
      }
    }

    AiContentService.AiGeneration generation =
        aiContentService.generate(
            new AiContentService.AiRequest(
                AiPromptCatalog.P3_CAPABILITY,
                // 同一个 userId 既决定"能看什么"（上面那道可见性判定），又决定"记在谁头上"（日预算桶）。
                // 两处用同一个值不是巧合：它必须来自同一次身份解析。
                userId,
                "CUSTOMER",
                cmd.customerId(),
                AiPromptCatalog.P3_SYSTEM_PROMPT,
                AiPromptCatalog.renderFollowUpPolishUserPrompt(
                    new AiPromptCatalog.PolishContext(cmd.content(), customerName, mode))));

    return new PolishResult(generation.text(), aiStatus.model(), generation.truncated());
  }

  /** 入参校验。返回<b>规范化后</b>的模式值。 */
  private String validate(PolishCommand cmd) {
    String content = cmd.content();
    if (content == null || content.isBlank()) {
      // ⚠️ 空白串与 null 同判：一段只含空格/换行的"跟进内容"没有任何可以整理的东西，放过去等于让模型
      // 面对一个空输入自由发挥（US3-AS2 要的正是"空输入被拒"）。
      throw new BusinessException(ErrorCode.BAD_REQUEST, "跟进内容不能为空");
    }
    if (content.length() > AiPromptCatalog.P3_CONTENT_MAX_CHARS) {
      // ⚠️ 这里是"拒绝"，不是"截断"：本能力的输入是用户自己写的那段话，静默截掉一截会让输出丢掉他写过的
      // 内容，而他看不出是模型漏了还是我们截了（渲染器没地方标"已节选"而不误导模型）。逐条理由见
      // AiPromptCatalog.P3_CONTENT_FIELDS 的 javadoc。
      throw new BusinessException(
          ErrorCode.BAD_REQUEST, "跟进内容不能超过 " + AiPromptCatalog.P3_CONTENT_MAX_CHARS + " 字符");
    }
    String mode = cmd.mode();
    if (mode == null || mode.isBlank()) {
      throw new BusinessException(
          ErrorCode.BAD_REQUEST, "mode 必填，只能是 " + String.join(" / ", AiPromptCatalog.P3_MODES));
    }
    String normalized = mode.trim().toUpperCase();
    if (!AiPromptCatalog.P3_MODES.contains(normalized)) {
      // 回显合法集合（同 P1 的 tone 分支：调用方要能照着这条消息改好这次请求）。
      throw new BusinessException(
          ErrorCode.BAD_REQUEST, "mode 只能是 " + String.join(" / ", AiPromptCatalog.P3_MODES));
    }
    return normalized;
  }

  private static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s.strip();
  }
}
