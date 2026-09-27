package com.crm.controller;

import com.crm.common.ApiResponse;
import com.crm.security.RateLimit;
import com.crm.security.RateLimitDimension;
import com.crm.security.RequirePermission;
import com.crm.service.AiCustomerSummaryService;
import com.crm.service.AiEmailDraftService;
import com.crm.service.AiFollowUpPolishService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 文本生成端点（104-ai-content-generation，契约 {@code contracts/ai-content-generation.md}）。
 *
 * <p><b>本类最终会有四个端点，已落三个</b>：{@code POST /email-draft}（P1，C3）、{@code POST /customer-summary}（P2，C6）与
 * {@code POST /followup-polish}（P3，C7）。 仅剩 P4 的 {@code
 * /opportunity-advice}（T071）在自己的批次里加进来——<b>不预先占位</b>： 一个没有实现的方法要么必须写死返回、要么直接抛，两者都是把
 * "未完成"伪装成"已完成"的形态。
 *
 * <p><b>三个端点共用同一套门</b>：同一个权限码 {@code ai:generate}、同一个限流 scope {@code ai-generate}——
 * 它们花的是同一笔外部计费调用、同一个日预算桶（FR-015/FR-017），分成两套限额只会让"总花费"变成两个都不能单独回答的数。 ⚠️ P3
 * 是第三个加入这条不变式的端点，<b>不是例外</b>：它的输入是用户自己的文本（不取任何客户数据），但"花谁的钱"
 * 与"能不能用"这两件事与前两个端点<b>完全同一</b>——按输入形态给权限却按成本给限额，会让这两条口径从此分裂。
 *
 * <p><b>权限码 {@code ai:generate} 按判据③ 裁为"一个角色都不授予"</b>（详见 {@code RoleConstants} 里那段
 * 注释）：本端点挂在<b>既有页面</b>上（不是新菜单），既无"改造前那道粗粒度门"可继承、也无菜单承诺可依据 ⇒ 不补授、只接码。这不是遗漏：它的可执行痕迹是 {@code
 * com.crm.integration.AiPermissionGrantIT} （照 {@code PermissionMatrixIT} 的 FR-G14 判例，带正对照）。
 *
 * <p><b>后果明写（含一处容易写反的地方）</b>：{@code PermissionAspect} 里 {@code ADMIN} 内建角色<b>恒放行</b> （{@code
 * PermissionAspect.java:43-44}），所以零授予的实际后果<b>不是</b>"连 ADMIN 也被拒"，而是—— {@code ADMIN}
 * 照旧能用（它压根不看这道门），<b>其余全部角色一律 403</b>，直到某位管理员在角色页上把 {@code ai:generate} 勾给某个角色为止。这正是 {@code
 * ADMIN_ONLY_BY_DEFAULT} 这个名字的含义。 「ADMIN 恒放行」是一个独立的不变式（有它自己的用例），本项不复制、也不依赖它来做授权裁决。
 *
 * <p><b>限流参数为什么是 {@code ai-generate} / 10 / 60s / USER</b>：① <b>独立 scope</b>——与 {@code
 * export-generate} 同级（单次成本高、无批量场景），但不共用它的桶：共用等于让导出把生成配额吃掉， 而两者的成本量级与失败模式都不同（导出是本地 CPU，生成是外部计费调用）。②
 * <b>窗口 10 次</b>——真正的成本闸 是 FR-015/FR-017 的<b>日预算</b>（Redis {@code
 * ai:gen:budget:*}），本注解管的是<b>突发</b>：拦截连点与脚本 循环，故可以给得比日预算宽松。③ <b>按 USER</b>——本端点已认证，且"谁在花钱"才是要限的量；按
 * IP 会在 NAT 后把同事一起限掉（照导出与导入的裁决）。
 *
 * <p><b>⚠️ 状态码与本契约 §3 原表不一致之处（就地订正，理由在契约里）</b>：
 *
 * <ul>
 *   <li>契约原表写"实体不存在 / 不在可见范围 → 404（不区分）"——实测既有实体端点对<b>不可见</b>抛的是 <b>403</b>，只有确实不存在才 404（{@code
 *       CustomerService.checkViewPermission} → {@code FORBIDDEN}； {@code ContactService} / {@code
 *       FollowUpService} 同）。照原表实现会造出全仓唯一一个"不可见也回 404" 的实体端点。⇒ <b>改走房规：缺失 404、不可见 403。</b>
 *   <li>契约原表写"长度超限 / 枚举非法 → 422"——全仓<b>没有通用 422 校验码</b>（422 那 49 处全是实体专属的）， 而 {@code @Valid}
 *       那条路在本仓一律映射成 <b>400</b>（{@code GlobalExceptionHandler} 写死，且 FR-V15 有 用例钉着"别的端点仍是
 *       400"）。为一个入参枚举值新造通用 422 码是与本项无关的全局口径变更 ⇒ <b>改走 400 + 既有 {@code BAD_REQUEST}</b>，由 {@link
 *       AiEmailDraftService} 在服务层判（该处有逐条理由）。
 *   <li>未配置 → 409、上游不可用 → 503、模型拒答 → 422、预算耗尽 → 429 <b>按原表</b>（四个状态码都有真实 先例，见契约
 *       §4）。截断<b>不是错误</b>：{@code truncated=true} 仍是 200。
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/ai")
@Tag(name = "AI 文本生成", description = "生成式内容辅助（104）")
public class AiContentController {

