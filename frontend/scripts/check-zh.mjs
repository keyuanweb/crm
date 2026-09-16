#!/usr/bin/env node
/**
 * 源码硬编码中文校验（098）。
 *
 * <p>本脚本存在的理由，与 `check-ui.mjs` 那条**同源**：硬编码中文是**静默失效**的典型——
 * <ul>
 *   <li>文案写死成中文，**中文界面上一切正常**，切到英文那一处**永远是中文**，而没有任何测试会红；
 *   <li>`alt` / `aria-label` 这类**只有读屏软件和排查问题的人看得见**的文案，写死了更没人发现；
 *   <li>服务层抛出的中文 `Error.message`，会被页面 `message.warning(err.message)` **原样弹给用户**。
 * </ul>
 * 这些都不是"会崩"的问题，是"会烂"的问题——只能靠护栏，不能靠记性。
 *
 * <p>**本项之前，`frontend/scripts/` 下没有任何脚本看着"源码里的硬编码中文"**：
 * `check-i18n.mjs` 管的是**资源键**（双语对齐、空值、菜单双射、枚举标签键），
 * `eslint.config.js` 没有 i18n 插件。既有的扫描器 `frontend/.i18n-keys/find-hardcoded-zh.mjs`
 * **被 gitignore、未入库、未接 CI**（`git ls-files` 为空）⇒ 任何别的人/机器都算不出"多少处"。
 * 本脚本把同一套取法**搬进版本库并接上判据**，顺手修掉那个可复现性缺陷。
 *
 * <h3>为什么口径必须是 AST，不能是 `grep`</h3>
 * **本仓库的注释按约定就是中文**。同一口径下用 `rg` 宽区间正则在 `src/` 上量到
 * **2452 行 / 194 个文件**，其中 **1876 行（76.5%）行首即注释**——噪音大到无法判断。
 * 这里用 TypeScript 解析出 AST，**注释天然不在 AST 里**，只统计三类节点：
 * `ts.isStringLiteral` / `ts.isNoSubstitutionTemplateLiteral` / `ts.isJsxText`（`trim()` 非空）。
 * CJK 判定用 `/[一-鿿]/`（= **U+4E00–U+9FFF**），与既有扫描器**逐字相同**；跳过 `import` 声明；
 * 排除 `src/i18n/**`（那两份语言文件本身就是"中文文案的容器"）与 `*.test.*`。
 *
 * <h3>为什么是独立脚本，而不是往 check-i18n.mjs 里加规则</h3>
 * `check-i18n.mjs` 是**早期形制**（正则式、不剥注释、四道检查各自 `process.exit(1)`、
 * 失败不带行号、无白名单/无候选下限）。在里面加这条规则，要**同时重构它既有的四道检查**，
 * 一次改动里混进"加新规则"与"重构旧检查"两件事，**两边的回归都难以归因**。
 * 受众也不同：它管资源键，本脚本管源码字面量。**不合并、不重命名既有两个脚本。**
 *
 * <h3>⚠️ 本脚本是「服务层取词」的第一个先例（098 第 5 次提交）</h3>
 * `services/apiClient.ts` 与 `services/visitService.ts` 的文案**用户可见**
 * （`VisitListPage.tsx:148` 是 `message.warning((err as Error).message)`），
 * 但它们**拿不到 `useTranslation` 的 hook** ⇒ 改走 **i18next 单例**（`import i18n from '../i18n'` + `i18n.t(...)`）。
 * <p>**这件事必须点名，不许静默引入**：`i18n.t(` 在本脚本落地时**全仓零命中**，
 * 而 `specs/075-page-i18n/spec.md` 的 **FR-P01/FR-P02 只裁到「页面」、从没裁过服务层**——
 * 本项补的是那个空缺。**边界**：i18next 单例**只用于「非组件模块拿不到 hook」这一种情形**，
 * **组件/页面仍走 `useTranslation`**（FR-P01 不被动摇）。若 lint 或既有门禁因此报错，
 * 按 `plan.md` 风险表**回退到台账登记**，**不硬推**。
 *
 * <h3>台账是**债务台账**，不是批准清单</h3>
 * `ZH_ALLOWED` 里的条目**不代表它们是对的**。第一版**照现状登记全部 15 条 / 300 处**，
 * 于是门禁**落地即绿**，同时证明**台账覆盖了现状**（未登记的命中一条都没有）；
 * 此后每个清零提交**摘掉对应条目**——所以"清零"这件事**不是靠人记得改台账，
 * 而是不改台账就红**（见下面的三分支双向校验）。
 * <p>其中 **4 条是真源、非欠账**（`usageMap` / `menuManifest` / `App.tsx` 的 53 处元数据 /
 * `breadcrumbTrail`，理由逐条写在台账里且都**独立复核过**），其余各条是本批要清的债，
 * 随提交 3/4/5/6 逐条摘除（`App.tsx` 那条**不清零**，只把 count 由 57 降到 55）。
 * <p>⚠️ **本注释不再写「还剩几条债」这个数**——它每个清零提交都要改一次，而**没有任何断言看着它**
 * （机器守的是 `count` 与实跑命中，见下文），写在这里只会悄悄漂移。历史版本写过「另 11 条」
 * （第一版 15 条台账的算术：15 − 4），提交 3/4 各摘一批后这个数已经不对了 ⇒
 * **要数就数 `ZH_ALLOWED` 本身**，别采信本段。
 *
 * <h3>口径边界自证（信息性，**不进退出码**）</h3>
 * 任何扫描器对自己的盲区**自证不了**——漏掉的那项不会出现在结果里。所以本脚本在判完之后
 * **另跑一遍放宽口径**（同一批节点 + 全角标点/全角字母数字 + 带插值的模板串），
 * 打印一行"口径外另有 M 处"。**那一行不是门禁**：它不进 `problems`、不改退出码。
 * 已知盲区两类：**全角标点** `：，（）；`（在 U+4E00–9FFF 之外）与
 * **带插值的模板串** `` `${t('…')} ${n} 条` ``（是 `TemplateExpression`，不是 `NoSubstitutionTemplateLiteral`）。
 *
 * <p>用法：`pnpm zh:check`（门禁）；`node scripts/check-zh.mjs --list`（逐文件逐行清单，
 * 既是本批工作单，也是下一批的入口）。
 */
