#!/usr/bin/env node
/**
 * UI 规范校验（088）。
 *
 * <p>本脚本存在的理由，与 `check-perms.mjs` 那条**同源**：这一批违规全是「静默失效」。
 * <ul>
 *   <li>品牌色字面量写错一个字符，编译通过、界面照常出图，只是那个按钮悄悄地还是旧的蓝；
 *   <li>`required: true` 漏了 `message`，表单还是会拦住你，只是错误提示是 antd 的默认英文；
 *   <li>`placeholder` 写成裸中文，中英文界面下它**永远是中文**，而没有任何测试会红；
 *   <li>组件写完却没人引用，文件在、代码在、功能不在，任何门禁都不会说话。
 * </ul>
 * 这些都不是"会崩"的问题，是"会烂"的问题——只能靠护栏，不能靠记性。
 *
 * <p>与 `i18n:check` / `menu:check` / `perms:check` 同形：`pnpm ui:check`，CI 已接入。
 *
 * <h3>为什么是独立脚本，而不是往 check-perms.mjs 里加规则</h3>
 * 后者管的是"挂错权限码导致按钮静默消失"，与 UI 规范**受众不同、白名单生命周期不同**。
 * 耦合会让一次 UI 白名单改动因无关原因搞红权限门禁，而**会因无关原因转红的门禁会被绕过**。
 *
 * <h3>两档机制（`--strict`）——**已于 2026-09-15 退役**（088 T045）</h3>
 * 这里原本有「先宽后紧」两档：**error**（R1/R4/R5/R6/R7，默认就红，白名单冻结既存债）与
 * **warn → error**（R2 / R3，躲在 `--strict` 后面，等 P3 逐页把债还到零再毕业）。
 * 现在**七条规则全在默认档**，`--strict` 这个开关**已删除**（连同 `package.json` 的
 * `ui:check:strict`）——留着一个什么也不改变的开关，会让"它看起来更严"变成一个假象。
 *
 * <p>两条毕业记录：
 * <ul>
 *   <li><b>R2</b>（承载表单的 Modal 必须显式定宽）2026-09-15 毕业（**T040**）：22 处清零。
 *   <li><b>R3</b>（表单内 `<Col>` 不得只写 `span`）2026-09-15 毕业（**T045**）：计数由
 *       **89 → 2 → 0**（T042 还 87 处、T041 还 2 处），`allowed` 始终为 `null` ⇒ 零容忍。
 *       两条都**没有**走过「整档切换」，而是**逐条毕业**——那一档在 R3 还有 89 处债时会把它们
 *       也变成失败，于是先还完的 R2 会被后还的 R3 一直拖着不生效。
 * </ul>
 * <p>一个**今天没有白名单**的规则（`allowed === null`）意味着"计数必须归零"，
 * 而不是"登记下来慢慢还"——写死一份 ~40 个文件的长白名单没人会维护，
 * 而不被维护的白名单**等价于没有门禁**。这正是 R2/R3 宁可要零容忍的理由。
 *
 * <p>⚠️ **CI 跑的一直是默认档**（`.github/workflows/ci.yml` 的 `pnpm run ui:check`）。
 * 所以 R3 毕业的意义不只是"门禁更严"，而是 **R3 从今天起第一次进 CI 强制**——
 * 它此前的 89 处债是在 CI 视野之外的。
 *
 * <h3>白名单必须是**债务台账**，不是批准清单</h3>
 * R1 的 32 处品牌色字面量**不代表它们是对的**——它们是本批次明确留给 P3/P4 的既存债
 * （P1 的退出判据是"零调用点改动"，不该顺手改 11 个页面的配色）。
 * 这个数在 P2 T031 由 33 降到 32（`InvoiceListPage` 的统计数字色随 `StatCard` 一起收掉），
 * **销账的方式就是删掉条目本身**——这正是下面那条双向校验存在的理由。
 * 之所以现在就把它们登记下来，是因为**登记下来的债才会单调收缩**：
 * `{file, count, reason}` 按文件+命中数**双向**校验，多了说明新增违规、少了说明有人还了债却没销账。
 *
 * <p>用法：`pnpm ui:check`（唯一的档，2026-09-15 起不再有 `--strict`）。
 */
import { readFileSync, readdirSync, statSync } from 'node:fs'
import { dirname, join, relative, sep } from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
const SRC = join(ROOT, 'src')

// 归一化路径分隔符，让白名单在 Windows / Linux 上都能匹配（与 check-perms.mjs 同）。
const rel = (p) => relative(ROOT, p).split(sep).join('/')

// ---------------------------------------------------------------- 文件收集

const walk = (dir, filter, out = []) => {
  for (const name of readdirSync(dir)) {
    const full = join(dir, name)
    if (statSync(full).isDirectory()) walk(full, filter, out)
    else if (filter(full)) out.push(full)
  }
  return out
}

const isTestFile = (p) => /\.test\.tsx?$/.test(p)
const isScaffold = (p) => rel(p).startsWith('src/test/')

/**
 * 产品源码 = `src/**` 下的 ts/tsx/css，**排除测试与测试脚手架**。
 *
 * <p>与 `check-perms.mjs` 同一条理由：测试不决定界面长什么样，
 * 而测试里出现一个颜色/文案常量往往是**断言**，把它判红只会逼人加白名单。
 */
const allFiles = walk(SRC, (p) => /\.(ts|tsx|css)$/.test(p) && !isTestFile(p) && !isScaffold(p))
const tsxFiles = allFiles.filter((p) => p.endsWith('.tsx'))

