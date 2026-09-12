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
