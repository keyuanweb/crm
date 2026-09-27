package com.crm.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.crm.AbstractIntegrationTest;
import com.crm.support.PermissionDictionaryTestSupport;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * {@code ai:generate} 的<b>零授予</b>是一条<b>记录在案的决定</b>，不是遗漏（104-ai-content-generation，FR-020）。
 *
 * <p><b>为什么要把一个"没有"写成用例</b>：本码按判据③ 裁为"一个角色都不授予"——{@code ai:generate} 覆盖的是
 * 一个此前不存在的能力，它挂在<b>既有页面</b>上（不是新菜单），因此既没有"改造前那道粗粒度门"可供继承（判据①）， 也没有菜单承诺可依据（判据②）。判据③
 * 的结论是"只接码、不补授"。同判例：{@code mail_account:manage} · {@code workflow:read} · {@code
 * integration:manage} · {@code open_platform:manage}。理由的逐条论证在 {@code RoleConstants} 里本码上方的注释。
 *
 * <p><b>但"零授予"与"没人知道"只差一件事：有没有一条可执行的痕迹。</b>没有它，本码在字典里（管理员能在角色页 勾选）、权限注解也在（{@code
 * RequirePermissionCatalogTest} 绿、{@code UnwiredPermissionCodeTest} 也绿 ——它已经被本端点引用了），于是"除 ADMIN
 * 外全部角色 403"这件事只会以一个现状的形式存在。谁哪天决定把 {@code ai:generate} 授给 SALES_MANAGER，本用例即转红，逼他把决定与记录一起改掉。
 *
 * <p><b>为什么另起一个类，而不是把码加进 {@code PermissionMatrixIT.ADMIN_ONLY_BY_DEFAULT}</b>：那个集合是 <b>FR-G14
 * 那一批</b>的决定（六个码，各有各的"改造前有门/有菜单"的论据），把它撑成七个，就等于把 104 的判据 挂在一条不属于它的 FR 名下——日后读那段 javadoc 的人会以为本码也有
 * FR-G14 的那套理由。判例照抄， <b>FR 归属不合并</b>。
 *
 * <p><b>这里只有数据面的一半</b>：{@code ADMIN} 在 {@code PermissionAspect} 里恒放行 （{@code
 * PermissionAspect.java:43-44}），故零授予的实际后果是"ADMIN 照旧、其余角色一律 403"。 行为面的那一半（预置角色真打 {@code POST
 * /api/v1/ai/email-draft} → 403）归 C4 的集成用例，<b>此处刻意不写</b>： C3
 * 是本项的"显式红窗"（新代码暂无对应用例），把一条行为断言塞进来会让那个窗口的形状变得含混。
 */
class AiPermissionGrantIT extends AbstractIntegrationTest {

  /** 本项唯一的权限码。 */
  private static final String AI_GENERATE = "ai:generate";

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("104 FR-020：ai:generate 预置矩阵对任何角色零授予（判据③ 的可执行痕迹）")
  void aiGenerateIsGrantedToNoPresetRole() {
    // 正对照：先证明这条查询真查得出授予——联表或列名写坏时，下面那个"空"会一起假绿
    // （照 PermissionMatrixIT 的做法）。
    assertThat(rolesHolding("customer:claim"))
        .as("正对照落空：查不出任何角色持有 customer:claim，说明查询本身写坏了")
        .isNotEmpty();

    // 前提：码必须在字典里。若它不在字典里，"零授予"就是句废话——字典里没有的码本来就授不出去，
    // 而这条用例会以一个恒真的形态绿着。
    assertThat(PermissionDictionaryTestSupport.codes())
        .as(
            "ai:generate 不在 PERMISSION_DEFS 里：那样「零授予」恒真，本用例不再看着任何东西"
                + "（角色页也勾不出它 ⇒ 零授予会从「决定」退化成「做不到」）")
        .contains(AI_GENERATE);

    assertThat(rolesHolding(AI_GENERATE))
        .as(
            "预置角色持有 %s。若这是有意的，请同时改掉本用例、RoleConstants 上方的注释与 spec.md FR-020 的"
                + "「默认后果」记录：零授予是一份**记录在案的决定**，不是一份没人知道的现状",
            AI_GENERATE)
        .isEmpty();
  }

  /** 哪些角色持有该码——用角色 code 而非 id，失败信息才读得懂。 */
  private List<String> rolesHolding(String permissionCode) {
    return jdbcTemplate.queryForList(
        "SELECT r.code FROM role_permission rp JOIN role r ON r.id = rp.role_id"
            + " WHERE rp.permission_code = ? ORDER BY r.code",
        String.class,
        permissionCode);
  }
}