// ---------------------------------------------------------------- 标签扫描器
// 与 specs/088-frontend-layout-consistency/measure-ui-baseline.mjs **同源**，此处为副本。
// 刻意不抽公共模块：那个脚本要能在 specs/ 下独立重跑（它是取证脚本，不是构建产物的一部分），
// 而 scripts/ 不能反向依赖 specs/。两份都要改时，measure-ui-baseline.mjs 的自我自检会同时报警。

/**
 * 找出 tagName 的所有开/闭标签事件，带**真实位置**。
 *
 * <p>逐字符扫描而非正则：JSX 属性值里可以出现 `>`（如 `a > b`），正则会在那里截断，
 * 把后面的属性漏掉——那样数出来的「缺 width 的 Modal」不可信。
 * 不支持正则字面量（本仓库属性里没有这种写法；遇到计数异常时**先怀疑这里**）。
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

    const isClose = atClose !== -1 && (atOpen === -1 || atClose < atOpen)
    const at = isClose ? atClose : atOpen
    // ⚠️ 两种针长度不同（`</Form` 比 `<Form` 多一个 `/`），必须各按各的长度往后看一格。
    // 一律用 openNeedle.length 会让 `</Form>` 看到的"后一个字符"是 `m`——恰好是标识符字符，
    // 于是被整体匹配守卫判成 `</FormItem` 而**整个丢弃**：闭标签一个都记不下。
    const after = code[at + (isClose ? closeNeedle.length : openNeedle.length)]

    // 标签名必须整体匹配：避免 `<Form` 命中 `<FormItem`
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

    let j = at + openNeedle.length
    let brace = 0
    let quote = null
    let end = -1
    while (j < code.length) {
      const c = code[j]
      if (quote) {
        if (c === '\\') {
          j += 2
          continue
        }
        if (c === quote) quote = null
        j++
        continue
      }
      if (c === "'" || c === '"' || c === '`') {
        quote = c
        j++
        continue
      }
      if (c === '{') {
        brace++
        j++
        continue
      }
      if (c === '}') {
        brace--
        j++
        continue
      }
      if (c === '>' && brace === 0) {
        end = j
        break
      }
      j++
    }
    if (end === -1) break
    const text = code.slice(at, end + 1)
    out.push({ kind: text.endsWith('/>') ? 'self' : 'open', text, index: at })
    i = end + 1
  }
  return out
}

/** 成对区域 `[start, end)`；自闭合标签不产生区域。 */
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

/** 从标签原文取属性：`attr="x"` / `attr={x}` / `attr`（裸布尔）。null = 属性不存在。 */
function attrOf(tag, name) {
  const m = tag.match(new RegExp(`\\b${name}\\s*=\\s*(?:"([^"]*)"|'([^']*)'|\\{([\\s\\S]*?)\\})`))
  if (m) return m[1] ?? m[2] ?? m[3]
  return new RegExp(`\\b${name}(?=[\\s/>])`).test(tag) ? '' : null
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
      if (ch === '/' && next === '/') break
      out += ch
    }
    lines.push(out)
  }
  return lines
}

const lineOf = (code, index) => code.slice(0, index).split('\n').length

/**
 * 读文件一次，给出**去注释后**的文本（`code` 是字符串、`lines` 是逐行数组）。
 *
 * ## 为什么 `code` 也必须去注释（2026-09-15 订正）
 *
 * 原先这里返回的 `code` 是**原文**，只有 `lines` 去过注释。于是 R4/R5/R6 用的是 `lines`（干净），
 * 而 **R2/R3 与 `formItemTags()` 用的是 `code`（含注释）**——两条口径并存，且**没人知道**。
 *
 * 后果不是"多算几处"，而是**规则会去管注释里的散文**，产生一种**代码改不掉的红**：
 * 一篇解释「原本这里是 `<Col span={12}>`，现已换成 `FormGrid`」的注释，会被 R3 记成违规
 * ——**写得越清楚越红**，唯一的"修复"办法是把说明删掉。R3 正要（T045）变成默认档零容忍规则，
 * 那样的门禁会逼着人删文档。且**本仓库真出现过**：`CustomerListPage.tsx` 里 P2 转换时写的那段
 * 说明，就是 R3 的 90 处命中之一（第 413 行，命中的"代码"是注释文本）。
 *
 * 现在两条口径合一：**所有规则都只看代码**。字符串字面量仍被保留（`stripComments` 只去
 * `//`、`/* *​/` 与 JSX 的 `{/* *​/}`），所以藏在字符串里的违规照旧会被抓到。
 *
 * **改动面已实测**（改动前后逐条对账，不是推断）：只有 R3 动了（命中 90 → **89**、
 * 候选点 92 → **89**），R1 32/32、R4 5/148、R5 6/149、R6 10/10、R7 0/21、R2 0/55 **逐数不变**。
 * 行号不受影响：`stripComments` 逐行产出（一进一出），故 `lineOf` 报的行号与原文一致。
 */
function readSource(file) {
  const raw = readFileSync(file, 'utf8')
  const lines = stripComments(raw)
  return { code: lines.join('\n'), lines }
}

// ---------------------------------------------------------------- 白名单

/**
 * R1：品牌色字面量的既存债。**这是一份债务台账，不是批准清单**——
 * 32 处全部应当被主题 token / `var(--color-primary)` 取代，
 * 只是 P1 的退出判据是"零调用点改动"，故冻结在此、随 P3/P4 逐页销账。
 *
 * <p>**绝不能**把这份清单当成"可以 sed 一把 `#1677ff` → `#6366f1`"的许可：
 * 下面前两条根本不是品牌色，替换它们是一次现成的生产事故（见各自 reason）。
 */
