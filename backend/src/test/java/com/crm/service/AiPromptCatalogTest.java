package com.crm.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 字段白名单与提示词渲染（104-ai-content-generation，U8 / FR-011）。
 *
 * <p><b>U8 断的不是"清单好看"，而是"清单与渲染函数是同一件事"</b>：{@code P1_*_FIELDS} 是给人读的常量， {@link
 * AiPromptCatalog#renderEmailDraftUserPrompt} 才是真正决定"什么会离开本系统"的那段代码。
 * 只断清单（"白名单里没有电话"）是登记表自证，盖不住渲染函数；只断渲染函数则漏掉"清单写着不许送、 有人照着清单加了一条"这种方向。故本类两件都断：
 *
 * <ol>
 *   <li><b>送出去的都按清单来</b>：每个白名单字段塞一个哨兵值，断言它<b>出现</b>在提示词里；
 *   <li><b>没送别的</b>：提示词里的标签集合 ⊆ 白名单字段数得出的集合——多渲染一行（例如 {@code line(sb, "手机号",
 *       customer.getPhone())}）会让这条红，而它对任何"只看 response 的用例" 都是不可见的（客户数据就那么流出去，响应里什么也看不出来）。
 * </ol>
 *
 * <p>第三条是结构性的：{@link AiPromptCatalog.Context} 的<b>记录分量本身就是白名单</b>，
 * 故"往上下文里加一个字段"（静默放宽）会让分量集合断言红。这是本项最重的一条安全面—— 每能力一份独立清单、<b>绝不合并</b>（合并即默认放宽）。
 */
class AiPromptCatalogTest {

  /** 敏感字段名：一旦出现在任何白名单里，本类即红（P1 不送电话/邮箱/证件/账号）。 */
  private static final Set<String> FORBIDDEN =
      Set.of(
          "phone",
          "mobile",
          "email",
          "idCard",
          "idNumber",
          "passport",
          "bankAccount",
          "password",
          "address",
          "remark");

  private static AiPromptCatalog.Context fullContext(List<AiPromptCatalog.FollowUpExcerpt> f) {
    return new AiPromptCatalog.Context(
        "哨兵客户名", "哨兵公司", "哨兵状态", "哨兵联系人", "哨兵商机名", "哨兵金额", "哨兵阶段", "哨兵预计成交日", f, "FORMAL",
        "哨兵补写要求");
  }

  // ===== 清单本身 =====

  @Test
  @DisplayName("U8-a 逐能力白名单不含电话/邮箱/证件/账号类字段")
  void noSensitiveFieldInAnyWhitelist() {
    for (Set<String> fields :
        List.of(
            AiPromptCatalog.P1_CUSTOMER_FIELDS,
            AiPromptCatalog.P1_CONTACT_FIELDS,
            AiPromptCatalog.P1_OPPORTUNITY_FIELDS,
            AiPromptCatalog.P1_FOLLOWUP_FIELDS,
            // P2 的四张清单同在此列：这是"逐能力"这条口径的落点，漏掉 P2 等于新能力不受此不变式约束。
            AiPromptCatalog.P2_CUSTOMER_FIELDS,
            AiPromptCatalog.P2_TAG_FIELDS,
            AiPromptCatalog.P2_HEALTH_FIELDS,
            AiPromptCatalog.P2_AGGREGATE_FIELDS,
            AiPromptCatalog.P2_FOLLOWUP_FIELDS)) {
      assertThat(fields).doesNotContainAnyElementsOf(FORBIDDEN);
    }
  }

  @Test
  @DisplayName("U8-b 白名单含该能力必需的字段（P1 要客户名与公司：没有它们写不出有内容的邮件）")
  void whitelistContainsRequiredFields() {
    assertThat(AiPromptCatalog.P1_CUSTOMER_FIELDS).contains("name", "company");
    assertThat(AiPromptCatalog.P1_CONTACT_FIELDS).contains("name");
    assertThat(AiPromptCatalog.P1_OPPORTUNITY_FIELDS)
        .as("商机四项缺一不可：金额与阶段是这封邮件的实质内容")
        .contains("name", "amount", "stage", "expectedCloseDate");
    assertThat(AiPromptCatalog.P1_FOLLOWUP_FIELDS).contains("createdAt", "contentExcerpt");
  }

  @Test
  @DisplayName("U8-c 上下文的分量集合就是白名单本身：多一个字段即红（静默放宽的判据）")
  void contextComponentsAreExactlyTheWhitelist() {
    Set<String> actual =
        Arrays.stream(AiPromptCatalog.Context.class.getRecordComponents())
            .map(c -> c.getName())
            .collect(Collectors.toCollection(LinkedHashSet::new));

    assertThat(actual)
        .as("Context 是「会离开本系统的东西」的完整清单；加一个分量就是放宽白名单，而那种放宽是静默的")
        .containsExactlyInAnyOrder(
            "customerName",
            "company",
            "customerStatus",
            "contactPerson",
            "opportunityName",
            "amount",
            "stage",
            "expectedCloseDate",
            "followUps",
            "tone",
            "instruction");
  }

  // ===== 渲染函数 =====

  @Test
  @DisplayName("U8-d 哨兵往返：每个白名单字段的值都出现在提示词里（缺字段 ⇔ 清单与渲染不一致）")
  void everyWhitelistedFieldReachesThePrompt() {
    String prompt =
        AiPromptCatalog.renderEmailDraftUserPrompt(
            fullContext(
                List.of(new AiPromptCatalog.FollowUpExcerpt("2026-09-01", "哨兵跟进内容", false))));

    assertThat(prompt).contains("哨兵客户名", "哨兵公司", "哨兵状态", "哨兵联系人");
    assertThat(prompt).contains("哨兵商机名", "哨兵金额", "哨兵阶段", "哨兵预计成交日");
    assertThat(prompt).contains("哨兵跟进内容").contains("哨兵补写要求");
  }

  @Test
  @DisplayName("U8-e 只渲染白名单字段：标签集合 ⊆ 已知集合（多渲染一行即红）")
  void noLabelOutsideTheWhitelistIsRendered() {
    Set<String> allowed =
        Set.of("客户名称", "公司", "客户状态", "联系人", "商机名称", "商机金额", "商机阶段", "预计成交日期", "最近跟进（按时间倒序，每条为节选）");

    String prompt =
        AiPromptCatalog.renderEmailDraftUserPrompt(
            fullContext(
                List.of(new AiPromptCatalog.FollowUpExcerpt("2026-09-01", "哨兵跟进内容", false))));

    List<String> labels =
        Arrays.stream(prompt.split("\n"))
            .filter(line -> line.startsWith("- "))
            .map(line -> line.substring(2))
            .map(
                line -> {
                  int colon = line.indexOf('：');
                  return colon < 0 ? line : line.substring(0, colon);
                })
            .toList();

    assertThat(labels)
        .as("提示词里出现了白名单之外的标签：客户数据就是这样流出去的，而响应里什么都看不出来")
        .isNotEmpty()
        .allSatisfy(label -> assertThat(allowed).contains(label));
  }

  @Test
  @DisplayName("U8-f 缺失字段整行不出现（不写「未知」——「未知」会被模型读成一种事实）")
  void absentFieldsProduceNoLine() {
    AiPromptCatalog.Context ctx =
        new AiPromptCatalog.Context(
            "只有名字", null, null, null, null, null, null, null, List.of(), "FORMAL", null);

    String prompt = AiPromptCatalog.renderEmailDraftUserPrompt(ctx);

    assertThat(prompt).contains("客户名称").doesNotContain("公司").doesNotContain("未知");
    assertThat(prompt).contains("补写要求：无");
  }

  @Test
  @DisplayName("U8-g 截断：明说「本条已节选」，不静默截断（excerpt 返回事实而不只是文本）")
  void truncationIsAnnounced() {
    AiPromptCatalog.Truncated truncated = AiPromptCatalog.excerpt("x".repeat(300), 200);
    assertThat(truncated.text()).hasSize(200);
    assertThat(truncated.truncated()).isTrue();

    String prompt =
        AiPromptCatalog.renderEmailDraftUserPrompt(
            fullContext(
                List.of(
                    new AiPromptCatalog.FollowUpExcerpt("2026-09-01", truncated.text(), true))));
    assertThat(prompt).as("截断必须被明说：只返回被截后的串的话，没人知道它到底截没截").contains("（本条已节选）");

    AiPromptCatalog.Truncated whole = AiPromptCatalog.excerpt("短", 200);
    assertThat(whole.truncated()).isFalse();
    assertThat(
            AiPromptCatalog.renderEmailDraftUserPrompt(
                fullContext(
                    List.of(
                        new AiPromptCatalog.FollowUpExcerpt("2026-09-01", whole.text(), false)))))
        .doesNotContain("（本条已节选）");
  }

  @Test
  @DisplayName("U8-h 语气映射：三种取值给出三种说明，未知/空退回默认（不是抛错）")
  void toneMapping() {
    assertThat(promptWithTone("FRIENDLY"))
        .isNotEqualTo(promptWithTone("CONCISE"))
        .isNotEqualTo(promptWithTone(null));
    assertThat(promptWithTone(null)).isEqualTo(promptWithTone("FORMAL"));
    assertThat(promptWithTone("没见过的值")).isEqualTo(promptWithTone("FORMAL"));
    assertThat(AiPromptCatalog.TONES).containsExactlyInAnyOrder("FORMAL", "FRIENDLY", "CONCISE");
  }

  private static String promptWithTone(String tone) {
    return AiPromptCatalog.renderEmailDraftUserPrompt(
        new AiPromptCatalog.Context(
            "名", null, null, null, null, null, null, null, List.of(), tone, null));
  }

  @Test
  @DisplayName("U8-i 系统提示词是稳定前缀：它是不可插值的常量，客户数据只能从 user 侧进（FR-009）")
  void systemPromptIsAConstantWithNoPlaceholders() {
    // ⚠️ 本条的判据是「不可插值」，不是「不含某个词」：初版断言的是 doesNotContain("业务资料")，
    // 而那句话<b>就在</b>提示词里（规则 3 写着"只能使用「业务资料」里给出的信息"——它指的是 user 消息那一段）。
    // 按词表断言的写法，只要有人换个措辞就会红，而真正的风险（把客户数据拼进 system 侧）它一点也挡不住。
    assertThat(AiPromptCatalog.P1_SYSTEM_PROMPT).isNotBlank();
    assertThat(AiPromptCatalog.P1_SYSTEM_PROMPT)
        .as("常量一旦变成模板（%s / {} / ${），「客户数据只进 user 消息」就不再是结构性的了")
        .doesNotContain("%s", "{}", "${")
        .doesNotContain("哨兵");
  }

  // ==================== P2 客户 360 摘要（U9） ====================

  /**
   * P2 的白名单与渲染（U9-a…U9-g）。<b>与 U8 同一套三层判据</b>（清单 / 分量 / 渲染），只是换了一份清单 —— 这正是"逐能力重新论证、不得默认继承"（§5.1 第
   * 4 条）在测试层的形态：P2 的用例<b>不</b>复用 P1 的上下文，也<b>不</b>假定两份清单相同。
   */
  @Test
  @DisplayName("U9-a 白名单含必需字段，且不含取不到的字段（行业/级别/来源在 Customer 上不存在）")
  void p2WhitelistIsBuildableAndNonEmpty() {
    assertThat(AiPromptCatalog.P2_CUSTOMER_FIELDS)
        .as("客户名与公司是摘要的骨架；状态是唯一一个既在清单里、又是 102 已登记 FLS 字段的项")
        .contains("name", "company", "status");
    assertThat(AiPromptCatalog.P2_TAG_FIELDS).containsExactly("name");
    assertThat(AiPromptCatalog.P2_HEALTH_FIELDS).containsExactlyInAnyOrder("score", "level");
    assertThat(AiPromptCatalog.P2_FOLLOWUP_FIELDS).contains("createdAt", "contentExcerpt");

    // 反方向：契约 §5.3 的"送入"半列点名了这三项，而它们在 Customer 上取不到（P1 开工时已实测）。
    // 断"不列取不到的字段"而不是"记得把这几个补上"——否则有人照着契约把它们加进清单，会得到一段永远为 null 的
    // 提示词行，而用例对"null 不渲染"是绿的（静默的、看不见的放宽）。
    assertThat(AiPromptCatalog.P2_CUSTOMER_FIELDS)
        .as("industry / level / source 在 Customer 上都没有对应列（source 是 Lead 的字段）")
        .doesNotContain("industry", "level", "source");
    // 联系人姓名刻意不在 P2 清单里（§5.1 第 4 条要求逐能力重新论证，而摘要不需要称呼语）。
    assertThat(AiPromptCatalog.P2_CUSTOMER_FIELDS).doesNotContain("contactPerson");
  }

  @Test
  @DisplayName("U9-b SummaryContext 的分量集合就是 P2 的白名单：多一个字段即红")
  void p2ContextComponentsAreExactlyTheWhitelist() {
    Set<String> actual =
        Arrays.stream(AiPromptCatalog.SummaryContext.class.getRecordComponents())
            .map(c -> c.getName())
            .collect(Collectors.toCollection(LinkedHashSet::new));

    assertThat(actual)
        .as("加了分量就等于静默放宽白名单；P2 的分量与 P1 的分量是两份独立的清单")
        .containsExactlyInAnyOrder(
            "customerName",
            "company",
            "customerStatus",
            "tags",
            "healthScore",
            "healthLevel",
            "orderCount",
            "orderAmount",
            "paidAmount",
            "overdueAmount",
            "contractStatuses",
            "ticketStatuses",
            "followUps");
  }

  /** 一份"什么都有"的 P2 上下文：哨兵值填满，用来做往返回合。 */
  private static AiPromptCatalog.SummaryContext fullSummaryContext() {
    return new AiPromptCatalog.SummaryContext(
        "哨兵客户名",
        "哨兵公司",
        "哨兵状态",
        List.of("哨兵标签甲", "哨兵标签乙"),
        88,
        "哨兵等级",
        7,
        "哨兵订单金额",
        "哨兵已回款",
        "哨兵逾期",
        List.of(new AiPromptCatalog.StatusCount("哨兵合同状态", 3)),
        List.of(new AiPromptCatalog.StatusCount("哨兵工单状态", 2)),
        List.of(new AiPromptCatalog.FollowUpExcerpt("2026-09-01", "哨兵跟进内容", false)));
  }

  @Test
  @DisplayName("U9-c 哨兵往返：每个 P2 白名单字段的值都出现在提示词里（缺字段 ⇔ 清单与渲染不一致）")
  void everyP2WhitelistedFieldReachesThePrompt() {
    String prompt = AiPromptCatalog.renderSummaryUserPrompt(fullSummaryContext());

    assertThat(prompt).contains("哨兵客户名", "哨兵公司", "哨兵状态");
    assertThat(prompt).contains("哨兵标签甲", "哨兵标签乙");
    assertThat(prompt).contains("88").contains("哨兵等级");
    assertThat(prompt).contains("7");
    assertThat(prompt).contains("哨兵订单金额", "哨兵已回款", "哨兵逾期");
    assertThat(prompt).contains("哨兵合同状态", "哨兵工单状态");
    assertThat(prompt).contains("哨兵跟进内容");
    // 两个"整段出现"的见证：只断字段值的话，一个"把两段都塞进一行"的实现照样绿。
    assertThat(prompt).contains("交易与回款", "服务");
  }

  @Test
  @DisplayName("U9-d 只渲染 P2 白名单字段：标签集合 ⊆ 已知集合（多渲染一行即红）")
  void noLabelOutsideTheP2WhitelistIsRendered() {
    Set<String> allowed =
        Set.of(
            "客户名称",
            "公司",
            "客户状态",
            "标签",
            "健康度",
            "交易与回款",
            "订单数",
            "订单金额合计",
            "已回款",
            "其中逾期",
            "合同（按状态）",
            "服务",
            "工单（按状态）",
            "最近跟进（按时间倒序，每条为节选）");

    String prompt = AiPromptCatalog.renderSummaryUserPrompt(fullSummaryContext());

    // ⚠️ 与 U8-e 的取法不同：P2 有<b>缩进一层</b>的子弹（段内条目以 "  - " 起头），故先去缩进再判前缀。
    // 照 U8-e 直接 startsWith("- ") 会<b>整段漏掉</b>嵌套条目——那些恰恰是最可能夹带明细（orderNo / title）的地方。
    List<String> bullets =
        Arrays.stream(prompt.split("\n"))
            .map(line -> line.stripLeading())
            .filter(line -> line.startsWith("- "))
            .map(line -> line.substring(2))
            .toList();

    // 「标签：值」这一形态才是"多送了一个字段"的载体（`line(sb, "手机号", customer.getPhone())` 长这样），
    // 故按冒号切开只取标签那一半。⚠️ 第一次写这条时把**没有冒号的整行**也当成了标签，于是"最近跟进的条目"
    // （形如 `2026-09-01 内容节选`）被判成白名单外的标签——红得没错、但红在了取值方式上，不是判据上。
    List<String> labels =
        bullets.stream()
            .filter(line -> line.indexOf('：') >= 0)
            .map(line -> line.substring(0, line.indexOf('：')))
            .toList();

    assertThat(labels)
        .as("提示词里出现了白名单之外的标签：客户数据就是这样流出去的，而响应里什么都看不出来")
        .isNotEmpty()
        .allSatisfy(label -> assertThat(allowed).contains(label));

    // 剩下那些没有冒号的子弹只应该是"最近跟进"的条目（`日期 节选`）。这条堵的是"有人新加了一个段却没用
    // `标签：` 的写法"——那种段会整行从上面那条断言里漏过去（没有冒号就没有标签可取）。
    assertThat(bullets.stream().filter(line -> line.indexOf('：') < 0).toList())
        .as("无冒号的子弹只能是「最近跟进」的条目；出现别的形态说明有人绕开了 `标签：值` 的写法，上面那条就盖不住它了")
        .allSatisfy(line -> assertThat(line).matches("\\d{4}-\\d{2}-\\d{2} .+"));
  }

  @Test
  @DisplayName("U9-e US2-AS2 结构侧：空数据客户没有任何可编造的段落（不是写「暂无」）")
  void emptyContextHasNoSectionToFabricate() {
    AiPromptCatalog.SummaryContext empty =
        new AiPromptCatalog.SummaryContext(
            "只有名字", null, null, List.of(), null, null, null, null, null, null, List.of(), List.of(),
            List.of());

    String prompt = AiPromptCatalog.renderSummaryUserPrompt(empty);

    assertThat(prompt).as("正对照：上下文真的到了提示词里").contains("客户名称").contains("只有名字");
    assertThat(prompt)
        .as("没有数据的段整段不出现——「暂无工单」会被模型读成一种事实，而「这一段不存在」才是「资料里没有」")
        .doesNotContain("交易与回款")
        .doesNotContain("服务")
        .doesNotContain("健康度")
        .doesNotContain("合同")
        .doesNotContain("工单")
        .doesNotContain("最近跟进")
        .doesNotContain("暂无");
  }

  @Test
  @DisplayName("U9-f 健康度只在有值时才成行（null ≠ 0 分：健康的客户可能真是 0 分，那是要送出去的事实）")
  void healthLineOnlyAppearsWhenPresent() {
    AiPromptCatalog.SummaryContext none =
        new AiPromptCatalog.SummaryContext(
            "名", null, null, List.of(), null, null, null, null, null, null, List.of(), List.of(),
            List.of());
    assertThat(AiPromptCatalog.renderSummaryUserPrompt(none)).doesNotContain("健康度");

    // 0 分必须成行——它与 null 是两件事（null = 没有这个数，0 = 这个数是 0）。这是 `line()` 那条规矩在**数值**上的形态，
    // 而 `line()` 只挡 null / 空白串，故 0 分的渲染要单独钉一下。
    AiPromptCatalog.SummaryContext zero =
        new AiPromptCatalog.SummaryContext(
            "名", null, null, List.of(), 0, "RED", null, null, null, null, List.of(), List.of(),
            List.of());
    assertThat(AiPromptCatalog.renderSummaryUserPrompt(zero))
        .contains("健康度")
        .contains("0")
        .contains("RED");
  }

  @Test
  @DisplayName("U9-g P2 系统提示词是稳定前缀：不可插值的常量（FR-009）")
  void p2SystemPromptIsAConstantWithNoPlaceholders() {
    assertThat(AiPromptCatalog.P2_SYSTEM_PROMPT).isNotBlank();
    assertThat(AiPromptCatalog.P2_SYSTEM_PROMPT)
        .as("常量一旦变成模板（%s / {} / ${），「客户数据只进 user 消息」就不再是结构性的了")
        .doesNotContain("%s", "{}", "${")
        .doesNotContain("哨兵");
  }

  @Test
  @DisplayName("U9-h 金额：分 → 元（库里是分，直送会让模型把 12345 读成 12345 元）")
  void amountsAreConvertedFromCentsToYuan() {
    assertThat(AiPromptCatalog.yuan(12345L)).isEqualTo("123.45");
    assertThat(AiPromptCatalog.yuan(100L)).isEqualTo("1.00");
    assertThat(AiPromptCatalog.yuan(0L)).as("0 分是「这个数是 0」，不是「没有这个数」").isEqualTo("0.00");
    assertThat(AiPromptCatalog.yuan(null)).as("null 进 null 出，缺的整行不出现").isNull();
  }
}
