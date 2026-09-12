#!/usr/bin/env node
/**
 * 菜单清单生成器（084：菜单信息架构与授权可见性收口）。
 *
 * <p><b>为什么要有生成器</b>：菜单的「有哪些项、属于哪个分组、叫什么名字」此前有四处作者——
 * 后端 `RoleConstants.MENU_TREE`（角色页勾选树的来源）、`App.tsx` 的路由数组与分组字面量、
 * 迁移里的 `role_menu` 种子、以及 `MENU_I18N_KEYS` + 双语表。四处之间没有任何机械约束，
 * 于是出现「管理员勾了却看不到菜单」（分组归属对不上）与「两侧名称不一致」两类静默故障。
 *
 * <p>本脚本把使用侧的分组、顺序与文案键**从权威处派生**出来，写进
 * `src/constants/menuManifest.ts`。生成物进版本库（可评审、可 diff），并由
 * `scripts/check-menu.mjs` 做「重新生成 → 比对 → 不一致即失败」的陈旧性校验。
 *
 * <p><b>为什么不改成运行时由后端下发</b>：唯一能下发菜单树的既有接口
 * `/api/v1/roles/menu-tree` 带 `@RequirePermission("role:manage")`——普通用户取不到；
 * 放宽它或新增一个公开端点都是把「菜单定义」变成新的对外契约，得不偿失（见 research.md 决策 ①）。
 *
 * <p><b>为什么权威处不是 `MENU_TREE` 自己带上文案键</b>：`MENU_TREE` **就是**该接口的响应体，
 * 给它加字段等于改响应结构。故文案键的派生规则留在这里，解析结果写进生成物，
 * 后端护栏读生成物的 `i18nKey` 而不复刻本规则（同一规则两份实现必然漂移）。
 *
 * <p>用法：`pnpm menu:gen` 写文件；`pnpm menu:check` 只比对不写。
 */
