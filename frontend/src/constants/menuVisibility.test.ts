import { describe, expect, it } from 'vitest'
import { MENU_MANIFEST, type MenuManifestGroup } from './menuManifest'
import { allMenuKeys, resolveVisibleMenuKeys } from './menuVisibility'

/**
 * 侧边栏可见性判定的行为约束（084 US1，FR-N01–N04）。
 *
 * <p><b>为什么必须是纯函数用例</b>：这条规则原先写在 `App.tsx` 的渲染表达式里，任何断言都得
 * 走一遍 React 渲染，于是没人断言——「管理员勾了、用户看不到」的故障就这么活了很久。
 * 现在规则可注入清单、可直接调用，单测才能把四条需求逐条钉死。
 *
 * <p><b>不读文件</b>：读 `RoleConstants.java` / 迁移的事归后端护栏
 * （`MenuRouteAlignmentTest`、`MenuAccessGrantAlignmentTest`）——前端测试跑在 jsdom 下，
 * 既没有 `file:` 协议的 `import.meta.url` 也没装 `@types/node`（与 `menuKeys.test.ts` 同一取舍）。
 * 本文件用**虚构的小清单**构造全部边界，另有 `MENU_MANIFEST` 的真实规模断言防「清单退化成空」。
 */
const fixture: readonly MenuManifestGroup[] = [
  { title: '首页', i18nKey: 'home', items: [{ menuKey: 'stats', i18nKey: 'home', title: '首页' }] },
  {
    title: '客户管理',
    i18nKey: 'customer',
    items: [{ menuKey: 'customers', i18nKey: 'customers', title: '客户' }],
  },
  {
    // 改造前这一组被 `App.tsx` 的 `isAdmin` 硬门整组挡掉——本组的用例是 FR-N01 的核心
    title: '系统管理',
    i18nKey: 'admin',
    items: [
      { menuKey: 'users', i18nKey: 'users', title: '用户管理' },
      { menuKey: 'roles', i18nKey: 'roles', title: '角色管理' },
    ],
  },
]

describe('resolveVisibleMenuKeys', () => {
  it('FR-N01：非 ADMIN 角色的可见集合恒等于其授权集合——即便授权落在「系统管理」组', () => {
    // 这正是 V75 的真实形态：SALES_MANAGER 被授了 users/roles/departments，
    // 而改造前这三项被整组硬门挡掉，界面上永远不出现。
    const visible = resolveVisibleMenuKeys('SALES_MANAGER', ['users', 'roles', 'customers'], fixture)
    expect([...visible].sort()).toEqual(['customers', 'roles', 'users'])
  })

  it('FR-N01：不认角色名——虚构角色同样按授权放行（没有按角色名的整组开关）', () => {
    // 「未来新增的角色」也必须成立：一旦有人加回 `role === 'X'` 的分支，此用例变红。
    const visible = resolveVisibleMenuKeys('AUDITOR_V2', ['users'], fixture)
    expect([...visible]).toEqual(['users'])
  })

  it('FR-N01：同一份授权给不同角色，可见集合相同', () => {
    const granted = ['customers', 'roles']
    const a = resolveVisibleMenuKeys('SUPPORT_MANAGER', granted, fixture)
    const b = resolveVisibleMenuKeys('VIEWER', granted, fixture)
    expect([...a].sort()).toEqual([...b].sort())
  })

  it('FR-N02：授权集合 ⊆ 可渲染集合——清单里没有的键不会变成可见项', () => {
    const visible = resolveVisibleMenuKeys('ANALYST', ['users', 'no-such-menu-key'], fixture)
    expect(visible.has('no-such-menu-key')).toBe(false)
    for (const key of visible) expect(allMenuKeys(fixture).has(key)).toBe(true)
  })

  it('FR-N03：既不放大也不缩小——授予的每一项都在，未授予的一项都不在', () => {
    // T002 基准里 ANALYST 的 7 项授权（approvals/custom-fields/custom-objects/data-vision/
    // exports/reports/stats）中，落在本小清单内的只有 stats。真实 13 个角色的逐角色比对见
    // verification.md §T010 与后端 MenuAccessGrantAlignmentTest（后者读真实迁移文件）。
    const visible = resolveVisibleMenuKeys('ANALYST', ['stats'], fixture)
    expect([...visible]).toEqual(['stats'])
    expect(visible.has('customers')).toBe(false)
    expect(visible.has('users')).toBe(false)
  })

  it('FR-N04：ADMIN 恒为全量，且不依赖授权数据是否齐全', () => {
    const full = [...allMenuKeys(fixture)].sort()
    expect([...resolveVisibleMenuKeys('ADMIN', [], fixture)].sort()).toEqual(full)
    expect([...resolveVisibleMenuKeys('ADMIN', undefined, fixture)].sort()).toEqual(full)
    // 真实清单规模：防止「清单退化成空」时上述断言静默通过
    expect(allMenuKeys().size).toBe(56)
  })

  it('缺授权数据（非 ADMIN）时可见集合为空，而不是全量', () => {
    // 保守方向：宁可少显示，也不要把「数据缺失」当作「全部授权」。
    expect(resolveVisibleMenuKeys('SALES', undefined, fixture).size).toBe(0)
    expect(resolveVisibleMenuKeys(undefined, undefined, fixture).size).toBe(0)
  })

  it('真实的生成物清单：56 项 / 11 组，键唯一', () => {
    expect(MENU_MANIFEST).toHaveLength(11)
    expect(allMenuKeys().size).toBe(56)
  })
})
