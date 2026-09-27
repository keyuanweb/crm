package com.crm.service;

import java.util.List;
import java.util.Set;

/**
 * 提示词目录（104-ai-content-generation）：逐能力的提示词常量 + **字段白名单** + 纯函数渲染。
 *
 * <p><b>为什么是纯类</b>：本类<b>不认识 Spring、不认识数据库、不认识当前用户</b>——它只把已经装配好的 {@link Context} 渲染成字符串。取数与判权在
 * {@link AiEmailDraftService}，两者分开是为了让白名单这件事 <b>可以在没有容器、没有桩的情况下被断言</b>（{@code
 * AiPromptCatalogTest}）——白名单是本项最重的安全面， 让它只能在起容器之后才测得动，等于让它难以被测。
 *
 * <p><b>分层：每能力一份独立清单，绝不合并</b>（{@code contracts/} §5.6）。四项能力的必要性不同：P3 的 输入是用户自由文本、几乎不需要客户字段；P1
 * 需要客户与商机上下文。合并成一份"全局允许字段集"等于 <b>默认放宽</b>，且放宽是静默的——所以本类里每个 {@code *_FIELDS} 都是<b>按能力分开的常量</b>，
 * 新增能力必须重新逐字段论证。
 *
 * <p><b>⚠️ 白名单常量与渲染函数是同一件事的两处表述，必须联动改</b>：{@code *_FIELDS} 是给人读的清单， {@link
 * #renderEmailDraftUserPrompt} 是真正决定"什么会进提示词"的那段代码。只改一处就会出现
 * 「清单写着不许送、代码照送」（或反过来），而两者都很难从测试里看出来。{@code AiPromptCatalogTest}
 * 用<b>哨兵值</b>看着这条：每个被渲染的字段都塞一个可辨识的值，断言它<b>出现</b>在输出里；同时给未列入
 * 白名单的字段塞哨兵，断言它<b>不出现</b>——只断清单（"白名单里没有电话"）是登记表自证，盖不住渲染函数。
 *
 * <p><b>P1 的上下文预算由构造保证上界</b>：{@link #P1_FOLLOWUP_LIMIT} × （{@link #P1_FOLLOWUP_EXCERPT_CHARS} +
 * 日期与前缀） + 固定字段 ≈ **1.3k**，**再加** {@link #INSTRUCTION_MAX_CHARS} 的 {@code instruction} 上限 ⇒ 单条 user
 * 消息的**标称上界 ≈ 5.3k 字符**。
 *
 * <p>⚠️ <b>这里的第一个数是订正过的</b>：初稿只写了"跟进 + 固定字段 ≈ 1.5k"，把 {@code instruction} 漏在
 * 了算式外面——而它是**唯一由调用方直接控制、且上限最大（4000）**的一段。谈"上界由构造给出"却漏掉最大的一项，
 * 那个上界就是假的（且它会以一个"看起来更安全"的方向失真）。订正后：**无论客户有多少条跟进、每条多长、 调用方要补写多少字，送入量都不超过这个数**——{@code instruction}
 * 超限由 {@code AiEmailDraftService.validate} 拦在出站之前（400，零出站）。
 *
 * <p>故 P1 <b>不另设</b>"总预算截断阈值"—— 上界既然由构造给出，就不存在"静默丢弃"这个失败模式（{@code contracts/} §5.2 要求的"截断并告知"在 P1
 * 退化为<b>逐条截断 + 在提示词里明说</b>）。
 */
public final class AiPromptCatalog {

  private AiPromptCatalog() {}

  // ==================== P1 邮件草稿 ====================

  /** P1 的能力名（进审计 detail 与日志，<b>不进</b>提示词）。 */
  public static final String P1_CAPABILITY = "email-draft";

