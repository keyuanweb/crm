/**
 * antd 主题的**单一真源**（088 交付物 1）。
 *
 * ## 为什么要有这个文件
 *
 * 在此之前，全库唯一的 `ConfigProvider`（`src/components/LocaleProvider.tsx`）**只传了 `locale`**，
 * 一行主题配置都没有。于是视觉效果全靠 `src/index.css`（816 行）去覆盖 antd 生成的类名，
 * 而 `index.css:13-38` 的 `:root` 把主色写成了 Indigo `#6366f1`，antd 自己的 `colorPrimary`
 * 仍是默认蓝 `#1677ff`——**两套主色并存**。
 *
 * 后果是**可见的分裂**，而不是"统一地偏蓝"或"统一地偏紫"：
 *
 * | 跟着 Indigo（被 CSS 覆盖到了） | 还是 antd 蓝（没被覆盖到） |
 * |---|---|
 * | `.ant-btn-primary`、`.ant-menu-item-selected`、`.ant-pagination-item-active` | 链接色、聚焦环、Checkbox/Radio/Switch 选中态、Select/DatePicker 激活边框、Tabs 墨条、Steps、Progress、Slider、Spin |
 *
 * 实测证据（`specs/088-frontend-layout-consistency/research.md` §2.7）：
 * `#1677ff` 33 处 / 12 个文件（全在 `.ts`/`.tsx`），`#6366f1` 等四个 Indigo 值各 1 处
 * 且**全在 `index.css`**——**没有任何一个文件同时含两套主色**，即这是结构性分裂而非笔误。
 * 接入本文件后，主色真值由 antd 自己生成，那些"对抗性 CSS"就可以逐条退场（P4）。
 *
 * ## 与 `index.css` 的关系
 *
 * `index.css` 的 `:root` **保留**（自有 CSS 仍要用那些变量），但自本文件起
 * **颜色/字体/圆角的真源在这里**；`:root` 只是同值的镜像，供非 antd 的样式引用。
 * 本批次不做 815 行的全量清理（见 plan 的「非目标」）。
 *
 * ## 明确不做的事
 *
 * - **不接 `theme.algorithm`**：本批次只做明色（暗色是总方案 §二期 2.7）。
 *   也**不显式写 `defaultAlgorithm`**——那只会给将来留歧义（`undefined` 就是默认算法）。
 * - **不改颜色语义**，只统一来源。
 * - **不设 `controlHeight`**：antd 的默认 controlHeight 本来就是 32（等同 `size="middle"`），
 *   写它等于没写。**紧凑密度的杠杆不在控件高度，而在栅格**——48 个纵向单列表单改两列，
 *   高度直接减半（见 `components/ui/FormGrid.tsx`）。这条曾被我写成 `size: 'middle'`，
 *   已证伪并记录在 plan 的「已证伪的做法」。
 */

import type { ThemeConfig } from 'antd'

/**
 * 品牌与语义色。值**逐字搬自** `src/index.css:13-38` 的 `:root`——搬而不是改，
 * 因为那些值已经在页面上生效（只是只对一半类名生效）。搬过来是为了让 antd 也照它生成。
 *
 * 导出的目的是让 `index.css` 的注释与测试都能指向同一个来源，避免又出现"两份真源"。
 */
export const palette = {
  primary: '#6366f1',
  primaryHover: '#4f46e5',
  primaryLight: '#eef2ff',
  primaryDark: '#4338ca',
  success: '#10b981',
  warning: '#f59e0b',
  error: '#ef4444',
  // 危险按钮 hover 的字色。此前只以裸字面量 `#f87171` 活在 `index.css` 的三条规则里，
  // `:root` 里没有对应变量（是 tailwind red-400，不是本项目 `--color-danger-*` 那一族）。
  errorHover: '#f87171',
  info: '#3b82f6',
} as const

/** 与 `index.css:54-56` 的 `--font-family-sans` 同一串值（逐字一致，不另起一套）。 */
export const fontFamily =
  "Inter, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', sans-serif"

