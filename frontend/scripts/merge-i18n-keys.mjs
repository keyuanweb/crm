#!/usr/bin/env node
/**
 * 合并各并行 agent 产出的新键（一期 1.4）。
 *
 * <p>为什么需要它：批 2 的 6 个转换 agent 并行工作，语言文件必须**单写者**，
 * 否则彼此的写入会互相覆盖。于是 agent 只产出 `.i18n-keys/<组名>.json`
 * （扁平键路径 → {zh, en}），由本脚本一次性合并进 `src/i18n/zh-CN.ts` / `en.ts`。
 *
 * <p>为什么用 TypeScript 编译器而不是手写扫描器：文件里是**嵌套对象字面量**，
 * 而字符串值本身就含花括号（如 `'已提交 {{count}} 封'`），按花括号计数必然错位。
 * 项目已依赖 typescript（devDependency），直接用它的解析器拿精确节点位置，
 * 只在文本上做插入，**不重新序列化整个文件**——那会丢掉 2400 行里的注释与既有排版。
 *
 * <p>用法（在 frontend/ 下）：
 *   node scripts/merge-i18n-keys.mjs            # 试运行，只报告
 *   node scripts/merge-i18n-keys.mjs --write    # 实际写入
 */
import { readFileSync, readdirSync, writeFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import ts from 'typescript'

const here = dirname(fileURLToPath(import.meta.url))
const root = resolve(here, '..')
const keysDir = resolve(root, '.i18n-keys')
const WRITE = process.argv.includes('--write')

const LOCALES = [
  { name: 'zh-CN', file: resolve(root, 'src/i18n/zh-CN.ts') },
  { name: 'en', file: resolve(root, 'src/i18n/en.ts') },
]

// ---------------------------------------------------------------- 读取 agent 产出

const files = readdirSync(keysDir).filter((f) => f.endsWith('.json') && !f.startsWith('_'))
if (files.length === 0) {
  console.error(`没有找到任何键文件（${keysDir}/*.json）。`)
  process.exit(1)
}

/** @type {Map<string, {zh: string, en: string, from: string}>} */
const pending = new Map()
const problems = []

for (const file of files) {
  const group = file.replace(/\.json$/, '')
  let parsed
  try {
    parsed = JSON.parse(readFileSync(resolve(keysDir, file), 'utf8'))
  } catch (e) {
    problems.push(`${file}: JSON 解析失败 —— ${e.message}`)
    continue
  }
  for (const [key, value] of Object.entries(parsed)) {
    if (key.startsWith('translation.')) {
      problems.push(`${file}: 键 ${key} 多写了 translation. 前缀`)
      continue
    }
    if (!value || typeof value.zh !== 'string' || typeof value.en !== 'string') {
      problems.push(`${file}: 键 ${key} 缺少 zh 或 en 字段`)
      continue
    }
    if (value.zh.trim() === '' || value.en.trim() === '') {
      problems.push(`${file}: 键 ${key} 的 zh 或 en 为空串（空值与缺键等价，会被 i18n:check 拦下）`)
      continue
    }
    const prev = pending.get(key)
    if (prev) {
      // 两个 agent 撞同一个键：只有内容一致才算可合并，否则必须由人裁决
      if (prev.zh !== value.zh || prev.en !== value.en) {
        problems.push(
          `${key} 冲突：${prev.from} 是 {zh:${JSON.stringify(prev.zh)}, en:${JSON.stringify(prev.en)}}，` +
            `${group} 是 {zh:${JSON.stringify(value.zh)}, en:${JSON.stringify(value.en)}}`,
        )
      }
      continue
    }
    pending.set(key, { ...value, from: group })
  }
}

// ---------------------------------------------------------------- 文本编辑原语

/** 解析出 `const x = { ... }` 的对象字面量节点。 */
function objectLiteralOf(file) {
  const text = readFileSync(file, 'utf8')
  const source = ts.createSourceFile(file, text, ts.ScriptTarget.Latest, true)
  let found = null
  const visit = (node) => {
    if (
      ts.isVariableDeclaration(node) &&
      node.initializer &&
      ts.isObjectLiteralExpression(node.initializer)
    ) {
      found = node.initializer
      return
    }
    ts.forEachChild(node, visit)
  }
  visit(source)
  if (!found) throw new Error(`${file}: 未找到对象字面量`)
  return { text, node: found, source }
}

/** 在对象字面量里找同名属性（要求其值也是对象字面量）。 */
function childObject(node, name) {
  for (const prop of node.properties) {
    if (
      ts.isPropertyAssignment(prop) &&
      ts.isIdentifier(prop.name) &&
      prop.name.text === name &&
      ts.isObjectLiteralExpression(prop.initializer)
    ) {
      return prop.initializer
    }
  }
  return null
}

/** 属性名 → 值，取一份可比较的现有键（仅字符串叶子）。 */
function flatten(node, prefix = '', out = new Map()) {
  for (const prop of node.properties) {
    if (!ts.isPropertyAssignment(prop) || !ts.isIdentifier(prop.name)) continue
    const path = prefix ? `${prefix}.${prop.name.text}` : prop.name.text
    if (ts.isObjectLiteralExpression(prop.initializer)) {
      flatten(prop.initializer, path, out)
    } else if (ts.isStringLiteral(prop.initializer)) {
      out.set(path, prop.initializer.text)
    }
  }
  return out
}

/** 某节点所在行的缩进。 */
function indentOf(source, text, pos) {
  const lineStart = text.lastIndexOf('\n', pos - 1) + 1
  const line = text.slice(lineStart, text.indexOf('\n', pos))
  const m = line.match(/^[ \t]*/)
  return m ? m[0] : ''
}

/** 该对象字面量内部属性应有的缩进。 */
function childIndentOf(source, text, objectNode) {
  const props = objectNode.properties
  if (props.length > 0) return indentOf(source, text, props[props.length - 1].getStart(source))
  return indentOf(source, text, objectNode.getStart(source)) + '  '
}

/**
 * 生成一处插入：把 `blockLines`（已带绝对缩进）追加到 `objectNode` 属性列表末尾。
 *
 * <p>逗号必须自己数清楚：TypeScript 的 `PropertyAssignment.getEnd()` **已把尾逗号包含在内**，
 * 所以不能盲目再补一个（会得到 `,,`）。这里向前跳过空白与注释，只有确实看不到逗号时才补。
 */
function spliceAfterLast(source, text, objectNode, blockLines) {
  const props = objectNode.properties
  if (props.length === 0) {
    const openBrace = objectNode.getStart(source) + 1
    const outerIndent = indentOf(source, text, objectNode.getStart(source))
    return { pos: openBrace, text: `\n${blockLines.join('\n')}\n${outerIndent}` }
  }
  const afterLast = props[props.length - 1].getEnd()

  // 向前找下一个有效字符，跳过空白与行/块注释
  let i = afterLast
  let sawComment = false
  for (;;) {
    const rest = text.slice(i)
    const ws = rest.match(/^(\s+)/)
    if (ws) {
      i += ws[1].length
      continue
    }
    if (rest.startsWith('//')) {
      i += rest.indexOf('\n')
      sawComment = true
      continue
    }
    if (rest.startsWith('/*')) {
      i += rest.indexOf('*/') + 2
      sawComment = true
      continue
    }
    break
  }

  if (text[i] === ',') {
    // 尾逗号已在（且 getEnd() 已含之）；若中间夹了注释，插到逗号之后以免把注释挤走
    return { pos: i + 1, text: `\n${blockLines.join('\n')}` }
  }
  return { pos: sawComment ? afterLast : afterLast, text: `,\n${blockLines.join('\n')}` }
}

/** 把 agent 的扁平键折成嵌套树：{name: subtree} 与 {name: 文案} 混存。 */
function buildTree(entries) {
  const tree = new Map()
  for (const [key, value] of entries) {
    const segs = key.split('.')
    let node = tree
    for (const seg of segs.slice(0, -1)) {
      if (!node.has(seg)) node.set(seg, new Map())
      node = node.get(seg)
      if (!(node instanceof Map)) throw new Error(`键 ${key} 与另一个键路径冲突`)
    }
    node.set(segs[segs.length - 1], value)
  }
  return tree
}

/** 按仓库既有风格输出单引号字符串（语言文件通篇单引号）。 */
function quote(value) {
  const escaped = value
    .replace(/\\/g, '\\\\')
    .replace(/'/g, "\\'")
    .replace(/\r?\n/g, '\\n')
  return `'${escaped}'`
}

/** 渲染子树为带绝对缩进的属性行。 */
function renderTree(tree, indent) {
  const out = []
  for (const [name, value] of tree) {
    if (value instanceof Map) {
      out.push(`${indent}${name}: {`, ...renderTree(value, indent + '  '), `${indent}},`)
    } else {
      out.push(`${indent}${name}: ${quote(value)},`)
    }
  }
  return out
}

/**
 * 递归落位：能进已有命名空间的进已有命名空间，缺的命名空间就地新建。
 * @returns 插入操作数组（含绝对偏移量），由调用方自深向浅应用。
 */
function planEdits(source, text, objectNode, tree) {
  const edits = []
  const direct = new Map() // 直接追加到本节点的叶子与新块
  for (const [name, value] of tree) {
    if (value instanceof Map) {
      const child = childObject(objectNode, name)
      if (child) {
        // 已有该命名空间：递归进去补（深层的偏移量更大，先应用，故先收集）
        edits.push(...planEdits(source, text, child, value))
        continue
      }
    }
    direct.set(name, value)
  }
  if (direct.size > 0) {
    const childIndent = childIndentOf(source, text, objectNode)
    edits.push(spliceAfterLast(source, text, objectNode, renderTree(direct, childIndent)))
  }
  return edits
}

// ---------------------------------------------------------------- 逐语言合并

for (const locale of LOCALES) {
  const { text, node, source } = objectLiteralOf(locale.file)
  const existing = flatten(node)
  const missing = [...pending.entries()].filter(([key]) => !existing.has(key))

  if (missing.length === 0) {
    console.log(`\n${locale.name}: 无新增键（${existing.size} 键已就绪）`)
    continue
  }

  // 值与原文件不一致的既有键：不覆盖，报给人看
  const conflicts = [...pending.entries()]
    .filter(([key, v]) => existing.has(key) && existing.get(key) !== (locale.name === 'zh-CN' ? v.zh : v.en))
    .map(([key, v]) => `${key}: 现有 ${JSON.stringify(existing.get(key))} vs 提案 ${JSON.stringify(locale.name === 'zh-CN' ? v.zh : v.en)}`)

  // 按「父路径」分组：父路径就是要插入的块
  const byParent = new Map()
  for (const [key, value] of missing) {
    const segs = key.split('.')
    const parent = segs.slice(0, -1).join('.')
    if (!byParent.has(parent)) byParent.set(parent, [])
    byParent.get(parent).push([segs[segs.length - 1], locale.name === 'zh-CN' ? value.zh : value.en])
  }

  console.log(`\n${locale.name}: 新增 ${missing.length} 键，分属 ${byParent.size} 个父路径`)
  for (const [parent, entries] of [...byParent.entries()].sort()) {
    console.log(`  ${parent}  (${entries.length})`)
  }
  if (conflicts.length > 0) {
    console.log(`  ⚠ 与现有值不一致（不覆盖，请人工裁决）：\n    ${conflicts.join('\n    ')}`)
  }

  if (!WRITE) continue

  const tree = buildTree(
    missing.map(([key, value]) => [key, locale.name === 'zh-CN' ? value.zh : value.en]),
  )
  // 插入是纯文本拼装，必须**自深向浅**应用：先改偏移量大的，前面节点的位置才不会失效
  const edits = planEdits(source, text, node, tree).sort((a, b) => b.pos - a.pos)

  let out = text
  for (const edit of edits) out = out.slice(0, edit.pos) + edit.text + out.slice(edit.pos)
  writeFileSync(locale.file, out)
  console.log(`  ✓ 已写入 ${locale.file}`)
}

if (problems.length > 0) {
  console.error(`\n需要处理的问题（${problems.length}）：`)
  for (const p of problems) console.error(`  - ${p}`)
}

if (!WRITE) console.log('\n（试运行。确认后用 --write 实际写入。）')
