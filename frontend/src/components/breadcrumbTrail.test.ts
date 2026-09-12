import { describe, expect, it } from 'vitest'
import { MENU_MANIFEST, type MenuManifestGroup } from '../constants/menuManifest'
import { menuKeyOf, pathOfMenuKey } from '../constants/menuKeys'
import { resolveTrail, topLevelItem } from './breadcrumbTrail'

/**
 * 面包屑解析的护栏（084 收口）。
 *
 * <p>改造前 `BreadcrumbNav.tsx` 自带两张手写表，没有任何测试覆盖，于是 `/opportunity-stages`
 * 在生成物与侧边栏里都在、唯独那两张表里没有 → 面包屑渲染成「首页 / 当前页面」而无人报警。
 * 本文件防的正是这一类：**第一条用例遍历整个清单**，任何新增项只要拿不到面包屑就会变红。
 *
 * <p>三条边界值得说明：
 * 1. 「新增一项即自动跟上」不能只靠遍历真实清单来证明——万一解析器其实是抄了一份恰好等价的表，
 *    遍历也会通过。故另有一条**虚构清单**的用例：往小清单里加一项，看它是否自己长出来。
 * 2. 置顶项（「首页」）在侧边栏是独立项，面包屑不该重复组名，判据取自清单的可观测形态，
 *    该形态由一条独立断言钉住（`topLevelItem` 的成立条件）。
 * 3. 详情页的「/xxx/:id 追加详情」与「`/customers/at-risk` 不追加」必须一起断言：
 *    这两个路径都以 `/customers/` 开头，只测前者的写法会把「最长前缀优先」这条失效掉而不自知。
 */
