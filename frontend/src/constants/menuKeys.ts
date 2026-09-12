/**
 * 路由 path → 服务端菜单 key（一期 1.5）。
 *
 * <p><b>权威在后端</b>：菜单 key 的唯一来源是 `backend/src/main/java/com/crm/common/RoleConstants.java`
 * 的 `MENU_TREE`——它既是角色页勾选项的渲染源，也是登录后下发给前端的 `user.menus` 的内容。
 * 本文件只是把前端的路由 path 翻译成那套 key，两边必须**逐字**对齐：拼错一个字符的后果不是报错，
 * 而是「管理员勾了、用户却看不到菜单」。
 *
 * <p><b>为什么默认实现就是去掉前导斜杠</b>：改造前这里是一张 40 条的手写映射表，把多段路径归并到某个
 * 粗粒度 key（`/invoices` → `orders`、`/visits` → `sales`、`/customer-merge` → `customers`）。
 * 那种归并看着省事，实际制造了两类静默故障：
 * 一是 `sales` 这个 key 在 `MENU_TREE` 里根本不存在，于是 `/visits` 对所有非 ADMIN 恒不可见；
 * 二是 `/invoices` 蹭 `orders`、`/customer-merge` 蹭 `customers`，让「看得见」与「有权限做」脱钩
 * （SUPPORT/VIEWER 长期看着一个点不动按钮的查重合并页）。
 * 现在只有两种情况才需要显式列出来，见 `COARSE_ALIASES`。
 */
const COARSE_ALIASES: Record<string, string> = {
  // 路由段数与菜单 key 不同：菜单里这一项就叫 at-risk（挂在「客户管理」组下）。
  '/customers/at-risk': 'at-risk',
  // 下面两条是真正的粗粒度降级：页面上有路由，MENU_TREE 里没有对应菜单项，
  // 只能挂到所属分组。这是**有意**的——它们本来就是分组内的子页面而不是独立菜单
  // （渠道 ROI 属于营销活动，工作流日志属于工作流）。副作用是拿到分组 key 的角色就能看到
  // 这两个页面，属于可接受的放宽。
  '/marketing/roi': 'marketing',
  '/workflows/logs': 'workflows',
}

/**
 * 取某条路由对应的菜单 key。
 *
 * @param path 路由 path，以 `/` 开头（如 `/settings/custom-fields`）
 */
export function menuKeyOf(path: string): string {
  return COARSE_ALIASES[path] ?? path.replace(/^\//, '')
}
