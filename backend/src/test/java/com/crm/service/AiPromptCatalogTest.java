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
            AiPromptCatalog.P1_FOLLOWUP_FIELDS)) {
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
}