  private final AiEmailDraftService aiEmailDraftService;
  private final AiCustomerSummaryService aiCustomerSummaryService;
  private final AiFollowUpPolishService aiFollowUpPolishService;

  public AiContentController(
      AiEmailDraftService aiEmailDraftService,
      AiCustomerSummaryService aiCustomerSummaryService,
      AiFollowUpPolishService aiFollowUpPolishService) {
    this.aiEmailDraftService = aiEmailDraftService;
    this.aiCustomerSummaryService = aiCustomerSummaryService;
    this.aiFollowUpPolishService = aiFollowUpPolishService;
  }

  /**
   * 请求体。字段与命名<b>逐字对着契约 §2.2 的表</b>；两个 id 至少提供一个（服务层判）。
   *
   * <p>刻意<b>不用 {@code @Valid}</b>：本仓的 Bean Validation 失败一律变成 400 + {@code BAD_REQUEST} + {@code
   * fieldErrors}，而本契约要的两条约束（长度、枚举）都需要<b>可读的中文说明</b>且枚举值必须回显合法集合 ——写在服务层比写在注解上更清楚，也避免为"另一种 400
   * 形状"再引入一套语义。
   */
  public record EmailDraftRequest(
      Long customerId, Long opportunityId, String tone, String instruction) {}

  /** 响应体：契约 §2.2 的 {@code data}。<b>不返回</b> token usage（FR-016 的 usage 只落审计）。 */
  public record EmailDraftResponse(String text, String model, boolean truncated) {}

  /**
   * P2 请求体。<b>只有一个字段</b>——契约 §2.3 明写本能力无 {@code tone} / {@code instruction}。
   *
   * <p>这不是"先留空、以后再补"：请求体多一个自由文本字段，就等于多一条把任意用户输入送进提示词的路径（P1 的 {@code instruction} 为此单独定了长度上限与 {@code
   * INSTRUCTION_MAX_CHARS}）。本能力不需要它，故不设。
   */
  public record CustomerSummaryRequest(Long customerId) {}

  /** P2 响应体：契约 §2.3 的 {@code data}，与 P1 同形。 */
  public record CustomerSummaryResponse(String text, String model, boolean truncated) {}

  /**
   * P3 请求体。字段与命名<b>逐字对着契约 §2.4 的表</b>；{@code content} 与 {@code mode} 必填，由服务层判。
   *
   * <p>⚠️ 与 P2 的形制<b>相反</b>：P2 刻意只有一个字段（请求体多一个自由文本字段就多一条注入路径），而本能力
   * <b>必须</b>收一段自由文本——它就是这项能力的输入。故这里多出来的是 {@code content}（有长度上限与"非空"两条 校验在服务层）与 {@code
   * mode}（二值枚举，决定整理方式）。
   *
   * <p>为什么仍是<b>不用 {@code @Valid}</b>：同 P1（两条约束都要可读的中文说明，且枚举非法时必须回显合法集合）。
   */
  public record FollowUpPolishRequest(String content, String mode, Long customerId) {}

