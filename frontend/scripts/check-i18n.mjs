#!/usr/bin/env node
/**
 * 语言资源一致性校验（一期 1.4 的防复发护栏）。
 *
 * <p>此前没有任何键对齐校验，而测试里 react-i18next 被全局 mock 成 `t(key) => key`：
 * 缺键既不报错、也不被任何测试发现，只表现为界面上渲染出一个键名字符串（如
 * `pages.ticket.list.colStatus`）。`pages.ticket.list.colStatus` 就是这么漂移的——en 有、zh-CN 没有。
 *
 * <p>本脚本把两个语言文件各自展平成点分键路径后逐项比对，任一方向的缺失都非零退出，
 * 并顺带报出空字符串值（空值等价于缺键，但更难肉眼发现）。
 *
 * <p>用法：`npm run i18n:check`（CI 已接入）。
 */
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
const LOCALES = [
  { name: 'zh-CN', file: resolve(here, '../src/i18n/zh-CN.ts'), constName: 'zhCN' },
  { name: 'en', file: resolve(here, '../src/i18n/en.ts'), constName: 'en' },
]

/**
 * 取出 `const xxx = { ... }` 里的对象字面量并求值。
 *
 * <p>两个语言文件都是纯字面量、无 import、无类型标注，因此取第一个 `{` 到最后一个 `}`
 * 即为对象体。不引入额外依赖（不新增 tsx/vite-node），CI 里最稳。
 */
function loadLocale({ name, file }) {
  const text = readFileSync(file, 'utf8')
  const start = text.indexOf('{')
  const end = text.lastIndexOf('}')
  if (start < 0 || end < start) {
    throw new Error(`${name}: 无法在 ${file} 中定位对象字面量`)
  }
  // eslint-disable-next-line no-new-func -- 见上方注释：纯字面量，且是本仓库受信任的源文件
  return new Function(`return (${text.slice(start, end + 1)})`)()
}

/** 展平为 `a.b.c -> value`；空对象本身不算键。 */
function flatten(obj, prefix = '', out = new Map()) {
  for (const [key, value] of Object.entries(obj)) {
    const path = prefix ? `${prefix}.${key}` : key
    if (value !== null && typeof value === 'object' && !Array.isArray(value)) {
      flatten(value, path, out)
    } else {
      out.set(path, value)
    }
  }
  return out
}

const maps = LOCALES.map((locale) => ({ ...locale, keys: flatten(loadLocale(locale)) }))
const [base, ...others] = maps
let failed = false

for (const other of others) {
  const missingInOther = [...base.keys.keys()].filter((k) => !other.keys.has(k)).sort()
  const missingInBase = [...other.keys.keys()].filter((k) => !base.keys.has(k)).sort()

  if (missingInOther.length > 0) {
    failed = true
    console.error(`\n✗ ${other.name} 缺少 ${missingInOther.length} 个键（${base.name} 有）：`)
    for (const key of missingInOther) console.error(`    ${key}`)
  }
  if (missingInBase.length > 0) {
    failed = true
    console.error(`\n✗ ${base.name} 缺少 ${missingInBase.length} 个键（${other.name} 有）：`)
    for (const key of missingInBase) console.error(`    ${key}`)
  }
}

/**
 * 有意留空的键（豁免名单）。空值与缺键对用户等价，但"中文要单位后缀、英文不要"这类
 * 差异只能用空串表达。逐条列出并写明理由，而不是关掉整个空值检查——
 * 否则将来真漏写的 `colX: ''` 也会被一起放过。
 */
const INTENTIONALLY_EMPTY = new Set([
  // 中文数字后要跟单位（个 / 元），英文数字后不加单位
  'pages.dashboard.funnel.count',
  'pages.dashboard.funnel.amountSuffix',
])

// 空值：与缺键等价，但 `colStatus: ''` 这种写法不会在键集合比对里暴露
for (const locale of maps) {
  const empty = [...locale.keys.entries()]
    .filter(([, value]) => typeof value === 'string' && value.trim() === '')
    .map(([key]) => key)
    .filter((key) => !INTENTIONALLY_EMPTY.has(key))
    .sort()
  if (empty.length > 0) {
    failed = true
    console.error(`\n✗ ${locale.name} 有 ${empty.length} 个空文案：`)
    for (const key of empty) console.error(`    ${key}`)
  }
}