const R1_ALLOWED = [
  {
    file: 'src/types/usageMap.ts',
    count: 11,
    reason:
      '**不是品牌色**：这是「使用图谱」的演示数据（流程图节点的配色），`color` 是数据字段不是样式。' +
      '把它替换成品牌色会改变演示图谱的观感，而那与主题统一无关',
  },
  {
    file: 'src/pages/landing/LandingPageView.tsx',
    count: 1,
    reason:
      '**不是品牌色**：租户自配主题的兜底值 `lp.themeColor || \'#1677ff\'`。' +
      '这里要的是"没有一个说得过去的兜底"，替换成 Indigo 会把租户未配置时的默认外观改掉',
  },
  {
    file: 'src/pages/dataVision/components/FunnelChart.tsx',
    count: 1,
    reason: '大屏自绘部分，属 088 非目标（"FunnelChart / 大屏自绘不动"）',
  },
  {
    file: 'src/pages/stats/DashboardPage.tsx',
    count: 8,
    // 093 复测：删掉「最近活动」卡（原 ActivityFeed 的 Timeline 色，−1），
    // 新增「跟进活动」「客户分析」两张卡的统计数字色（+2）⇒ 7 → 8。
    // 取实测值，不取预测值：白名单是双向校验的，陈旧条目会立刻转红（本次即由它抓出）。
    reason:
      '图表调色板与统计数字色，与 FunnelChart 同族。' +
      '它们是**一整套连续色阶**里的一个（同族还有 #69b1ff/#a0c4ff/#d6e4ff），单独换一个色相会让色阶明显断裂，' +
      '故要连同整条色阶一起决定——属 P4 视觉清扫，不在 P1',
  },
  {
    file: 'src/pages/map/UsageMapPage.tsx',
    count: 4,
    reason: '同上：使用图谱页面自身（节点默认色 + hover 边框色），与 types/usageMap.ts 的演示数据同一套',
  },
  {
    file: 'src/App.tsx',
    count: 2,
    reason: '品牌强调色的字面量（侧栏图标色 + 头像底色）。应改用主题 token，P3/P4 逐页迁移',
  },
  {
    file: 'src/components/CommentSection.tsx',
    count: 2,
    reason: '同上（评论图标色 + 头像底色）',
  },
  {
    file: 'src/components/InstallPrompt.tsx',
    count: 1,
    reason: '同上（安装提示图标色）',
  },
  {
    file: 'src/pages/LoginPage.tsx',
    count: 1,
    reason: '同上（登录页左侧品牌渐变，另有 #0958d9/#003eb3 两个同族色，需整组决定）',
  },
  {
    file: 'src/pages/tasks/TaskListPage.tsx',
    count: 1,
    reason: '同上（待办统计数字色）',
  },
  {
    file: 'src/pages/visits/VisitListPage.tsx',
    count: 1,
    reason: '同上（计划拜访统计数字色）',
  },
]

/**
 * R4：`required: true` 却没有 `message`（**这是真缺陷**：界面会显示 antd 的默认英文提示）。
 *
 * <p>5 处都**不需要新 i18n 键**——`common.message.required` = `请输入{{field}}` 已存在，
 * 修法是 `rules={[{ required: true, message: t('common.message.required', { field: t('<该字段的 label 键>') }) }]}`
 * （每个字段的 label 键就在同一行上）。之所以不在 P1 就修：P1 的退出判据是
 * **"零调用点改动，72 个测试文件全绿"**——那条判据是整个阶段"不合口味就整体删掉"的保险，
 * 不该为了 5 处提示文案破例。随 P3 逐页销账。
 */
const R4_ALLOWED = [
  {
    file: 'src/pages/calls/CallRecordPage.tsx',
    count: 2,
    reason: '债（非正当写法）：第 224/229 行。修法见上方注释；该页另带 2 处同类问题，是 rule 6 的天然首批',
  },
  {
    file: 'src/pages/custom-object/CustomObjectListPage.tsx',
    count: 1,
    reason: '债（非正当写法）：第 179 行，`Form.List` 行项目内的 `type` 下拉',
  },
  {
    file: 'src/pages/marketing/EmailCampaignPage.tsx',
    count: 1,
    reason: '债（非正当写法）：第 254 行 `sourceType`',
  },
  {
    file: 'src/pages/roles/RoleListPage.tsx',
    count: 1,
    reason: '债（非正当写法）：第 326 行 `dataScope`',
  },
]

/**
 * R5：必填却没有 `label`。
 *
 * <p>两处都是**有意为之的版式**，不是遗漏：`Form.List` 的行项目靠 `placeholder` 自证，
 * 加了 label 会让每一行都多出重复文字；登录页同理（前缀图标 + placeholder 即字段标识）。
 * 这类"加 label 反而更糟"的情形正是白名单存在的理由。
 */
