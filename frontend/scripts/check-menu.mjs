#!/usr/bin/env node
/**
 * 菜单生成物陈旧性校验（084）。
 *
 * <p>派生机制只有在「生成物被真的重生成过」时才有意义：改了 `MENU_TREE` 却忘了重跑生成器，
 * 界面会继续按旧定义渲染，而所有断言都会通过——这是比没有护栏更坏的状态（护栏给了虚假的安心）。
 *
 * <p>做法是「重新生成 → 与磁盘上的生成物逐字节比对」，与仓库既有的 `spotless:check` 同一形态：
 * 不判断「像不像最新的」，只判断「一不一样」。
 *
 * <p>用法：`pnpm menu:check`（CI 已接入，与 `i18n:check` 并列）。不一致时按提示跑 `pnpm menu:gen`。
 */
import { readFileSync } from 'node:fs'
import { countManifestItems, generateManifestSource, MANIFEST_PATH } from './gen-menu.mjs'

const expected = generateManifestSource()

// 防呆：生成器那边已有「解析结果过少即失败」的断言，这里再钉一次——
// 若生成物本身变得可疑（比如只剩几个菜单项），说明校验会在「输入为空」的情况下通过。
const itemCount = countManifestItems(expected)
if (itemCount < 50) {
  console.error(
    `✗ 生成结果只有 ${itemCount} 个菜单项，疑似解析失效（预期 50+）。` +
      '护栏在输入为空时通过等于没有护栏，故此处直接判失败——请检查 gen-menu.mjs 的解析。',
  )
  process.exit(1)
}

let actual
try {
  actual = readFileSync(MANIFEST_PATH, 'utf8')
} catch {
  console.error(`✗ 生成物不存在：${MANIFEST_PATH}\n    请运行：pnpm menu:gen`)
  process.exit(1)
}

if (actual === expected) {
  console.log(`✓ 菜单清单是最新的（${itemCount} 个菜单项，来源：RoleConstants.MENU_TREE）`)
  process.exit(0)
}

// 逐行报差异：只给「不一致」的结论对修复没有帮助，要指出具体是哪一行、差在哪。
const actualLines = actual.split('\n')
const expectedLines = expected.split('\n')
const diff = []
for (let i = 0; i < Math.max(actualLines.length, expectedLines.length); i++) {
  if (actualLines[i] !== expectedLines[i]) {
    diff.push({ line: i + 1, actual: actualLines[i], expected: expectedLines[i] })
  }
}

console.error(`✗ 菜单清单已陈旧：${MANIFEST_PATH}`)
console.error(`    与「从 MENU_TREE 重新生成的结果」有 ${diff.length} 行不一致。`)
for (const d of diff.slice(0, 20)) {
  console.error(`    第 ${d.line} 行`)
  console.error(`      磁盘上： ${d.actual === undefined ? '<无此行>' : d.actual}`)
  console.error(`      重生成： ${d.expected === undefined ? '<无此行>' : d.expected}`)
}
if (diff.length > 20) console.error(`    …另有 ${diff.length - 20} 行，从略`)

console.error('\n修复：跑 `pnpm menu:gen` 重生成生成物，再跑 `pnpm menu:check` 确认。')
console.error('若重生成后仍不一致，说明本脚本与生成器不同步——那是缺陷，请勿手工编辑生成物。')
process.exit(1)
