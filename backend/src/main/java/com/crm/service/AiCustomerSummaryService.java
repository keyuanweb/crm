package com.crm.service;

import com.crm.common.AiNotConfiguredException;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.config.AiStatus;
import com.crm.dto.customer.Customer360Response;
import com.crm.dto.customer.HealthScoreDTO;
import com.crm.dto.followup.FollowUpResponse;
import com.crm.dto.tag.TagResponse;
import com.crm.entity.Customer;
import com.crm.security.SecurityUtil;
import com.crm.support.BuiltinFieldRegistry;
import com.crm.support.FieldMaskPlanner;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * P2 客户 360 摘要的<b>上下文装配</b>（104-ai-content-generation，契约 {@code contracts/} §5.3）。
 *
 * <p><b>与 P1 同源的三条纪律</b>（理由不重复，见 {@link AiEmailDraftService} 的类注释）：
 *
 * <ol>
 *   <li><b>取数只经既有服务方法</b>：本类<b>不注入任何 Mapper</b>。客户 360 的聚合走 {@link
 *       Customer360Service#aggregate}、标签走 {@link TagService#customerTags}、跟进走 {@link
 *       FollowUpService#page}——都是公开的既有服务方法，本类一次 {@code select*} 都没有。
 *   <li><b>可见性在读取之前判</b>：{@code CustomerService.require}（不存在 → 404）+ {@code
 *       EntityAccessService.canViewCustomer}（不可见 → 403）。这一步在取 360 之前完成。
 *   <li><b>字段级权限（102 FLS）在装配时应用</b>（FR-013）：见下面那段"交集只有 status"的推导。
 * </ol>
 *
 * <p><b>⚠️ 为什么"复用 {@code Customer360Service.aggregate} 没有违反纪律 ①"——这句话必须写下来，否则会被读成破例</b>： 纪律 ①
 * 钉的是<b>本类的依赖清单</b>（构造器里没有 Mapper ⇒"顺手查一下"写不出来），而"调用既有的公开服务方法"<b>正是</b>纪律 ① 要求的东西。真正的风险在别处：{@code
 * Customer360Service} <b>自身不做任何可见性过滤</b>（它注入 6 个 Mapper、按 customerId 直查），所以纪律 ②
 * 必须由本类在<b>调用它之前</b>自己完成——{@code aggregate} 里没有任何一道门可以依赖。 这也是 P1 里 {@code canViewOpportunity}
 * 那段注释的同一条道理：别的类的内部实现会变，本端点的门不能建在它上面。
 *
 * <p><b>⚠️ 为什么健康度不是"有就送"（这是 US2-AS2 在本能力里最容易被忽略的落点）</b>：{@code HealthScoreService}
 * 是<b>从满分往下扣</b>的——订单金额、逾期回款、未结工单、最近跟进、最近活动，五个维度一件素材都没有时它照样返回 {@code 100 /
 * GREEN}。若照直送出去，模型会写"客户健康状况良好"，而事实是这个客户<b>一笔业务都没有</b>。这条编造不是模型干的，是<b>我们先喂给它的</b>——
 * 所以护栏必须在这一层，提示词侧写多少条规则都拦不住（提示词规则 3 说的是"资料里没有的不得编造"，而"健康度 100"在资料里<b>有</b>）。
 * 判据因此写成"<b>至少一个评分维度有素材</b>"，而不是"{@code health != null}"（后者恒真）。
 */
@Service
public class AiCustomerSummaryService {

  private final CustomerService customerService;
  private final Customer360Service customer360Service;
  private final TagService tagService;
  private final FollowUpService followUpService;
  private final EntityAccessService entityAccessService;
  private final FieldMaskPlanner fieldMaskPlanner;
  private final AiContentService aiContentService;
  private final AiStatus aiStatus;

  public AiCustomerSummaryService(
      CustomerService customerService,
      Customer360Service customer360Service,
      TagService tagService,
      FollowUpService followUpService,
      EntityAccessService entityAccessService,
      FieldMaskPlanner fieldMaskPlanner,
      AiContentService aiContentService,
      AiStatus aiStatus) {
    this.customerService = customerService;
    this.customer360Service = customer360Service;
    this.tagService = tagService;
    this.followUpService = followUpService;
    this.entityAccessService = entityAccessService;
    this.fieldMaskPlanner = fieldMaskPlanner;
    this.aiContentService = aiContentService;
    this.aiStatus = aiStatus;
  }

  /** 摘要结果。{@code model} 是<b>实际使用</b>的模型名（来自配置，不是调用方给的）；{@code truncated} 见契约 §2.2。 */
  public record SummaryResult(String text, String model, boolean truncated) {}

  /**
   * 生成一名客户的 360 摘要。请求体只有 {@code customerId}（契约 §2.3：本能力无 {@code tone} / {@code
   * instruction}）——上下文完全由服务端构造，故提示词的上界也完全由构造给出。
   *
   * <p>顺序即判据：<b>判门 → 校验 → 取数（含可见性）→ 装配（含 FLS）→ 出站</b>。任何一步失败都在出站之前。
   */
  public SummaryResult generate(Long customerId) {
    // 未配置：连取数都不做。"零出站"的结构性保证在 AiContentService 里（它之上没有任何网络调用）。
    if (!aiStatus.isConfigured()) {
      throw new AiNotConfiguredException();
    }
    if (customerId == null) {
      throw new BusinessException(ErrorCode.BAD_REQUEST, "customerId 不能为空");
    }

    Long userId = SecurityUtil.currentUserId();
    Customer customer = customerService.require(customerId);
    if (!entityAccessService.canViewCustomer(userId, customerId)) {
      throw new BusinessException(ErrorCode.FORBIDDEN);
    }

    AiPromptCatalog.SummaryContext ctx = buildContext(customer);
    AiContentService.AiGeneration generation =
        aiContentService.generate(
            new AiContentService.AiRequest(
                AiPromptCatalog.P2_CAPABILITY,
                // 与 P1 同理：同一个 userId 既决定"能看什么"（上面那道可见性判定），又决定"记在谁头上"（日预算桶）。
                userId,
                "CUSTOMER",
                customerId,
                AiPromptCatalog.P2_SYSTEM_PROMPT,
                AiPromptCatalog.renderSummaryUserPrompt(ctx)));

    return new SummaryResult(generation.text(), aiStatus.model(), generation.truncated());
  }

  /** 装配 P2 上下文：每一步取数都写出"它凭什么安全"。 */
  private AiPromptCatalog.SummaryContext buildContext(Customer customer) {
    Long customerId = customer.getId();

    // 102 FLS：HIDDEN 的内建字段既不送、也不该出现在输出里。
    //
    // ⚠️ 本能力的白名单与已登记的 CUSTOMER 内建字段**只有一个交集：status**。逐项对照（这是"交集只剩一个"的
    // 自证，不是印象）：内建 7 项 = contactPerson / phone / email / address / remark / status / campaignId，
    // 而 P2_CUSTOMER_FIELDS = name / company / status ⇒ phone / email / address 本就不在送入清单里（不是靠
    // HIDDEN 拦的）；contactPerson / remark / campaignId 由 §5.3 明文排除（类注释与 AiPromptCatalog 里各有理由）。
    // ⇒ 只有 status 需要这一类判定。⚠️ 白名单一旦变动到与上述字段相交，这段推理即失效，必须补上过滤。
    Set<String> hidden =
        fieldMaskPlanner.plan(fieldMaskPlanner.currentRole(), BuiltinFieldRegistry.ENTITY_CUSTOMER);

    // 360 聚合：orders / paymentSummaries / contracts / tickets / amountSummary / health。
    // ⚠️ 可见性已在上游判过（本方法只被 generate 调用），此处**只按 customerId 过滤** ⇒ 取不到别人的行。
    Customer360Response agg = customer360Service.aggregate(customerId);
    List<Customer360Response.OrderBrief> orders =
        agg.getOrders() == null ? List.of() : agg.getOrders();
    Customer360Response.AmountSummary amount = agg.getAmountSummary();

    List<AiPromptCatalog.StatusCount> contractStatuses =
        bucket(agg.getContracts(), Customer360Response.ContractBrief::getStatus);
    List<AiPromptCatalog.StatusCount> ticketStatuses =
        bucket(agg.getTickets(), Customer360Response.TicketBrief::getStatus);

    List<String> tags = new ArrayList<>();
    for (TagResponse t : tagService.customerTags(customerId)) {
      String name = blankToNull(t.getName());
      if (name != null) {
        tags.add(name);
      }
      if (tags.size() >= AiPromptCatalog.P2_TAG_LIMIT) {
        break;
      }
    }

    List<AiPromptCatalog.FollowUpExcerpt> followUps = new ArrayList<>();
    // FollowUpService.page 自带行级可见性校验（不可见 → 403），且按 createdAt DESC 排序 ⇒ 第 1 页即"最近 N 条"。
    for (FollowUpResponse f :
        followUpService
            .page(customerId, null, null, 1, AiPromptCatalog.P2_FOLLOWUP_LIMIT)
            .getItems()) {
      AiPromptCatalog.Truncated excerpt =
          AiPromptCatalog.excerpt(f.getContent(), AiPromptCatalog.P2_FOLLOWUP_EXCERPT_CHARS);
      if (excerpt.text().isEmpty()) {
        continue;
      }
      followUps.add(
          new AiPromptCatalog.FollowUpExcerpt(
              f.getCreatedAt() == null ? null : f.getCreatedAt().toLocalDate().toString(),
              excerpt.text(),
              excerpt.truncated()));
    }

    // 健康度：只在"至少一个评分维度有素材"时才送（理由见类注释）。素材与 HealthInput 的五个输入一一对应：
    // 订单金额 ← 订单；未结工单 ← 工单；最近跟进 / 最近活动 ← 跟进。
    // ⚠️ 合同**不是**评分输入——只有合同的客户在引擎眼里同样是"零素材"，别把它算进来。
    HealthScoreDTO health = agg.getHealth();
    Integer healthScore = null;
    String healthLevel = null;
    if (health != null
        && (!orders.isEmpty() || !ticketStatuses.isEmpty() || !followUps.isEmpty())) {
      healthScore = health.getScore();
      healthLevel = blankToNull(health.getLevel());
    }

    return new AiPromptCatalog.SummaryContext(
        blankToNull(customer.getName()),
        blankToNull(customer.getCompany()),
        hidden.contains("status") ? null : blankToNull(customer.getStatus()),
        tags,
        healthScore,
        healthLevel,
        orders.isEmpty() ? null : orders.size(),
        amount == null ? null : AiPromptCatalog.yuan(amount.getTotalOrder()),
        amount == null ? null : AiPromptCatalog.yuan(amount.getPaid()),
        amount == null ? null : AiPromptCatalog.yuan(amount.getDueOverdue()),
        contractStatuses,
        ticketStatuses,
        followUps);
  }

  /**
   * 把一组实体按某个状态字段折成"状态 → 条数"的桶，<b>按状态名升序</b>。
   *
   * <p>为什么是"按状态名"而不是"按条数"：这两个在提示词里都只是给人看的一行字，但<b>排序必须确定</b>——
   * 同一次聚合两次运行必须渲染出逐字相同的提示词，否则用例无法断言、审计也无法复盘。状态名是枚举常量，天然确定且无并列。
   *
   * <p>为什么桶数有上限：上界由实体自身的状态枚举给出（合同 8 个、工单 4 个 ⇒ {@code P2_STATUS_BUCKETS_LIMIT} = 8
   * 是结构性上界，不是抽样阈值）。理论上取不满，留着是为了"白名单不变而枚举先变多" 时不至于无声超预算。
   */
  private static <T> List<AiPromptCatalog.StatusCount> bucket(
      List<T> rows, java.util.function.Function<T, String> statusOf) {
    if (rows == null || rows.isEmpty()) {
      return List.of();
    }
    Map<String, Integer> counts = new LinkedHashMap<>();
    for (T row : rows) {
      String status = blankToNull(statusOf.apply(row));
      if (status != null) {
        // 不用 merge(status, 1, Integer::sum)：本仓的静态空值分析会把那个 BiFunction 的方法描述符
        // 判成需要 unchecked 转换（两个 Integer 入参 → int 出参），而这里根本不需要装箱的那套语义。
        counts.put(status, counts.getOrDefault(status, 0) + 1);
      }
    }
    return counts.entrySet().stream()
        .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
        .limit(AiPromptCatalog.P2_STATUS_BUCKETS_LIMIT)
        .map(e -> new AiPromptCatalog.StatusCount(e.getKey(), e.getValue()))
        .toList();
  }

  private static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s.strip();
  }
}