const R5_ALLOWED = [
  {
    file: 'src/pages/custom-object/CustomObjectListPage.tsx',
    count: 3,
    reason:
      '`Form.List` 的行项目（字段名 / 显示名 / 类型）。靠 `placeholder` 标识是刻意的：' +
      '加了 label 会让每一行都重复一遍同样的文字，反而更难扫读',
  },
  {
    file: 'src/pages/LoginPage.tsx',
    count: 5,
    reason:
      '登录页的 username / password / captchaCode：刻意无 label，靠前缀图标 + placeholder 标识。' +
      '加 label 会改变登录页（最显眼的一屏）的版式，且与主题统一无关。' +
      '082 增至 5 处：二次验证那一步的 code / recoveryCode 沿用**同一屏的同一版式**' +
      '（各自带前缀图标 + placeholder），且两者互斥、同一时刻只挂载一个 —— ' +
      '给它们单独加 label 会让"密码三步走"的这一屏出现前两步没有的表头。' +
      '⚠️ 注意这个理由**不外溢**：个人中心那两个 2FA 弹窗是带 label 的（那边是表单弹窗，不是登录页）',
  },
]

/**
 * R6：裸字符串 `placeholder` / `aria-label`。
 *
 * <p>⚠️ 这条规则实测 **11 处，其中 9 处不该翻译**——它们是**格式/单位示例**
 * （`sales@corp.com`、`Currency`、`{"status": "active"}`、`BUDGET_APPROVAL`…）：
 * 中文界面下把示例改写成中文，反而让人看不出该填什么格式。
 * 也就是说"placeholder 必须走 `t()`"这条规则**从第一天起就需要白名单**，
 * 这是 088 的 plan 把它列为 error 时没有预见的（起草时估的是 8 处、且未区分性质）。
 * 真正是缺陷的只有 2 处 aria-label，见下。
 *
 * <p>⚠️ **2026-09-16 订正（097）：上面那两个数（11 / 9）已不成立，实测是 10 / 8。**
 * **原文逐字保留在上、不改写**——它是写下时的真实读数。差 1 的**根因已查明：不是算错，是漂移**。
 * `src/pages/products/ProductListPage.tsx` 那条的理由自己就写着「条目由 **2** 处收成 **1** 处」：
 * 2026-09-13 的 T035 复核把 `Currency` 改走了 `t()`（改用新键 `pages.product.list.priceCurrencyPlaceholder`），
 * 于是**命中 11 → 10、其中不该翻译的 9 → 8**，而**块头的这两个聚合数没有跟着回改**（该条目改了自己，块头没改）。
 * **两处真缺陷没变**（`App.tsx` 与 `LoginPage.tsx` 的 aria-label，见下），`2 + 8 = 10` 对得上。
 *
 * <p>**读者可自证**（`R6_ALLOWED` 才是当前的事实，别采信本注释里的数）——
 * 以下两条命令的输出即为「条目数」与「count 合计」：
 *
 * <p>`awk '/^const R6_ALLOWED = \[/,/^\]/' scripts/check-ui.mjs | grep -c "file:"` ⇒ **6**
 *
 * <p>`awk '/^const R6_ALLOWED = \[/,/^\]/' scripts/check-ui.mjs | grep -o "count: [0-9]*" | awk '{s+=$2} END{print s}'` ⇒ **8**
 *
 * <p>⚠️ **2026-09-16 第四次订正（098 提交 6）：`9 / 7` → `8 / 6`。**
 * **上面那两段（含 097 与 098 提交 3 的订正）原文逐字保留、不改写** —— 它们是写下时的真实读数。
 * 本次变化**不是漂移，是 098 本批的最后一步**：`src/App.tsx` 的 `aria-label="折叠/展开菜单"`
 * 已改走 i18n 键 `app.toggleMenu` ⇒ **必须摘除该条目**，否则双向校验会因"命中数少于登记数"当场转红。
 * 至此 `R6_ALLOWED` 里**两处 i18n 真缺陷已全部清零**，剩下 6 条按各自理由**不是缺陷**
 * （格式示例 / `Price (CNY)` 这类待拍板的业务文案 / 既有设计）。
 *
 * <p>⚠️ **三个数互相不是一回事，别混**：`R6_ALLOWED` 的**条目数 = 7**（7 个文件）、
 * **count 合计 = 9**（9 处命中；`CANDIDATE_READINGS.R6` 仍是 **10**，它数的是**裸写法命中**、
 * **不是台账大小**，两者不等是正常的）、
 * `MIN_CANDIDATES.R6 = 1`（**反假绿**的下限，不是台账大小）。
 * **机器守的是 count 与命中**：块头注释里的数**没有任何断言看着它**——这正是它能悄悄漂移的原因，
 * 故本处**只能订正 + 留下复算命令**，不声称此处有护栏。
 * ⚠️ **摘的是台账条目，规则本身不动**：R6 仍看着这两处 —— **本注释写下的这一刻尚未观测**，
 * 098 的交付阶段以反向破坏 D6（把 `App.tsx` 的 aria-label 改回裸字符串）**应**使其转红；
 * **「应」不是「已」**，实测留痕以 `specs/098-i18n-zh-residue/falsification-evidence.md` 为准。
 */
