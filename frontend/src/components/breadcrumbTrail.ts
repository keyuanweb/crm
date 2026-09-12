/**
 * 面包屑的「当前路径 → 分段」解析（084 收口：分组与名称改由生成物派生）。
 *
 * <p><b>为什么要有这个文件</b>：`BreadcrumbNav.tsx` 原先自带两张手写表——`GROUPED_ROUTES`
 * （7 组、56 条）与 `MENU_KEY_MAP`（路径 → 文案键）。它们是菜单结构的**第五份副本**，
 * 且不在 084 那三道护栏（`pnpm menu:check`、`pnpm i18n:check`、后端 `MenuRouteAlignmentTest`）
 * 的任何一道管辖范围内。后果实测到了：`/opportunity-stages` 在生成物与侧边栏里都存在，
 * 两张手写表里都没有 → 匹配落空 → 面包屑渲染成「首页 / 当前页面」，**没有任何断言会变红**。
 * 同一份表的另外两处症状是组名自成分歧（配置类页面面包屑写「系统管理」、侧边栏写「流程配置」，
 * 而「流程配置」在那份表的词汇表里根本不存在）与 `'/data-vision': '酷炫大屏'` 这个裸中文字面量
 * （英文界面下仍显示中文）。
 *
 * <p>现在分组、顺序、名称一律取自生成物 `MENU_MANIFEST`，路径由 `pathOfMenuKey` 推出；
 * 只有「借分组显示的子页面」（有路由、不占菜单位，权威定义里没有对应项）另立一张**仅含文案**的小表。
 * 「清单新增一项 ⇒ 面包屑自动跟上」由 `breadcrumbTrail.test.ts` 钉住。
 *
 * <p><b>为什么不从 `App.tsx` 引入它身上的两处事实</b>：置顶分组（`TOP_LEVEL_GROUP_I18N_KEYS`）
 * 与子页面的锚点（`SUB_PAGE_AFTER_MENU_KEY`）都在 `App.tsx` 里，而它 import 本组件——反向 import 成环。
 * 前者改由清单的**可观测形态**推出（见 `topLevelItem`），后者的锚点直接取 `menuKeys.ts` 的
 * `COARSE_ALIASES`（后端 `MenuRouteAlignmentTest` 已断言那两条与 `App.tsx` 的表逐条一致，
 * 故锚点仍然只有一个作者）。
 *
 * <p><b>为什么判定放在这里而不是组件里</b>：这条规则原先埋在 JSX 的 `useMemo` 里，
 * 于是「哪个路径该显示什么」只能靠读渲染代码推断，也没有任何单测能覆盖它。抽成纯函数后，
 * 上面那个 bug 的形态（一组典型的路径 → 分段）可以直接写成断言。
 */
import { MENU_MANIFEST, type MenuManifestGroup } from '../constants/menuManifest'
import { menuKeyOf, pathOfMenuKey } from '../constants/menuKeys'

/** 面包屑里一段的名称（分组名不可点，与改造前一致）。 */
export interface TrailNamedSegment {
  /** `menu` 分组的文案键，**不含** `menu.` 前缀 */
  readonly i18nKey: string
  /** 权威中文名——缺文案时由调用方降级显示（见 `i18n/labelOf.ts`） */
  readonly title: string
}

/**
 * 面包屑的一段。用 `kind` 区分而不是让调用方猜：组件只做一次 switch，
 * 「哪条路径产生哪些段」全部由本模块决定、并被单测钉住。
 */
export type TrailSegment =
  /** 「首页」——清单里的置顶项。`current` 为真表示它**就是**当前页（`/stats`），渲染成纯文本而非链接。 */
  | { readonly kind: 'home'; readonly i18nKey: string; readonly title: string; readonly path: string; readonly current: boolean }
  /** 所属分组名。 */
  | { readonly kind: 'group'; readonly i18nKey: string; readonly title: string }
  /** 当前页面项。 */
  | { readonly kind: 'item'; readonly i18nKey: string; readonly title: string; readonly path: string }
  /** 「详情」段：当前路径比匹配到的项更深一层（如 `/quotes/123`）。 */
  | { readonly kind: 'detail' }
  /** 兜底段：路径不属于任何菜单项（如 `/account/password`）。 */
  | { readonly kind: 'unmatched' }

