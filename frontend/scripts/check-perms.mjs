#!/usr/bin/env node
/**
 * 权限判定接线校验（086）。
 *
 * <p>本脚本存在的唯一理由：<b>挂错一个权限码是「静默失效」</b>。`hasPerm` 走
 * `user.permissions.includes(code)`，字典里不存在的码永远不会被授给任何角色，于是它对除 ADMIN 外的
 * 所有人恒为 `false`——按钮无声无息地消失，没有报错、没有 403、控制台也没有任何提示。
 *
 * <p>而既有的护栏只管住了**登记表**那半边：`FrontendPermissionCodeAlignmentTest`（后端）断言
 * `permissions.ts` 里的码 ⊆ 字典 ∧ ⊆ 被 `@RequirePermission` 真实校验的集合。它**管不到**
 * 「页面有没有用对键」「有没有人写回 `role === 'ADMIN'` 的旧写法」——这正是本脚本的四项检查：
 *
 * <ol>
 *   <li><b>ADMIN 硬判断</b>：`user.role === 'ADMIN' || hasPerm(...)` 是 081 **之前**的正确写法。
 *       081 扩了角色之后它变成错的——`hasPerm` 本就对 ADMIN 直通，那半边多余，而它会让**真正被授权的
 *       非内建角色**（如 SALES_MANAGER 持 `lead:assign`）看不到按钮。
 *   <li><b>码值不重复</b>：两个不同的键指向同一个码，会让两处判据悄悄同源——改一处以为只影响一处。
 *   <li><b>无未定义引用</b>：`PERMS.typo` 的类型错误 `tsc` 能抓，但本脚本要能独立跑（与 `menu:check`
 *       一样不依赖构建产物）。
 *   <li><b>白名单</b>：合法的 ADMIN 短路与**非权限**的角色字面量。白名单挡不住误报，开发者就会学会
 *       忽略这个脚本——那比没有脚本更糟。
 * </ol>
 *
 * <p>用法：`pnpm perms:check`（CI 已接入，与 `i18n:check` / `menu:check` 并列）。
 */
import { readFileSync, readdirSync, statSync } from 'node:fs'
import { dirname, join, relative, sep } from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
const SRC = join(ROOT, 'src')
const PERMS_FILE = join(SRC, 'constants', 'permissions.ts')

// 与后端 `FrontendPermissionCodeAlignmentTest` 使用**同一个**解析正则，两边不许漂移。
// 行尾不锚定是刻意的：允许同一行后面跟注释。
const ENTRY_RE = /^\s+([A-Za-z0-9_]+):\s*'([^']+)',/gm

/**
 * 白名单。**每条必须附理由**——没有理由注释的白名单会变成藏污纳垢处。
 *
 * <p>按**文件 + 预期命中数**登记，而不是按行号：行号会随无关改动漂移，那会让护栏无故转红，
 * 而一个会无故转红的护栏很快就会被绕过。命中数**双向**校验：
 * 多了说明该文件新增了未批准的 ADMIN 判断；少了说明这条白名单已陈旧（有人修好了却没删条目），
 * 两种情况都报错，迫使维护者回来处理。
 */
const ALLOWED = [
  {
    file: 'src/hooks/usePermission.ts',
    count: 2,
    reason: '判定原语自身的 ADMIN 直通语义（hasPerm / useHasMenu）——这是直通语义的**唯一**实现处',
  },
  {
    file: 'src/components/PermissionGuard.tsx',
    count: 1,
    reason: '组件自身的 ADMIN 直通语义，与 hasPerm 保持一致',
  },
  {
    file: 'src/constants/menuVisibility.ts',
    count: 1,
    reason: '菜单可见性的 ADMIN 兜底（管理员恒可见全部菜单）',
  },
  {
    file: 'src/pages/users/UserManagementPage.tsx',
    count: 1,
    reason: '**非权限判断**：角色 → Tag 颜色。朴素扫描的必然误报，与权限收窄无关',
  },
  {
    file: 'src/pages/customers/CustomerDetailPage.tsx',
    count: 1,
    reason:
      '**数据归属规则的 ADMIN 例外**，不是权限判断：镜像后端 `CustomerShareService.share` 的 ' +
      '`if (!isAdmin && !ownerId.equals(currentUserId)) throw FORBIDDEN`。' +
      '`hasPerm` 无法表达"是 ADMIN 但不是 owner"这一支——它对 ADMIN 与持码者都返回 true，两者被合并了。' +
      '该处**同时**另有 `hasPerm(PERMS.customerUpdate, ...)` 这道码闸门，两者是 ∧ 关系',
  },
  {
    file: 'src/components/CommentSection.tsx',
    count: 1,
    reason:
      '**数据归属规则的 ADMIN 例外**（096 后已收口，不再是「不可收口」）：删除判据现在是 ' +
      '`hasPerm(comment:delete) && (role === ADMIN || id === authorId)`，' +
      '镜像后端 `CommentService.delete` 的「非作者且非 ADMIN ⇒ FORBIDDEN」。' +
      '`hasPerm` 表达不出「是 ADMIN 但不是作者」这一支，故那半个例外保留，两者是 ∧ 关系。' +
      '（086 时后端是类级 @PreAuthorize、字典无 `comment:*`；096 建码并撤类级门后此条已可收口）',
  },
  {
    file: 'src/components/FollowUpTimeline.tsx',
    count: 1,
    reason:
      '**不可收口**：该判据下只有「编辑」链接，而编辑属排除类（086 只收删除/审批/分配/导出/触发/启停/权限配置）',
  },
  {
    file: 'src/pages/stats/DashboardPage.tsx',
    count: 1,
    reason:
      '**数据范围判定，不是权限判断**（096 订正，不再是「不可收口」）：`StatsController.setSalesTarget` ' +
      '的闸门是方法体内联的「全局目标仅 ADMIN、个人目标仅本人」，与角色码无关；' +
      '`kpi:view` 归 ADMIN+ANALYST ⇒ 挂它反而给 ANALYST 露出**必然 403** 的按钮。' +
      '⇒ **不该设码**。086 清单该行原写「后端先加码」，096 已订正其判据' +
      '（见 specs/086-frontend-button-gating/research.md §2 的 2026-09-16 ⚠️ 块）',
  },
]

