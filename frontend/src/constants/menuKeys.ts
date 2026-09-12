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

/**
 * 菜单 key → 规范路由 path（`menuKeyOf` 的逆方向），**只登记默认规则不成立的键**。
 *
 * <p>默认规则 `'/' + menuKey` 对 56 项里的 55 项成立，`at-risk` 是唯一例外：菜单里这一项
 * 就叫 `at-risk`（挂在「客户管理」组下），而它的路由是 `/customers/at-risk`——键与路径段数不同，
 * `COARSE_ALIASES` 里已有同一条事实的正方向（`'/customers/at-risk': 'at-risk'`），
 * 两条放在同一文件是刻意的：分开写必然有一天只改一边，届时面包屑会链到一个 404 的地址。
 *
 * <p>**不得**把 `COARSE_ALIASES` 里另外两条（`/marketing/roi`、`/workflows/logs`）搬进来：
 * 它们的目标键 `marketing`/`workflows` 各自有默认路径，那两条是「借分组显示」的子页面而不是菜单项本身
 * （后端 `MenuRouteAlignmentTest.subPageBorrowersStayExactlyTwo` 已把两张表钉成恰好这两条）。
 *
 * <p>本表的正确性有两条护栏：前端 `breadcrumbTrail.test.ts` 断言每个清单键的往返
 * （`menuKeyOf(pathOfMenuKey(k)) === k`），后端 `MenuRouteAlignmentTest` 断言推出的路径
 * **是一条真实声明的菜单路由**——后者能把「清单新增项的路由不是 `/${menuKey}` 却忘了在此登记」抓出来。
 */
const CANONICAL_PATH_OVERRIDES: Record<string, string> = {
  'at-risk': '/customers/at-risk',
}

/**
 * 取某个菜单项的路由 path。
 *
 * @param menuKey `MENU_TREE` 里的菜单 key（如 `settings/custom-fields`）
 */
export function pathOfMenuKey(menuKey: string): string {
  return CANONICAL_PATH_OVERRIDES[menuKey] ?? '/' + menuKey
}