import { readFileSync, readdirSync, statSync } from 'node:fs'
import { dirname, join, relative, sep } from 'node:path'
import { fileURLToPath } from 'node:url'
import ts from 'typescript'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
const SRC = join(ROOT, 'src')
const LIST = process.argv.includes('--list')

// 归一化路径分隔符，让台账在 Windows / Linux 上都能匹配（与 check-ui.mjs 同）。
const rel = (p) => relative(ROOT, p).split(sep).join('/')

// ---------------------------------------------------------------- 口径

/** 主口径：与既有扫描器 `.i18n-keys/find-hardcoded-zh.mjs` **逐字相同**（= U+4E00–9FFF）。 */
const CJK = /[一-鿿]/
/**
 * 宽口径（次级，**只印不判**）：在主口径上补 **全角标点**（U+3000–303F）与
 * **全角字母数字**（U+FF00–FFEF）。用它跑同一批节点，多出来的那些就是"口径外"的一部分。
 */
const CJK_WIDE = /[一-鿿　-〿＀-￯]/

// ---------------------------------------------------------------- 扫描

const files = []
const walk = (dir) => {
  for (const entry of readdirSync(dir)) {
    const p = join(dir, entry)
    const st = statSync(p)
    // 跳过 src/i18n：那两份语言文件本身就是「中文文案的容器」，统计它毫无意义。
    if (st.isDirectory()) {
      if (rel(p) === 'src/i18n') continue
      walk(p)
    } else if (/\.(ts|tsx)$/.test(p) && !/\.test\./.test(p)) files.push(p)
  }
}
walk(SRC)

const results = []
/** 口径外的命中（宽口径多出来的 + 带插值的模板串），仅供打印。 */
const wideOnly = []
let candidates = 0

for (const file of files) {
  const text = readFileSync(file, 'utf8')
  const sf = ts.createSourceFile(
    file,
    text,
    ts.ScriptTarget.Latest,
    true,
    file.endsWith('.tsx') ? ts.ScriptKind.TSX : ts.ScriptKind.TS,
  )
  const hits = []
  const lineOf = (node) => sf.getLineAndCharacterOfPosition(node.getStart(sf)).line + 1

  const visit = (node) => {
    // `import` 声明：模块路径不会含中文（`import i18n from '../i18n'` 这类）。
    if (ts.isImportDeclaration(node) || ts.isImportEqualsDeclaration(node)) return

    if (ts.isStringLiteral(node) || ts.isNoSubstitutionTemplateLiteral(node)) {
      candidates++
      if (CJK.test(node.text)) hits.push({ line: lineOf(node), text: node.text })
      else if (CJK_WIDE.test(node.text)) {
        wideOnly.push({ file: rel(file), line: lineOf(node), text: node.text, why: '全角标点 / 全角字母数字' })
      }
    } else if (ts.isJsxText(node)) {
      const t = node.text.trim()
      // 纯空白的 JSX 文本节点（缩进换行）不是候选点，与既有扫描器同。
      if (t) {
        candidates++
        if (CJK.test(t)) hits.push({ line: lineOf(node), text: t })
        else if (CJK_WIDE.test(t)) {
          wideOnly.push({ file: rel(file), line: lineOf(node), text: t, why: '全角标点 / 全角字母数字' })
        }
      }
    } else if (ts.isTemplateExpression(node)) {
      // 带插值的模板串：只取**字面部分**（head + 各 span 的 literal），
      // 不取 `node.getText()`——否则 `${t('中文')}` 里的那个字符串会被重复算一次。
      const parts = [node.head, ...node.templateSpans.map((s) => s.literal)]
      for (const p of parts) {
        if (CJK_WIDE.test(p.text)) {
          wideOnly.push({ file: rel(file), line: lineOf(p), text: p.text.trim(), why: '带插值的模板串（字面部分）' })
        }
      }
    }
    ts.forEachChild(node, visit)
  }
  visit(sf)
  if (hits.length > 0) results.push({ file: rel(file), hits })
}