// `={2,3}` 是刻意的：`==?` 会连 `role = 'ADMIN'` 这种**赋值**一起命中，
// 而测试里 `me.role = 'ADMIN'`（造数据）到处都是 —— 一个会误报的护栏很快就会被绕过。
const ADMIN_RE = /\brole\s*!?={2,3}\s*'ADMIN'|'ADMIN'\s*!?={2,3}\s*\brole/

/**
 * 递归收集 `src` 下的 ts/tsx 源文件，**排除测试文件**。
 *
 * <p>测试不决定界面上按钮是否渲染；而测试里断言"管理员能看见全部"是对直通语义的**正当**验证，
 * 把它判红只会逼人加白名单。gating 只发生在产品代码里。
 */
function collectFiles(dir, { includeTests = false } = {}) {
  const out = []
  for (const name of readdirSync(dir)) {
    const full = join(dir, name)
    if (statSync(full).isDirectory()) {
      out.push(...collectFiles(full, { includeTests }))
    } else if (/\.tsx?$/.test(name)) {
      if (!includeTests && /\.test\.tsx?$/.test(name)) continue
      out.push(full)
    }
  }
  return out
}

/**
 * 去掉注释，保留行号。逐字符扫描并跟踪引号与块注释状态——
 * 必须引号感知：否则 `'https://…'` 里的 `//` 会被当成行注释开头，把其后的真代码截掉。
 */
function stripComments(source) {
  const lines = []
  let inBlock = false
  let quote = null

  for (const raw of source.split('\n')) {
    let out = ''
    for (let i = 0; i < raw.length; i++) {
      const ch = raw[i]
      const next = raw[i + 1]

      if (inBlock) {
        if (ch === '*' && next === '/') {
          inBlock = false
          i++
        }
        continue
      }
      if (quote) {
        out += ch
        if (ch === '\\') {
          out += next ?? ''
          i++
        } else if (ch === quote) {
          quote = null
        }
        continue
      }
      if (ch === "'" || ch === '"' || ch === '`') {
        quote = ch
        out += ch
        continue
      }
      if (ch === '/' && next === '*') {
        inBlock = true
        i++
        continue
      }
      if (ch === '/' && next === '/') break // 行注释：本行剩余部分丢弃
      out += ch
    }
    lines.push(out)
  }
  return lines
}

/** 归一化路径分隔符，让白名单在 Windows / Linux 上都能匹配。 */
const rel = (p) => relative(ROOT, p).split(sep).join('/')

const problems = []

// ---------- 检查 1：ADMIN 硬判断 ----------
const hitsByFile = new Map()
for (const file of collectFiles(SRC)) {
  const lines = stripComments(readFileSync(file, 'utf8'))
  lines.forEach((line, idx) => {
    if (ADMIN_RE.test(line)) {
      const key = rel(file)
      if (!hitsByFile.has(key)) hitsByFile.set(key, [])
      hitsByFile.get(key).push({ line: idx + 1, text: line.trim() })
    }
  })
}

