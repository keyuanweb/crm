package com.crm.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作权限校验注解（028-role-permissions，FR-006）：由 PermissionAspect 校验当前用户角色是否含指定权限码； ADMIN 恒放行。
 *
 * <p><b>该标注在哪些方法上（1.5 明确的口径）</b>——此前只有「标注关键写操作方法」一句，实践中几个 Controller
 * 给读接口也标了，又没有一致的判据，于是「哪些读接口该设防」变成了各人随手决定的事。现在的规则是：
 *
 * <ul>
 *   <li><b>写操作一律标注</b>：create / update / delete / import / export，以及推进状态机的动作 （submit / approve /
 *       reject / effective / terminate / assign / reply / transition / convert / check-in …）。
 *       这类动作的码用字典里既有的实体动作码。
 *   <li><b>读操作默认不标注</b>：登录即可读，靠菜单可见性与数据范围（{@code DataPermissionService}）限制。
 *       加一层码在这里通常是多余的——菜单本就是按码/键下发的，重复设防只会让「管理员勾了却还是 403」。
 *   <li><b>读操作的例外</b>：出参含 PII/财务数据、且该查询**没有数据范围过滤**的（发票列表含税号）、
 *       以及纯管理面的配置读（角色、字段权限、用户且非选人用途）。这类读使用独立的 {@code <实体>:read}
 *       码，而**不是**复用写码——复用会让「能看」与「能改」被绑成同一个集合， 于是「只读用户」这种角色要么看不到、要么连带拿到写权限。
 * </ul>
 *
 * <p><b>与 {@code @PreAuthorize("hasAnyRole(...)")} 的关系</b>：本仓库历史上两层授权并存，粗粒度的一层 只认
 * ADMIN/SALES/SUPPORT 三个内建角色名，于是 081 新增的 10 个角色（SALES_MANAGER、SUPPORT_AGENT、 FINANCE_* 等）在 33 个
 * Controller 上被整体挡在门外，而角色页的权限矩阵却声称它们有权限——这正是 1.5 要消灭的那类「矩阵上写了、实际拿不到」。方向是让本注解成为唯一闸门，粗粒度那层逐步退场。
 *
 * <p>⚠️ 新增标注前必须先核对 {@code role_permission} 里目标角色是否已有该码：字典里有码 ≠ 有角色拿得到， 而 {@code
 * RoleService.replacePermissions} 是先删后插，漏授的码不会报错、只会在管理员保存角色时静默消失。 {@code
 * RequirePermissionCatalogTest}（注解 ⊆ 字典）与 {@code PermissionMatrixIT}（授予 ⊆ 字典）是这层护栏。
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {

  /** 权限码（如 customer:delete、order:payment、user:manage）。 */
  String value();
}
