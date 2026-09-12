package com.crm.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crm.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * 权限接线（1.5）：按角色逐条验证「矩阵说能，就真能；矩阵说不能，就真不能」。
 *
 * <p><b>为什么这类断言不能只在单测里做</b>：权限的实际效果由三层叠加决定——{@code @PreAuthorize} （Spring
 * 方法安全）、{@code @RequirePermission} + {@code PermissionAspect}（业务码）、数据范围。
 * 任何一层写错，单测（只跑其中一层）都是绿的，而用户看到的是 403。本类用真实登录拿到各角色令牌后打真接口， 因此它也是「矩阵没写错」这件事的回归测试。
 *
 * <p><b>两层 403 必须分开断言</b>：权限码拒绝返回 {@code PERMISSION_DENIED}，数据范围拒绝返回 {@code FORBIDDEN}（{@code
 * ErrorCode} 里两个码的文案完全相同，只有 code 不同）。只断言 HTTP 403 会让
 * 「权限码没生效、恰好被数据范围兜住了」和「权限码正常工作」看起来一模一样——本类第二个测试专门造出后者， 证明这两层各自独立地在工作。
 *
 * <p>第一批覆盖联系人（{@code ContactController}）：它在 1.5 里同时踩了两个坑——类级 {@code
 * hasAnyRole('ADMIN','SALES','SUPPORT')} 把 081 新增角色整体挡在门外，以及 {@code contact:create/update/delete}
 * 三个码在种子里无人持有。
 *
 * <p><b>读接口什么时候需要设码</b>：最后一个测试覆盖的是本次新加的一批读码。判据不是"读就设码"， 而是"这条读路径有没有数据范围"——联系人、客户、线索的列表与详情都由 Service
 * 里的 {@code resolveVisibleOwnerIds} / {@code checkXxxPermission} 真实过滤，于是它们不设码；
 * 而合同、订单、报价单、商机、知识库以及线索导出的读路径在 Service 里一个范围过滤都没有 （报价与商机是按主键直取，线索导出是 Excel
 * 服务自拼查询），类级门一撤就是"任何登录用户读全表"， 因此必须设码。这条界线决定了本类里两类断言的写法为什么不同。
 *
 * <p><b>批 2</b>（{@code adminOnlyModulesFollowTheMatrix}）覆盖七个 ADMIN-only 配置类模块。它们的共同形态 是「类级
 * {@code @PreAuthorize} 把整类锁成 ADMIN，而菜单却授给了别人」——撤门之后每个码都要明确回答 「授给谁」：{@code sla:manage} 与 {@code
 * department:manage} 有矩阵承诺（前者 V75 已授、后者 V81 补授）， 其余四个（{@code audit:view} / {@code recycle:*} /
 * {@code field_permission:manage} / {@code integration:manage}）无人被授，于是**保持仅 ADMIN**，只是从此勾得出来。
 *
 * <p><b>批 2 · 协作/工作台</b>（{@code workbenchModulesFollowTheMatrix}）覆盖另外六个模块，它们分成两类，本类用一个
 * 测试同时钉住两类：<b>自限范围</b>的（通知中心、智能建议——数据只属于调用者本人，或来自已按范围过滤的 Service）
 * 撤门后什么码都不加，因为那道门从来没在保护数据，只是在按角色名字拦人；<b>有数据面</b>的（跟进记录、通话记录、
 * 任务）撤门后必须设码，其中前两个还要<b>新增读码</b>——它们的读路径压根没有数据范围（跟进是"三个 id 都不传就全表 分页"，通话记录干脆一条过滤都没有）。判据与理由写在 {@code
 * RoleConstants} 与各自的控制器 Javadoc 里。
 *
 * <p><b>批 2 · 数据侧</b>（{@code dataModulesFollowTheMatrix}）覆盖统计 / 报表 / 导出 / 签署 / 销售机会 / 定价。
 * 它们比前几批多出两种形态：一是 <b>"门是死的、矩阵另有承诺"</b>——{@code kpi-board} 的方法级 {@code hasRole('ADMIN')} 让持有数据大屏菜单的
 * ANALYST 恒 403，{@code report:manage} 授给了五个角色却没有任何端点引用； 二是
 * <b>"读了也不设码"的反向判据</b>——统计首页的授予范围会等于全部角色（"首页"菜单人手一个），给它设码等于 造一个可以在角色页误关的开关，于是本批第一次出现"撤门且不设码"的读接口。
 *
 * <p><b>批 2 · 合同/客户/字段</b>（{@code contractCustomerAndFieldModulesFollowTheMatrix}）覆盖本轮的收尾。
 * 两条值得记住的边界：{@code contract:renewal} 这个码与 {@code contract-renewal} 这个菜单此前<b>都无人持有</b>
 * ——功能做好了、菜单树里也有，却对所有人不可见，本批把这一对还给销售四角色；而导出与合同续约相反， {@code export:create}
 * 刻意<b>不按菜单扩</b>（那两条导出没有范围过滤），因此本类里"有意的锁死"与 "把功能还回去"同时存在，各自都有断言看着。
 */
class PermissionEnforcementIT extends AbstractIntegrationTest {

  private static final String PASSWORD = "Passw0rd!";