const allowMap = new Map(ALLOWED.map((a) => [a.file, a]))
for (const [file, hits] of hitsByFile) {
  const allowed = allowMap.get(file)
  if (!allowed) {
    problems.push({
      kind: 'ADMIN 硬判断',
      file,
      detail: hits.map((h) => `第 ${h.line} 行：${h.text}`),
      fix:
        '改用 `hasPerm(PERMS.xxx, user)` / `usePerms([...])` / `<PermissionGuard>`。\n' +
        '    注意 `hasPerm` 对 ADMIN 已直通，`|| isAdmin` 是多余的且会漏掉真正被授权的非内建角色。\n' +
        '    若该处**不是**权限判断（如角色 → 颜色/文案），或该端点后端**无权限注解**（不可收口），\n' +
        '    请在本脚本的 ALLOWED 中登记并写明理由。',
    })
  } else if (hits.length !== allowed.count) {
    problems.push({
      kind: '白名单陈旧',
      file,
      detail: [
        `白名单登记 ${allowed.count} 处，实际命中 ${hits.length} 处。`,
        ...hits.map((h) => `第 ${h.line} 行：${h.text}`),
      ],
      fix:
        hits.length > allowed.count
          ? '该文件新增了未批准的 ADMIN 判断。请先确认它是不是权限判断；是则改掉，否则更新 ALLOWED 的 count 与理由。'
          : `命中数少于登记数——可能有人修好了却没删白名单条目。请核对后把 count 改成 ${hits.length}；若已归零则整条删除。`,
    })
  }
}
for (const a of ALLOWED) {
  if (!hitsByFile.has(a.file)) {
    problems.push({
      kind: '白名单陈旧',
      file: a.file,
      detail: [`白名单登记 ${a.count} 处，实际一处也没命中（文件可能已改名或该写法已消失）。`],
      fix: '删除这条白名单，或修正 file 路径。',
    })
  }
}

// ---------- 检查 2：PERMS 码值不重复 ----------
const permsSource = readFileSync(PERMS_FILE, 'utf8')
const entries = [...permsSource.matchAll(ENTRY_RE)].map((m) => ({ key: m[1], value: m[2] }))

// 防呆：解析结果过少即失败。护栏在「输入为空」时通过等于没有护栏（照抄 check-menu.mjs 的做法）。
if (entries.length < 16) {
  console.error(
    `✗ 从 ${rel(PERMS_FILE)} 只解析出 ${entries.length} 个权限码，疑似解析失效（预期 16+）。\n` +
      '    护栏在输入为空时通过等于没有护栏，故此处直接判失败——请检查 ENTRY_RE 与文件格式。',
  )
  process.exit(1)
}

const byValue = new Map()
for (const e of entries) {
  if (!byValue.has(e.value)) byValue.set(e.value, [])
  byValue.get(e.value).push(e.key)
}
for (const [value, keys] of byValue) {
  if (keys.length > 1) {
    problems.push({
      kind: 'PERMS 码值重复',
      file: rel(PERMS_FILE),
      detail: [`码 '${value}' 被 ${keys.length} 个键登记：${keys.join(', ')}`],
      fix: '两个键指向同一个码，则两处判据悄悄同源——改一处会以为只影响一处。删掉多余的那个键。',
    })
  }
}

// ---------- 检查 3：PERMS.xxx 无未定义引用 ----------
const knownKeys = new Set(entries.map((e) => e.key))
const referenced = new Set()
// 这一项**含测试**：测试里引用 PERMS 同样会拼错，而拼错在测试里更隐蔽（断言静默不成立）。
for (const file of collectFiles(SRC, { includeTests: true })) {
  const lines = stripComments(readFileSync(file, 'utf8'))
  lines.forEach((line, idx) => {
    for (const m of line.matchAll(/\bPERMS\.([A-Za-z0-9_]+)/g)) {
      referenced.add(m[1])
      if (!knownKeys.has(m[1])) {
        problems.push({
          kind: '未定义的 PERMS 引用',
          file: rel(file),
          detail: [`第 ${idx + 1} 行：PERMS.${m[1]}`],
          fix: `该键不存在于 PERMS。请核对拼写，或在 ${rel(PERMS_FILE)} 中登记它（登记前先确认该码在后端字典里且被真实校验）。`,
        })
      }
    }
  })
}

const unused = [...knownKeys].filter((k) => !referenced.has(k))

// ---------- 结论 ----------
if (problems.length > 0) {
  console.error(`✗ 权限判定接线校验失败：${problems.length} 处问题\n`)
  const grouped = new Map()
  for (const p of problems) {
    if (!grouped.has(p.kind)) grouped.set(p.kind, [])
    grouped.get(p.kind).push(p)
  }
  for (const [kind, list] of grouped) {
    console.error(`【${kind}】${list.length} 处`)
    for (const p of list) {
      console.error(`  ${p.file}`)
      for (const d of p.detail) console.error(`      ${d}`)
      console.error(`    修复：${p.fix}`)
    }
    console.error('')
  }
  process.exit(1)
}

console.log(
  `✓ 权限判定接线校验通过（${entries.length} 个权限码；` +
    `${hitsByFile.size} 个文件含已登记的 ADMIN 判断，共 ${ALLOWED.reduce((s, a) => s + a.count, 0)} 处）`,
)

// 提示（非失败）：登记了却没有任何页面引用的码。`permissions.ts` 的头部注释声明
// 「只登记前端真的在用它做 gating 的码」，这里是那条声明的软性提醒——
// 不做成失败，是因为「登记在先、接线在后」在分批交付时是正常的中间态。
if (unused.length > 0) {
  console.log(`  提示：${unused.length} 个已登记的码尚无页面引用 —— ${unused.join(', ')}`)
}