/**
 * 「借分组显示」的子页面在面包屑里的**文案**。
 *
 * <p>它们有路由、不占菜单位，因此不在生成物里，文案键只能在这里登记。**归属的分组不在这里**——
 * 由 `menuKeys.ts` 的 `COARSE_ALIASES` 决定（`/marketing/roi → marketing`），
 * 那张表与 `App.tsx` 的 `SUB_PAGE_AFTER_MENU_KEY` 逐条一致已由后端
 * `MenuRouteAlignmentTest.subPageBorrowersStayExactlyTwo` 断言，故锚点只有 `COARSE_ALIASES` 一个作者。
 *
 * <p>文案键取自 `menu.*`（`channelRoi` / `workflowLogs` 在两个语言文件里都有），缺键时降级为这里的中文名。
 */
const SUB_PAGE_LABELS: Record<string, TrailNamedSegment> = {
  '/marketing/roi': { i18nKey: 'channelRoi', title: '渠道 ROI' },
  '/workflows/logs': { i18nKey: 'workflowLogs', title: '工作流日志' },
}

/** 一个可匹配的候选：一条路由 path 及其所属分组与名称。 */
interface Candidate {
  readonly path: string
  readonly group: TrailNamedSegment
  readonly item: TrailNamedSegment & { readonly path: string }
  /**
   * 只接受精确匹配。
   *
   * <p>置顶项（「首页」）在侧边栏里是独立项、没有下钻页面，故 `/stats/xxx` 不应被判成
   * 「首页 > 详情」——那样会凭空造出一个不存在的层级。改造前 `/stats` 不在那张手写表里，
   * 这类路径一律走兜底段，此处保持同一结果。
   */
  readonly exactOnly: boolean
}

/**
 * 判定一个分组是否是**置顶组**（成员以独立项渲染、没有分组头）。
 *
 * <p>权威定义里没有「置顶」这个概念（角色页照样把「首页」当普通分组勾选），这是表现层选择。
 * 本判据不新增第二份「哪些组置顶」的清单，而是取清单的**可观测形态**：置顶组恰好是
 * 「单成员 **且** 该成员的文案键与组同名」的那一个组。
 *
 * <p>该形态由两道既有/新增护栏共同钉住：后端 `MenuRouteAlignmentTest.noItemEchoesItsGroupName`
 * 断言**非**置顶组里不得出现与组同名的项（项名与组名并排会让侧边栏出现「客户服务 > 客户服务」），
 * 本模块的单测断言清单里恰好只有这一个分组符合该形态。若将来权威处真新增一个符合该形态的分组，
 * 两条护栏会同时变红——届时正确的修法是把「哪些组置顶」提升为清单里的显式字段（改生成器），
 * 而不是放宽这里。
 */
function isTopLevelShape(group: MenuManifestGroup): boolean {
  return group.items.length === 1 && group.items[0].i18nKey === group.i18nKey
}

/**
 * 置顶项（「首页」）的路径与名称；清单里没有符合置顶形态的分组时返回 `null`。
 *
 * <p>导出是为了让单测能直接断言「清单里恰好只有这一个符合置顶形态」——上面那条判据的成立条件。
 */
export function topLevelItem(
  manifest: readonly MenuManifestGroup[] = MENU_MANIFEST,
): (TrailNamedSegment & { readonly path: string }) | null {
  for (const group of manifest) {
    if (!isTopLevelShape(group)) continue
    const only = group.items[0]
    return { path: pathOfMenuKey(only.menuKey), i18nKey: only.i18nKey, title: only.title }
  }
  return null
}

