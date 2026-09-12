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
  /** 转移客户：POST /api/v1/customers/batch-transfer（后端当前仍是 ADMIN-only，见 CustomerListPage）。 */
  customerTransfer: 'customer:transfer',
  /** 导入客户 / 下载导入模板：POST /customers/import、GET /customers/import-template。 */
  customerImport: 'customer:import',

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

  // ---- 系统管理（RoleController） ----
  /** 角色权限的增删改：POST/PUT/DELETE /api/v1/roles。 */
  roleManage: 'role:manage',
} as const

export type PermCode = (typeof PERMS)[keyof typeof PERMS]