const R6_ALLOWED = [
  {
    file: 'src/pages/mail/MailSyncPage.tsx',
    count: 3,
    reason: '**不是缺陷**：`sales@corp.com` / `imap.corp.com` / `smtp.corp.com` 是**格式示例**，翻译它们会让用户看不出该填什么',
  },
  {
    file: 'src/pages/products/ProductListPage.tsx',
    count: 1,
    reason:
      '**已复核（2026-09-13，T035），条目由 2 处收成 1 处**：原理由把这处写成"**不是缺陷**：' +
      '`Currency` / `Price (CNY)` 是单位/币种示例"——**对了一半**。' +
      '① `Currency` 已修：它是**字段提示词**（一个英文单词），不是 `sales@corp.com` 那种"填什么格式"的' +
      '示例，且同弹窗另 3 个 placeholder 早已走 `t()`。改用新键 `pages.product.list.priceCurrencyPlaceholder`。' +
      '② `Price (CNY)` **留下待你裁决，它不是 i18n 问题而是语义问题**：该行选的是**非基准币种**' +
      '（`fetchCurrencies` 过滤掉 `isBase`），而 `setProductPrice(productId, currencyCode, price)` 存的正是' +
      '**所选币种**的价 ⇒ 在 USD 行里提示"按 CNY 填"是**误导性文案**。正解是改成"按所选币种填写"一类，' +
      '但那是要拍板的业务文案，故本批次不改，见 research.md §13.3',
  },
  {
    file: 'src/pages/contacts/ContactListPage.tsx',
    count: 1,
    reason: '**不是缺陷**：第 307 行 `Default: Other` 是**默认值示例**，展示的是数据形态而非界面文案',
  },
  {
    file: 'src/pages/exports/ScheduledExportCreatePage.tsx',
    count: 1,
    reason: '**不是缺陷**：第 115 行 `{"status": "active"}` 是 **JSON 格式示例**，翻译它会破坏示例本身',
  },
  {
    file: 'src/pages/marketing/OnlineFormPage.tsx',
    count: 1,
    reason: '**不是缺陷**：第 295 行 `field` 是**字段名占位示例**（对应提交数据的 key，不是界面文案）',
  },
  {
    file: 'src/pages/settings/OpportunityStagePage.tsx',
    count: 1,
    reason: '**不是缺陷**：第 294 行 `BUDGET_APPROVAL` 是**阶段 code 示例**，值是后端枚举，不能翻译',
  },
]

/** R7：孤儿组件。 */
// 088 P4（T054）已把唯一一条登记销掉：`src/components/ContactsCard.tsx` 是零引用真孤儿，
// 已连同本条目一起删除。**空数组是当前的事实，不是占位**——R7 现有 0 处既存债。
// 保留数组本身是因为 `ruleDefs` 里 `allowed: R7_ALLOWED` 直接引用它。
const R7_ALLOWED = []

// ---------------------------------------------------------------- 规则实现

/** R1：品牌色字面量。 */
const BRAND_COLORS = ['#1677ff', '#6366f1', '#4f46e5', '#4338ca', '#eef2ff']
/** 真源所在，天然豁免：主题文件本身，以及 `:root` 变量表。 */
const R1_EXEMPT_PATHS = ['src/theme/', 'src/index.css']

function rule1() {
  const hits = []
  let candidates = 0
  for (const file of allFiles) {
    const key = rel(file)
    if (R1_EXEMPT_PATHS.some((p) => key.startsWith(p))) continue
    const { lines } = readSource(file)
    lines.forEach((line, idx) => {
      const lower = line.toLowerCase()
      for (const color of BRAND_COLORS) {
        let from = 0
        for (;;) {
          const at = lower.indexOf(color, from)
          if (at === -1) break
          candidates++
          hits.push({ file: key, line: idx + 1, text: line.trim() })
          from = at + color.length
        }
      }
    })
  }
  return { hits, candidates }
}

/** 找出所有 `<Form.Item>` 开/自闭合标签（带文件与行号）。 */
function formItemTags() {
  const out = []
  for (const file of tsxFiles) {
    const { code } = readSource(file)
    for (const e of scanTagEvents(code, 'Form.Item')) {
      if (e.kind === 'close') continue
      out.push({ file: rel(file), line: lineOf(code, e.index), text: e.text })
    }
  }
  return out
}

/** R4：`rules` 里声明了 `required: true`，却没有 `message`。 */
function rule4(items) {
  const hits = []
  let candidates = 0
  for (const it of items) {
    const rules = attrOf(it.text, 'rules')
    if (rules === null || !/required\s*:\s*true/.test(rules)) continue
    candidates++
    if (!/message\s*:/.test(rules)) hits.push(it)
  }
  return { hits, candidates }
}

/**
 * R5：必填却没有 `label`。
 *
 * <p>必填的两种写法都算：`rules={[{required: true}]}`，以及 `<Form.Item required>` 裸属性。
 */
function rule5(items) {
  const hits = []
  let candidates = 0
  for (const it of items) {
    const rules = attrOf(it.text, 'rules')
    const requiredProp = attrOf(it.text, 'required')
    const isRequired = (rules !== null && /required\s*:\s*true/.test(rules)) || requiredProp !== null
    if (!isRequired) continue
    candidates++
    const hasLabel = attrOf(it.text, 'label') !== null || attrOf(it.text, 'aria-label') !== null
    if (!hasLabel) hits.push(it)
  }
  return { hits, candidates }
}

/** R6：裸字符串 `placeholder` / `aria-label`（必须走 `t()`）。 */
const BARE_ATTR_RE = /\b(placeholder|aria-label)\s*=\s*("[^"]*"|'[^']*')/g

function rule6() {
  const hits = []
  let candidates = 0
  for (const file of tsxFiles) {
    const { lines } = readSource(file)
    lines.forEach((line, idx) => {
      for (const m of line.matchAll(BARE_ATTR_RE)) {
        candidates++
        hits.push({ file: rel(file), line: idx + 1, text: m[0] })
      }
    })
  }
  return { hits, candidates }
}