  /**
   * P1 送入模型的客户字段（{@code contracts/} §5.2 的落地）。
   *
   * <p>⚠️ <b>与立项期契约的差异（实测订正）</b>：契约原列 {@code industry} / {@code level}，而这两个字段 在本仓<b>不存在</b>（{@code
   * Customer} 实体只有 {@code name/company/contactPerson/phone/email/address/
   * remark/status/createdBy/ownerId/campaignId}；全仓 {@code industry} 零命中；也没有名为行业/级别的
   * 自定义字段种子）。本项不因此新增字段（{@code spec.md} 关键实体节：零新增实体、零新增表）。 ⇒
   * 清单里<b>不列取不到的字段</b>：列一个纸面存在的字段，等于把"白名单很好看但代码送不出去"这种 假象再造成一遍。
   *
   * <p>{@code status} 与 {@code contactPerson} 是 <b>102 FLS 已登记</b>的客户内建字段 （{@code
   * BuiltinFieldRegistry}）⇒ 装配时必须过 {@code FieldMaskPlanner}；{@code name} / {@code company}
   * <b>未登记</b>，故无 FLS 可言（既无权限可言，就不存在"无权读"）。
   */
  public static final Set<String> P1_CUSTOMER_FIELDS =
      Set.of("name", "company", "status", "contactPerson");

  /** P1 送入模型的联系人字段：<b>只有姓名</b>（问候语必需）。电话/邮箱一律不送（{@code contracts/} §5.1 第 4 条）。 */
  public static final Set<String> P1_CONTACT_FIELDS = Set.of("name");

  /**
   * P1 送入模型的商机字段。⚠️ {@code stage} / {@code expectedCloseDate} 住在 {@code SalesOpportunity} 上，不在
   * {@code Opportunity} 上。
   */
  public static final Set<String> P1_OPPORTUNITY_FIELDS =
      Set.of("name", "amount", "stage", "expectedCloseDate");

  /** P1 送入模型的跟进字段：只有时间与<b>节选</b>，不送全文。 */
  public static final Set<String> P1_FOLLOWUP_FIELDS = Set.of("createdAt", "contentExcerpt");

  /** P1 取最近几条跟进（{@code contracts/} §5.2 里"须在 research.md 定稿"的那个 N——已定稿为 5）。 */
  public static final int P1_FOLLOWUP_LIMIT = 5;

  /** P1 每条跟进的节选长度上限（字符）。 */
  public static final int P1_FOLLOWUP_EXCERPT_CHARS = 200;

  /** P1 取几条联系人（只要姓名，取最近的一条即可；留 2 条是为了在最近一条无姓名时有备选）。 */
  public static final int P1_CONTACT_LIMIT = 2;

  /** P1 取几条商机明细行（取最近一行取 stage/amount/expectedCloseDate）。 */
  public static final int P1_SALES_OPPORTUNITY_LIMIT = 1;

  /** 输入长度上限（{@code contracts/} §3）。 */
  public static final int INSTRUCTION_MAX_CHARS = 4000;

  /** 允许的语气取值（{@code contracts/} §2.2 的枚举）。非法值由服务层拒（400），不走 Bean Validation。 */
  public static final Set<String> TONES = Set.of("FORMAL", "FRIENDLY", "CONCISE");

  /** 语气缺省值。 */
  public static final String DEFAULT_TONE = "FORMAL";

  /**
   * 语气 → 中文说明（进提示词）。
   *
   * <p>⚠️ {@code tone} 为 null <b>必须</b>走缺省分支：本类其余每一个字段都对 null 宽容（{@code line} 判空、 {@code followUps}
   * 判空、{@code instruction} 判空，见 {@link #renderEmailDraftUserPrompt}），语气是其中
   * <b>唯一</b>能因缺值抛异常的那一个。{@code switch} 对 null 抛 NPE ⇒ 会以 500 的形式出现在一个 AI 端点上，
   * 而"调用方应当先规范化"是一条<b>没有任何东西看着</b>的推断（本仓先例：022 的"范围过滤在别处"）。 调用方 {@code
   * AiEmailDraftService.validate} 现在确实会规范化，但渲染器不该是"缺值变成异常"的那一处。
   */
  private static String toneLabel(String tone) {
    return switch (tone == null ? DEFAULT_TONE : tone) {
      case "FRIENDLY" -> "亲切、口语化，但不失礼貌";
      case "CONCISE" -> "简洁，直入主题，尽量少客套";
      default -> "正式、专业、书面";
    };
  }