  @Test
  @DisplayName("联系人写操作按权限码放行：有码的角色通过，只读角色 403(PERMISSION_DENIED)")
  void contactWritesFollowPermissionCodesNotRoleNames() throws Exception {
    String admin = loginAndGetToken();
    String rep = tokenFor(admin, "pw_rep", "SALES_REP");
    String support = tokenFor(admin, "pw_support", "SUPPORT");
    String viewer = tokenFor(admin, "pw_viewer", "VIEWER");

    // 客户由本人创建：064 之后非 ADMIN 建的客户默认归自己。用 ADMIN 建客户再拿 rep 令牌去改，
    // 403 会来自数据范围而不是权限码，那样的红绿证明不了接线（见本类第二个测试）。
    long repCustomerId = createCustomer(rep, "探针客户-销售");
    long supportCustomerId = createCustomer(support, "探针客户-客服");

    // SALES_REP：081 新增角色，矩阵给了 contact:* —— 改造前它在 ContactController 上恒 403
    // （类级 hasAnyRole 只认 ADMIN/SALES/SUPPORT 三个字面量），连读都读不到。
    mockMvc
        .perform(get("/api/v1/contacts").header("Authorization", bearer(rep)))
        .andExpect(status().isOk());

    JsonNode created = createContact(rep, repCustomerId, "王联系");
    long contactId = created.path("id").asLong();
    int version = created.path("version").asInt();

    mockMvc
        .perform(
            put("/api/v1/contacts/" + contactId)
                .header("Authorization", bearer(rep))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(repCustomerId, "王联系改", version)))
        .andExpect(status().isOk());

    mockMvc
        .perform(delete("/api/v1/contacts/" + contactId).header("Authorization", bearer(rep)))
        .andExpect(status().isOk());

    // SUPPORT：老角色，但 V80 之前种子数据里没有 contact:* —— 它在旧类级门里能过，换动作码后必须补授，
    // 否则就是从"能改"变成 403。这条是 V80 第 3 节那批补授的回归测试。
    createContact(support, supportCustomerId, "李联系");

