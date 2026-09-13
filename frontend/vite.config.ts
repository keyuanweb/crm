import { configDefaults, coverageConfigDefaults, defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        // 默认代理到 8081；后端使用其他端口时设置 VITE_PROXY_TARGET=http://localhost:<port>
        target: process.env.VITE_PROXY_TARGET ?? 'http://localhost:8081',
        changeOrigin: true,
      },
      '/ws': {
        // 026：WebSocket 通知实时推送代理
        target: process.env.VITE_PROXY_TARGET ?? 'http://localhost:8081',
        changeOrigin: true,
        ws: true,
      },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
    css: false,
    testTimeout: 20000,
    exclude: [...configDefaults.exclude, 'e2e/**'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html'],
      // 分母必须是可复现的：默认口径会把「当次运行恰好加载过的任何文件」计入
      // （含 vite.config.ts、public/sw.js），随运行方式浮动，阈值随之失去意义。
      include: ['src/**/*.{ts,tsx}'],
      // 测试脚手架（setup / 自定义 render）每次运行都近乎全绿，计入只会抬高数字，
      // 并不反映产品代码被覆盖的程度，故排除；其余默认排除项（测试文件、node_modules 等）原样保留。
      exclude: [...coverageConfigDefaults.exclude, 'src/test/**'],
      /*
        实测（2026-09-12，`pnpm run test:coverage`，20 文件 / 79 用例全通过，退出码 0）：
        statements 46.86 / branches 70.03 / functions 21.55 / lines 46.86。
        阈值本身是按 083 交付时的实测（statements 34.92 / branches 49.05 / functions 22.88）下浮约 0.5
        个百分点确定的，**此后未再下调**（084 的 T037 明确要求不得下调），本次只刷新实测记录。

        statements 与 branches 的大幅上移，主因不是新增用例堆出来的：084 修好了 App.render.test.tsx
        里写成 `'../../services/…'` 的 mock 路径——该路径从 `src/` 出发指向仓库外，mock 静默失效，
        用户始终处于未登录态，整个 Shell 连同 App.tsx 的菜单构建代码从未被执行过。路径改对后该文件
        才真正渲染出登录态 Shell，于是这两个指标一次性抬升。

        functions 仍是三项里最紧的一项（21.55 vs 21.4，余量 0.15 个百分点）：它只统计「函数是否被调用
        过」，而 084 重写 App.tsx 增加了函数总数却不增加调用，故新用例须优先打未调用函数（如交互路径、
        窄屏形态），而不是继续堆已覆盖路径的断言。改动此值时必须同步更新
        specs/083-engineering-consolidation/data-model.md §4 的实测记录。

        【实测刷新，2026-09-13】`pnpm run test:coverage`，22 文件 / 96 用例全通过，退出码 0：
        statements 47.02 / branches 71.35–71.49 / functions 22.29–22.43 / lines 47.02。
        上一段快照（2026-09-12 的 46.86 / 70.03 / 21.55 / 46.86）按惯例保留，不改写。

        其中「functions 余量 0.15 个百分点」**已不再成立**：面包屑收口（提交 `226d55c`）新增的
        `breadcrumbTrail.ts` 与 `i18n/labelOf.ts` 的函数全部被单测打中，functions 余量由 0.15 抬到约 1 个百分点。

        【实测刷新，2026-09-13（088 的 P1 完成后）】`pnpm run test:coverage`，**76 文件 / 343 用例**全通过，
        退出码 0：statements **67.65** / branches **73.02** / functions **34.29** / lines **67.65**。
        上两段快照（09-12 的 46.86 / 70.03 / 21.55 / 46.86、09-13 的 47.02 / 71.35–71.49 / 22.29–22.43）
        按惯例保留，不改写。

        **上数大幅上移的成因不是本次的 35 个新用例**，而是 086（45 页权限接线）与 087（18 个码的渲染补课）
        新增的大批整页渲染用例把此前从未执行过的页面代码真正跑了起来——statements 47.02 → 67.65（+20.6pp）
        正是这个量级。新原语自身：`src/components/ui` 92.12 / 86.84 / 90.9 / 92.12，`src/theme` 100/100/100/100。

        **「functions 是最紧的一项」这句仍然成立（四项里余量最小），但「紧」已不成立**：
        functions 34.29 vs 21.4 ⇒ 余量 **12.89pp**（此前记录的 0.15pp / 约 1pp 均已失效）。
        故 088 的 plan 里「新组件会压穿 functions」这条风险的依据（余量仅约 0.9pp）**不成立**，
        其推论「`PageState` 只能合成一个、`PageShell` 不做」的**结论保留但论证作废**——
        详见 `specs/088-frontend-layout-consistency/research.md` §1 与 §10。

        **本次未改任何阈值。** 三段快照对应的是同一条纪律：余量在扩大是好事，但不构成上调阈值的理由
        （上调只把抖动风险引进门禁，收益有限）；末尾那条「不要引用单次运行的小数位当论据」同样适用——
        本次 088 复测中 branches 报过 **73.08** 与 **73.02**（两次相邻运行），与下文记录的非确定性同源。
        改动此处数值时必须**同步**更新 `specs/083-engineering-consolidation/data-model.md` §4 的实测记录。

        注意：branches 与 functions 记的是**区间**而非单点。三次复跑里有一次报 71.49 / 22.29、
        两次报 71.35 / 22.43（statements/lines 稳定在 47.02）。**差的成因未查明**——候选有运行间
        非确定性，也有「本工作区常有多会话并行、复跑期间确有会话在改 `src/pages/**`」这一可能，
        两者都与本次收口无关。故判定以「阈值是否通过」为准（两端都过），不要引用单次运行的小数位当论据。
        **本次未改任何阈值**——只在收口后复跑并刷新实测记录。
      */
      thresholds: {
        statements: 33.6,
        branches: 47.2,
        functions: 21.4,
        lines: 33.6,
      },
    },
  },
})