/**
 * R7：`src/components/**` 下的组件零**非测试**引用。
 *
 * <p>判据是"组件名（文件基名）是否出现在任何其它产品文件里"，**barrel 再导出算引用**——
 * `components/ui/StatCard.tsx` 只被 `components/ui/index.ts` 提到，那是正常的。
 */
function rule7() {
  const hits = []
  let candidates = 0
  const componentFiles = allFiles.filter(
    (p) => rel(p).startsWith('src/components/') && p.endsWith('.tsx') && !/[/\\]index\.tsx$/.test(p),
  )
  // 每个产品文件的去注释文本，供全局查找名字用（只建一次）。
  const sources = allFiles.map((p) => ({ key: rel(p), code: readSource(p).lines.join('\n') }))

  for (const file of componentFiles) {
    const key = rel(file)
    const name = key.slice(key.lastIndexOf('/') + 1).replace(/\.tsx$/, '')
    candidates++
    const referenced = sources.some((s) => s.key !== key && s.code.includes(name))
    if (!referenced) hits.push({ file: key, line: 1, text: `组件 ${name} 无任何非测试引用` })
  }
  return { hits, candidates }
}

/**
 * R2（**默认档**，2026-09-15 由 `--strict` 毕业）：承载表单的 `Modal` 必须显式定宽（或改用 `FormModal`）。
 *
 * <p>"含表单"的判据是**该 Modal 的区域里出现真实的 `<Form` 标签**——不是全文 grep，
 * 因为 `<FormItem` 也含 `<Form` 这三个字符。12 个不含表单的 Modal 不在本规则内。
 */
function rule2() {
  const hits = []
  let candidates = 0
  for (const file of tsxFiles) {
    const { code } = readSource(file)
    const openEvents = scanTagEvents(code, 'Modal').filter((e) => e.kind === 'open')
    const regionByStart = new Map(tagRegions(code, 'Modal').map((r) => [r.start, r]))
    for (const e of openEvents) {
      const region = regionByStart.get(e.index)
      if (!region) continue // 自闭合的 Modal 没有正文，不可能含表单
      const body = code.slice(region.start, region.end)
      if (scanTagEvents(body, 'Form').length === 0) continue
      candidates++
      if (attrOf(e.text, 'width') === null) {
        hits.push({ file: rel(file), line: lineOf(code, e.index), text: e.text })
      }
    }
  }
  return { hits, candidates }
}

/**
 * R3（**默认档**，2026-09-15 由 `--strict` 毕业——见文件头的两档机制一节）：表单内的 `<Col>` 不得只写 `span`。
 *
 * <p>判据：有 `span` 属性，且 `xs/sm/md/lg/xl/flex` 一个都没有。
 * 这正是"94% 的写死 span"那条实测（96/102）——320px 屏上它与视口断点无关，
 * 所以它既是响应式缺陷，也是 `FormGrid` 要取代的对象。
 */
function rule3() {
  const hits = []
  let candidates = 0
  for (const file of tsxFiles) {
    const { code } = readSource(file)
    const colTags = scanTagEvents(code, 'Col').filter((e) => e.kind !== 'close')
    if (colTags.length === 0) continue
    const formRegions = tagRegions(code, 'Form')
    if (formRegions.length === 0) continue
    for (const col of colTags) {
      if (!formRegions.some((r) => col.index > r.start && col.index < r.end)) continue
      candidates++
      if (attrOf(col.text, 'span') === null) continue
      const hasBreakpoint = ['xs', 'sm', 'md', 'lg', 'xl', 'flex'].some(
        (p) => attrOf(col.text, p) !== null,
      )
      if (!hasBreakpoint) hits.push({ file: rel(file), line: lineOf(code, col.index), text: col.text })
    }
  }
  return { hits, candidates }
}

/**
 * R8（**默认档**，2026-09-15 由 **094** 引入）：`Descriptions` 的 `column` 不得写死为**大于 1** 的数字。
 *
 * <p>由来：088 的 FR-015 立了「详情区块列数不得写死、按视口分档」这条规矩，
 * 但**它没有任何门禁**（FR-015 自己写着「既没有门禁规则、也没有独立的自动化用例」）。
 * 于是 T044 改完 4 个详情页之后，库里还留着 3 处旧写法（`SignSection` / `SurveyBlock` 的 `column={2}`、
 * `CustomerPortalPage` 服务状态块的 `column={3}`），谁都不会红——094 补掉它们，并把这件事钉成规则，
 * 免得同一个坑再踩第四次。
 *
 * <p>判据：`column` 的值若是**字面量数字且 > 1** ⇒ 命中。以下一律**放行**：
 * <ul>
 *   <li>`column={{ xs: 1, sm: 2, md: 3 }}` 这类**断点对象**——正是要的写法；
 *   <li>`column={1}`——单列是**最窄档**，机制上不可能因窄屏溢出；`CustomerPortalPage` 的查询结果面板
 *       就是这种刻意的设计（`specs/094-088-debt-closeout/spec.md` 的 FR-094-003 在册）；
 *   <li>**不写 `column`**——antd 的 `DEFAULT_COLUMN_MAP`（`xs:1 sm:2 md:3 lg:3 xl:3 xxl:3`）
 *       本身就是按视口分档的，省略它得到的是响应式，不是写死。
 * </ul>
 *
 * <p>⚠️ **边界：这是源码级护栏，不是渲染级**。只判**字面量数字**——
 * `column={someConst}` / `{...spread}` / 三元表达式**判不到**（既可能对也可能错，靠正则猜只会制造假红，
 * 而假红会被绕过）。这几类要管，得走 092 那种打真实布局引擎的用例。
 */