    // VIEWER：只读角色，一个写码都没有 → 必须 403。断言 code 而不只是 403，是为了确认拒绝来自权限码，
    // 而不是被数据范围顺手拦下——后者在权限码整体失效时同样会返回 403。
    mockMvc
        .perform(
            post("/api/v1/contacts")
                .header("Authorization", bearer(rep))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(repCustomerId, "赵联系", 0))) // 先确认这个 body 本身是合法的
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/v1/contacts")
                .header("Authorization", bearer(viewer))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(repCustomerId, "孙联系", 0)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));
  }

  @Test
  @DisplayName("数据范围仍是独立一层：有写码也改不了别人客户下的联系人(403 FORBIDDEN)")
  void contactAccessStillFollowsDataScope() throws Exception {
    String admin = loginAndGetToken();
    String rep = tokenFor(admin, "pw_rep_scope", "SALES_REP");

    // ADMIN 建的客户不带负责人（064：ADMIN 是池子/分配场景），落在任何非 ADMIN 的可见集之外
    long adminCustomerId = createCustomer(admin, "探针客户-管理员");
    long adminContactId = createContact(admin, adminCustomerId, "管理员联系人").path("id").asLong();

    // 权限码这一层：rep 持有 contact:update，切面放行 → 403 只能来自 ContactService.checkContactPermission
    mockMvc
        .perform(get("/api/v1/contacts/" + adminContactId).header("Authorization", bearer(rep)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

    mockMvc
        .perform(
            put("/api/v1/contacts/" + adminContactId)
                .header("Authorization", bearer(rep))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(adminCustomerId, "越权改名", 0)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

    // 反证：同一套令牌、同一个接口，作用在自己客户下的联系人时是通的。
    // 没有这一条，上面两条在"接口整体坏掉"时也会通过。
    long repCustomerId = createCustomer(rep, "探针客户-自己");
    long repContactId = createContact(rep, repCustomerId, "自己的联系人").path("id").asLong();
    mockMvc
        .perform(get("/api/v1/contacts/" + repContactId).header("Authorization", bearer(rep)))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("工单：客服角色从「全部 403」恢复；无工单权限的角色连读都不行")
  void ticketAccessFollowsTheMatrix() throws Exception {
    String admin = loginAndGetToken();
    String agent = tokenFor(admin, "pw_agent", "SUPPORT_AGENT");
    String supMgr = tokenFor(admin, "pw_sup_mgr", "SUPPORT_MANAGER");
    String analyst = tokenFor(admin, "pw_analyst", "ANALYST");

    // 客户由 ADMIN 建：SUPPORT_AGENT 的权限列表里没有 customer:create，它建不了客户，
    // 而工单必须挂客户。建单路径不校验客户可见性（只有 page 对 SALES 做过滤），所以这条能过。
    long customerId = createCustomer(admin, "探针客户-工单");

    // 改造前：SUPPORT_AGENT 在 /api/v1/tickets 的**全部**接口上恒 403（类级 hasAnyRole 只认
    // ADMIN/SALES/SUPPORT 三个字面量），而它的菜单里有「工单管理」、权限列表里也有 ticket:create 等
    // ——081 的客服角色模型在本模块完全不可用。这条是那次锁死的回归测试。
    JsonNode created = createTicket(agent, customerId, "客服专员建的工单");
    long ticketId = created.path("id").asLong();

    mockMvc
        .perform(get("/api/v1/tickets").header("Authorization", bearer(agent)))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            put("/api/v1/tickets/" + ticketId)
                .header("Authorization", bearer(agent))
                .contentType(MediaType.APPLICATION_JSON)
                .content(ticketBody(customerId, "客服专员改的工单", created.path("version").asInt())))
        .andExpect(status().isOk());

    // 状态流转挂 ticket:update：SUPPORT_AGENT 持 update 但**不持** assign/approve，
    // 所以这是它该能做的最关键动作（把工单推进到已解决）
    mockMvc
        .perform(
            post("/api/v1/tickets/" + ticketId + "/transition")
                .header("Authorization", bearer(agent))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetStatus\": \"IN_PROGRESS\"}"))
        .andExpect(status().isOk());

    // 分配：SUPPORT_MANAGER 有 ticket:assign，SUPPORT_AGENT 没有 → 两个角色在这里分岔。
    // 这是"矩阵说不能就不能"在同一实体上的正反两面，只测一面证明不了码真的生效。
    mockMvc
        .perform(
            post("/api/v1/tickets/" + ticketId + "/assign")
                .header("Authorization", bearer(supMgr))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"assigneeId\": 1}"))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/tickets/" + ticketId + "/assign")
                .header("Authorization", bearer(agent))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"assigneeId\": 1}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));

    // ANALYST 的菜单与权限列表里都没有工单 → 连读都读不到。工单的数据范围只覆盖 SALES 一个角色，
    // 若读留空不设码，这里就会是 200，"全体登录用户可拉全部工单"。
    mockMvc
        .perform(get("/api/v1/tickets").header("Authorization", bearer(analyst)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));
  }

  private JsonNode createTicket(String token, long customerId, String title) throws Exception {
    String resp =
        mockMvc
            .perform(
                post("/api/v1/tickets")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(ticketBody(customerId, title, 0)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(resp).path("data");
  }

  private String ticketBody(long customerId, String title, int version) {
    return "{\"customerId\": "
        + customerId
        + ", \"title\": \""
        + title
        + "\", \"priority\": \"MEDIUM\", \"version\": "
        + version
        + "}";
  }

  @Test
  @DisplayName("客户/用户：原先完全没闸门的写接口接上码；ADMIN 专属的管理接口改为矩阵码")
  void customerAndUserWritesFollowTheMatrix() throws Exception {
    String admin = loginAndGetToken();
    String rep = tokenFor(admin, "pw_rep_cust", "SALES_REP");
    String mgr = tokenFor(admin, "pw_mgr_cust", "SALES_MANAGER");
    String analyst = tokenFor(admin, "pw_analyst_cust", "ANALYST");

    // CustomerController 原先**没有任何写注解**：任何登录用户只要数据范围过得去就能改删客户。
    // ANALYST 的权限列表里一个 customer:* 都没有 → 接上码后它必须被挡住（收窄，且是矩阵的本意）。
    mockMvc
        .perform(
            post("/api/v1/customers")
                .header("Authorization", bearer(analyst))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"越权客户\", \"company\": \"越权公司\", \"phone\": \"13900000002\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));

    // 有码的角色照常（SALES_REP 持 customer:create/update/delete）
    long customerId = createCustomer(rep, "探针客户-销售代表");
    mockMvc
        .perform(
            put("/api/v1/customers/" + customerId)
                .header("Authorization", bearer(rep))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"探针客户-销售代表\", \"company\": \"探针客户-销售代表公司\", \"version\": 0}"))
        .andExpect(status().isOk());
    // 删除：SALES_REP 本来就有 customer:delete；SALES/SUPPORT 的删除是靠 V80 补授保住的
    mockMvc
        .perform(delete("/api/v1/customers/" + customerId).header("Authorization", bearer(rep)))
        .andExpect(status().isOk());

    // 用户管理：原先是方法级 hasRole('ADMIN')，只认字面量 ADMIN，而 SALES_MANAGER 的权限列表与菜单里
    // 都有「用户管理」——改成 user:manage 后它才真的能进去。
    mockMvc
        .perform(get("/api/v1/users").header("Authorization", bearer(mgr)))
        .andExpect(status().isOk());

    // ANALYST 没有 user:manage → 仍然进不去。换码不等于放开。
    mockMvc
        .perform(get("/api/v1/users").header("Authorization", bearer(analyst)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));

    // 自助改密是例外：PUT /users/me/password 不挂 user:manage，任何登录用户都能改自己的密码
    mockMvc
        .perform(
            put("/api/v1/users/me/password")
                .header("Authorization", bearer(analyst))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"oldPassword\": \"Passw0rd!\", \"newPassword\": \"Passw0rd!2\"}"))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("读码：菜单即读权——有码的角色读得到，没码的角色连读都读不到")
  void readCodesFollowTheMatrix() throws Exception {
    String admin = loginAndGetToken();
    String rep = tokenFor(admin, "pw_rep_read", "SALES_REP");
    String viewer = tokenFor(admin, "pw_viewer_read", "VIEWER");
    String analyst = tokenFor(admin, "pw_analyst_read", "ANALYST");
    String support = tokenFor(admin, "pw_support_read", "SUPPORT");

    // 订单：类级门原文是 hasAnyRole('ADMIN','SALES')，而订单的 Service 里没有数据范围过滤——
    // 撤门不设码，就是任何登录用户都能拉走全部订单金额。
    // 三条缺一不可：VIEWER 拿的**只有读码**（它是种子里唯一"无编辑权限"的角色，复用写码会把
    // 编辑订单一起给出去），SALES_REP 是菜单里本就有订单的角色，ANALYST 的菜单与权限列表里
    // 都没有订单 → 必须停在 403，而且必须是 PERMISSION_DENIED。
    mockMvc
        .perform(get("/api/v1/orders").header("Authorization", bearer(viewer)))
        .andExpect(status().isOk());
    mockMvc
        .perform(get("/api/v1/orders").header("Authorization", bearer(rep)))
        .andExpect(status().isOk());
    mockMvc
        .perform(get("/api/v1/orders").header("Authorization", bearer(analyst)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));

    // 知识库：读码补的是两个方向的缺口——SUPPORT_MANAGER/SUPPORT_AGENT 的矩阵里有 knowledge:*
    // 却长期被类级门整体挡住（读码兑现这张承诺），而 SUPPORT 靠那道门事实上一直在维护文章
    // （保留它的事实能力）。ANALYST 一个 knowledge:* 都没有，读码把它挡在外面。
    mockMvc
        .perform(get("/api/v1/knowledge").header("Authorization", bearer(support)))
        .andExpect(status().isOk());
    mockMvc
        .perform(get("/api/v1/knowledge").header("Authorization", bearer(analyst)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));

    // 商机：读码**刻意不含** SUPPORT。OpportunityIT.supportRoleForbidden 断言的就是这件事，
    // 此前靠类级门顺手满足；这里把它钉成接线的回归，免得日后有人"顺手"把 SUPPORT 加进授予名单。
    mockMvc
        .perform(get("/api/v1/opportunities").header("Authorization", bearer(support)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));
  }

  @Test
  @DisplayName("批 2（配置/管理类）：撤掉类级 ADMIN 门后，矩阵说能就能、说不能就不能")
  void adminOnlyModulesFollowTheMatrix() throws Exception {
    String admin = loginAndGetToken();
    String rep = tokenFor(admin, "pw_rep_batch2", "SALES_REP");
    String mgr = tokenFor(admin, "pw_mgr_batch2", "SALES_MANAGER");
    String supMgr = tokenFor(admin, "pw_sup_mgr_batch2", "SUPPORT_MANAGER");
    String agent = tokenFor(admin, "pw_agent_batch2", "SUPPORT_AGENT");

    // SLA 策略/日历：原先是类级 hasRole('ADMIN')，而 V75 早就把 sla:manage 授给了 SUPPORT_MANAGER——
    // 矩阵说能、端点说 403，而且这个码当时在字典里根本不存在（角色页勾不出来，管理员也无从开口）。
    // 撤门 + 补字典后 SUPPORT_MANAGER 才真的进得去；SUPPORT_AGENT 没有这个码 → 仍然 403。
    mockMvc
        .perform(get("/api/v1/sla-policies").header("Authorization", bearer(supMgr)))
        .andExpect(status().isOk());
    mockMvc
        .perform(get("/api/v1/sla-calendar").header("Authorization", bearer(supMgr)))
        .andExpect(status().isOk());
    mockMvc
        .perform(get("/api/v1/sla-policies").header("Authorization", bearer(agent)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));

    // 部门：**批 2 里唯一一处实质扩权**（V81）。「部门管理」菜单由 V46/V75 授给了 ADMIN + 四个管理
    // 角色，与 user:manage / role:manage 的持有者完全是同一批人，而本类此前是 ADMIN-only——它们看得见
    // 菜单、点进去恒 403。这条断言把这个决定钉住：要收回就得同时收回菜单，而不是让菜单继续指向 403。
    mockMvc
        .perform(get("/api/v1/departments/tree").header("Authorization", bearer(mgr)))
        .andExpect(status().isOk());
    mockMvc
        .perform(get("/api/v1/departments/tree").header("Authorization", bearer(rep)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));

    // 其余四个模块（审计日志 / 回收站 / 字段权限 / 集成中心）：菜单无人持有、码也无人被授，所以撤门后
    // **一个码都不补**——可访问范围与改造前完全一致（仅 ADMIN），区别只是这些码从此在角色页上勾得出来。
    // 四个都正反两面断言：ADMIN 200 证明端点本身是通的（否则单看 403 无法区分"矩阵在拦"和"接口坏了"），
    // 非 ADMIN 403 且 code = PERMISSION_DENIED 证明拒绝来自矩阵码、而不是被数据范围顺手兜住。
    for (String path :
        List.of(
            "/api/v1/audit-logs",
            "/api/v1/recycle-bin",
            "/api/v1/field-permissions",
            "/api/v1/integration-channels")) {
      mockMvc.perform(get(path).header("Authorization", bearer(admin))).andExpect(status().isOk());
      mockMvc
          .perform(get(path).header("Authorization", bearer(mgr)))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));
    }
  }

  @Test
  @DisplayName("批 2（协作/工作台）：自限范围的模块撤门不设码，有数据面的模块按码设防")
  void workbenchModulesFollowTheMatrix() throws Exception {
    String admin = loginAndGetToken();
    String rep = tokenFor(admin, "pw_rep_wb", "SALES_REP");
    String agent = tokenFor(admin, "pw_agent_wb", "SUPPORT_AGENT");
    String viewer = tokenFor(admin, "pw_viewer_wb", "VIEWER");
    String analyst = tokenFor(admin, "pw_analyst_wb", "ANALYST");

    // 通知中心：**撤门且不设码**。四个方法都只碰本人的数据，那道 hasAnyRole('ADMIN','SALES','SUPPORT')
    // 不是保护数据、只是按角色名拦人——SALES_REP 因此连自己的未读角标都拉不到。
    // 这条断言钉住"锁死已解除"：撤门之后 081 角色能读自己的通知，且不需要任何码。
    mockMvc
        .perform(get("/api/v1/notifications").header("Authorization", bearer(rep)))
        .andExpect(status().isOk());
    mockMvc
        .perform(get("/api/v1/notifications/unread-count").header("Authorization", bearer(rep)))
        .andExpect(status().isOk());
    // 智能建议同理：数据来自已按范围过滤的 Service。原先那道门只放 ADMIN/SALES，
    // 而「智能建议」菜单在 V46/V75 里授给了 SALES_MANAGER / SALES_REP——菜单点得进、接口 403。
    mockMvc
        .perform(get("/api/v1/suggestions").header("Authorization", bearer(agent)))
        .andExpect(status().isOk());

    // 跟进记录与通话记录：读路径**没有数据范围**（跟进是"三个 id 都不传就全表分页"，通话记录干脆一条
    // 过滤都没有），所以撤门必须设读码——这两条是"任意登录用户可读全量"的正面反证。
    mockMvc
        .perform(get("/api/v1/follow-ups").header("Authorization", bearer(rep)))
        .andExpect(status().isOk());
    mockMvc
        .perform(get("/api/v1/follow-ups").header("Authorization", bearer(analyst)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));
    mockMvc
        .perform(get("/api/v1/call-records").header("Authorization", bearer(rep)))
        .andExpect(status().isOk());
    mockMvc
        .perform(get("/api/v1/call-records").header("Authorization", bearer(viewer)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));

    // 任务：读不设码（按 owner 自限），写按 task:* 设防。SALES_REP 在 V75 里本就有 task:create，
    // 却一直被类级门挡着——这条是"矩阵说能就能"的兑现。
    mockMvc
        .perform(
            post("/api/v1/tasks")
                .header("Authorization", bearer(rep))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"销售代表的任务\"}"))
        .andExpect(status().isCreated());

    // VIEWER 是本批**唯一一处刻意不授**的地方：它同样持有「任务管理」菜单，但它是种子数据里唯一
    // "不插任何操作权限"的角色（V46 注释原话）。所以它能读（读不设码）、不能写——正反两面一起断言，
    // 免得日后有人"顺手"把 VIEWER 加进 task:* 的授予名单而没人发现。
    mockMvc
        .perform(get("/api/v1/tasks").header("Authorization", bearer(viewer)))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/tasks")
                .header("Authorization", bearer(viewer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"只读角色的任务\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));
  }

  @Test
  @DisplayName("批 2（数据侧）：大屏/报表/导出/签署/销售机会/定价按矩阵接线")
  void dataModulesFollowTheMatrix() throws Exception {
    String admin = loginAndGetToken();
    String rep = tokenFor(admin, "pw_rep_data", "SALES_REP");
    String mgr = tokenFor(admin, "pw_mgr_data", "SALES_MANAGER");
    String agent = tokenFor(admin, "pw_agent_data", "SUPPORT_AGENT");
    String analyst = tokenFor(admin, "pw_analyst_data", "ANALYST");
    String viewer = tokenFor(admin, "pw_viewer_data", "VIEWER");

    // 数据大屏：类级门撤掉之后 kpi-board 单独挂 kpi:view。持有「数据大屏」菜单的 ANALYST 从此进得去
    // （此前是方法级 hasRole('ADMIN')——菜单点得进、接口恒 403），SALES_REP 没有这个码 → 仍然 403。
    mockMvc
        .perform(get("/api/v1/stats/kpi-board").header("Authorization", bearer(analyst)))
        .andExpect(status().isOk());
    mockMvc
        .perform(get("/api/v1/stats/kpi-board").header("Authorization", bearer(rep)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));

    // 首页与排行榜**不设码**（授予范围会等于全部角色）。这条正面断言钉住那个决定：SUPPORT_AGENT 的首页
    // 此前恒定 403，现在读得到——即便它的菜单里没有「团队排行」，我们也不再为此造一个"只排掉客服与分析师、
    // 却放行 VIEWER"的码（理由见 StatsController 的 Javadoc）。
    mockMvc
        .perform(get("/api/v1/stats/dashboard").header("Authorization", bearer(agent)))
        .andExpect(status().isOk());
    mockMvc
        .perform(get("/api/v1/stats/leaderboard").header("Authorization", bearer(agent)))
        .andExpect(status().isOk());

    // 报表：读码 report:view 的授予名单 = 「自定义报表」菜单的全部持有者（含 VIEWER 与客服），而模板的增删
    // 仍归 report:manage。三条断言把这个边界钉住——VIEWER 读得到模板列表，SALES_MANAGER 能存模板
    // （V80 之前这个码授出了却没有端点引用，五个持有者里只有 ADMIN 真的用得上），SUPPORT_AGENT 不能。
    mockMvc
        .perform(get("/api/v1/reports/templates").header("Authorization", bearer(viewer)))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/reports/templates")
                .header("Authorization", bearer(mgr))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"探针模板\", \"dimension\": \"STAGE\", \"metric\": \"COUNT\"}"))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/reports/templates")
                .header("Authorization", bearer(agent))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"越权模板\", \"dimension\": \"STAGE\", \"metric\": \"COUNT\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));

    // 导出：create **刻意不按菜单扩**（商机/工单两条导出没有范围过滤，是整表导出），只补改造前那道门放行的
    // 三个角色。SALES_REP 打开导出中心读得到自己的历史，但"新建导出"仍然 403——这是一处**有意的锁死**，
    // 单列断言是为了让日后想"顺手补上"的人先看到理由。
    mockMvc
        .perform(get("/api/v1/exports").header("Authorization", bearer(rep)))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/exports")
                .header("Authorization", bearer(rep))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"exportType\": \"CUSTOMER\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));

    // 签署：零补授——写用文档自己的编辑码（quote:update / contract:update），读用文档的读码。VIEWER 持有
    // contract:read，所以读得到签署记录；SUPPORT_AGENT 两个码都没有，读与写都是 403。
    // 这里刻意用"不存在的合同 id"：403 必须在读数据之前就发生（若换成 404，说明码没接上）。
    mockMvc
        .perform(get("/api/v1/contracts/1/signature").header("Authorization", bearer(viewer)))
        .andExpect(status().isOk());
    mockMvc
        .perform(get("/api/v1/contracts/1/signature").header("Authorization", bearer(agent)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));
    mockMvc
        .perform(
            post("/api/v1/contracts/1/sign")
                .header("Authorization", bearer(agent))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"signatureImage\": \"probe\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));

    // 销售机会：复用商机家族的动作码。SALES_REP 在矩阵里本就有 opportunity:read/create/update 与这张菜单，
    // 此前被类级门挡在外面；SUPPORT_AGENT 不在读码名单里（V80 刻意排除 SUPPORT）→ 403。
    mockMvc
        .perform(get("/api/v1/sales-opportunities").header("Authorization", bearer(rep)))
        .andExpect(status().isOk());
    mockMvc
        .perform(get("/api/v1/sales-opportunities").header("Authorization", bearer(agent)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));

    // 定价：写挂 product:update，授予范围 =「产品」菜单的持有者。SALES_REP 能给产品定价（矩阵承诺了却一直
    // 进不来），VIEWER 没有任何产品写码 → 403。
    long productId = createProduct(admin);
    // H2 无 seed：USD 币种要先建出来。否则 setPrice 内部的 rateOf 会抛 CURRENCY_NOT_FOUND（404），
    // 那个 404 会把探针真正要判的东西（403 有没有接上、能不能写进去）整个掩盖掉。
    mockMvc
        .perform(
            post("/api/v1/currencies")
                .header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\": \"USD\", \"name\": \"美元\", \"rate\": 7.2}"))
        .andExpect(status().isCreated());
    mockMvc
        .perform(
            post("/api/v1/products/{id}/prices", productId)
                .header("Authorization", bearer(rep))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currencyCode\": \"USD\", \"price\": 14000}"))
        .andExpect(status().isCreated());
    mockMvc
        .perform(
            delete("/api/v1/products/{id}/prices/USD", productId)
                .header("Authorization", bearer(viewer)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));
  }

  @Test
  @DisplayName("批 2（合同/客户/字段）：无人持有的续约码与菜单一起还给销售，附件与共享零补授")
  void contractCustomerAndFieldModulesFollowTheMatrix() throws Exception {
    String admin = loginAndGetToken();
    String rep = tokenFor(admin, "pw_rep_b2d", "SALES_REP");
    String agent = tokenFor(admin, "pw_agent_b2d", "SUPPORT_AGENT");
    String finance = tokenFor(admin, "pw_fin_b2d", "FINANCE_MANAGER");
    String viewer = tokenFor(admin, "pw_viewer_b2d", "VIEWER");
    String analyst = tokenFor(admin, "pw_analyst_b2d", "ANALYST");

    // 合同续约：contract:renewal 此前**无人持有、无人引用**，'contract-renewal' 菜单也无人持有——前端路由、
    // 接口、MENU_TREE 条目都齐全，功能却对所有人不可见（只有 ADMIN 靠菜单兜底看得见）。本批把这一对还给
    // 销售四角色，这里同时钉住两侧：SALES_REP 进得去；FINANCE_MANAGER 进不去（它持有 contract:read，
    // 但续约漏斗是销售的作战视图，不是一个更宽的合同读面）。
    mockMvc
        .perform(
            get("/api/v1/contracts/renewal-overview")
                .param("group", "EXPIRING_SOON")
                .header("Authorization", bearer(rep)))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            get("/api/v1/contracts/renewal-overview")
                .param("group", "EXPIRING_SOON")
                .header("Authorization", bearer(finance)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));

    // 客户共享：撤掉类级门后，写挂 customer:update（而不是 customer:transfer——那个码会把 SALES_REP 挡在
    // 门外）。归属者共享成功；VIEWER 作为"一个写码都没有"的角色被矩阵拦住，而不再是靠类级门顺手拦住。
    long customerId = createCustomer(rep, "批2D共享客户");
    long targetUserId = userIdFor(admin, "pw_share_target_b2d", "SALES_REP");
    mockMvc
        .perform(
            post("/api/v1/customer-shares")
                .header("Authorization", bearer(rep))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"customerId\": "
                        + customerId
                        + ", \"sharedToUserId\": "
                        + targetUserId
                        + "}"))
        .andExpect(status().isCreated());
    mockMvc
        .perform(
            post("/api/v1/customer-shares")
                .header("Authorization", bearer(viewer))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"customerId\": "
                        + customerId
                        + ", \"sharedToUserId\": "
                        + targetUserId
                        + "}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));

    // 字段定义：page 是配置面（custom_field:read = ADMIN + ANALYST），/definitions 是表单要用的**元数据读**
    // 而**不设码**——客户/线索/商机/工单四个列表页都依赖它，原先的方法级门把 SALES_REP 的筛选项静默打没了
    // （页面照常打开，只是少了几项，不报错，所以此前没人发现）。
    mockMvc
        .perform(get("/api/v1/custom-fields").header("Authorization", bearer(analyst)))
        .andExpect(status().isOk());
    mockMvc
        .perform(get("/api/v1/custom-fields").header("Authorization", bearer(agent)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));
    mockMvc
        .perform(
            get("/api/v1/custom-fields/definitions")
                .param("entityType", "CUSTOMER")
                .header("Authorization", bearer(rep)))
        .andExpect(status().isOk());
  }

  /** 建联系人并返回 data 节点（调用方从中取 id / version）。 */
  private JsonNode createContact(String token, long customerId, String name) throws Exception {
    String resp =
        mockMvc
            .perform(
                post("/api/v1/contacts")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(customerId, name, 0)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(resp).path("data");
  }

  private String body(long customerId, String name, int version) {
    return "{\"customerId\": "
        + customerId
        + ", \"name\": \""
        + name
        + "\", \"phone\": \"13800000001\", \"version\": "
        + version
        + "}";
  }

  /**
   * 084（菜单 IA 收口）：两个控制器从角色字面量改为按权限码放行。
   *
   * <p>与 V80~V84 各批同一形态，只是这次的触发点不是"类级门挡住新角色"，而是<b>撤掉前端 {@code isAdmin} 硬门 之后暴露出来的</b>：菜单授权（{@code
   * role_menu}）与端点闸门是两套开关，撤门之前"菜单永不出现"掩盖了 "页面打不开"；撤门之后两者必须一致（FR-N06）。{@code
   * MenuAccessGrantAlignmentTest} 机械地对**任意角色** 断言这件事（它是通用式，不枚举角色），本测试是它的行为层对照：真登录、打真接口。
   */
  @Test
  @DisplayName("084：自定义对象定义面与多币种按码放行——菜单已授的角色真能打开，无码者仍被挡住")
  void menuIaBatchGatesByCode() throws Exception {
    String admin = loginAndGetToken();
    String analyst = tokenFor(admin, "pw_analyst_084", "ANALYST");
    String financeManager = tokenFor(admin, "pw_finmgr_084", "FINANCE_MANAGER");
    String viewer = tokenFor(admin, "pw_viewer_084", "VIEWER");

    // ---------- 自定义对象**定义**面（CustomObjectController，改造前整类是 hasRole('ADMIN')）----------
    // ANALYST 持有「自定义对象」菜单（V75）与读写码（V85 补 read）→ 撤门接线后它真的进得来。
    // 改造前这里恒 403，而它的角色页上一直勾着这个菜单。
    mockMvc
        .perform(get("/api/v1/custom-objects").header("Authorization", bearer(analyst)))
        .andExpect(status().isOk());
    String created =
        mockMvc
            .perform(
                post("/api/v1/custom-objects")
                    .header("Authorization", bearer(analyst))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"name\": \"084探针对象\", \"code\": \"PW084OBJ\", \"fields\": ["
                            + "{\"field\": \"name\", \"label\": \"名称\", \"type\": \"TEXT\"}],"
                            + " \"enabled\": true}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long objectId = objectMapper.readTree(created).path("data").path("id").asLong();
    // 启停挂的是 update 码（不是独立码）——这里钉住这个映射，免得日后被拆成"第四种写法"。
    mockMvc
        .perform(
            post("/api/v1/custom-objects/{id}/toggle", objectId)
                .header("Authorization", bearer(analyst)))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            delete("/api/v1/custom-objects/{id}", objectId)
                .header("Authorization", bearer(analyst)))
        .andExpect(status().isOk());
    // VIEWER 一个 custom_object:* 都没有 → 停在一码之隔，且必须是 PERMISSION_DENIED（不是数据范围那种 403）。
    mockMvc
        .perform(get("/api/v1/custom-objects").header("Authorization", bearer(viewer)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));

    // ---------- 多币种（CurrencyRateController，改造前是 hasAnyRole('ADMIN','SALES')）----------
    // FINANCE_MANAGER 持有「多币种」菜单与 currency:manage（V75），却在角色字面量里 → 改造前恒 403。
    // 这是 084 T020 的裁决结果：接线到权限码，让已发布的矩阵成真（FR-N24，批准人 龙星）。
    mockMvc
        .perform(get("/api/v1/currencies").header("Authorization", bearer(financeManager)))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/currencies")
                .header("Authorization", bearer(financeManager))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\": \"PW084\", \"name\": \"084探针币\", \"rate\": 1.5}"))
        .andExpect(status().isCreated());
    // 写码与读码是两码：VIEWER 连读都不该有。
    mockMvc
        .perform(get("/api/v1/currencies").header("Authorization", bearer(viewer)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));
    // 折算属读（POST 只是因为带参，不是写）——ANALYST 无 currency:read，同样被挡。
    mockMvc
        .perform(
            post("/api/v1/currencies/convert")
                .header("Authorization", bearer(analyst))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\": 100, \"fromCurrency\": \"CNY\", \"toCurrency\": \"USD\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));
  }

  /**
   * 1.5 批 3（全仓最后一批角色字面量）：九个控制器改为按权限码放行。
   *
   * <p>本批的断言重点与 V80~V87 各批不同——那几批主要是"接线让**早已存在的授权**成真"，而本批有三处**补授** （不补就等于把原来能用的功能打成 403）：{@code
   * campaign:*} 给 SALES、{@code mail_sync:manage} 给 SALES、 {@code quota:*} 给
   * SALES_MANAGER。这三处是本批唯一真实的回归风险，也是全仓**唯一**能用行为层钉住的东西 ——既有的 {@code MarketingIT} / {@code
   * EmailSyncIT} / {@code SalesQuotaIT} 全部只用管理员令牌 （管理员在切面里直通，走不到码上），所以它们对这三处补授的存在与否**完全不敏感**。
   *
   * <p>另外六个新码（{@code customer:pool_manage} / {@code contract_template:manage} / {@code
   * workflow:read} / {@code mail_account:manage} / {@code playbook:manage} / {@code
   * open_platform:manage}）授给任何角色， 可访问范围与改造前一致（仅 ADMIN）。这四个探针的价值不在于"挡住了谁"，而在于钉住它们走的是 {@code
   * PermissionAspect}（{@code PERMISSION_DENIED}）而非数据范围那条 403（{@code FORBIDDEN}）—— 两种 403 共用同一个 HTTP
   * 状态码，只看状态是分不出来的。
   */
  @Test
  @DisplayName("1.5 批 3：九控制器按码放行——三处补授保住原能力，无人持有的码停在 PERMISSION_DENIED")
  void permissionBatch3ModulesFollowTheMatrix() throws Exception {
    String admin = loginAndGetToken();
    String sales = tokenFor(admin, "pw_sales_b3", "SALES");
    String salesManager = tokenFor(admin, "pw_salesmgr_b3", "SALES_MANAGER");
    String salesRep = tokenFor(admin, "pw_salesrep_b3", "SALES_REP");
    String support = tokenFor(admin, "pw_support_b3", "SUPPORT");

    // ---------- 产品：改是销售码（V83 为定价授的），增删不是（V75 只给了 MARKETING_MANAGER）----------
    long productId = createProduct(admin);
    String productJson =
        mockMvc
            .perform(get("/api/v1/products/{id}", productId).header("Authorization", bearer(admin)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    int version = objectMapper.readTree(productJson).path("data").path("version").asInt();
    // SALES 持有 product:update → 持有「产品」菜单的销售真的点得动"编辑"（改造前被 hasRole('ADMIN') 挡着）。
    mockMvc
        .perform(
            put("/api/v1/products/{id}", productId)
                .header("Authorization", bearer(sales))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"code\": \"P-PW-001\", \"name\": \"权限探针产品\", \"unit\": \"套\","
                        + " \"standardPrice\": 100000, \"version\": "
                        + version
                        + "}"))
        .andExpect(status().isOk());
    // 同一个人的新建/删除仍是 403——这不是接线失误而是矩阵的答案（create/delete 从未授给销售角色）。
    mockMvc
        .perform(
            post("/api/v1/products")
                .header("Authorization", bearer(sales))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\": \"P-PW-B3\", \"name\": \"批三产品\", \"unit\": \"套\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));
    mockMvc
        .perform(delete("/api/v1/products/{id}", productId).header("Authorization", bearer(sales)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));

    // ---------- 公海：claim 是本批唯一"改造前一条校验都没有"的写 ----------
    long poolCustomer = createCustomer(admin, "批三公海客户");
    mockMvc
        .perform(
            post("/api/v1/customers/pool/{id}/claim", poolCustomer)
                .header("Authorization", bearer(salesRep)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.ownerId").isNumber());
    // SUPPORT 持有「客户」菜单但不持有 customer:claim（领取不是客服的职能）→ 一码之隔。
    long poolCustomer2 = createCustomer(admin, "批三公海客户二");
    mockMvc
        .perform(
            post("/api/v1/customers/pool/{id}/claim", poolCustomer2)
                .header("Authorization", bearer(support)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));
    // 批量分配与扫描刻意**不复用** customer:transfer——批转移只 set(ownerId)，既不校验调用者是否拥有这些
    // 客户也无数据范围过滤。SALES 持有 customer:transfer（V46），若当时图省事复用它，这一行会是 200。
    mockMvc
        .perform(post("/api/v1/customers/pool/scan").header("Authorization", bearer(sales)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));

    // ---------- 营销活动：本批最可能被用户察觉的一处补授 ----------
    // 旧门是 hasAnyRole('ADMIN','SALES')，而 SALES 并不持有 campaign:*（只有 MARKETING_* 持有）→
    // 照原样接线会把"销售建活动"打成 403。这一行钉住补授确实发生了。
    mockMvc
        .perform(
            post("/api/v1/campaigns")
                .header("Authorization", bearer(sales))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"批三活动\", \"channel\": \"WEBSITE\"}"))
        .andExpect(status().isCreated());
    mockMvc
        .perform(
            post("/api/v1/campaigns")
                .header("Authorization", bearer(support))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"批三活动二\", \"channel\": \"WEBSITE\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));

    // ---------- 邮件：账户配置面与同步面刻意拆成两个码 ----------
    String accountEmail = "pw-b3-" + (System.nanoTime() % 100000) + "@corp.com";
    String accountJson =
        mockMvc
            .perform(
                post("/api/v1/mail-accounts")
                    .header("Authorization", bearer(admin))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            "{\"email\": \"%s\", \"displayName\": \"批三\", \"smtpHost\":"
                                + " \"smtp.corp.com\", \"smtpPort\": 465, \"enabled\": true}",
                            accountEmail)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long mailAccountId = objectMapper.readTree(accountJson).path("data").path("id").asLong();
    // 同步面（旧门 hasAnyRole('ADMIN','SALES')）→ 补 mail_sync:manage 给 SALES，能力保住。
    mockMvc
        .perform(
            post("/api/v1/mail-accounts/{id}/sync", mailAccountId)
                .header("Authorization", bearer(sales)))
        .andExpect(status().isOk());
    // 配置面（旧门 hasRole('ADMIN')）→ 不授：改错一次全公司邮件链路哑掉。两个面若合并成一个码，
    // 上面那行与这行必有一行是错的，这正是拆码的理由。
    mockMvc
        .perform(get("/api/v1/mail-accounts").header("Authorization", bearer(sales)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));

    // ---------- 销售配额：撤掉类级门后靠五个码兜住，其中一个读码是本批新增的 ----------
    mockMvc
        .perform(get("/api/v1/sales-quota").header("Authorization", bearer(salesManager)))
        .andExpect(status().isOk());
    mockMvc
        .perform(get("/api/v1/sales-quota").header("Authorization", bearer(sales)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));

    // ---------- 工作流：读码刻意不复用 workflow:manage ----------
    // SALES_MANAGER 持有 workflow:manage（V75）——若当时图省事把读端点挂在 manage 上，这一行会是 200，
    // 而它并不持有「工作流」菜单，等于被"管理者礼包"顺手带出规则与执行日志的访问权。
    mockMvc
        .perform(get("/api/v1/workflows/rules").header("Authorization", bearer(salesManager)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));

    // ---------- 合同模板：不复用 contract:*（授给销售四角色与 FINANCE_*）----------
    // 复用的后果是"能签合同的人顺便能改合同的法定文本"。SALES 持有 contract:delete（V46），
    // 所以这一行只有在 contract_template:manage 无人持有时才是 403。
    mockMvc
        .perform(
            delete("/api/v1/contract-templates/{id}", 999999L)
                .header("Authorization", bearer(sales)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));

    // ---------- Playbook 与开放平台：配置面仍是"仅 ADMIN"，只是从此刻得出来 ----------
    mockMvc
        .perform(
            post("/api/v1/stage-actions")
                .header("Authorization", bearer(sales))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"stage\": \"NEGOTIATING\", \"actionName\": \"批三动作\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));
    mockMvc
        .perform(get("/api/v1/platform/api-keys").header("Authorization", bearer(sales)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));
  }

  /** 客户查重是全局的 (name, company)，同一个用例里建多个客户必须给不同名字。 */
  private long createCustomer(String token, String name) throws Exception {
    String resp =
        mockMvc
            .perform(
                post("/api/v1/customers")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"name\": \""
                            + name
                            + "\", \"company\": \""
                            + name
                            + "公司\", \"phone\": \"13900000000\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(resp).path("data").path("id").asLong();
  }

  /** 建一个产品（产品本身是 ADMIN-only，权限探针只需要一个可定价的对象）。 */
  private long createProduct(String adminToken) throws Exception {
    String resp =
        mockMvc
            .perform(
                post("/api/v1/products")
                    .header("Authorization", bearer(adminToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"code\": \"P-PW-001\", \"name\": \"权限探针产品\", \"unit\": \"套\","
                            + " \"standardPrice\": 100000}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(resp).path("data").path("id").asLong();
  }

  /** 建一个指定角色的用户并返回其 id（共享类探针需要一个真实的"被共享人"）。 */
  private long userIdFor(String adminToken, String username, String role) throws Exception {
    String resp =
        mockMvc
            .perform(
                post("/api/v1/users")
                    .header("Authorization", bearer(adminToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"username\": \""
                            + username
                            + "\", \"password\": \""
                            + PASSWORD
                            + "\", \"displayName\": \""
                            + role
                            + "探针\", \"role\": \""
                            + role
                            + "\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(resp).get("data").get("id").asLong();
  }

  /** 用管理员令牌建一个指定角色的用户，再以该用户登录取令牌。 */
  private String tokenFor(String adminToken, String username, String role) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/users")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"username\": \""
                        + username
                        + "\", \"password\": \""
                        + PASSWORD
                        + "\", \"displayName\": \""
                        + role
                        + "探针\", \"role\": \""
                        + role
                        + "\"}"))
        .andExpect(status().isCreated());
    return loginAndGetToken(username, PASSWORD);
  }
}
