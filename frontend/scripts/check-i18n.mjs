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
 * 菜单可渲染性检查（060 起，084 改造输入源）。
 *
 * <p>**084 的变更**：此前这里以 `App.tsx` 里手写的 `MENU_I18N_KEYS`（58 条）为输入，
 * 而菜单的分组与名次已收敛到生成物 `src/constants/menuManifest.ts`（见 plan 决策 P1/P2）。
 * 现在**键集合的来源是生成物**，本检查也随之改变关注点：
 *
 * <p>改造后侧边栏是**由清单驱动渲染**的（`App.tsx` 遍历 `MENU_MANIFEST`），于是多出两个
 * 静默故障面，且都不会被任何单测发现（菜单不在单测覆盖范围内）：
 *
 * 1. **路由无对应清单项**：`App.tsx` 里挂了一条侧边栏路由，但 `MENU_TREE` 里没有对应项——
 *    该路由在侧边栏**彻底消失**（改造前只是没文案，现在是整项不见）。
 * 2. **清单项无对应路由**：`MENU_TREE` 里有一项、`role_menu` 也授了，但前端没有路由接——
 *    角色页勾得上、用户永远看不到，正是 084 要根除的那类断链。
 *
 * <p>两个方向都必须为空，且这是一个**双射**：实测当前 56 个清单键 ↔ 56 个路由键，两侧差集皆空。
 * 除此之外还检查清单引用的 `menu.*` 文案键在语言文件里存在（缺键会渲染出 `menu.xxx` 字面量）。
 */
const APP = resolve(here, '../src/App.tsx')
const appText = readFileSync(APP, 'utf8')

/** 解析生成物里的菜单项；顺带做防呆断言——解析零命中时「全部通过」是假绿。 */
function manifestItems() {
  const MANIFEST = resolve(here, '../src/constants/menuManifest.ts')
  const text = readFileSync(MANIFEST, 'utf8')
  const items = [
    ...text.matchAll(/\{ menuKey: '([^']+)', i18nKey: '([^']+)', title: '([^']+)' \}/g),
  ].map((m) => ({ menuKey: m[1], i18nKey: m[2], title: m[3] }))
  if (items.length < 50) {
    throw new Error(
      `menuManifest.ts 只解析出 ${items.length} 个菜单项（预期远大于此）。` +
        '多半是生成物的写法变了而这里的正则没跟上——请修脚本，不要放宽断言。',
    )
  }
  return items
}

/**
 * 取「路由 path → 菜单 key」的粗粒度别名表。
 *
 * <p>不在这里复刻规则：`src/constants/menuKeys.ts` 的 `COARSE_ALIASES` 已是权威（它同时被
 * `App.tsx` 与 `menuKeys.test.ts` 使用）。复刻一份必然漂移。
 */
function coarseAliases() {
  const text = readFileSync(resolve(here, '../src/constants/menuKeys.ts'), 'utf8')
  const anchor = text.indexOf('const COARSE_ALIASES')
  if (anchor < 0) throw new Error('menuKeys.ts 中找不到 COARSE_ALIASES')
  const open = text.indexOf('{', anchor)
  let depth = 0
  let end = -1
  for (let i = open; i < text.length; i++) {
    if (text[i] === '{') depth++
    else if (text[i] === '}') {
      depth--
      if (depth === 0) {
        end = i
        break
      }
    }
  }
  if (end < 0) throw new Error('COARSE_ALIASES 花括号不配平')
  return new Map(
    [...text.slice(open, end + 1).matchAll(/'(\/[^']+)':\s*'([^']+)'/g)].map((m) => [m[1], m[2]]),
  )
}

const aliases = coarseAliases()
const items = manifestItems()
const manifestKeys = new Set(items.map((i) => i.menuKey))
const keyOf = (path) => aliases.get(path) ?? path.replace(/^\//, '')

/** 侧边栏路由声明：`{ path: '...', name: '...' }`（带 `name` 的才是菜单项，详情页没有）。 */
const routePaths = [...appText.matchAll(/path:\s*'([^']+)',\s*name:\s*/g)].map((m) => m[1])
const unstakedRoutes = routePaths.filter((path) => !manifestKeys.has(keyOf(path))).sort()
const orphanKeys = [...manifestKeys]
  .filter((key) => !routePaths.some((p) => keyOf(p) === key))
  .sort()
const missingMenuKeys = [...new Set(items.map((i) => i.i18nKey))]
  .filter((key) => !maps[0].keys.has(`menu.${key}`))
  .sort()

if (unstakedRoutes.length > 0) {
  console.error(
    `\n✗ App.tsx 有 ${unstakedRoutes.length} 条侧边栏路由在菜单清单里没有对应项（该项会从侧边栏消失）：`,
  )
  for (const path of unstakedRoutes) console.error(`    ${path} → 菜单键 ${keyOf(path)}`)
  console.error('  修法：在 RoleConstants.java 的 MENU_TREE 里补上该菜单项后跑 `pnpm menu:gen`；')
  console.error(
    '        若它本就不是菜单项（子页面），在 menuKeys.ts 的 COARSE_ALIASES 里挂到所属分组。',
  )
}
if (orphanKeys.length > 0) {
  console.error(
    `\n✗ 菜单清单有 ${orphanKeys.length} 项在 App.tsx 里没有路由（角色勾得上、用户看不到）：`,
  )
  for (const key of orphanKeys) console.error(`    ${key}`)
  console.error(
    '  修法：在 App.tsx 的路由数组里挂上该路由；若菜单项已废弃，从 MENU_TREE 里删掉后重跑 `pnpm menu:gen`。',
  )
}
if (missingMenuKeys.length > 0) {
  console.error(`\n✗ 菜单清单引用了不存在的 menu.* 键（${missingMenuKeys.length}）：`)
  for (const key of missingMenuKeys) console.error(`    menu.${key}`)
}
if (unstakedRoutes.length > 0 || orphanKeys.length > 0 || missingMenuKeys.length > 0) {
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
    `；菜单路由与清单双向对齐（路由 ${routePaths.length} 条 / 清单 ${items.length} 项，` +
    `粗粒度别名 ${aliases.size} 条）`,
)