import { readFileSync, writeFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
const OUT = resolve(here, '../src/constants/menuManifest.ts')
const RELATIVE_SOURCE = 'backend/src/main/java/com/crm/common/RoleConstants.java'

/**
 * 菜单项文案键的例外表：`menuKey -> i18nKey`。
 *
 * <p>默认规则是 `camelCase(menuKey 的最后一段)`（如 `stats/leaderboard → leaderboard`）。
 * 下面 7 条是既有文案表里不遵从该规则的键——它们承载着比规则本身更早的历史，改名会波及
 * 双语表与用户可见文案，故**保留现状并逐条显式列出**，而不是把规则改得更绕。
 *
 * <p>该表在 084 实施时用「规则产出 vs 既有 `MENU_I18N_KEYS`」逐一比对得出：当时共有 9 处不符，
 * 其中 2 处是两个「借分组显示」的子页面（`/marketing/roi`、`/workflows/logs`），
 * 它们不是菜单项，留在前端的别名表里，故此处是 7 条。
 *
 * <p>**缺一条的后果**：不报错，而是生成一个指向不存在文案的键，界面上渲染出 `menu.xxx`。
 * 所以本表只允许「减少」（当某条改名到符合规则时），不允许沉默地漏。
 */
const ITEM_I18N_EXCEPTIONS = {
  stats: 'home',
  'customer-merge': 'merge',
  'contract-renewal': 'renewal',
  marketing: 'marketingActivity',
  'marketing/email': 'emailMarketing',
  'email-unsubscribes': 'unsubscribe',
  'exports/scheduled': 'scheduledExports',
}

/**
 * 分组标题 → 分组文案键（11 条）。
 *
 * <p>**缺映射即报错退出**，不静默兜底：分组名是用户直接看到的东西，自动音译或留空都会
 * 制造一个「界面上少一个组名」的故障，还不如在生成期就拦下。
 */
const GROUP_I18N_KEYS = {
  首页: 'home',
  客户管理: 'customer',
  销售管理: 'sales',
  交易管理: 'deal',
  营销管理: 'marketing',
  客户服务: 'service',
  工作台: 'workbench',
  数据分析: 'data',
  系统管理: 'admin',
  流程配置: 'config',
  审计维护: 'audit',
}

/** 从 `scripts/` 向上找到含后端权威定义的仓库根；找不到即显式失败（不猜、不回退）。 */
function resolveRepoRoot() {
  let dir = here
  for (let i = 0; i < 6; i++) {
    const candidate = resolve(dir, RELATIVE_SOURCE)
    try {
      readFileSync(candidate, 'utf8')
      return dir
    } catch {
      /* 继续向上一级 */
    }
    const parent = dirname(dir)
    if (parent === dir) break
    dir = parent
  }
  throw new Error(
    `找不到 ${RELATIVE_SOURCE}。护栏需要 frontend/ 与 backend/ 同处一个检出——` +
      `本脚本从 ${here} 起向上找了 6 级仍未命中。`,
  )
}

/** 剥掉 `//` 行注释：`MENU_TREE` 的注释里会出现与真实项同形的文本，不剥会误解析。 */
const stripLineComments = (text) =>
  text
    .split('\n')
    .filter((line) => !line.trim().startsWith('//'))
    .join('\n')

/**
 * 解析 `MENU_TREE`。
 *
 * <p>不引 TS/Java 解析器（不新增依赖，与 `check-i18n.mjs` 的取舍一致）：按 `group(` 起头、
 * 花括号/圆括号配平截出每个分组的成员块，再从中取 `item("key", "名称")`。
 */
function parseMenuTree(javaSource) {
  const start = javaSource.indexOf('MENU_TREE =')
  const end = javaSource.indexOf('PERMISSION_DEFS', start)
  if (start < 0 || end < start) {
    throw new Error('RoleConstants.java 中定位不到 MENU_TREE（或 PERMISSION_DEFS 在其之前）')
  }
  const block = stripLineComments(javaSource.slice(start, end))

  const groups = []
  for (let i = block.indexOf('group('); i !== -1; i = block.indexOf('group(', i + 1)) {
    const nameMatch = block.slice(i).match(/^group\(\s*"([^"]+)"/)
    if (!nameMatch) continue
    let depth = 0
    let j = block.indexOf('(', i)
    for (; j < block.length; j++) {
      if (block[j] === '(') depth++
      else if (block[j] === ')') {
        depth--
        if (depth === 0) break
      }
    }
    const body = block.slice(i, j + 1)
    const items = [...body.matchAll(/item\(\s*"([^"]+)"\s*,\s*"([^"]+)"/g)].map((m) => ({
      menuKey: m[1],
      title: m[2],
    }))
    groups.push({ title: nameMatch[1], items })
  }
  return groups
}

/** `camelCase(最后一段)`：`exports/scheduled → scheduled`、`customer-merge → customerMerge`。 */
const camelCaseLastSegment = (menuKey) =>
  menuKey
    .split('/')
    .pop()
    .replace(/-([a-z0-9])/g, (_, c) => c.toUpperCase())

const i18nKeyOf = (menuKey) => ITEM_I18N_EXCEPTIONS[menuKey] ?? camelCaseLastSegment(menuKey)

/**
 * 生成单引号字符串字面量。
 *
 * <p>`JSON.stringify` 会产出双引号，与仓库的 prettier 约定（`singleQuote: true`）相左；
 * 生成物是要被评审和 diff 的源码，让它一眼看去就像手写的。
 */
const quote = (text) => `'${text.replace(/\\/g, '\\\\').replace(/'/g, "\\'")}'`

/**
 * 生成物里菜单项对象的开头标记——用于统计项数（防呆断言与提示信息共用）。
 *
 * <p>用 `{ menuKey:` 而不是 `menuKey:`：接口里的 `readonly menuKey: string` 也含后者，
 * 会把项数数多一个。用行首缩进也不可靠——单元素分组会被折成一行，缩进随之为 4 而非 6。
 */
const ITEM_LINE = /\{ menuKey: /g

/**
 * 数生成物里的菜单项行数。
 *
 * <p>导出给 `check-menu.mjs` 用：陈旧性校验要能判断「生成结果是空的」——若两边都退化成
 * 空清单，逐字节比对依然相等。计数逻辑只留这一份，避免两处正则各自漂移。
 */
export function countManifestItems(source) {
  return (source.match(ITEM_LINE) ?? []).length
}

/**
 * 由权威定义产出生成物源码。
 *
 * <p>导出以便 `check-menu.mjs` 复用——陈旧性校验必须与生成走同一条代码路径，
 * 否则「校验通过」只说明两个脚本恰好一致，不说明生成物是最新的。
 */
export function generateManifestSource() {
  const javaSource = readFileSync(resolve(resolveRepoRoot(), RELATIVE_SOURCE), 'utf8')
  const parsed = parseMenuTree(javaSource)

  // 防呆：解析零命中或明显偏少时立刻失败。没有这道断言，「正则没匹配上」会伪装成「校验通过」。
  const totalItems = parsed.reduce((n, g) => n + g.items.length, 0)
  if (parsed.length === 0 || totalItems < 50) {
    throw new Error(
      `MENU_TREE 解析结果异常：${parsed.length} 个分组 / ${totalItems} 个菜单项（预期远大于此）。` +
        '多半是权威处的写法变了而本脚本的正则没跟上——请修脚本，不要放宽断言。',
    )
  }

  const unmapped = parsed.map((g) => g.title).filter((t) => !GROUP_I18N_KEYS[t])
  if (unmapped.length > 0) {
    throw new Error(
      `分组标题缺少文案键映射：${unmapped.join('、')}。请在 GROUP_I18N_KEYS 里补上——` +
        '不兜底是有意的：兜底会让界面少一个组名，而没人会收到报错。',
    )
  }

  const seen = new Map()
  for (const group of parsed) {
    for (const item of group.items) {
      const i18nKey = i18nKeyOf(item.menuKey)
      if (!i18nKey) throw new Error(`菜单项 ${item.menuKey} 派生不出文案键`)
      const previous = seen.get(i18nKey)
      if (previous) {
        throw new Error(
          `文案键冲突：${previous} 与 ${item.menuKey} 都派生出 menu.${i18nKey}。` +
            '两者会显示成同一个名字（且互相覆盖），请在 ITEM_I18N_EXCEPTIONS 里区分开。',
        )
      }
      seen.set(i18nKey, item.menuKey)
    }
  }

  const lines = []
  lines.push('/**')
  lines.push(' * 菜单清单（**生成物**）——请勿手改。')
  lines.push(' *')
  lines.push(' * 由 `frontend/scripts/gen-menu.mjs` 从后端权威定义')
  lines.push(` * \`${RELATIVE_SOURCE}\` 的 \`MENU_TREE\` 生成，`)
  lines.push(` * 共 ${parsed.length} 个分组 / ${totalItems} 个菜单项。`)
  lines.push(' *')
  lines.push(' * 重新生成：`pnpm menu:gen`；陈旧性校验：`pnpm menu:check`（CI 已接入）。')
  lines.push(' * 要改菜单的分组、顺序或名称，改 `MENU_TREE` 后重跑生成器，本文件自动跟上。')
  lines.push(' *')
  lines.push(' * `title` 是权威中文名：它既是缺文案时的降级显示，也是「两侧名称一致」的判据。')
  lines.push(' * `i18nKey` 不含 `menu.` 前缀——用 `t(`menu.${i18nKey}`)` 取文案。')
  lines.push(' */')
  lines.push('')
  lines.push('export interface MenuManifestItem {')
  lines.push('  /** 菜单 key，与 `role_menu.menu_key` 及 `MenuRouteAlignmentTest` 的解析结果逐字一致 */')
  lines.push('  readonly menuKey: string')
  lines.push('  /** 界面文案键（不含 `menu.` 前缀） */')
  lines.push('  readonly i18nKey: string')
  lines.push('  /** 权威中文名 */')
  lines.push('  readonly title: string')
  lines.push('}')
  lines.push('')
  lines.push('export interface MenuManifestGroup {')
  lines.push('  /** 分组标题（权威中文名） */')
  lines.push('  readonly title: string')
  lines.push('  /** 分组文案键（不含 `menu.` 前缀） */')
  lines.push('  readonly i18nKey: string')
  lines.push('  readonly items: readonly MenuManifestItem[]')
  lines.push('}')
  lines.push('')
  lines.push('export const MENU_MANIFEST: readonly MenuManifestGroup[] = [')
  for (const group of parsed) {
    lines.push('  {')
    lines.push(`    title: ${quote(group.title)},`)
    lines.push(`    i18nKey: ${quote(GROUP_I18N_KEYS[group.title])},`)
    const rendered = group.items.map(
      (item) =>
        `{ menuKey: ${quote(item.menuKey)}, i18nKey: ${quote(i18nKeyOf(item.menuKey))}, title: ${quote(item.title)} }`,
    )
    // 折行规则与 prettier（printWidth 100）对齐：单元素且放得下就不折。
    // 生成物是要被 diff 的源码，与仓库的格式化工具打架只会制造无意义的「陈旧」告警。
    const collapsed = `    items: [${rendered.join(', ')}],`
    if (group.items.length === 1 && collapsed.length <= 100) {
      lines.push(collapsed)
    } else {
      lines.push('    items: [')
      for (const one of rendered) lines.push(`      ${one},`)
      lines.push('    ],')
    }
    lines.push('  },')
  }
  lines.push(']')
  lines.push('')
  return lines.join('\n')
}

const invokedDirectly = process.argv[1] && resolve(process.argv[1]) === resolve(fileURLToPath(import.meta.url))
if (invokedDirectly) {
  const source = generateManifestSource()
  writeFileSync(OUT, source)
  const itemCount = countManifestItems(source)
  console.log(`✓ 已生成 ${OUT}（${itemCount} 个菜单项，来源：${RELATIVE_SOURCE}）`)
}

export { OUT as MANIFEST_PATH, RELATIVE_SOURCE }
