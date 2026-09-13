/**
 * 088 现状取证脚本 —— 把 plan.md 引用的每一个数字用**同一套可复现的定义**重新导出。
 *
 * 为什么要有这个文件：088 的 plan.md 在起草时引用了一批实测数字，其中至少两处
 * （「49 页裸 fragment / 12 页包 Card」）在复核时**未能复现**（换个数法得到 88 / 40）。
 * 那次失败不是数错了，而是**定义没写下来**——同一个词（"页面"、"表单"）在两次统计里
 * 指的不是同一批文件。故本脚本的第一条纪律：**每个数字必须连定义一起输出**，
 * 让下一个人能重跑而不是重猜。
 *
 * 用法（在仓库根目录）：
 *   node specs/088-frontend-layout-consistency/measure-ui-baseline.mjs
 *
 * 输出的数字应当与 specs/088-frontend-layout-consistency/research.md 的表格逐条对应。
 *
 * ⚠️ 已知边界（不影响结论，但引用数字时须知道）：
 *   1. 标签扫描器**不是** JSX 解析器。它按「<Tag ... >」逐字符扫描、正确处理字符串与
 *      `{}` 花括号嵌套，但**不识别正则字面量**。若某个属性值是含 `>` 或 `/` 的正则，
 *      该标签会被截断。本仓库的属性里没有这种写法（已抽查），故接受此边界；
 *      遇到计数异常时**先怀疑这里**。
 *   2. 「页面根元素」（§7）走 TypeScript AST，不是启发式——那是全部指标里唯一由 AST 给出的，
 *      因为它是唯一一个曾经数错的。
 *   3. 所有计数都排除 `*.test.tsx` / `*.test.ts` / `src/test/**`（测试脚手架不是产品代码）。
 *      §6 的 CSS 类使用率另说：它**必须**扫 tsx，故单列定义。
 *   4. **行号是标签的起始行**，由扫描器记录的真实位置给出（不是 `indexOf` 猜的）——
 *      同一段标签文本在文件里出现两次时，两者会给出不同的行号。这是刻意修的：
 *      初版用 `code.indexOf(tag)` 取位置，重复标签全都指向第一次出现，行号是错的。
 */

import fs from 'node:fs'
import path from 'node:path'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'

const HERE = path.dirname(fileURLToPath(import.meta.url))
const ROOT = path.resolve(HERE, '..', '..')
const FRONTEND = path.join(ROOT, 'frontend')
const SRC = path.join(FRONTEND, 'src')

// typescript 装在 frontend/node_modules 里；本脚本住在 specs/ 下，
// 故显式按 frontend 解析，而不是依赖调用时的 cwd。
const require = createRequire(path.join(FRONTEND, 'package.json'))
const ts = require('typescript')

// ---------------------------------------------------------------- 文件收集

const isTestFile = (p) => /\.test\.(ts|tsx)$/.test(p)
const isScaffold = (p) => p.includes(`${path.sep}src${path.sep}test${path.sep}`)

function walk(dir, filter) {
  const out = []
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name)
    if (entry.isDirectory()) {
      if (entry.name === 'node_modules' || entry.name === '.git') continue
      out.push(...walk(full, filter))
    } else if (filter(full)) {
      out.push(full)
    }
  }
  return out
}

const read = (p) => fs.readFileSync(p, 'utf8')
/** 相对 frontend/ 的路径，用于输出（正斜杠，跨平台稳定）。 */
const rel = (p) => path.relative(FRONTEND, p).split(path.sep).join('/')

const codeFiles = walk(SRC, (p) => /\.(ts|tsx)$/.test(p) && !isTestFile(p) && !isScaffold(p))
const cssFiles = walk(SRC, (p) => /\.css$/.test(p))
const allProductFiles = [...codeFiles, ...cssFiles]
// 「页面」定义为 src/pages 下的 **.tsx**。src/pages 下还有非组件的 .ts（hooks/types），
// 把它们算进来会让页数虚高 4 个、并让 §7 的根元素解析多出 4 个"无法解析"——
// plan.md 起草时写的 101 页就是 .tsx 口径，此处对齐，并单列被排除的 .ts。
const pageFiles = codeFiles.filter((p) => rel(p).startsWith('src/pages/') && p.endsWith('.tsx'))
const pageNonComponent = codeFiles.filter((p) => rel(p).startsWith('src/pages/') && !p.endsWith('.tsx'))
const testFiles = walk(SRC, (p) => isTestFile(p))

