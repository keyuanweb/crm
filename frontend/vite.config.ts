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
        实测（2026-09-12，`pnpm run test:coverage`，18 文件 / 64 用例全通过）：
        statements 34.92 / branches 49.05 / functions 22.88 / lines 34.92。
        阈值当时按实测值下浮约 0.5 个百分点确定；此后用例增补使实测上移，
        当前余量为 1.3–1.9 个百分点——够吸收浮点与用例增删的抖动，
        但不给静默退化留空间（此前该项完全未设置，等于零防护）。
        改动此值时必须同步更新 specs/083-engineering-consolidation/data-model.md §4 的实测记录。
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