export const antdTheme: ThemeConfig = {
  token: {
    colorPrimary: palette.primary,
    colorSuccess: palette.success,
    colorWarning: palette.warning,
    // ⚠️ 种子 token 名是 `colorError`，**不是** `colorDanger`——`--color-danger` 是 CSS 侧的名字，
    // 两者的词不同，照 CSS 的名字写 ts 会静默无效（未知键不报错）。
    colorError: palette.error,
    colorInfo: palette.info,
    // 链接色跟随主色：这是"一半 Indigo 一半蓝"里最显眼的一半，此前完全没被覆盖到。
    colorLink: palette.primary,

    // 与 `--radius-md: 8px` 对齐（antd 默认是 6）。
    // 注意：页面上另有 66 处 `borderRadius: 10` 字面量（54 个文件），本批次**不动**它们——
    // 那是 P4 的清扫项，且 10 与 12（`--radius-lg`）本身也不一致，属另一个决定。
    borderRadius: 8,

    // 与 `index.css:59` 的 `--font-size-base: 13px` 及 `body{font-size:13px}` 对齐。
    // 注意这是**统一**而不是**改小**：CSS 早就把 body 设成 13px 了，只有 antd 组件还在 14，
    // 所以今天页面上两种字号是混着的。这是可见度最高的一项，已列为验收项。
    fontSize: 13,

    fontFamily,
  },

  components: {
    // P4：按钮的**派生** hover 色。
    //
    // antd 由种子 `colorPrimary` 自行推导 `colorPrimaryHover`（此处会得到 `#9197ff`，
    // 比 `--color-primary-hover` 的 `#4f46e5` **更浅**），因此 `index.css` 里那几条
    // hover 规则原先只能靠 `!important` 硬压——删掉 `!important` 就会退回浅色。
    // 把派生色按本项目的取值写进主题，那些 `!important` 才是真的冗余。
    //
    // 与 `Card.paddingLG` 同款做法：`components.X` 的入参类型是
    // `Partial<ComponentToken> & Partial<AliasToken>`，别名 token 可以**按组件收窄**。
    // 这里刻意不写全局 `token.colorPrimaryHover`——那会顺带改掉链接 hover、聚焦环、
    // Select/Tabs 激活态等一大片，远超本批次意图（详见 plan 的风险表）。
    Button: {
      colorPrimaryHover: palette.primaryHover, // #4f46e5，与 `--color-primary-hover` 同值
      colorErrorHover: palette.errorHover, // #f87171
    },
    // 紧凑密度（已定决策 4）。取值都核对过确实存在，不是照文档猜的：
    // `Form.itemMarginBottom` 见 antd/es/form/style/index.d.ts。
    Form: {
      itemMarginBottom: 12, // 24 → 12
    },
    Card: {
      // ⚠️ Card **没有** `bodyPadding` 这个 token——那是 **Modal** 的
      // （`antd/es/modal/style/index.d.ts` 里有，Card 的没有）。Card 的正文内边距来自
      // `cardPaddingBase: token.paddingLG`（`antd/es/card/style/index.js:333`），
      // 默认 24。此处把 `paddingLG` 写在 **Card 名下**而不是全局：`components.X` 的入参类型是
      // `Partial<ComponentToken> & Partial<AliasToken>`，所以别名 token 可以按组件收窄。
      // 写全局 `paddingLG: 16` 会顺带改掉 Modal/Descriptions 等一大片，超出本批次意图。
      paddingLG: 16, // 24 → 16
      headerHeight: 44, // 56 → 44
    },
    Table: {
      cellPaddingBlockSM: 6, // 默认 paddingXS = 8 → 6：压行高。与全库 123 处 `size="small"` 配套
      // 刻意**不设** `cellPaddingInlineSM`：它的默认值同样是 paddingXS = 8，
      // 而 plan 起草时写的「cellPaddingInlineSM: 10」是**变大**、与"紧凑"方向相反，
      // 且没给理由。没有理由的 token 不写——与"不写 controlHeight"是同一条纪律。
    },
  },
}

export default antdTheme