// ---------------------------------------------------------------- 标签扫描器

/**
 * 找出 code 中 tagName 的所有开/闭标签事件，**带真实位置**。
 *
 * 逐字符扫描而非正则：JSX 属性值里可以出现 `>`（如 `a > b`），正则会在那里截断，
 * 把后面的属性漏掉——那样数出来的「缺 width 的 Modal」不可信。
 * 正确处理：`'...'` / `"..."` / `` `...` `` 三种字符串，以及 `{}` 嵌套（含对象字面量）。
 * 不处理正则字面量（见文件头「已知边界」）。
 *
 * 返回 `{ kind: 'open'|'close'|'self', text, index }`；index 是该 `<` 的真实下标。
 */
function scanTagEvents(code, tagName) {
  const out = []
  const openNeedle = `<${tagName}`
  const closeNeedle = `</${tagName}`
  let i = 0
  while (i < code.length) {
    const atOpen = code.indexOf(openNeedle, i)
    const atClose = code.indexOf(closeNeedle, i)
    if (atOpen === -1 && atClose === -1) break

    // 取更靠前的那个
    const isClose = atClose !== -1 && (atOpen === -1 || atClose < atOpen)
    const at = isClose ? atClose : atOpen
    // ⚠️ 两种针长度不同（`</Form` 比 `<Form` 多一个 `/`），必须各按各的长度往后看一格。
    // 初版一律用 openNeedle.length，于是 `</Form>` 看到的"后一个字符"是 `m`——
    // 恰好是标识符字符，被下面的整体匹配守卫判成 `</FormItem` 而**整个丢弃**：
    // 闭标签一个都记不下，`tagRegions` 恒为空。这正是自我自检里
    // 「表单级 Col = 0」与「32 个无宽弹窗全被判成不含表单」的成因。
    const after = code[at + (isClose ? closeNeedle.length : openNeedle.length)]

    // 标签名必须整体匹配：避免 <Form 命中 <FormItem / </Form 命中 </Form.Item>
    if (after !== undefined && /[A-Za-z0-9_.]/.test(after)) {
      i = at + (isClose ? closeNeedle.length : openNeedle.length)
      continue
    }
    if (isClose) {
      const gt = code.indexOf('>', at)
      if (gt === -1) break
      out.push({ kind: 'close', text: code.slice(at, gt + 1), index: at })
      i = gt + 1
      continue
    }

    // 扫描开标签直到花括号深度 0 的 `>`
    let j = at + openNeedle.length
    let brace = 0
    let quote = null
    let end = -1
    while (j < code.length) {
      const c = code[j]
      if (quote) {
        if (c === '\\') { j += 2; continue }
        if (c === quote) quote = null
        j++
        continue
      }
      if (c === "'" || c === '"' || c === '`') { quote = c; j++; continue }
      if (c === '{') { brace++; j++; continue }
      if (c === '}') { brace--; j++; continue }
      if (c === '>' && brace === 0) { end = j; break }
      j++
    }
    if (end === -1) break
    const text = code.slice(at, end + 1)
    out.push({ kind: text.endsWith('/>') ? 'self' : 'open', text, index: at })
    i = end + 1
  }
  return out
}

/** 只要开标签的原文（含自闭合）。 */
const openTags = (code, tagName) => scanTagEvents(code, tagName).filter((e) => e.kind !== 'close')

/**
 * 找出 tagName 的成对区域 [startIndex, endIndex)（自闭合标签不产生区域）。
 * 用于回答「这个 <Col> 在不在 <Form> 里面」这类必须靠嵌套关系才能回答的问题。
 */
function tagRegions(code, tagName) {
  const regions = []
  const stack = []
  for (const e of scanTagEvents(code, tagName)) {
    if (e.kind === 'self') continue
    if (e.kind === 'open') stack.push(e.index)
    else if (stack.length) regions.push({ start: stack.pop(), end: e.index })
  }
  return regions
}