  /** P3 响应体：契约 §2.4 的 {@code data}，与 P1/P2 同形。 */
  public record FollowUpPolishResponse(String text, String model, boolean truncated) {}

  /**
   * 生成一封发给客户的邮件草稿。
   *
   * <p>审计落在 {@code CUSTOMER}（本能力的载体是客户）上，动作 {@code GENERATE}，由 {@link
   * com.crm.service.AiContentService} 统一写入——<b>控制器不写审计</b>：写审计的地方若有两个，就必须在 两处都保证"detail
   * 不含提示词与客户数据"（FR-016），而那是同一件事的两处表述。
   */
  @PostMapping("/email-draft")
  @RequirePermission("ai:generate")
  @RateLimit(scope = "ai-generate", limit = 10, windowSeconds = 60, by = RateLimitDimension.USER)
  @Operation(summary = "生成邮件草稿（P1，返回纯文本正文）")
  public ApiResponse<EmailDraftResponse> emailDraft(@RequestBody EmailDraftRequest request) {
    AiEmailDraftService.DraftResult result =
        aiEmailDraftService.generate(
            new AiEmailDraftService.DraftCommand(
                request.customerId(),
                request.opportunityId(),
                request.tone(),
                request.instruction()));
    return ApiResponse.ok(
        new EmailDraftResponse(result.text(), result.model(), result.truncated()));
  }

  /**
   * 生成一名客户的 360 摘要（P2）。
   *
   * <p>审计同样落在 {@code CUSTOMER} 上、动作 {@code GENERATE}，由 {@link com.crm.service.AiContentService}
   * 统一写入（理由同 P1：写审计的地方若有两个，就要在两处都保证 detail 不含提示词与客户数据）。
   */
  @PostMapping("/customer-summary")
  @RequirePermission("ai:generate")
  @RateLimit(scope = "ai-generate", limit = 10, windowSeconds = 60, by = RateLimitDimension.USER)
  @Operation(summary = "生成客户 360 摘要（P2，返回纯文本）")
  public ApiResponse<CustomerSummaryResponse> customerSummary(
      @RequestBody CustomerSummaryRequest request) {
    AiCustomerSummaryService.SummaryResult result =
        aiCustomerSummaryService.generate(request.customerId());
    return ApiResponse.ok(
        new CustomerSummaryResponse(result.text(), result.model(), result.truncated()));
  }

  /**
   * 润色 / 总结一段跟进记录（P3）。
   *
   * <p><b>审计同样锚在 {@code CUSTOMER} 上</b>、动作 {@code GENERATE}，由 {@link
   * com.crm.service.AiContentService} 统一写入（理由同 P1/P2：写审计的地方若有两个，就要在两处都保证 detail 不含提示词与用户数据）。⚠️
   * {@code customerId} 在本端点是<b>可选</b>的：没给时那次生成的审计行 {@code entity_id} 为
   * null，语义是"这次生成没有绑定任何实体"——逐条理由在 {@link AiFollowUpPolishService} 的类注释里。
   */
  @PostMapping("/followup-polish")
  @RequirePermission("ai:generate")
  @RateLimit(scope = "ai-generate", limit = 10, windowSeconds = 60, by = RateLimitDimension.USER)
  @Operation(summary = "润色 / 总结跟进记录（P3，返回纯文本）")
  public ApiResponse<FollowUpPolishResponse> followUpPolish(
      @RequestBody FollowUpPolishRequest request) {
    AiFollowUpPolishService.PolishResult result =
        aiFollowUpPolishService.generate(
            new AiFollowUpPolishService.PolishCommand(
                request.content(), request.mode(), request.customerId()));
    return ApiResponse.ok(
        new FollowUpPolishResponse(result.text(), result.model(), result.truncated()));
  }
}