function rule8() {
  const hits = []
  let candidates = 0
  for (const file of tsxFiles) {
    const { code } = readSource(file)
    // `scanTagEvents` 按标签名**整体**匹配（`<Descriptions.Item` 不会被误算），且逐字符扫描
    // ⇒ **跨行**的开标签同样命中（094 的第一版 `grep` 就漏了跨行的 4 个，见 research.md §3）。
    for (const tag of scanTagEvents(code, 'Descriptions')) {
      if (tag.kind === 'close') continue
      candidates++
      const column = attrOf(tag.text, 'column')
      if (column === null) continue // 未写 ⇒ antd 默认档，响应式
      if (!/^\s*\d/.test(column)) continue // 断点对象 / 表达式 ⇒ 不判
      if (Number(column) > 1) {
        hits.push({ file: rel(file), line: lineOf(code, tag.index), text: tag.text })
      }
    }
  }
  return { hits, candidates }
}

// ---------------------------------------------------------------- 执行

const formItems = formItemTags()

const ruleDefs = [
  { id: 'R1', title: '品牌色字面量（应走主题 token / var(--color-primary)）', run: rule1, allowed: R1_ALLOWED },
  { id: 'R4', title: '`required: true` 必须带 `message`', run: () => rule4(formItems), allowed: R4_ALLOWED },
  { id: 'R5', title: '`required: true` 必须带 `label`（或 `aria-label`）', run: () => rule5(formItems), allowed: R5_ALLOWED },
  { id: 'R6', title: '禁裸字符串 `placeholder` / `aria-label`（必须走 `t()`）', run: rule6, allowed: R6_ALLOWED },
  { id: 'R7', title: '组件零非测试引用（孤儿组件）', run: rule7, allowed: R7_ALLOWED },
  { id: 'R2', title: '承载表单的 Modal 必须显式定宽', run: rule2, allowed: null,
    fix: '给 Modal 加 `width`，或改用 `@/components/ui` 的 `FormModal`（四档宽度 sm/md/lg/xl）。' },
  { id: 'R3', title: '表单内 `<Col>` 不得只写 `span`', run: rule3, allowed: null,
    fix: '把 `<Col span={N}>` 换成 `FormGrid`（或至少补 `xs/sm/md/lg` 断点）。见 components/ui/FormGrid.tsx 的使用纪律。' },
  { id: 'R8', title: '`Descriptions` 的 `column` 不得写死为大于 1 的数字', run: rule8, allowed: null,
    fix:
      '改成断点对象 `column={{ xs: 1, sm: 2, md: 3 }}`；**全宽项**（备注/签名/长文本这类整行字段）的 `span`' +
      '与该 `column` 的上限一致（`span={3}`）。若这里**就是要单列**，写 `column={1}`——那是最窄档，本规则不判它。' },
]

/**
 * 反假绿：**规则解析出的候选点为 0 时直接失败**。
 *
 * <p>"护栏在输入为空时通过等于没有护栏"——正则改坏、扫描器等错文件、路径变了，
 * 都会表现为"零违规、门禁全绿"，而这与"真的都合规了"**外观完全一致**。
 * 这一条抄自 `check-i18n.mjs` / `check-perms.mjs` 的现有措辞，是同一类教训。
 */
const MIN_CANDIDATES = {
  R1: 1, // 至少扫到过品牌色（含豁免路径里的真源）
  R4: 1, // 至少解析出过带 required 的 Form.Item
  R5: 1,
  R6: 1, // 至少见过一个**裸写法**的 placeholder / aria-label（走 t() 的不计入，故这里小是正常的）
  R7: 1, // 至少有一个 components/*.tsx
  R2: 1,
  // ⚠️ R3 的**零余量**在这里（094 补记实测解剖，免得下一个人把它误读成"探针坏了"）：
  //    全库 `<Col` **88** 处；其中位于 `<Form>` 区域内（= 本规则的候选点）的**只有 1 处**
  //    ——`pages/tags/TagListPage.tsx` 的色板 `Col`，它带 `flex`、无 `span`，本就合规；
  //    `<FormGrid` 用法已 **58** 处。⇒ 这条规则的**对象是被 FormGrid 取代殆尽的**，
  //    它离「无可判对象」只差那**一个** `Col` 被重构掉。那一天的红色**是对的**（一条判不到对象的规则
  //    已不再是护栏，该按 R2/T040、R3/T045 的先例**退役**），错的是下面那句只说了一半的失败信息。
  R3: 1,
  // R8（094 新增）的候选池 = 全库 `<Descriptions>` 开标签数，**今天实测 14**（单行 10 + 跨行 4）。
  // 取 12 = 今天 − 2（留给"合法删掉一个 Descriptions 区块"），
  // 同时 12 > 10 ⇒ 能抓住「扫描器只认单行开标签」这一档失效（那会掉到 10）——094 的探针**真的踩过**这个坑。
  R8: 12,
}

/**
 * 每条规则的候选点**历史实测读数**（2026-09-15，094 实测补记）——只用于**比对**，不参与判定。
 *
 * <p>用途：候选点掉到下限以下时，先拿这里的数比一比，再决定是**扫描器坏了**还是**规则该退役**。
 * 这两个成因的处置相反（见下面的失败信息），而在此之前，信息只印了前一种。
 */