/** 从一个标签原文里取属性值：attr="x" / attr={x} / attr（裸布尔）。 */
function attrOf(tag, name) {
  const m = tag.match(new RegExp(`\\b${name}\\s*=\\s*(?:"([^"]*)"|'([^']*)'|\\{([\\s\\S]*?)\\})`))
  if (m) return m[1] ?? m[2] ?? m[3]
  const bare = new RegExp(`\\b${name}(?=[\\s/>])`).test(tag)
  return bare ? '' : null // null = 属性不存在；'' = 裸布尔属性
}

/** 行号（1 起）。位置一律来自扫描器，不用 indexOf 猜。 */
const lineOf = (code, index) => code.slice(0, index).split('\n').length
const at = (code, index) => `${lineOf(code, index)}`

// ---------------------------------------------------------------- 计数容器

let checks = 0
function section(title) {
  console.log(`\n## ${title}\n`)
}
function kv(label, value, note) {
  checks++
  console.log(`- ${label}: **${value}**${note ? `  — ${note}` : ''}`)
}

// ---------------------------------------------------------------- §1 规模

section('1. 规模')
kv('src 下产品 .ts/.tsx 文件数', codeFiles.length, '定义：排除 *.test.* 与 src/test/**')
kv('src 下 .css 文件数', cssFiles.length, cssFiles.map(rel).join(', '))
kv('其中 src/pages 下的页面文件数', pageFiles.length, '定义：src/pages 下的 .tsx（组件）')
kv('src/pages 下的非组件 .ts（被上面的口径排除）', pageNonComponent.length, pageNonComponent.map(rel).join(', '))
kv('页面文件总行数', pageFiles.reduce((n, p) => n + read(p).split('\n').length, 0))
kv('测试文件数', testFiles.length)
kv('src/index.css 行数', read(path.join(SRC, 'index.css')).split('\n').length)
kv('src/index.css 里 `!important` 次数', (read(path.join(SRC, 'index.css')).match(/!important/g) ?? []).length)

// ---------------------------------------------------------------- §2 表单

section('2. 表单（定义：页面文件里 `<Form` 开标签，不含 `<Form.Item`/`<Form.List`）')

const formStats = { forms: 0, vertical: 0, horizontal: 0, inline: 0, none: 0, items: 0 }
const formFiles = new Set()
const labelColFlex = new Map()
const labelColSpanForms = []