  /**
   * P1 的系统提示词：<b>稳定前缀</b>，不含任何客户数据（FR-009）。
   *
   * <p>它同时是提示注入的边界：客户数据一律进 user 消息，故"资料里的一段话被当成指令"这类风险落在 user 侧，而 system
   * 侧只讲规则。本项<b>不宣称</b>这段前缀会被提示缓存命中（前缀可能短于可缓存下限， 以 {@code usage().cacheReadInputTokens()} 实测为准，0
   * 就记为未命中）。
   */
  public static final String P1_SYSTEM_PROMPT =
      """
      你是一名企业 CRM 系统的销售助理，负责起草发给客户的商务邮件正文。

      规则：
      1. 只输出邮件正文本身。不要主题行，不要收件人/发件人，不要 Markdown 标记，不要代码块，不要解释你的写法。
      2. 用简体中文；结构为「称呼 + 正文 + 结尾敬语」，段落之间空一行。
      3. 只能使用「业务资料」里给出的信息。资料里没有的事实——价格、交付时间、产品型号、案例、承诺、人名——
         一律不得编造、不得推测，也不要用占位符假装已知。
      4. 资料不足以写出一封有内容的邮件时，直接说明还缺什么，不要硬写。
      5. 不要替客户做承诺；不要提及折扣、返点、账期等商务条款，除非资料里已经明确写了。
      6. 全文控制在 300 字以内。
      """;

  /** P1 装配好的上下文——<b>它的字段就是白名单本身</b>（可为 null 的字段表示"资料里没有"）。 */
  public record Context(
      String customerName,
      String company,
      String customerStatus,
      String contactPerson,
      String opportunityName,
      String amount,
      String stage,
      String expectedCloseDate,
      List<FollowUpExcerpt> followUps,
      String tone,
      String instruction) {}

  /** 一条跟进的节选。{@code truncated} 为真表示原文被截（提示词里会明说，不静默丢）。 */
  public record FollowUpExcerpt(String date, String text, boolean truncated) {}

  /**
   * 把上下文渲染成 P1 的 user 消息（<b>含客户数据</b>，FR-009）。
   *
   * <p>只写"有值"的字段：缺的字段<b>整行不出现</b>，而不是写成"未知"——"未知"会被模型读成一种事实 （"这个客户的行业是未知"），而"这一行不存在"才是"资料里没有"。
   */
  public static String renderEmailDraftUserPrompt(Context ctx) {
    StringBuilder sb = new StringBuilder();
    sb.append("业务资料：\n");
    line(sb, "客户名称", ctx.customerName());
    line(sb, "公司", ctx.company());
    line(sb, "客户状态", ctx.customerStatus());
    line(sb, "联系人", ctx.contactPerson());
    line(sb, "商机名称", ctx.opportunityName());
    line(sb, "商机金额", ctx.amount());
    line(sb, "商机阶段", ctx.stage());
    line(sb, "预计成交日期", ctx.expectedCloseDate());

    List<FollowUpExcerpt> followUps = ctx.followUps();
    if (followUps != null && !followUps.isEmpty()) {
      sb.append("- 最近跟进（按时间倒序，每条为节选）：\n");
      for (FollowUpExcerpt f : followUps) {
        sb.append("  - ").append(f.date() == null ? "" : f.date()).append(' ').append(f.text());
        if (f.truncated()) {
          sb.append("（本条已节选）");
        }
        sb.append('\n');
      }
    }

    sb.append("补写要求：")
        .append(ctx.instruction() == null || ctx.instruction().isBlank() ? "无" : ctx.instruction())
        .append('\n');
    sb.append("语气：").append(toneLabel(ctx.tone()));
    return sb.toString();
  }