describe('resolveTrail', () => {
  it('清单里每一项都能解析出「分组 + 项」，没有一项落到兜底段', () => {
    const items = MENU_MANIFEST.flatMap((group) =>
      group.items.map((item) => ({ group, item })),
    )
    // 防假绿：清单退化成空时下面的循环会「零违规通过」
    expect(MENU_MANIFEST).toHaveLength(11)
    expect(items).toHaveLength(56)

    const missing: string[] = []
    for (const { group, item } of items) {
      const trail = resolveTrail(pathOfMenuKey(item.menuKey))
      if (trail.some((segment) => segment.kind === 'unmatched')) {
        missing.push(`${item.menuKey}（${item.title}）落到了兜底段`)
        continue
      }
      const isTopLevel = group.items.length === 1 && item.i18nKey === group.i18nKey
      if (isTopLevel) {
        // 置顶项（「首页」）：单独一段，不重复组名
        expect(trail.map((segment) => segment.kind)).toEqual(['home'])
        continue
      }
      const kinds = trail.map((segment) => segment.kind)
      expect(kinds, `${item.menuKey} 的分段形态`).toEqual(['home', 'group', 'item'])
      expect(trail[1]).toMatchObject({ kind: 'group', i18nKey: group.i18nKey, title: group.title })
      expect(trail[2]).toMatchObject({
        kind: 'item',
        i18nKey: item.i18nKey,
        title: item.title,
        path: pathOfMenuKey(item.menuKey),
      })
    }

    expect(missing).toEqual([])
  })

  it('清单新增一项即自动获得面包屑（用虚构清单证明是派生而非另抄一份表）', () => {
    const fictional: MenuManifestGroup[] = [
      {
        title: '首页',
        i18nKey: 'home',
        items: [{ menuKey: 'stats', i18nKey: 'home', title: '首页' }],
      },
      {
        title: '虚构组',
        i18nKey: 'fake',
        items: [
          { menuKey: 'fake-thing', i18nKey: 'fakeThing', title: '虚构项' },
          { menuKey: 'nested/fake-thing', i18nKey: 'nestedFakeThing', title: '嵌套虚构项' },
        ],
      },
    ]

    expect(resolveTrail('/nested/fake-thing', fictional)).toEqual([
      { kind: 'home', current: false, path: '/stats', i18nKey: 'home', title: '首页' },
      { kind: 'group', i18nKey: 'fake', title: '虚构组' },
      {
        kind: 'item',
        i18nKey: 'nestedFakeThing',
        title: '嵌套虚构项',
        path: '/nested/fake-thing',
      },
    ])
    // 虚构清单里没有的路径照样走兜底段——否则「解析出了东西」可能只是匹配了什么都能中的规则
    expect(resolveTrail('/not-in-fictional', fictional).map((s) => s.kind)).toEqual([
      'home',
      'unmatched',
    ])
  })

  it('商机阶段：/opportunity-stages 显示为「首页 / 流程配置 / 商机阶段」（本次修复的回归点）', () => {
    // 改造前这张表里没有 /opportunity-stages → 渲染成「首页 / 当前页面」
    expect(resolveTrail('/opportunity-stages')).toEqual([
      { kind: 'home', current: false, path: '/stats', i18nKey: 'home', title: '首页' },
      { kind: 'group', i18nKey: 'config', title: '流程配置' },
      {
        kind: 'item',
        i18nKey: 'opportunityStages',
        title: '商机阶段',
        path: '/opportunity-stages',
      },
    ])
  })

  it('分组取自清单，不再与侧边栏各说各话', () => {
    // 三条原先归在「系统管理」组下的配置类页面，侧边栏是「流程配置」
    for (const path of ['/workflows', '/sla-policies', '/custom-objects']) {
      expect(resolveTrail(path)[1]).toMatchObject({ kind: 'group', i18nKey: 'config' })
    }
    // 数据保留原先归「数据分析」，权威定义与侧边栏都是「审计维护」
    expect(resolveTrail('/data-retention')[1]).toMatchObject({
      kind: 'group',
      i18nKey: 'audit',
      title: '审计维护',
    })
    // 数据大屏原先归「基础资料」，权威定义与侧边栏都是「数据分析」
    expect(resolveTrail('/data-vision')[1]).toMatchObject({
      kind: 'group',
      i18nKey: 'data',
      title: '数据分析',
    })
  })

  it('项文案取自清单的 menu.* 键——不再出现中文裸字面量', () => {
    // '/data-vision' 原先在映射表里直接写着 '酷炫大屏'：英文界面下仍显示中文
    expect(resolveTrail('/data-vision')[2]).toMatchObject({
      kind: 'item',
      i18nKey: 'dataVision',
      title: '数据大屏',
    })
    expect(resolveTrail('/tags')[2]).toMatchObject({ kind: 'item', i18nKey: 'tags' })
  })

  it('详情页追加「详情」，且最长前缀优先（/customers/at-risk 不追加）', () => {
    expect(resolveTrail('/customers/123').map((s) => s.kind)).toEqual([
      'home',
      'group',
      'item',
      'detail',
    ])
    expect(resolveTrail('/quotas/9/breakdown').map((s) => s.kind)).toEqual([
      'home',
      'group',
      'item',
      'detail',
    ])
    // `/customers` 也是候选，但 `/customers/at-risk` 更长——它自己是一个菜单项，不是详情页
    expect(resolveTrail('/customers/at-risk')).toEqual([
      { kind: 'home', current: false, path: '/stats', i18nKey: 'home', title: '首页' },
      { kind: 'group', i18nKey: 'customer', title: '客户管理' },
      { kind: 'item', i18nKey: 'atRisk', title: '流失预警', path: '/customers/at-risk' },
    ])
  })

  it('置顶项 `/stats` 只有一段，且不渲染成指向自身的链接', () => {
    expect(resolveTrail('/stats')).toEqual([
      { kind: 'home', current: true, path: '/stats', i18nKey: 'home', title: '首页' },
    ])
    // 置顶项没有下钻页面：`/stats/xxx` 不应被判成「首页 > 详情」
    expect(resolveTrail('/stats/anything').map((s) => s.kind)).toEqual(['home', 'unmatched'])
  })

  it('不属于任何菜单项的路径走兜底段（保持改造前行为）', () => {
    expect(resolveTrail('/account/password')).toEqual([
      { kind: 'home', current: false, path: '/stats', i18nKey: 'home', title: '首页' },
      { kind: 'unmatched' },
    ])
  })

  it('「借分组显示」的子页面挂在所借分组下，文案取自 menu.* 键', () => {
    // 锚点取自 COARSE_ALIASES（`/marketing/roi → marketing`），不是此处再写一遍
    expect(resolveTrail('/marketing/roi')).toEqual([
      { kind: 'home', current: false, path: '/stats', i18nKey: 'home', title: '首页' },
      { kind: 'group', i18nKey: 'marketing', title: '营销管理' },
      { kind: 'item', i18nKey: 'channelRoi', title: '渠道 ROI', path: '/marketing/roi' },
    ])
    expect(resolveTrail('/workflows/logs')).toEqual([
      { kind: 'home', current: false, path: '/stats', i18nKey: 'home', title: '首页' },
      { kind: 'group', i18nKey: 'config', title: '流程配置' },
      { kind: 'item', i18nKey: 'workflowLogs', title: '工作流日志', path: '/workflows/logs' },
    ])
  })
})

describe('菜单 key 与路由 path 的往返', () => {
  it('每个清单键推出的路径都能翻译回它自己（at-risk 这条例外由本断言钉住）', () => {
    const mismatched = MENU_MANIFEST.flatMap((group) => group.items)
      .map((item) => ({
        menuKey: item.menuKey,
        path: pathOfMenuKey(item.menuKey),
        back: menuKeyOf(pathOfMenuKey(item.menuKey)),
      }))
      .filter((row) => row.menuKey !== row.back)

    expect(mismatched).toEqual([])
    // 例外只有一条：菜单项键是 at-risk，而路由挂在 /customers/ 下
    expect(pathOfMenuKey('at-risk')).toBe('/customers/at-risk')
    expect(pathOfMenuKey('settings/custom-fields')).toBe('/settings/custom-fields')
  })
})

describe('置顶组的判据（resolveTrail 依赖它的成立条件）', () => {
  it('清单里符合「单成员且成员与组同名」形态的分组恰好一个', () => {
    const shaped = MENU_MANIFEST.filter(
      (group) => group.items.length === 1 && group.items[0].i18nKey === group.i18nKey,
    )
    expect(shaped.map((group) => group.i18nKey)).toEqual(['home'])
    expect(topLevelItem(MENU_MANIFEST)).toEqual({
      path: '/stats',
      i18nKey: 'home',
      title: '首页',
    })
    // 反向：非置顶形态的清单里没有置顶项，此时面包屑退化为「分组 + 项」而不是抛错
    expect(topLevelItem(MENU_MANIFEST.filter((group) => group.i18nKey !== 'home'))).toBeNull()
  })
})
