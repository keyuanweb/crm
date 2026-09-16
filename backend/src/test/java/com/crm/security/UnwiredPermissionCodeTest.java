package com.crm.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.crm.support.PermissionDictionaryTestSupport;
import com.crm.support.RequirePermissionScanTestSupport;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * 未接线权限码的冻结台账（097）。
 *
 * <p><b>防的是什么</b>：{@link com.crm.common.RoleConstants#PERMISSION_DEFS} 里的码**被谁校验**这件事，今天没有一个权威答案——
 * {@code follow_up:delete} 的理由在 {@code RoleConstants} 的组注释、{@code quote:delete} 的在 {@code
 * QuoteController} 的类 javadoc、{@code ticket:approve} 的在 {@code TicketController} 的类 javadoc，**其余 19
 * 个 码没有任何地方说过话**。管理员在角色页上勾得到全部 22 个，勾了却不生效。本测试把这 22 条**逐条**记下来（码 + 性质 + 理由），
 * 让「哪些码今天不生效、各是什么性质、为什么留着」有一个可核的答案。
 *
 * <p><b>为什么是「冻结台账」而不是「必须为空」</b>：{@link RequirePermissionCatalogTest} 的类注释写着「反向（字典里有、注解没用）
 * **刻意不断言**：字典同时承载菜单权限点与『将来要用』的码，未使用是常态」。本测试**不推翻**那条裁决——它断言的是**这 22 条**， 不是「一条都不许有」。两者的射程也不同：096
 * 的非目标 4「只建被端点真引用的码」管的是**新增**（不要凭空造码），本条管的是**存量** （不要因未用而删）。
 *
 * <p><b>为什么不删这 22 个码</b>：其中 20 个已被 {@code V46}/{@code V75} 授给真实角色，删字典会直接撞 {@code
 * PermissionMatrixIT} 的「已授予码 ⊆ 字典」，除非再写一条**删除授权**的迁移去动已发布的权限语义。
 *
 * <p><b>断言是双向的</b>：多一个 = 又造了一个没人用的码；**少一个也要红** = 某个码已经接线（或已从字典删除）却没更新台账—— 一个会骗人的台账比没有台账更坏。
 *
 * <p>扫描与展平都复用既有 support（{@link RequirePermissionScanTestSupport} 问「谁真的被校验」、{@link
 * PermissionDictionaryTestSupport} 展平字典），不新写一份——两份各写一遍会一起写错、一起假绿。
 */
class UnwiredPermissionCodeTest {

  /** 未接线码的性质。两类**必须分开**：A 类写成「暂无对应操作」是**假话**（操作是有的，只是由别的码放行）。 */
  enum Nature {
    /** A 类：**有**对应操作，但服务端由相邻的码放行（或故意不挂码）。 */
    ADJACENT_CODE_GRANTS,
    /** B 类：**全仓没有**对应操作。 */
    NO_SUCH_OPERATION
  }

  /**
   * 台账的一条：码 + 性质 + 理由。
   *
   * <p>理由**逐条必填**（{@link #everyLedgerEntryCarriesItsNatureAndReason()} 会把空理由按住）：台账若退化成 22 个裸码，
   * 它与「没有台账」就没有区别了。
   */
  private record Unwired(String code, Nature nature, String reason) {}

  /**
   * 冻结的 22 条（2026-09-16 实测；A 类 11 / B 类 11）。
   *
   * <p>A 类的「由谁放行」与 B 类的「最接近的那个端点为何不算对应」逐条写过，取证与命令见 {@code
   * specs/097-debt-ledger-closeout/research.md} §1.4。
   */
  @SuppressWarnings("checkstyle:LineLength")
  private static final List<Unwired> LEDGER =
      List.of(
          // ---- A 类 11：有对应操作，由相邻的码放行 ----
          new Unwired(
              "email:create",
              Nature.ADJACENT_CODE_GRANTS,
              "POST /email-templates、POST /email-campaigns 存在；EmailController 类级挂 email:manage"),
          new Unwired(
              "email:update",
              Nature.ADJACENT_CODE_GRANTS,
              "PUT /email-templates/{id} 存在；同由 email:manage 放行"),
          new Unwired(
              "email:delete",
              Nature.ADJACENT_CODE_GRANTS,
              "DELETE /email-templates/{id}、DELETE /email/unsubscribes/{id} 存在；同由 email:manage 放行"),
          new Unwired(
              "email:send",
              Nature.ADJACENT_CODE_GRANTS,
              "POST /email-campaigns 的 summary 就是「创建并发送邮件群发」；同由 email:manage 放行"),
          new Unwired(
              "invoice:create",
              Nature.ADJACENT_CODE_GRANTS,
              "POST /invoices 存在；InvoiceController 挂 invoice:manage"),
          new Unwired(
              "invoice:update",
              Nature.ADJACENT_CODE_GRANTS,
              "POST /invoices/{id}/void（状态变更 = 写）存在；同由 invoice:manage 放行"),
          new Unwired(
              "segment:manage",
              Nature.ADJACENT_CODE_GRANTS,
              "SegmentController 的三个写端点存在；由 tag:manage 放行（该模块属 031-customer-tags）"),
          new Unwired(
              "approval:create",
              Nature.ADJACENT_CODE_GRANTS,
              "POST /approval-flows 存在；由 workflow:manage 放行"),
          new Unwired(
              "approval:update",
              Nature.ADJACENT_CODE_GRANTS,
              "PUT /approval-flows/{id} 存在；同由 workflow:manage 放行"),
          new Unwired(
              "approval:delete",
              Nature.ADJACENT_CODE_GRANTS,
              "DELETE /approval-flows/{id} 存在；同由 workflow:manage 放行"),
          new Unwired(
              "approval:approve",
              Nature.ADJACENT_CODE_GRANTS,
              "审批端点存在但**故意不挂码**：ApprovalEngineService.checkApprover 逐任务判归属（对 ADMIN 也不通融），"
                  + "挂码会从「分给你才能审」退到「有码就能审任何任务」——是削弱授权（096 §2 US3 已书面论证）"),
          // ---- B 类 11：全仓没有对应操作 ----
          new Unwired(
              "invoice:delete",
              Nature.NO_SUCH_OPERATION,
              "InvoiceController 无 DELETE；void 是作废（状态变更），不是删除"),
          new Unwired(
              "follow_up:delete",
              Nature.NO_SUCH_OPERATION,
              "FollowUpController 只有 GET / POST / PUT {id}，跟进记录没有删除端点"),
          new Unwired("quote:delete", Nature.NO_SUCH_OPERATION, "QuoteController 无 DELETE"),
          new Unwired(
              "ticket:approve",
              Nature.NO_SUCH_OPERATION,
              "工单没有「审批」动作；最接近的 POST /tickets/{id}/transition 是状态流转、挂 ticket:update"),
          new Unwired(
              "quota:delete",
              Nature.NO_SUCH_OPERATION,
              "SalesQuotaController 无 DELETE（V87 头注释已写明「本类没有删除动作」）"),
          new Unwired(
              "contract:delete",
              Nature.NO_SUCH_OPERATION,
              "最接近的 POST /contracts/{id}/terminate 是终止、不是删除，且挂 contract:update——近似不算对应"),
          new Unwired(
              "customer:transfer",
              Nature.NO_SUCH_OPERATION,
              "最接近的 POST /customers/merge 是合并、不是转移，且挂 customer:merge——近似不算对应"),
          new Unwired(
              "export:delete",
              Nature.NO_SUCH_OPERATION,
              "最接近的 ScheduledExportController 的 DELETE /{id} 是**订阅**删除、挂 export:scheduled；"
                  + "一次性导出没有删除端点"),
          new Unwired(
              "opportunity:export",
              Nature.NO_SUCH_OPERATION,
              "只有通用 POST /exports（挂 export:create），没有按业务对象分的导出端点"),
          new Unwired(
              "campaign:export", Nature.NO_SUCH_OPERATION, "同上：只有通用 POST /exports，没有按业务对象分的导出端点"),
          new Unwired(
              "system:manage", Nature.NO_SUCH_OPERATION, "全仓没有 SystemController；V46 曾把它授给 ADMIN"));

  @Test
  void everyUnwiredCodeIsInTheFrozenLedger() {
    Map<String, Set<String>> usages = RequirePermissionScanTestSupport.usages();

    // 先证明扫描确实扫到了东西：扫描坏掉时下面两个方向都会「一个差异都没有」——假绿
    assertThat(usages).containsKey("customer:merge");

    Set<String> unwired = new TreeSet<>(PermissionDictionaryTestSupport.codes());
    unwired.removeAll(usages.keySet());

    Set<String> ledger = ledgerCodes();

    Set<String> undocumented = new TreeSet<>(unwired);
    undocumented.removeAll(ledger);
    Set<String> stale = new TreeSet<>(ledger);
    stale.removeAll(unwired);

    assertThat(undocumented)
        .as(
            "字典里没有被任何 @RequirePermission 引用的码，却不在台账里：这是新造了一个「建了但没人用」的码——"
                + "要么把它接到端点上，要么在 LEDGER 里写明为什么留着")
        .isEmpty();
    assertThat(stale)
        .as(
            "台账里有、但已经不在「未接线」集合里的码：它已经接线（或已从字典删除）却没更新台账——"
                + "一个会骗人的台账比没有台账更坏。接线后请从 LEDGER 删掉该条，并同步 spec/README 里的措辞")
        .isEmpty();
  }

  @Test
  void everyLedgerEntryCarriesItsNatureAndReason() {
    assertThat(LEDGER).extracting(Unwired::code).doesNotHaveDuplicates();

    assertThat(LEDGER)
        .allSatisfy(
            entry -> {
              assertThat(entry.code()).isNotBlank();
              assertThat(entry.nature()).isNotNull();
              assertThat(entry.reason()).as("台账条目 %s 的理由", entry.code()).isNotBlank();
            });

    // A/B 的比例是记录过的判断（不是随手划的），变了就要改这里并同步 spec/README 的措辞
    assertThat(LEDGER)
        .filteredOn(entry -> entry.nature() == Nature.ADJACENT_CODE_GRANTS)
        .hasSize(11);
    assertThat(LEDGER).filteredOn(entry -> entry.nature() == Nature.NO_SUCH_OPERATION).hasSize(11);
  }

  private static Set<String> ledgerCodes() {
    Set<String> codes = new TreeSet<>();
    LEDGER.forEach(entry -> codes.add(entry.code()));
    return codes;
  }
}