/**
 * 台账（**债务台账，不是批准清单**）。每条 `reason` 必填散文，且必须写明**类别**与
 * **其中是否含欠账**；含欠账的**必须点名**。
 *
 * <p>第一版**照现状登记全部 300 处**（2026-09-16 实测：300 处 / 15 文件）⇒ 门禁落地即绿。
 * 此后每个清零提交摘掉对应条目 —— **不清零就要红**（第三分支）。
 *
 * <p>⚠️ **顺序无所谓**（按 `file` 查表），但**别把 reason 写成批准语气**。
 */
const ZH_ALLOWED = [
  // ---- 四条**真源、非欠账**（理由逐条独立复核过，见 research.md §3）----
  {
    file: 'src/types/usageMap.ts',
    count: 142,
    reason:
      '**产品文案（演示图谱数据）⇒ 非欠账。** 本文件**头部注释自己就写着**「本文件里的中文字符串是刻意保留的，' +
      '不是漏翻」：这里存的是流程图节点标题/节点说明/角色说明这类产品文案（约 140 条），由**产品侧**维护，' +
      '工程侧自行翻译会与产品口径分叉。属 `075` FR-P03 排除的动态数据。⚠️ 将来替换时注意：页面' +
      '（`UsageMapPage`）现在**直接渲染** `flow.title` / `node.title` / `action.label`（未经过 `t()`）' +
      '⇒ 改成 i18n 键**必须同时改页面**，不能只改本文件。',
  },
  {
    file: 'src/constants/menuManifest.ts',
    count: 67,
    reason:
      '**生成物 ⇒ 降级真源、非欠账。** 本文件由 `scripts/gen-menu.mjs` 产出' +
      '（`:29 const OUT = resolve(here, …)`、`:280 writeFileSync(OUT, source)`，生成物进版本库以便评审与 diff）。' +
      '`title` 是菜单的**权威中文名**，`i18n/labelOf.ts#menuLabel()` 在**缺键时**拿它降级。' +
      '⚠️ **手改会被下次 `pnpm menu:gen` 覆盖**——要动就动生成器。',
  },
  {
    file: 'src/App.tsx',
    count: 55,
    reason:
      '**混合：53 处路由元数据（非欠账）+ 2 处界面真会显示的中文（欠账）。** ' +
      '53 处是路由表里的 `name:` 字段——界面渲染走 `MENU_MANIFEST` + `menuLabel()`，' +
      '那些 `name:` **只被 `check-i18n.mjs` 的路由枚举正则消费**，故非欠账。' +
      '⚠️ **另 2 处是真欠账，点名**（它们真的会显示在界面上）：' +
      '`渠道 ROI` 与 `工作流日志`（经 `SUB_PAGE_AFTER_MENU_KEY` 在 App.tsx 内被 `aliasRoute.name` 渲染）' +
      '——清零要给 route 加 `i18nKey` 并改那处取词（修法范式见 `components/breadcrumbTrail.ts` 的 `i18nKey`），' +
      '属**结构调整**，与那 53 处同处一段代码，**留给下一批**。' +
      '⚠️ **2026-09-16 订正（098 提交 6；上面这段的原文已按本节第一句改写，仅保留结论，逐条订正如下）**：' +
      '① 本条目原先写 count **57**（53 元数据 + 4 处欠账），其中 `aria-label="折叠/展开菜单"` 与 `label: \'中文\'` ' +
      '两条已由 098 清零 ⇒ **57 → 55**（53 + 2）。' +
      '② 原文写「后者的键是 `app.language.zh`」—— **错**。`app.language` 是同级那个**字符串**（『语言』这个标签），' +
      '语言项是它的两个**兄弟键** `app.zh` / `app.en`。**实证**：先按错路径改时，' +
      '`src/test/setup.ts` 的缺键抛错把 `App.render.test.tsx` 的 **7 个用例当场全打红**' +
      '（`i18n 缺键: app.language.zh（zh-CN）`）——这条护栏确实有牙。零新键的说法成立，只是键名要写成 `app.zh`/`app.en`。' +
      '③ 原文引的 `L417` / `L458` / `L645` / `L665` / `:542` 这些**行号已漂移**（同批在 App.tsx 里加了注释）' +
      '⇒ 此后**一律改用锚字符串**：`aria-label="折叠/展开菜单"`、`label: \'中文\'`、`渠道 ROI`、`工作流日志`、' +
      '`SUB_PAGE_AFTER_MENU_KEY`、`aliasRoute.name`。',
  },
  {
    file: 'src/components/breadcrumbTrail.ts',
    count: 2,
    reason:
      '**降级兜底中文名 ⇒ 非欠账。** `SUB_PAGE_LABELS` 的两条**已经带 `i18nKey`**，取词优先走键，' +
      '注释原文就是「**缺键时降级为这里的中文名**」，与 `i18n/labelOf.ts#menuLabel()` 是**同一条降级机制**。',
  },
  // ---- 本批要清的债已全部清完（提交 3/4/5）⇒ 以下只剩**真源条目**；下一批再清零时从这里往下加 ----
]