for (const p of pageFiles) {
  const code = read(p)
  const tags = openTags(code, 'Form')
  if (tags.length) formFiles.add(rel(p))
  formStats.forms += tags.length
  formStats.items += scanTagEvents(code, 'Form.Item').filter((e) => e.kind !== 'close').length
  for (const e of tags) {
    const layout = attrOf(e.text, 'layout')
    const bucket = layout === 'vertical' ? 'vertical' : layout === 'horizontal' ? 'horizontal' : layout === 'inline' ? 'inline' : 'none'
    formStats[bucket]++
    const lc = attrOf(e.text, 'labelCol')
    if (lc) {
      const flex = lc.match(/flex:\s*['"]?(\d+)px/)
      if (flex) labelColFlex.set(flex[1], (labelColFlex.get(flex[1]) ?? 0) + 1)
      else if (/span:/.test(lc)) labelColSpanForms.push(`${rel(p)}:${at(code, e.index)} (${lc.trim()})`)
    }
  }
}

kv('含表单的页面文件数', formFiles.size)
kv('`<Form` 总数', formStats.forms)
kv('`<Form.Item` 总数', formStats.items)
kv('layout=vertical', formStats.vertical)
kv('layout=horizontal', formStats.horizontal)
kv('layout=inline', formStats.inline)
kv('未设 layout', formStats.none)

// Descriptions 的 layout 是另一码事，必须单列以示清分
const descHorizontal = []
for (const p of pageFiles) {
  const code = read(p)
  for (const e of openTags(code, 'Descriptions')) {
    if (attrOf(e.text, 'layout') === 'horizontal') descHorizontal.push(`${rel(p)}:${at(code, e.index)}`)
  }
}
kv('<Descriptions layout="horizontal"> 处数（**不是表单**）', descHorizontal.length, descHorizontal.join(', '))

console.log('\n### labelCol flex 取值分布（定义：`<Form ... labelCol={{ flex: \'Npx\' }}`）\n')
for (const [px, n] of [...labelColFlex.entries()].sort((a, b) => b[1] - a[1])) console.log(`- ${px}px × ${n}`)
kv('labelCol 用 span 数字的表单数', labelColSpanForms.length,
  labelColSpanForms.length ? labelColSpanForms.join(', ') : '为 0 ⇒「横向表单漏设 labelCol 退化成 8/16 分栏」这个常见判断**不成立**')

// ---------------------------------------------------------------- §3 栅格

section('3. 栅格')

let colsTotal = 0
const colsFixedSpan = []
const colsResponsive = []
let formCols = 0
let formColsFixed = 0
const gutters = new Map()

for (const p of pageFiles) {
  const code = read(p)
  const formSpans = tagRegions(code, 'Form')
  const inForm = (idx) => formSpans.some((s) => idx > s.start && idx < s.end)
  for (const e of openTags(code, 'Col')) {
    colsTotal++
    const loc = `${rel(p)}:${at(code, e.index)}`
    const responsive = /\b(xs|sm|md|lg|xl|xxl)\s*=/.test(e.text)
    if (responsive) colsResponsive.push(loc)
    else colsFixedSpan.push(loc)
    if (inForm(e.index)) {
      formCols++
      if (!responsive) formColsFixed++
    }
  }
  for (const e of openTags(code, 'Row')) {
    const g = attrOf(e.text, 'gutter')
    if (g !== null) {
      const num = g.match(/\d+/)?.[0] ?? g.trim()
      gutters.set(num, (gutters.get(num) ?? 0) + 1)
    }
  }
}

kv('`<Col` 总数（**页面级**，含非表单布局）', colsTotal)
kv('其中带响应式断点的', colsResponsive.length, colsResponsive.join(', ') || '（无）')
kv('其中只写死 span 的', colsFixedSpan.length)
kv('`<Col` 落在 `<Form>` 区域内（**表单级**，plan 的判据口径）', formCols)
kv('其中只写死 span 的（表单级）', formColsFixed,
  formCols ? `${((formColsFixed / formCols) * 100).toFixed(1)}%` : '')
console.log('\n⚠️ **页面级与表单级是两个不同的分母**——plan.md 起草时写的「96/102 个表单 Col」是表单级口径。\n')
console.log('### Row gutter 取值分布\n')
for (const [g, n] of [...gutters.entries()].sort((a, b) => b[1] - a[1])) console.log(`- gutter={${g}} × ${n}`)

// ---------------------------------------------------------------- §4 Modal

section('4. 弹窗（定义：页面文件里的 `<Modal` 开标签）')

const modalWidths = new Map()
const modalsNoWidthWithForm = []
const modalsNoWidthNoForm = []
let modalCount = 0
const modalFlags = { okText: 0, cancelText: 0, confirmLoading: 0, destroyOnClose: 0, destroyOnHidden: 0, footer: 0 }

for (const p of pageFiles) {
  const code = read(p)
  const regions = new Map(tagRegions(code, 'Modal').map((r) => [r.start, r]))
  for (const e of openTags(code, 'Modal')) {
    modalCount++
    const w = attrOf(e.text, 'width')
    if (w === null) {
      // Modal 内是否含表单：用标签区域判定，不用「往后切 4000 字符」那种猜测
      const r = regions.get(e.index)
      const inner = r ? code.slice(r.start, r.end) : ''
      const hasForm = openTags(inner, 'Form').length > 0
      const loc = `${rel(p)}:${at(code, e.index)}`
      ;(hasForm ? modalsNoWidthWithForm : modalsNoWidthNoForm).push(loc)
    } else {
      modalWidths.set(w.trim(), (modalWidths.get(w.trim()) ?? 0) + 1)
    }
    for (const f of Object.keys(modalFlags)) if (attrOf(e.text, f) !== null) modalFlags[f]++
  }
}
kv('`<Modal` 总数', modalCount)
kv('width 缺失 **且区域内含表单**（受「必须显式定宽」约束）', modalsNoWidthWithForm.length)
kv('width 缺失但不含表单（不在该规则内）', modalsNoWidthNoForm.length)
console.log('\n### Modal width 取值分布\n')
for (const [w, n] of [...modalWidths.entries()].sort((a, b) => b[1] - a[1])) console.log(`- width={${w}} × ${n}`)
kv('不同 width 取值个数', modalWidths.size)
console.log('\n### Modal 属性覆盖\n')
for (const [f, n] of Object.entries(modalFlags)) console.log(`- ${f}: ${n}`)

// ---------------------------------------------------------------- §5 宽度缺陷

section('5. 宽度缺陷（定义：无 width **且无 style** 的控件标签）')


const pickers = []
const selectLike = []
for (const p of pageFiles) {
  const code = read(p)
  for (const name of ['DatePicker', 'RangePicker', 'TimePicker']) {
    for (const e of openTags(code, name)) {
      if (attrOf(e.text, 'width') === null && !/\bstyle\s*=/.test(e.text)) pickers.push(`${name} ${rel(p)}:${at(code, e.index)}`)
    }
  }
  for (const name of ['Select', 'TreeSelect', 'Cascader', 'InputNumber']) {
    for (const e of openTags(code, name)) {
      if (attrOf(e.text, 'width') === null && !/\bstyle\s*=/.test(e.text)) selectLike.push(`${name} ${rel(p)}:${at(code, e.index)}`)
    }
  }
}
kv('DatePicker/RangePicker/TimePicker 无 width（**真缺陷**，antd 无 in-form-item 规则）', pickers.length, pickers.join(', ') || '（无）')
kv('Select/TreeSelect/Cascader/InputNumber 无 width（**非缺陷**，antd 5 自动补 `.ant-select-in-form-item{width:100%}`）', selectLike.length)

// ---------------------------------------------------------------- §5b 表单字段规范

section('5b. 表单字段规范（check-ui.mjs 规则 4/5/6 的实测基数）')

/*
  定义（与 check-ui.mjs 将要实现的口径一致）：
    规则 4：`<Form.Item>` 的 rules 里出现 `required: true`，但整个标签内没有 `message`。
    规则 5：同上，但标签内没有 `label`，也没有 `aria-label`。
            两者都为「无 label」——但**在 `<Form.List>` 区域内**的行项目是有意不写 label 的
            （靠 placeholder 标识），故按是否落在 Form.List 区域内分开报，whitelist 由此而来。
    规则 6：裸字符串文案属性（`placeholder="x"` / `aria-label="x"` / `title="x"`），
            即给了字面量而不是 `{t('...')}`——这类字符串**没有任何护栏能拦**。
*/
{
  const noMessage = []
  const noLabel = { outside: [], insideList: [] }
  let itemsWithRequired = 0
  let requiredInList = 0

  for (const p of pageFiles) {
    const code = read(p)
    const listSpans = tagRegions(code, 'Form.List')
    const inList = (idx) => listSpans.some((s) => idx > s.start && idx < s.end)
    for (const e of scanTagEvents(code, 'Form.Item').filter((x) => x.kind !== 'close')) {
      const rules = attrOf(e.text, 'rules')
      if (!rules || !/required\s*:\s*true/.test(rules)) continue
      itemsWithRequired++
      const loc = `${rel(p)}:${at(code, e.index)}`
      const listed = inList(e.index)
      if (listed) requiredInList++
      if (!/\bmessage\s*:/.test(e.text)) noMessage.push(loc)
      const hasLabel = attrOf(e.text, 'label') !== null || attrOf(e.text, 'aria-label') !== null || attrOf(e.text, 'ariaLabel') !== null
      if (!hasLabel) (listed ? noLabel.insideList : noLabel.outside).push(loc)
    }
  }
  kv('rules 里含 `required: true` 的 Form.Item', itemsWithRequired, `其中在 <Form.List> 区域内 ${requiredInList}`)
  kv('规则 4 违规：`required: true` 无 `message`', noMessage.length, noMessage.join(', ') || '（无）')
  kv('规则 5 违规（Form.List **之外**）', noLabel.outside.length, noLabel.outside.join(', ') || '（无）')
  kv('规则 5 在 Form.List **之内**（有意为之，进白名单）', noLabel.insideList.length, noLabel.insideList.join(', ') || '（无）')

  const bareStrings = []
  for (const p of codeFiles) {
    const code = read(p)
    for (const m of code.matchAll(/\b(placeholder|aria-label|title)\s*=\s*"([^"]*)"/g)) {
      bareStrings.push(`${rel(p)} ${m[1]}="${m[2]}"`)
    }
  }
  kv('规则 6 违规：裸字符串 `placeholder`/`aria-label`/`title`', bareStrings.length)
  console.log(`\n裸字符串清单：\n\n${bareStrings.map((s) => `- ${s}`).join('\n')}\n`)
}

section('5c. 孤儿组件（定义：`src/components/**/*.tsx` 的组件名，在任何**别的**产品文件里 0 次出现）')
{
  const compFiles = codeFiles.filter((p) => rel(p).startsWith('src/components/') && p.endsWith('.tsx'))
  const orphans = []
  const barrelOnly = []
  for (const p of compFiles) {
    const name = path.basename(p, '.tsx')
    const referrers = codeFiles.filter((o) => o !== p && read(o).includes(name)).map(rel)
    if (!referrers.length) orphans.push(rel(p))
    // 只被 index.ts 这类 barrel 提到（且 barrel 位于它自己的目录下）⇒ 可疑：可能是"导出了但没人用"
    else if (referrers.every((r) => /\/index\.ts$/.test(r))) barrelOnly.push(`${rel(p)} ← ${referrers.join(', ')}`)
  }
  kv('src/components 下的 .tsx 组件数', compFiles.length)
  kv('零引用（**真孤儿**）', orphans.length, orphans.join(', ') || '（无）')
  kv('只被 barrel 提到（可疑：导出了但没人用）', barrelOnly.length, barrelOnly.join('; ') || '（无）')
}

// ---------------------------------------------------------------- §6 设计系统采纳度

section('6. 设计系统采纳度（定义：**JSX 调用点**计数，不含 import 与组件自身定义）')

const adoption = {
  '`<Tag` 带 `color=`': /<Tag\b[^>]*\bcolor=/g,
  '`<StatusTag`': /<StatusTag\b/g,
  '`<AmountDisplay`': /<AmountDisplay\b/g,
  '`<StatCard`': /<StatCard\b/g,
  '`<Statistic`': /<Statistic\b/g,
  '`.toLocaleString(`': /\.toLocaleString\(/g,
  '`¥` 字面量': /¥/g,
  '`/ 100`（分转元形态）': /\/\s*100\b/g,
  '`borderRadius: 10` 字面量': /borderRadius:\s*10\b/g,
}
for (const [label, re] of Object.entries(adoption)) {
  let n = 0
  const files = new Set()
  for (const p of codeFiles) {
    const m = read(p).match(re)
    if (m) { n += m.length; files.add(rel(p)) }
  }
  kv(label, n, `${files.size} 个文件`)
}
{
  const adopters = codeFiles.filter((p) => /from '.*components\/ui'/.test(read(p)))
  kv("从 `components/ui` barrel 导入的文件数", adopters.length, adopters.map(rel).join(', '))
}

console.log('\n### 品牌色字面量分布（定义：全 src，含 .css）\n')
for (const color of ['#1677ff', '#6366f1', '#4f46e5', '#4338ca', '#eef2ff']) {
  const hits = []
  for (const p of allProductFiles) {
    const n = (read(p).match(new RegExp(color, 'gi')) ?? []).length
    if (n) hits.push(`${rel(p)}×${n}`)
  }
  kv(color, hits.reduce((s, h) => s + Number(h.split('×')[1]), 0), `${hits.length} 个文件`)
  if (hits.length) console.log(`  - ${hits.join(', ')}`)
}

// ---------------------------------------------------------------- §6b index.css 死类

section('6b. index.css 里的类选择器：antd 覆盖 vs 自有类，各自的使用率')

/*
  ⚠️ 定义陷阱：`.ant-btn-primary` 这类**本来就是 antd 生成的类名**，CSS 里写它是在"覆盖"，
  tsx 里天然不会引用它——把它算成"死类"会得出一个虚高的死代码比例（初版就是 65%）。
  真正要量的是**自有类**（`.page-header` / `.filter-bar` 这种由本仓库定义的）有没有人用。
  故此处按前缀切成三类，只有「自有类」那一类的 0 引用才叫死代码。
*/
{
  const cssRaw = read(path.join(SRC, 'index.css'))
  // 先剥注释：`.Item` 这类"类名"其实是注释里 `/* … Row/Col 内 Form.Item 的间距 */` 的
  // `Form.Item`。不剥的话自有类总数虚高、清单里混进假条目。CSS 注释不含字符串嵌套，
  // 直接整段剥离即可（此处不需要 check-perms.mjs 那种带引号感知的剥离——那是对 JS 而言的）。
  const css = cssRaw.replace(/\/\*[\s\S]*?\*\//g, ' ')
  const names = new Set()
  for (const m of css.matchAll(/\.([a-zA-Z][\w-]*)/g)) names.add(m[1])
  const haystack = codeFiles.map(read).join('\n')
  const all = [...names].sort()
  const antd = all.filter((n) => n.startsWith('ant-') || n.startsWith('anticon-') || n.startsWith('pro-'))
  const own = all.filter((n) => !antd.includes(n))
  const ownDead = own.filter((n) => !haystack.includes(n))

  kv('类选择器名总数', all.length)
  kv('其中 antd/pro 生成的（`.ant-*` / `.anticon-*` / `.pro-*`）', antd.length, '这些无 tsx 引用是**正常的**——它们由 antd 渲染，CSS 在此是覆盖')
  kv('其中**自有类**', own.length)
  kv('自有类里 0 处 tsx 引用的（**真死代码**）', ownDead.length, `${((ownDead.length / own.length) * 100).toFixed(0)}% of own`)
  console.log(`\n真死代码清单（自有类 × 0 引用，全量）：\n\n${ownDead.map((d) => `- .${d}`).join('\n')}\n`)
  const ownLive = own.filter((n) => haystack.includes(n))
  console.log(`仍在用的自有类（${ownLive.length}）：${ownLive.map((d) => `.${d}`).join(', ')}\n`)
}

// ---------------------------------------------------------------- §7 页面根元素（AST）

section('7. 页面根元素（定义：默认导出组件的返回表达式的最外层 JSX；走 TypeScript AST）')

function defaultExportNames(sf) {
  const names = []
  sf.forEachChild((n) => {
    if (ts.isFunctionDeclaration(n) && n.modifiers?.some((m) => m.kind === ts.SyntaxKind.DefaultKeyword) && n.name) {
      names.push(n.name.text)
    }
    if (ts.isExportAssignment(n)) {
      const find = (e) => {
        if (ts.isIdentifier(e)) names.push(e.text)
        else if (ts.isCallExpression(e)) e.arguments.forEach(find)
        else if (ts.isParenthesizedExpression(e)) find(e.expression)
      }
      find(n.expression)
    }
  })
  return names
}

function findComponent(sf, name) {
  let found = null
  const visit = (n) => {
    if (found) return
    if (ts.isVariableDeclaration(n) && ts.isIdentifier(n.name) && n.name.text === name) found = n.initializer
    else if (ts.isFunctionDeclaration(n) && n.name?.text === name) found = n
    ts.forEachChild(n, visit)
  }
  sf.forEachChild(visit)
  return found
}

/** 收集返回表达式，但不进入嵌套函数（排除 renderXxx 这类内部回调）。 */
function topLevelReturns(node) {
  const out = []
  const visit = (n) => {
    if (ts.isFunctionLike(n) && n !== node) return
    if (ts.isReturnStatement(n) && n.expression) out.push(n.expression)
    ts.forEachChild(n, visit)
  }
  if (node) ts.forEachChild(node, visit)
  if (node && ts.isFunctionLike(node) && node.body && !ts.isBlock(node.body)) out.push(node.body)
  return out
}

function unwrap(e) {
  while (ts.isParenthesizedExpression(e) || ts.isAsExpression(e) || ts.isNonNullExpression(e)) e = e.expression
  return e
}

function rootKinds(e) {
  e = unwrap(e)
  if (ts.isJsxFragment(e)) return ['fragment']
  if (ts.isJsxElement(e)) return [e.openingElement.tagName.getText()]
  if (ts.isConditionalExpression(e)) return [...new Set([...rootKinds(e.whenTrue), ...rootKinds(e.whenFalse)])]
  if (ts.isBinaryExpression(e) && e.operatorToken.kind === ts.SyntaxKind.AmpersandAmpersandToken) return rootKinds(e.right)
  return ['<other>']
}

const rootTally = new Map()
const rootByKind = new Map()
const rootUnresolved = []

for (const p of pageFiles) {
  const sf = ts.createSourceFile(p, read(p), ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX)
  let kinds = null
  for (const name of defaultExportNames(sf)) {
    const node = findComponent(sf, name)
    if (!node) continue
    const returns = topLevelReturns(node)
    if (!returns.length) continue
    kinds = rootKinds(returns[returns.length - 1])
    break
  }
  if (!kinds) { rootUnresolved.push(rel(p)); continue }
  const key = kinds.sort().join(' | ')
  rootTally.set(key, (rootTally.get(key) ?? 0) + 1)
  if (!rootByKind.has(key)) rootByKind.set(key, [])
  rootByKind.get(key).push(rel(p))
}

console.log('根元素种类 → 页数（**这是 plan 起草时数错的那一对数，以本节为准**）：\n')
for (const [kind, n] of [...rootTally.entries()].sort((a, b) => b[1] - a[1])) console.log(`- \`${kind}\`: ${n}`)
kv('无法解析的页面', rootUnresolved.length, rootUnresolved.join(', ') || '（无）')
console.log('\n明细：\n')
for (const [kind, list] of rootByKind) {
  console.log(`**${kind}** (${list.length}): ${list.join(', ')}\n`)
}

// ---------------------------------------------------------------- §8 测试耦合

section('8. 测试耦合（定义：每个测试文件里以下 API 的出现次数）')

const coupling = testFiles.map((p) => {
  const code = read(p)
  const c = (re) => (code.match(re) ?? []).length
  return {
    file: rel(p),
    within: c(/\bwithin\(/g),
    closest: c(/\.closest\(/g),
    getByText: c(/getByText\(/g),
    tagName: c(/\.tagName\b/g),
    toHaveClass: c(/toHaveClass\(/g),
  }
})
const sensitive = coupling.filter((c) => c.within || c.closest)
kv('测试文件数', coupling.length)
kv('含 `within(` 或 `closest(` 的文件数（DOM 结构敏感）', sensitive.length)
kv('其中含 `closest(` 的（**致命**：`closest("tr")` 为 null 会硬抛）', coupling.filter((c) => c.closest).length)
// 这两类必须**逐文件点名**，不能只给个数：它们是不改 DOM 就看不出来的"静默翻转"耦合，
// 铺开时要逐页确认。只给个数的话，它们会淹没在上面按 within 排序的长列表之外（铁证：初版
// 报告"1 个文件含 toHaveClass"却不印出来，看起来与另一份报告矛盾，实际只是没打印）。
const tagNameFiles = coupling.filter((c) => c.tagName)
const toHaveClassFiles = coupling.filter((c) => c.toHaveClass)
kv('含 `.tagName` 判别的文件数（`<a>`→`<Button>` 会**静默翻转**匹配）', tagNameFiles.length,
  tagNameFiles.map((c) => `${c.file}×${c.tagName}`).join(', ') || '（无）')
kv('含 `toHaveClass(` 判别的文件数', toHaveClassFiles.length,
  toHaveClassFiles.map((c) => `${c.file}×${c.toHaveClass}`).join(', ') || '（无）')
console.log('\n按 `within` 降序：\n')
for (const c of [...coupling].sort((a, b) => b.within - a.within).slice(0, 15)) {
  console.log(`- ${c.file}: within×${c.within} closest×${c.closest} getByText×${c.getByText} tagName×${c.tagName} toHaveClass×${c.toHaveClass}`)
}

// ---------------------------------------------------------------- §9 反空洞

section('9. 反空洞自检（护栏在输入为空时通过 = 没有护栏）')
const failures = []
if (pageFiles.length < 50) failures.push(`页面文件数 ${pageFiles.length} < 50，扫描根目录可能错了`)
if (formStats.forms < 30) failures.push(`<Form> 总数 ${formStats.forms} < 30，标签扫描器可能失效`)
if (modalCount < 20) failures.push(`<Modal> 总数 ${modalCount} < 20，标签扫描器可能失效`)
if (checks < 30) failures.push(`产出指标数 ${checks} < 30，脚本可能中途 return 了`)
if (!pickers.length) failures.push('DatePicker 无 width 命中 0 —— 该结论此前是明确的 4 处，为 0 说明扫描器坏了')
if (!formCols) failures.push('表单级 Col 为 0 —— 说明 <Form> 区域匹配失效')
if (!cssFiles.length) failures.push('.css 文件数为 0 —— 说明文件收集漏了 css')
for (const f of failures) console.log(`- ❌ ${f}`)
if (failures.length) {
  console.log('\n**自检未通过：上面的数字不得引用。**')
  process.exitCode = 1
} else {
  console.log(`- ✓ ${checks} 项指标全部产出，且各项下限自检通过`)
}
