import { describe, expect, it } from 'vitest'
import { menuKeyOf } from './menuKeys'

/**
 * `menuKeyOf` 的行为约束（一期 1.5）。
 *
 * <p>这里只钉住**纯函数行为**——尤其是那三条别名。跨语言的「路由 key 是否真的存在于后端
 * MENU_TREE」由后端 `MenuRouteAlignmentTest` 校验：那需要读 `RoleConstants.java`，
 * 而后端测试里读前端源文件比反过来方便（前端测试跑在 jsdom 下，没有 file: 协议的
 * `import.meta.url`，也没装 `@types/node`，读文件要先加依赖）。
 *
 * <p>两个测试各管一半、且都必须存在：本文件防「有人把别名删了/改错」，后端那个防
 * 「后端改了 MENU_TREE 的拼写」。
 */
describe('menuKeyOf', () => {
  it('默认取去掉前导斜杠的路径，与 MENU_TREE 的扁平命名一致', () => {
    expect(menuKeyOf('/stats')).toBe('stats')
    expect(menuKeyOf('/settings/custom-fields')).toBe('settings/custom-fields')
    expect(menuKeyOf('/stats/leaderboard')).toBe('stats/leaderboard')
    expect(menuKeyOf('/exports/scheduled')).toBe('exports/scheduled')
  })

  it('多段路径不再被归并到粗粒度 key（这正是 /visits 失效的原因）', () => {
    // 改造前 /visits → 'sales'，而 MENU_TREE 里没有 'sales' 这个 key，
    // 于是「外勤拜访」对所有非 ADMIN 角色恒不可见。
    expect(menuKeyOf('/visits')).toBe('visits')
    // /invoices 曾归并到 'orders'、/customer-merge 曾归并到 'customers'：
    // 让「看得见」与「有权限做」脱钩（SUPPORT/VIEWER 长期看着一个点不动按钮的页面）。
    expect(menuKeyOf('/invoices')).toBe('invoices')
    expect(menuKeyOf('/customer-merge')).toBe('customer-merge')
    expect(menuKeyOf('/marketing/email')).toBe('marketing/email')
    expect(menuKeyOf('/online-forms')).toBe('online-forms')
    expect(menuKeyOf('/approvals')).toBe('approvals')
  })

  it('只在「路由段数不同」和「MENU_TREE 无对应项」时才走别名', () => {
    expect(menuKeyOf('/customers/at-risk')).toBe('at-risk')
    expect(menuKeyOf('/marketing/roi')).toBe('marketing')
    expect(menuKeyOf('/workflows/logs')).toBe('workflows')
  })
})