/**
 * 反假绿：**解析出的候选点为 0 时直接失败**。
 *
 * <p>"护栏在输入为空时通过等于没有护栏"——正则改坏、扫描器等错文件、`JsxText` 分支被摘掉，
 * 都会表现为"零违规、门禁全绿"，而这与"真的都合规了"**外观完全一致**。
 * 这一条抄自 `check-ui.mjs` / `check-i18n.mjs` / `check-perms.mjs` 的现有措辞，是同一类教训。
 */
const MIN_CANDIDATES = {
  // 候选点 = 三类 AST 节点（字符串字面量 + 无插值模板串 + 非空 JSX 文本）**总数**，
  // 与是否含中文无关 ⇒ 它衡量的是"扫描器到底看进去多少"，而不是"违规多少"。
  //
  // 2026-09-16 实测 **9161**，构成已量清：**字符串字面量 + 无插值模板串 9075** + **非空 JsxText 86**。
  // 取 **8000** ≈ 实测 − 13%，它守的是**大面积失效**（见下面那条 ⚠️）。
  //
  // ⚠️ **不要指望这个下限去抓"漏了 `JsxText` 分支"**——实测它只贡献 86 个候选点。
  // 要抓住那一档，下限得卡在 9076~9161 这 **0.9%** 的窗口里，而任何一次正常的文案删除都会误报
  // ——那种下限不是护栏，是噪音源。**那一档由**下面的**第三分支**兜住：漏掉 JsxText 会让
  // CJK 命中数掉下来，而台账登记的是 300 ⇒ **「count 少了」当场红**（098 交付时已按此法观测过）。
  ZH: 8000,
}

/** 候选点的**历史实测读数**（2026-09-16）——只用于**比对**，不参与判定。 */
const CANDIDATE_READINGS = {
  ZH: 9161, // = 字符串字面量 + 无插值模板串 9075 + 非空 JsxText 86
}

// ---------------------------------------------------------------- 判定

const problems = []