if (failed) {
  console.error('\n语言资源不一致。请在两个文件里补齐后重跑 npm run i18n:check。')
  process.exit(1)
}

/**
 * 菜单路由覆盖检查（060 的菜单 i18n）。
 *
 * <p>`App.tsx` 用 `MENU_I18N_KEYS[path]` 把路由映射到 `menu.*` 键。若新增路由漏配映射，
 * 菜单会渲染出 `menu.<中文名>` 这样的字面量——**比不翻译更糟**，而且没有任何测试会发现
 * （菜单不在单测覆盖范围内）。这里把两件事钉死：每条路由都有映射、每个映射到的 `menu.*`
 * 键在两种语言里都存在。
 */
const APP = resolve(here, '../src/App.tsx')
const appText = readFileSync(APP, 'utf8')

/** 从 `const MENU_I18N_KEYS = {` 起按花括号配平截取映射块。 */
function menuKeyMap() {
  const anchor = appText.indexOf('const MENU_I18N_KEYS')
  if (anchor < 0) throw new Error('App.tsx 中找不到 MENU_I18N_KEYS')
  const open = appText.indexOf('{', anchor)
  let depth = 0
  let end = -1
  for (let i = open; i < appText.length; i++) {
    if (appText[i] === '{') depth++
    else if (appText[i] === '}') {
      depth--
      if (depth === 0) {
        end = i
        break
      }
    }
  }
  if (end < 0) throw new Error('MENU_I18N_KEYS 花括号不配平')
  return new Map(
    [...appText.slice(open, end + 1).matchAll(/'([^']+)':\s*'([^']+)'/g)].map((m) => [m[1], m[2]]),
  )
}

const menuMap = menuKeyMap()
const routePaths = [...appText.matchAll(/path:\s*'([^']+)',\s*name:\s*/g)].map((m) => m[1])
const unmappedRoutes = routePaths.filter((path) => !menuMap.has(path))
const missingMenuKeys = [...new Set(menuMap.values())]
  .filter((key) => !maps[0].keys.has(`menu.${key}`))
  .sort()

if (unmappedRoutes.length > 0) {
  console.error(`\n✗ App.tsx 有 ${unmappedRoutes.length} 条路由未配置 MENU_I18N_KEYS（菜单会渲染出 menu.<中文名>）：`)
  for (const path of unmappedRoutes) console.error(`    ${path}`)
}
if (missingMenuKeys.length > 0) {
  console.error(`\n✗ MENU_I18N_KEYS 引用了不存在的 menu.* 键（${missingMenuKeys.length}）：`)
  for (const key of missingMenuKeys) console.error(`    menu.${key}`)
}
if (unmappedRoutes.length > 0 || missingMenuKeys.length > 0) {
  process.exit(1)
}

/**
 * 枚举标签键登记表检查（1.4 扩展）。
 *
 * <p>`src/constants/enumLabels.ts` 把「枚举值 → i18n 键」集中登记，好处是单一来源，代价是
 * 键名写错时**只会在运行时渲染出一个键名字面量**（`enums.ticketStatus.OPEN`），没有任何测试会发现。
 * 这里把登记表里出现的每个 `enums.*` / `common.*` 键与两个语言文件比对，写错即构建失败。
 */
const ENUM_LABELS = resolve(here, '../src/constants/enumLabels.ts')
const enumText = readFileSync(ENUM_LABELS, 'utf8')
// 只取形如 'enums.xxx' / 'common.xxx' 的字符串字面量，避免把注释里的示例也算进来
const enumKeys = [...new Set([...enumText.matchAll(/'((?:enums|common)\.[A-Za-z0-9_.]+)'/g)].map((m) => m[1]))].sort()
const badEnumKeys = enumKeys.filter((key) => !maps[0].keys.has(key))
if (badEnumKeys.length > 0) {
  console.error(`\n✗ enumLabels.ts 引用了 ${badEnumKeys.length} 个不存在的键（会渲染出键名字面量）：`)
  for (const key of badEnumKeys) console.error(`    ${key}`)
  process.exit(1)
}


console.log(
  `✓ 语言资源一致：${maps.map((m) => `${m.name} ${m.keys.size} 键`).join(' / ')}` +
    `；菜单路由 ${routePaths.length}/${menuMap.size} 条全部有映射`,
)
