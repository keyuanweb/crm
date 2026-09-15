/**
 * 关键字高亮原语：把文本里命中关键词的片段包进 `<mark>`。
 *
 * ## 它是**提取**来的，不是新写的
 *
 * 实现逐字来自 `pages/search/SearchResultPage.tsx` 的页内私有件（095 T003）。
 * 提取而非复制，理由有一条是具体的：**两份实现的转义行为一旦分叉，
 * 同一个关键词在两个页面会高亮出不同结果**——用户看到的差异会表现为「搜索页标了、
 * 部门页没标」，而那是同一个词。
 *
 * 提取本身还顺手消掉一个告警：从**页面文件**里 `export` 组件会踩
 * `react-refresh/only-export-components`（本仓为 warn 档），而这里不再有该问题。
 *
 * ## 两处刻意的保留（改动前先读）
 *
 * - **关键词按空格分词**，逐个转义正则元字符后以 `|` 连接 ⇒「华东 销售」这类多词输入
 *   会命中任意一个词，而不是要求整串匹配。
 * - **色值 `#ffe58f` 是写死的**，不在 `check-ui.mjs` 的 `BRAND_COLORS` 词表内，
 *   故不触发门禁 R1。换成主色字面量会**立刻**让 R1 转红。
 *
 * ## 使用约束
 *
 * `keyword` 必须是**与过滤同一份**的值。若过滤用防抖后的值、高亮用未防抖的原值，
 * 会出现「节点被留下、却没标出命中在哪」——那比不高亮更坏，用户看不出这个节点为什么留下。
 */

export interface HighlightProps {
  /** 被搜索的文本。 */
  text: string
  /** 关键词；为空（或 `text` 为空）时**原样返回文本**，不做任何包装。 */
  keyword: string
}

export default function Highlight({ text, keyword }: HighlightProps) {
  if (!text || !keyword) return <>{text}</>
  const parts = text.split(
    new RegExp(`(${keyword.split(' ').map((k) => k.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')).join('|')})`, 'gi'),
  )
  return (
    <>
      {parts.map((p, i) =>
        keyword.toLowerCase().split(' ').some((k) => k && p.toLowerCase() === k) ? (
          <mark key={i} style={{ background: '#ffe58f', padding: '0 2px', borderRadius: 3 }}>
            {p}
          </mark>
        ) : (
          <span key={i}>{p}</span>
        ),
      )}
    </>
  )
}