/** 由清单与子页面表构造候选集。清单有改动时这里自动跟上，无需第二张表。 */
function buildCandidates(manifest: readonly MenuManifestGroup[]): Candidate[] {
  const groupOfKey = new Map<string, TrailNamedSegment>()
  for (const group of manifest) {
    for (const item of group.items) {
      groupOfKey.set(item.menuKey, { i18nKey: group.i18nKey, title: group.title })
    }
  }

  const candidates: Candidate[] = []
  for (const group of manifest) {
    const named: TrailNamedSegment = { i18nKey: group.i18nKey, title: group.title }
    for (const item of group.items) {
      const path = pathOfMenuKey(item.menuKey)
      candidates.push({
        path,
        group: named,
        item: { path, i18nKey: item.i18nKey, title: item.title },
        exactOnly: isTopLevelShape(group),
      })
    }
  }
  for (const [path, label] of Object.entries(SUB_PAGE_LABELS)) {
    // 锚点取自 COARSE_ALIASES（不是这里再写一遍）：查不到说明该子页面在 `MENU_TREE` 里没有对应项，
    // 此时**跳过**——它会落到下面「按前缀匹配到父项」的路径上，而不是静默造一个空分组。
    const anchor = groupOfKey.get(menuKeyOf(path))
    if (!anchor) continue
    candidates.push({ path, group: anchor, item: { path, ...label }, exactOnly: false })
  }
  return candidates
}

/**
 * 解出当前路径的面包屑分段。
 *
 * @param pathname `location.pathname`
 * @param manifest 菜单清单，默认取生成物；显式传入是为了让单测能构造小清单并证明
 *   「新增一项即自动获得面包屑」（否则「解析得对」可能只是恰好与一份抄来的表一致）
 */
export function resolveTrail(
  pathname: string,
  manifest: readonly MenuManifestGroup[] = MENU_MANIFEST,
): TrailSegment[] {
  let matched: Candidate | null = null
  for (const candidate of buildCandidates(manifest)) {
    const hit = candidate.exactOnly
      ? pathname === candidate.path
      : pathname === candidate.path || pathname.startsWith(candidate.path + '/')
    // 最长前缀优先：`/customers/at-risk` 必须胜过 `/customers`，否则会显示成「客户 > 详情」。
    if (hit && (!matched || candidate.path.length > matched.path.length)) matched = candidate
  }

  const home = topLevelItem(manifest)
  const homeSegment = (current: boolean): TrailSegment | null =>
    home === null
      ? null
      : { kind: 'home', current, path: home.path, i18nKey: home.i18nKey, title: home.title }

  if (matched === null) {
    // 未匹配（如 /account/password）：改造前显示「首页 / 当前页面」，此处保持同一结果
    const lead = homeSegment(false)
    return lead === null ? [{ kind: 'unmatched' }] : [lead, { kind: 'unmatched' }]
  }

  if (matched.exactOnly) {
    // 置顶项自身（`/stats`）：改造前就是单个「首页」纯文本，且不再重复一次首页链接
    return [{ kind: 'home', current: true, path: matched.item.path, i18nKey: matched.item.i18nKey, title: matched.item.title }]
  }

  const segments: TrailSegment[] = []
  const lead = homeSegment(false)
  if (lead !== null) segments.push(lead)
  segments.push({ kind: 'group', i18nKey: matched.group.i18nKey, title: matched.group.title })
  segments.push({ kind: 'item', ...matched.item })
  // 深层路径追加「详情」。改造前这里是 `isDetail || isNestedDetail` 两个条件，但后者的
  // `DETAIL_SEGMENTS` 集合是前者的子集（凡 `path` 以 `/customers/` 开头，`/customers` 就必然
  // 已被匹配为某项，`isDetail` 已然成立），属不可达分支，故收成一个条件。
  // `breadcrumbTrail.test.ts` 对 `/customers/123` 与 `/customers/at-risk` 各有一条断言钉住这点。
  if (pathname !== matched.path) segments.push({ kind: 'detail' })
  return segments
}