  private static void line(StringBuilder sb, String label, String value) {
    if (value != null && !value.isBlank()) {
      sb.append("- ").append(label).append('：').append(value).append('\n');
    }
  }

  // ==================== P2 客户 360 摘要 ====================

  /** P2 的能力名（进审计 detail 与日志，<b>不进</b>提示词）。 */
  public static final String P2_CAPABILITY = "customer-summary";

  /**
   * P2 送入模型的客户字段（{@code contracts/} §5.3 的落地）。
   *
   * <p>⚠️ <b>与立项期契约的差异（实测订正，与 P1 同源）</b>：§5.3 的"送入"半列列了 <b>行业 / 级别 / 来源</b>， 而这三者在 {@code Customer}
   * 上<b>都不存在</b>——{@code industry} / {@code level} 全仓零实体命中（P1 开工时已实测过一次，见 {@link
   * #P1_CUSTOMER_FIELDS}），<b>来源</b>在 Customer 上也没有对应列（{@code source} 是 {@code Lead}
   * 的字段，不是客户的；{@code customer} 表历次迁移只加过 {@code owner_id} 与 {@code campaign_id}， 而 {@code
   * campaignId} 是<b>营销活动归因</b>、语义不等于"客户来源"）。**本项不因此新增字段** ⇒ 清单里<b>不列取不到的字段</b>。
   *
   * <p>⚠️ <b>联系人姓名不在本能力的白名单里（与 P1 相反，这是刻意的）</b>：§5.1 第 4 条明写"逐能力都要重新论证，<b>不得</b>默认继承"。
   * 摘要的对象是客户的<b>业务态势</b>（订单/回款/合同/工单/跟进），称呼语不是它的用途——P1 要联系人是<b>因为要写称呼</b>。 少送一个字段就少一处 FLS
   * 判定、少一条出网路径。
   *
   * <p>⚠️ {@code remark}（备注）也<b>不在</b>清单里：它是自由文本、可能含内部敏感信息，而 §5.3 的"不送入"半列 点名了它。同理 {@code
   * campaignId} 不进——本能力的摘要里没有"来源"这一节。
   */
  public static final Set<String> P2_CUSTOMER_FIELDS = Set.of("name", "company", "status");

  /** P2 送入模型的标签字段：<b>只要名称</b>（{@code color} 是给人看的，对摘要没有信息量）。 */
  public static final Set<String> P2_TAG_FIELDS = Set.of("name");

  /** P2 送入模型的健康度字段：<b>只要分数与等级，不要扣分明细</b>（{@code deductions} 是内部规则名，非必要）。 */
  public static final Set<String> P2_HEALTH_FIELDS = Set.of("score", "level");

  /**
   * P2 送入模型的<b>聚合量</b>字段。
   *
   * <p>⚠️ §5.3 明写"<b>不送逐笔明细</b>"——故这里全是计数/合计/状态分布，<b>没有</b> {@code orderNo} / {@code title} /
   * {@code contractNo} / {@code ticketTitle}。把明细送出去不仅超预算，还等于把"客户买了什么"整包交给模型，
   * 而摘要要表达的只是"这个客户有多少业务往来"。
   */
  public static final Set<String> P2_AGGREGATE_FIELDS =
      Set.of(
          "orderCount",
          "orderAmount",
          "paidAmount",
          "overdueAmount",
          "contractStatuses",
          "ticketStatuses");

  /** P2 送入模型的跟进字段（只有时间与<b>节选</b>，不送全文）。 */
  public static final Set<String> P2_FOLLOWUP_FIELDS = Set.of("createdAt", "contentExcerpt");