if (candidates < MIN_CANDIDATES.ZH) {
  problems.push({
    kind: 'ZH 自检失败',
    file: '-',
    detail: [
      '源码硬编码中文（AST 口径）',
      `本规则只解析出 ${candidates} 个候选点（下限 ${MIN_CANDIDATES.ZH}；` +
        `2026-09-16 的历史实测读数 ${CANDIDATE_READINGS.ZH}）。`,
    ],
    fix:
      '护栏在输入为空时通过等于没有护栏。但先分清是**哪一种**失败——两种成因的处置相反：\n' +
      '    ① **扫描器大面积失效**（`walk` 没扫到文件、TS 解析全失败、`visit` 提前 return、只看单行…）：\n' +
      '       修扫描器，候选点应回到上面那个历史读数。\n' +
      '    ② **口径该重定**（真的删掉了大量文案，候选池本身变小了）：那要先**独立量一遍**确认，再改这里与 CANDIDATE_READINGS，\n' +
      '       **不是**把下限调低来放行。',
  })
} else {
  const byFile = new Map()
  for (const { file, hits } of results) byFile.set(file, hits)
  const allowMap = new Map(ZH_ALLOWED.map((a) => [a.file, a]))

  // 第一分支：**未登记命中**（回潮、或新写的硬编码中文）
  for (const [file, list] of byFile) {
    const allowed = allowMap.get(file)
    if (!allowed) {
      problems.push({
        kind: 'ZH 源码硬编码中文',
        file,
        detail: list.map((h) => `第 ${h.line} 行：${h.text.slice(0, 120)}`),
        fix:
          '把它们改走 `t()`（组件/页面用 `useTranslation`；**非组件模块**用 i18next 单例，见文件头）。' +
          '若确有正当理由（生成物 / 降级真源 / 产品侧维护的文案），请在 check-zh.mjs 的 ZH_ALLOWED 中登记：\n' +
          `    { file: '${file}', count: ${list.length}, reason: '<**类别**，以及其中**是否含欠账**>' },`,
      })
    } else if (list.length !== allowed.count) {
      // 第二/第三分支：count 多了 / 少了（含已归零）
      problems.push({
        kind: 'ZH 台账陈旧',
        file,
        detail: [
          `台账登记 ${allowed.count} 处，实际命中 ${list.length} 处。`,
          ...list.map((h) => `第 ${h.line} 行：${h.text.slice(0, 120)}`),
        ],
        fix:
          list.length > allowed.count
            ? '该文件新增了硬编码中文。请改走 `t()`，或更新台账的 count 与理由——**台账是债务台账，新增要写清类别**。'
            : `命中数少于登记数——**要么有人还了债却没销账，要么本批清零后忘了摘条目**。` +
              `请核对后把 count 改成 ${list.length}；**若已归零则整条删除**（不清零就要红，这正是本脚本的机制）。`,
      })
    }
  }
  // 第三分支的另一半：台账里有、实际一处也没命中（文件改名，或债已还清）
  for (const a of ZH_ALLOWED) {
    if (!byFile.has(a.file)) {
      problems.push({
        kind: 'ZH 台账陈旧',
        file: a.file,
        detail: [`台账登记 ${a.count} 处，实际一处也没命中（文件可能已改名，或债已还清）。`],
        fix: '删除这条台账条目，或修正 file 路径。',
      })
    }
  }
}

// ---------------------------------------------------------------- 口径边界自证（信息性，不进退出码）

const total = results.reduce((s, r) => s + r.hits.length, 0)
const boundary = () => {
  if (wideOnly.length === 0) return '本脚本判 0 处；口径外另有 0 处'
  const byWhy = new Map()
  for (const w of wideOnly) byWhy.set(w.why, (byWhy.get(w.why) ?? 0) + 1)
  const breakdown = [...byWhy].map(([why, n]) => `${why} ${n}`).join('、')
  return `口径外另有 ${wideOnly.length} 处（${breakdown}）`
}

// ---------------------------------------------------------------- 输出

if (LIST) {
  for (const { file, hits } of results.sort((a, b) => b.hits.length - a.hits.length)) {
    console.log(`\n${file}  (${hits.length})`)
    for (const h of hits) console.log(`  L${h.line}: ${h.text.length > 60 ? h.text.slice(0, 60) + '…' : h.text}`)
  }
  console.log(`\n【主口径】合计 ${total} 处，分布在 ${results.length} 个文件（候选点 ${candidates}）`)
  if (wideOnly.length > 0) {
    console.log(`\n【口径外】（**只印不判**，不进退出码）`)
    for (const w of wideOnly) {
      const shown = w.text.length > 60 ? w.text.slice(0, 60) + '…' : w.text
      console.log(`  ${w.file}:${w.line}  [${w.why}]  ${shown}`)
    }
  }
  console.log(`\n${boundary()}`)
  process.exit(0)
}

console.log(`扫描 ${files.length} 个产品文件（排除 src/i18n 与 *.test.*）、候选点 ${candidates} 个`)

if (problems.length > 0) {
  console.error(`\n✗ 源码硬编码中文校验失败：${problems.length} 处问题\n`)
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

const allowedTotal = ZH_ALLOWED.reduce((s, a) => s + a.count, 0)
console.log(
  `✓ 源码硬编码中文校验通过（未登记命中 0 处；台账内冻结 ${allowedTotal} 处、${ZH_ALLOWED.length} 条）`,
)
// 本脚本判 N；**口径外另有 M 处** —— **这一行不是门禁**（不进 problems、不改退出码），
// 存在的理由是：任何扫描器对自己的盲区自证不了，所以边界必须被印出来，不能靠读者假设。
console.log(`  本脚本判 ${total} 处（AST 主口径）；${boundary()} —— **只印不判**`)
