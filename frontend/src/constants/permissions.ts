/**
 * 前端用到的权限码登记表。
 *
 * <p><b>权威来源是后端 `com.crm.common.RoleConstants.PERMISSION_DEFS`</b>（以及各 Controller 方法上的
 * `@RequirePermission("...")`）。这里的每一条都必须能在那个字典里找到，改名前先回去对一遍。
 *
 * <p><b>写错一个码是「静默失效」而不是报错</b>：`hasPerm` 走的是
 * `user.permissions.includes(code)`，字典里不存在的码永远不会被授给任何角色（角色页根本勾不出来），
 * 于是这个码对除 ADMIN 以外的所有人恒为 `false`——按钮无声无息地消失，没有任何报错、没有 403、
 * 控制台也不会有任何提示。所以往这里加码之前，必须先在 `PERMISSION_DEFS` 里确认它存在。
 *
 * <p><b>不需要写「或者 ADMIN」</b>：`hasPerm` 对 `role === 'ADMIN'` 直接短路返回 true，
 * 所以每个码天然对管理员放行，调用点不要写成 `isAdmin || hasPerm(...)`——那是改造前的旧写法，
 * 而且在 081 扩了角色之后会漏掉真正的授权角色。
 *
 * <p><b>刻意只登记「前端真的在用它做 gating」的码</b>：前端调用了某个接口但没做 gating 的
 * （如客户导出 `customer:export`、订单创建 `order:create`）不在此列——登记不使用的码，等于给自己
 * 多留几个写错了也看不出来的名字。
 */

/** 权限码集合。键是实体 + 动作，值是后端字典里的 `实体:动作` 码。 */
export const PERMS = {
  // ---- 客户管理（CustomerController / CustomerPoolController） ----
  /** 创建客户：POST /api/v1/customers。 */
  customerCreate: 'customer:create',
  /** 编辑客户：PUT /api/v1/customers/{id}。 */
  customerUpdate: 'customer:update',
  /** 删除客户：DELETE /api/v1/customers/{id}。 */
  customerDelete: 'customer:delete',
  /** 导入客户 / 下载导入模板：POST /customers/import、GET /customers/import-template。 */
  customerImport: 'customer:import',
  /**
   * 领取公海客户：POST /customers/pool/{id}/claim（1.5 批 3 新码）。
   *
   * <p>改造前该端点**没有任何闸门**，于是「领取」按钮按 `view === 'pool'` 无条件渲染——谁点都行。
   * 接线后只有销售三角色持有本码，按钮必须跟着收，否则非销售角色会看到一个必然 403 的死按钮。
   */
  customerClaim: 'customer:claim',
  /**
   * 公海扫描 / 批量转移：POST /customers/pool/scan、POST /customers/batch-transfer（1.5 批 3 新码）。
   *
   * <p>「扫描」按钮、「转移」按钮与行选择（转移的前置）都按本码判断——两个端点挂的是同一个码，
   * 界面上就不该出现第二个判据。字典里**没有任何角色被授**（刻意不复用 `customer:transfer`：
   * 批转移不校验调用者是否拥有这些客户、也无数据范围过滤），于是效果是"仅管理员可见、可用"，
   * 与后端放行范围一致。
   *
   * <p>`customer:transfer` **刻意不登记**：它描述的是转移归属，且已被授给 SALES / SALES_MANAGER
   * （V46 / V75），但后端**没有任何端点校验它**——拿它 gating 只会让这些角色看到一个必然点不动的
   * 按钮（该端点的码其实是 pool_manage）。本文件只登记真的在用、且真的对得上端点的码。
   */
  customerPoolManage: 'customer:pool_manage',

  // ---- 合同管理（ContractController） ----
  /** 审批合同（通过 / 驳回）：POST /contracts/{id}/approve、/reject。 */
  contractApprove: 'contract:approve',

  // ---- 报价单管理（QuoteController） ----
  /** 审批报价单（通过 / 驳回）：POST /quotes/{id}/approve、/reject。 */
  quoteApprove: 'quote:approve',

  // ---- 线索管理（LeadController） ----
  /** 分配线索（含「分配给我」）：POST /leads/{id}/assign。 */
  leadAssign: 'lead:assign',

  // ---- 订单管理（OrderController） ----
  /** 删除订单：DELETE /api/v1/orders/{id}。 */
  orderDelete: 'order:delete',

  // ---- 产品管理（ProductController） ----
  /**
   * 产品增删改：POST /api/v1/products、PUT|DELETE /api/v1/products/{id}（1.5 批 3 起挂码）。
   *
   * <p>三个码的授予范围**不一样**，这正是要分开 gating 的原因：create / delete 只有
   * MARKETING_MANAGER 持有（销售角色没有），而 update 还授给了 SALES / SALES_MANAGER / SALES_REP /
   * MARKETING_SPECIALIST（V83 为产品定价授出）——所以持有「产品」菜单的销售角色能编辑、不能新建/删除。
   */
  productCreate: 'product:create',
  productUpdate: 'product:update',
  productDelete: 'product:delete',

  // ---- 营销活动（MarketingController） ----
  /** 创建活动：POST /api/v1/campaigns（1.5 批 3 起；三个码同批授给 SALES）。 */
  campaignCreate: 'campaign:create',
  /** 编辑 / 启动 / 结束活动：PUT /campaigns/{id}、POST /campaigns/{id}/start、/end。 */
  campaignUpdate: 'campaign:update',
  /** 删除活动：DELETE /api/v1/campaigns/{id}。 */
  campaignDelete: 'campaign:delete',

  // ---- 系统管理（RoleController） ----
  /** 角色权限的增删改：POST/PUT/DELETE /api/v1/roles。 */
  roleManage: 'role:manage',
} as const

export type PermCode = (typeof PERMS)[keyof typeof PERMS]