  /** P2 取最近几条跟进。⚠️ 数值与 P1 相同，但这是<b>独立常量</b>——§5.6 明写不得合并，改一处不得连带另一处。 */
  public static final int P2_FOLLOWUP_LIMIT = 5;

  /** P2 每条跟进的节选长度上限（字符）。同上：独立常量，不与 P1 共用。 */
  public static final int P2_FOLLOWUP_EXCERPT_CHARS = 200;

  /** P2 最多送入几个标签（客户可能挂了上百个标签；摘要只需要"这个客户被打了什么标"的概貌）。 */
  public static final int P2_TAG_LIMIT = 10;

  /**
   * P2 状态分布最多送入几个桶。
   *
   * <p>上界由<b>实体自身的状态枚举</b>给出（合同 {@code ContractService} 8 个常量、工单 4 个），故 8 是结构性上界而不是抽样阈值 ——
   * 这条与"跟进只取最近 5 条"不同：那个是<b>真的在丢数据</b>（故必须明说节选），这个只是给桶数一个确定的上界。
   */
  public static final int P2_STATUS_BUCKETS_LIMIT = 8;

  /**
   * P2 的系统提示词：<b>稳定前缀</b>，不含任何客户数据（FR-009）。
   *
   * <p>规则 3/4 是 US2-AS2（"空数据客户不编造"）的<b>提示词侧</b>落点；它的<b>结构侧</b>落点是 {@link
   * #renderSummaryUserPrompt}——没有数据的段落<b>整段不出现</b>，所以模型手里根本没有可以编造的素材。两处缺一不可：
   * 只做提示词，模型仍可能凭"客户摘要"这个题目自己发挥；只做结构，模型可能把"没提到工单"补写成"暂无工单"。
   */
  public static final String P2_SYSTEM_PROMPT =
      """
      你是一名企业 CRM 系统的销售助理，负责把一名客户的业务往来资料压缩成一段结构化摘要。

      规则：
      1. 只输出摘要本身。不要标题、不要 Markdown 标记、不要代码块、不要解释你的写法。
      2. 用简体中文，分段书写；每段以一个短小标题起头（如「基本情况」「交易与回款」「服务与跟进」）。
      3. 只能使用「业务资料」里给出的信息。资料里没有的事实——金额、日期、产品、承诺、评价——
         一律不得编造、不得推测，也不要用占位符假装已知。
      4. 资料里没有的段落，整个不写。不要写「暂无工单」「无回款记录」这类填空句，也不要把缺失
         当成一种情况来描述。
      5. 资料少到写不出摘要时，直接说明还缺什么，不要硬写。
      6. 全文控制在 400 字以内。
      """;

  /** P2 装配好的上下文——<b>它的字段就是白名单本身</b>（可为 null 的字段表示"资料里没有"）。 */
  public record SummaryContext(
      String customerName,
      String company,
      String customerStatus,
      List<String> tags,
      Integer healthScore,
      String healthLevel,
      Integer orderCount,
      String orderAmount,
      String paidAmount,
      String overdueAmount,
      List<StatusCount> contractStatuses,
      List<StatusCount> ticketStatuses,
      List<FollowUpExcerpt> followUps) {}

  /** 一个状态桶（{@code status} 是实体自身的枚举值，不是客户数据）。 */
  public record StatusCount(String status, int count) {}

