package com.crm.service;

import com.crm.common.AiNotConfiguredException;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.config.AiStatus;
import com.crm.dto.contact.ContactResponse;
import com.crm.dto.followup.FollowUpResponse;
import com.crm.dto.opportunity.SalesOpportunityResponse;
import com.crm.entity.Customer;
import com.crm.entity.Opportunity;
import com.crm.security.SecurityUtil;
import com.crm.support.BuiltinFieldRegistry;
import com.crm.support.FieldMaskPlanner;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * P1 邮件草稿的<b>上下文装配</b>（104-ai-content-generation）。
 *
 * <p><b>拿捏的是本项最重的安全面（FR-010 / FR-011 / FR-013）</b>：这个类决定"客户的哪些数据会离开本系统、交给
 * 一个外部模型"。因此它只做三件事，且每件都有对应判据：
 *
 * <ol>
 *   <li><b>取数只经既有服务方法</b>（FR-010）：本类<b>不注入任何 Mapper</b>、不出现 {@code selectList} / {@code
 *       selectById}。022 的缺陷正是"直查裸 Mapper 而不加范围过滤"（{@code SuggestionService.java:87-92}
 *       等三处，而全仓没有任何数据权限拦截器 ⇒ 没有隐式兜底）。本类靠<b>依赖注入的清单</b>把这条钉死： 构造器里没有 Mapper，"顺手查一下"就写不出来。
 *   <li><b>可见性在读取之前判</b>：客户走 {@code CustomerService.require}（不存在 → 404）+ {@code
 *       EntityAccessService.canViewCustomer}（不可见 → 403）；商机同理（{@code canViewOpportunity}）。
 *       跟进与联系人由既有服务自带范围校验（{@code FollowUpService.page} 在不可见时抛 403； {@code ContactService.page} 用
 *       {@code customerId IN 可见集} 过滤）。
 *   <li><b>字段级权限（102 FLS）在装配时应用</b>（FR-013）：{@code status} / {@code contactPerson} 是 102
 *       已登记的客户内建字段，故按 {@code FieldMaskPlanner.plan(...)} 的 HIDDEN 集合<b>逐个决定送不送</b>。 ⚠️
 *       这里必须自己判——{@code FieldMaskingResponseBodyAdvice} 只在<b>HTTP 响应</b>链路上擦字段，
 *       本类拼出来的提示词<b>不经过它</b>。靠"响应侧会擦"来保护提示词，是保护不到的。
 * </ol>
 *
 * <p><b>为什么不在类里再判一次 {@code isConfigured()}</b>：判了。这一层判是为了"未配置时连<b>取数</b>都不做" （外层省掉全部数据库往返）；{@link
 * AiContentService#generate} 里那一句才是"未配置 ⇒ 零出站"的<b>结构性</b> 保证（它之上没有任何网络调用）。两道都在，且各自有各自的理由——不是重复。
 *
 * <p><b>商机字段不需要 FLS 过滤（不是漏了）</b>：{@code BuiltinFieldRegistry} 为 {@code OPPORTUNITY} 登记的 是 {@code
 * expectedAmountMin} / {@code expectedAmountMax} / {@code remark} / {@code status} 四个，而 P1 送的是
 * {@code name} / {@code amount} / {@code stage} / {@code expectedCloseDate}——与那四个<b>无一相交</b>。
 * 一旦白名单变动到与它们相交，这段推理即失效，必须补上过滤。
 */
@Service
public class AiEmailDraftService {

  private final CustomerService customerService;
  private final OpportunityService opportunityService;
  private final SalesOpportunityService salesOpportunityService;
  private final ContactService contactService;
  private final FollowUpService followUpService;
  private final EntityAccessService entityAccessService;
  private final FieldMaskPlanner fieldMaskPlanner;
  private final AiContentService aiContentService;
  private final AiStatus aiStatus;

  public AiEmailDraftService(
      CustomerService customerService,
      OpportunityService opportunityService,
      SalesOpportunityService salesOpportunityService,
      ContactService contactService,
      FollowUpService followUpService,
      EntityAccessService entityAccessService,
      FieldMaskPlanner fieldMaskPlanner,
      AiContentService aiContentService,
      AiStatus aiStatus) {
    this.customerService = customerService;
    this.opportunityService = opportunityService;
    this.salesOpportunityService = salesOpportunityService;
    this.contactService = contactService;
    this.followUpService = followUpService;
    this.entityAccessService = entityAccessService;
    this.fieldMaskPlanner = fieldMaskPlanner;
    this.aiContentService = aiContentService;
    this.aiStatus = aiStatus;
  }

  /** 一次草稿请求（HTTP 层已把 body 映射成本记录）。 */
  public record DraftCommand(
      Long customerId, Long opportunityId, String tone, String instruction) {}

  /** 草稿结果。{@code model} 是<b>实际使用</b>的模型名（来自配置，不是调用方给的）；{@code truncated} 见契约 §2.2。 */
  public record DraftResult(String text, String model, boolean truncated) {}

  /**
   * 生成一封邮件草稿。
   *
   * <p>顺序即判据：<b>判门 → 校验 → 取数（含可见性）→ 装配（含 FLS）→ 出站</b>。任何一步失败都在出站之前。
   */
  public DraftResult generate(DraftCommand cmd) {
    // 未配置：连取数都不做（外层省去全部数据库往返）。"零出站"的结构性保证在 AiContentService 里。
    if (!aiStatus.isConfigured()) {
      throw new AiNotConfiguredException();
    }
    String tone = validate(cmd);

    Long userId = SecurityUtil.currentUserId();
    Opportunity opportunity = null;
    if (cmd.opportunityId() != null) {
      opportunity = opportunityService.require(cmd.opportunityId());
      if (!entityAccessService.canViewOpportunity(userId, cmd.opportunityId())) {
        throw new BusinessException(ErrorCode.FORBIDDEN);
      }
    }

    Long customerId = cmd.customerId();
    if (customerId == null) {
      // 只给了 opportunityId：客户由商机反查得出。这里的判空在 validate 之后**不可达**，写出来是为了让
      // "两者至少其一"这条不变式在本方法内自证——否则它就靠"读者去 validate 里推断"才成立，而静态分析
      // 看不见那种推断（会一直报 opportunity 可能为 null）。
      if (opportunity == null) {
        throw new BusinessException(ErrorCode.BAD_REQUEST, "customerId 与 opportunityId 至少提供一个");
      }
      customerId = opportunity.getCustomerId();
    } else if (opportunity != null && !customerId.equals(opportunity.getCustomerId())) {
      // 两个 id 指向不同客户：这是调用方把上下文搞错了，不是权限问题 ⇒ 400 而不是 403/404。
      throw new BusinessException(
          ErrorCode.BAD_REQUEST, "customerId 与 opportunityId 不属于同一个客户，无法确定上下文");
    }
    Customer customer = customerService.require(customerId);
    // 显式再判一次客户可见性：canViewOpportunity 目前**恰好**委托到 canViewCustomer（商机无 owner），
    // 但那是对另一个类内部实现的依赖——它一变，本端点就会静默丢掉这道检查。这里各判各的，代价是一次查询。
    if (!entityAccessService.canViewCustomer(userId, customer.getId())) {
      throw new BusinessException(ErrorCode.FORBIDDEN);
    }

    AiPromptCatalog.Context ctx = buildContext(customer, opportunity, cmd.instruction(), tone);
    AiContentService.AiGeneration generation =
        aiContentService.generate(
            new AiContentService.AiRequest(
                AiPromptCatalog.P1_CAPABILITY,
                "CUSTOMER",
                customer.getId(),
                AiPromptCatalog.P1_SYSTEM_PROMPT,
                AiPromptCatalog.renderEmailDraftUserPrompt(ctx)));

    return new DraftResult(generation.text(), aiStatus.model(), generation.truncated());
  }

  /** 入参校验。返回<b>规范化后</b>的语气值。 */
  private String validate(DraftCommand cmd) {
    if (cmd.customerId() == null && cmd.opportunityId() == null) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "customerId 与 opportunityId 至少提供一个");
    }
    String instruction = cmd.instruction();
    if (instruction != null && instruction.length() > AiPromptCatalog.INSTRUCTION_MAX_CHARS) {
      throw new BusinessException(
          ErrorCode.BAD_REQUEST, "补写要求不能超过 " + AiPromptCatalog.INSTRUCTION_MAX_CHARS + " 字符");
    }
    String tone = cmd.tone();
    if (tone == null || tone.isBlank()) {
      return AiPromptCatalog.DEFAULT_TONE;
    }
    String normalized = tone.trim().toUpperCase();
    if (!AiPromptCatalog.TONES.contains(normalized)) {
      // ⚠️ 400 而不是 422：全仓没有<b>通用</b>的 422 校验码（422 那 49 处全是实体专属的），而
      // @Valid 那条路在本仓一律映射成 400（GlobalExceptionHandler 里写死，且 FR-V15 有用例钉着
      // "别的端点仍是 400"）。为一个入参枚举值新造一个通用 422 码，是与本项无关的全局口径变更。
      throw new BusinessException(
          ErrorCode.BAD_REQUEST, "tone 只能是 " + String.join(" / ", AiPromptCatalog.TONES));
    }
    return normalized;
  }

  /** 装配 P1 上下文：每一步取数都写出"它凭什么安全"。 */
  private AiPromptCatalog.Context buildContext(
      Customer customer, Opportunity opportunity, String instruction, String tone) {
    // 102 FLS：HIDDEN 的内建字段既不送、也不该出现在输出里。
    Set<String> hidden =
        fieldMaskPlanner.plan(fieldMaskPlanner.currentRole(), BuiltinFieldRegistry.ENTITY_CUSTOMER);

    String contactName = null;
    if (!hidden.contains("contactPerson")) {
      contactName = blankToNull(customer.getContactPerson());
    }
    if (contactName == null) {
      // 客户档案上的联系人字段为空时，退到联系人表（ContactService.page 自带范围过滤：
      // 非 ADMIN 只会看到 customerId ∈ 可见集的联系人）。
      List<ContactResponse> contacts =
          contactService
              .page(null, customer.getId(), null, 1, AiPromptCatalog.P1_CONTACT_LIMIT)
              .getItems();
      for (ContactResponse c : contacts) {
        if (c.getName() != null && !c.getName().isBlank()) {
          contactName = c.getName();
          break;
        }
      }
    }

    String opportunityName = null;
    String amount = null;
    String stage = null;
    String expectedCloseDate = null;
    if (opportunity != null) {
      opportunityName = blankToNull(opportunity.getName());
      // stage / expectedCloseDate / amount 住在 SalesOpportunity 上，须多一跳。
      // 该 page 自身无范围校验，但上面已判过商机可见性，且这里**只按 opportunityId 过滤** ⇒ 取不到别人家的行。
      List<SalesOpportunityResponse> rows =
          salesOpportunityService
              .page(null, opportunity.getId(), null, 1, AiPromptCatalog.P1_SALES_OPPORTUNITY_LIMIT)
              .getItems();
      if (!rows.isEmpty()) {
        SalesOpportunityResponse row = rows.get(0);
        amount = row.getAmount() == null ? null : String.valueOf(row.getAmount());
        stage = blankToNull(row.getStage());
        expectedCloseDate =
            row.getExpectedCloseDate() == null ? null : row.getExpectedCloseDate().toString();
      }
    }

    List<AiPromptCatalog.FollowUpExcerpt> followUps = new ArrayList<>();
    // FollowUpService.page 自带行级可见性校验（不可见 → 403），且按 createdAt DESC 排序 ⇒ 第 1 页即"最近 N 条"。
    for (FollowUpResponse f :
        followUpService
            .page(customer.getId(), null, null, 1, AiPromptCatalog.P1_FOLLOWUP_LIMIT)
            .getItems()) {
      AiPromptCatalog.Truncated excerpt =
          AiPromptCatalog.excerpt(f.getContent(), AiPromptCatalog.P1_FOLLOWUP_EXCERPT_CHARS);
      if (excerpt.text().isEmpty()) {
        continue;
      }
      followUps.add(
          new AiPromptCatalog.FollowUpExcerpt(
              f.getCreatedAt() == null ? null : f.getCreatedAt().toLocalDate().toString(),
              excerpt.text(),
              excerpt.truncated()));
    }

    return new AiPromptCatalog.Context(
        blankToNull(customer.getName()),
        blankToNull(customer.getCompany()),
        hidden.contains("status") ? null : blankToNull(customer.getStatus()),
        contactName,
        opportunityName,
        amount,
        stage,
        expectedCloseDate,
        followUps,
        tone,
        blankToNull(instruction));
  }

  private static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s.strip();
  }
}