const CANDIDATE_READINGS = {
  R1: 33, // 品牌色字面量（含 R1_EXEMPT_PATHS 里的真源）
  R4: 148, // 带 required 的 Form.Item（命中白名单 5 处）
  R5: 149, // 同口径再含"有 label 但无 required"的那些（命中 6 处）
  R6: 10, // **裸写法**的 placeholder / aria-label（走 t() 的不计入，故这个数小是正常的）
  R7: 21, // components/*.tsx 文件数（命中 0 = 无孤儿）
  R2: 55, // 承载表单的 Modal（命中 0）
  R3: 1, // 见上：FormGrid 已把对象取代殆尽
  R8: 14, // `<Descriptions>` 开标签（单行 10 + 跨行 4）
}

const problems = []

for (const rule of ruleDefs) {
  const { hits, candidates } = rule.run()

  if (candidates < MIN_CANDIDATES[rule.id]) {
    const reading = CANDIDATE_READINGS[rule.id]
    problems.push({
      kind: `${rule.id} 自检失败`,
      file: '-',
      detail: [
        `${rule.title}`,
        `本规则只解析出 ${candidates} 个候选点（下限 ${MIN_CANDIDATES[rule.id]}；` +
          `2026-09-15 的历史实测读数 ${reading ?? '未记录'}）。`,
      ],
      fix:
        '护栏在输入为空时通过等于没有护栏。但先分清是**哪一种**失败——两种成因的处置相反：\n' +
        `    ① **扫描器失效**（正则改坏、扫描范围跑偏、只看单行标签…）：修扫描器，候选点应回到上面那个历史读数。\n` +
        `    ② **规则已无可判对象**（如实测读数本就很小或已归零，例如 R3 只剩 1 个候选点、FormGrid 已取代它）：\n` +
        `       那这条规则**已经不再是一条护栏**，应按先例（R2 于 T040、R3 于 T045 逐条毕业；\`--strict\` 两档机制\n` +
        `       也因"不再改变任何行为"而整条删除）**退役它**——连同 MIN_CANDIDATES 条目、CANDIDATE_READINGS 条目\n` +
        `       与它的白名单一起删，**不是**把下限调低来放行。\n` +
        '    判据：把该规则的候选点用另一条独立的取法量一遍（`grep`/探针）。与扫描器读数一致 ⇒ 是 ②。',
    })
    continue
  }

  // `allowed === null` 的规则：**零容忍、无白名单**。今天有两条走这里：R2 与 R3
  // （两条都于 2026-09-15 从 `--strict` 毕业，见文件头的两档机制一节）。
  // 写死一份长白名单没人维护，而不被维护的白名单等价于没有门禁
  // ——所以这两条宁可要"计数必须归零"，不要"登记下来慢慢还"。
  if (rule.allowed === null) {
    for (const h of hits) {
      problems.push({
        kind: `${rule.id} ${rule.title}`,
        file: h.file,
        detail: [`第 ${h.line} 行：${h.text.slice(0, 120)}`],
        fix: rule.fix ?? '修复它，或把它降级成一条登记在册的规则。',
      })
    }
    continue
  }

  // error 档：按文件 + 命中数**双向**校验白名单。
  const byFile = new Map()
  for (const h of hits) {
    if (!byFile.has(h.file)) byFile.set(h.file, [])
    byFile.get(h.file).push(h)
  }
  const allowMap = new Map(rule.allowed.map((a) => [a.file, a]))
  for (const [file, list] of byFile) {
    const allowed = allowMap.get(file)
    if (!allowed) {
      problems.push({
        kind: `${rule.id} ${rule.title}`,
        file,
        detail: list.map((h) => `第 ${h.line} 行：${h.text.slice(0, 120)}`),
        fix:
          `修复它们；若确有正当理由，请在 check-ui.mjs 的 ${rule.id}_ALLOWED 中登记：\n` +
          `    { file: '${file}', count: ${list.length}, reason: '<为什么这里必须这样>' },`,
      })
    } else if (list.length !== allowed.count) {
      problems.push({
        kind: `${rule.id} 白名单陈旧`,
        file,
        detail: [
          `白名单登记 ${allowed.count} 处，实际命中 ${list.length} 处。`,
          ...list.map((h) => `第 ${h.line} 行：${h.text.slice(0, 120)}`),
        ],
        fix:
          list.length > allowed.count
            ? '该文件新增了违规。请修掉，或更新白名单的 count 与理由。'
            : `命中数少于登记数——可能有人还了债却没销账。请核对后把 count 改成 ${list.length}；若已归零则整条删除。`,
      })
    }
  }
  for (const a of rule.allowed) {
    if (!byFile.has(a.file)) {
      problems.push({
        kind: `${rule.id} 白名单陈旧`,
        file: a.file,
        detail: [`白名单登记 ${a.count} 处，实际一处也没命中（文件可能已改名，或债已还清）。`],
        fix: '删除这条白名单，或修正 file 路径。',
      })
    }
  }
}

// ---------------------------------------------------------------- 结论

console.log(
  `扫描 ${allFiles.length} 个产品文件（其中 ${tsxFiles.length} 个 tsx）、` +
    `${formItems.length} 个 Form.Item`,
)

if (problems.length > 0) {
  console.error(`\n✗ UI 规范校验失败：${problems.length} 处问题\n`)
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

const allowedTotal = ruleDefs.reduce((s, r) => s + (r.allowed ?? []).reduce((n, a) => n + a.count, 0), 0)
console.log(`✓ UI 规范校验通过（白名单内冻结的既存债 ${allowedTotal} 处，未新增违规）`)