  /**
   * 把上下文渲染成 P2 的 user 消息（<b>含客户数据</b>，FR-009）。
   *
   * <p>与 P1 同一条规矩：只写"有值"的字段，缺的<b>整行/整段不出现</b>，而不是写成"无"——"无"会被模型读成一种事实
   * （"这个客户没有工单"），而"这一段不存在"才是"资料里没有"。
   */
  public static String renderSummaryUserPrompt(SummaryContext ctx) {
    StringBuilder sb = new StringBuilder();
    sb.append("业务资料：\n");
    line(sb, "客户名称", ctx.customerName());
    line(sb, "公司", ctx.company());
    line(sb, "客户状态", ctx.customerStatus());
    if (ctx.tags() != null && !ctx.tags().isEmpty()) {
      line(sb, "标签", String.join("、", ctx.tags()));
    }
    if (ctx.healthScore() != null) {
      line(
          sb,
          "健康度",
          ctx.healthScore() + (ctx.healthLevel() == null ? "" : "（" + ctx.healthLevel() + "）"));
    }

    // 交易与回款：整段按"有没有业务"决定出不出现（US2-AS2 的结构侧判据）。
    boolean hasOrders = ctx.orderCount() != null && ctx.orderCount() > 0;
    if (hasOrders || ctx.contractStatuses() != null && !ctx.contractStatuses().isEmpty()) {
      sb.append("- 交易与回款：\n");
      if (hasOrders) {
        sb.append("  - 订单数：").append(ctx.orderCount()).append('\n');
        appendAmount(sb, "订单金额合计", ctx.orderAmount());
        appendAmount(sb, "已回款", ctx.paidAmount());
        appendAmount(sb, "其中逾期", ctx.overdueAmount());
      }
      appendStatuses(sb, "合同（按状态）", ctx.contractStatuses());
    }
    if (ctx.ticketStatuses() != null && !ctx.ticketStatuses().isEmpty()) {
      sb.append("- 服务：\n");
      appendStatuses(sb, "工单（按状态）", ctx.ticketStatuses());
    }

    List<FollowUpExcerpt> followUps = ctx.followUps();
    if (followUps != null && !followUps.isEmpty()) {
      sb.append("- 最近跟进（按时间倒序，每条为节选）：\n");
      for (FollowUpExcerpt f : followUps) {
        sb.append("  - ").append(f.date() == null ? "" : f.date()).append(' ').append(f.text());
        if (f.truncated()) {
          sb.append("（本条已节选）");
        }
        sb.append('\n');
      }
    }
    return sb.toString();
  }

  private static void appendAmount(StringBuilder sb, String label, String yuan) {
    if (yuan != null && !yuan.isBlank()) {
      sb.append("  - ").append(label).append('：').append(yuan).append(" 元\n");
    }
  }

  private static void appendStatuses(StringBuilder sb, String label, List<StatusCount> counts) {
    if (counts == null || counts.isEmpty()) {
      return;
    }
    sb.append("  - ").append(label).append('：');
    for (int i = 0; i < counts.size(); i++) {
      if (i > 0) {
        sb.append('、');
      }
      sb.append(counts.get(i).status()).append(' ').append(counts.get(i).count());
    }
    sb.append('\n');
  }

  /**
   * 分 → 元的字符串（金额在库里是<b>分</b>，直接送出去会让模型把"12345"读成 12345 元）。
   *
   * <p>{@code null} 进 {@code null} 出（"没有这个数"与"这个数是 0"是两件事，见 {@link #line}）。
   */
  public static String yuan(Long fen) {
    if (fen == null) {
      return null;
    }
    return java.math.BigDecimal.valueOf(fen).movePointLeft(2).toPlainString();
  }

  /**
   * 按上限截断自由文本并给出"是否截过"的事实。
   *
   * <p>返回事实而不是只返回字符串：调用方要拿这个布尔去决定提示词里要不要明说"已节选"—— 只返回被截后的串，就没人知道它到底截没截（于是"截断并告知"必然退化成"悄悄截断"）。
   */
  public static Truncated excerpt(String raw, int maxChars) {
    String text = raw == null ? "" : raw.strip();
    if (text.length() <= maxChars) {
      return new Truncated(text, false);
    }
    return new Truncated(text.substring(0, maxChars), true);
  }

  /** {@link #excerpt} 的结果：文本 + 是否被截。 */
  public record Truncated(String text, boolean truncated) {}
}
