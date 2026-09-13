/**
 * 整页渲染型用例的异步等待上限（088 P2 新增的四个 `.form.test.tsx` 用）。
 *
 * <p>为什么要有一个共享常量：Testing Library 的 `findBy*` / `waitFor`
 * **默认只等 1000ms**，而这个仓库的页面用例每次都要把 `ProTable` 整页渲染出来
 * （mock 了 service 之后仍要跑完 antd 的表格、表单、i18n）。1000ms 在**单文件隔离运行**时
 * 够用，在全量并发（本仓 80 个测试文件争抢 worker）时会不够——这不是断言错了，
 * 是等待上限错了。
 *
 * <p>实测（2026-09-13，本机）：`ProductListPage.form.test.tsx` 隔离运行 13.6 秒 / 5 个用例，
 * 全量并发下同一文件 37.8 秒，并在
 * `创建弹窗的 onSubmit 接到校验：必填项为空时不提交，并显示字段级错误` 一条上以
 * `findByText` 超时失败（`ProductListPage.form.test.tsx:165`）。四个新文件隔离运行均绿
 * （22 个用例：invoices 6.0s / tags 4.2s / products 13.6s / customers 18.9s）。
 *
 * <p>取值 20000 是**照抄仓库既有约定**，不是新拍的数：`src/App.render.test.tsx:98`
 * 的 `const SHELL_TIMEOUT = 20000` 用于同一类"整页外壳渲染"的等待，并在该文件每一处
 * `findBy*` / `waitFor` 上显式传 `{ timeout: SHELL_TIMEOUT }`。
 *
 * <p>为什么用 `configure({ asyncUtilTimeout })` 而不是逐处传 `{ timeout }`：
 * 前者是这个库为**恰好这种情形**提供的机制（`findBy*` 与 `waitFor` 都吃这个默认值），
 * 一处声明覆盖整个文件；逐处传参要在四个文件里改约 30 个调用点，而漏掉一个就是一个
 * 更难查的超时。**它只放宽等待上限，不使用例变得更宽松**——所有否定断言仍走
 * `queryBy*`（不等），正向断言的判据一字未改。
 *
 * <p>注意：这是**本项目自有测试基础设施的增量**，不要把它当成"仓库全局约定"——
 * 其余 76 个测试文件仍按默认 1000ms 编写，且目前全绿。是否把上限提到全局
 * （`src/test/setup.ts`）是另一件事，需要单独拍板。
 */
export const HEAVY_RENDER_ASYNC_TIMEOUT = 20000

/**
 * 同一批用例的**单条用例**上限，默认取自 `vite.config.ts:24` 的 `testTimeout: 20000`。
 *
 * <p>为什么 20 秒不够：`testTimeout` 量的是**整条用例**（渲染 + 交互 + 断言），
 * 而这些用例每次都要挂载 antd `Modal` + `Form`（`openCreate()`），是本仓最重的形态。
 * 2026-09-13 用 `--coverage` 实测（同一棵冻结的树）：
 *
 * | 条件 | `ProductListPage.form`（5 条） | `CustomerListPage.form`（7 条） |
 * |---|---|---|
 * | 隔离跑（4 个文件） | 19.0s（3.8s/条） | 26.8s（3.8s/条） |
 * | 全量 + 默认 worker 池（16） | 109.8s，**3 条超 20s 失败** | 132.7s，**3 条超 20s 失败** |
 * | 全量 + `--maxWorkers=4` | 62.7s（12.5s/条，全绿） | 92.1s（13.2s/条，全绿） |
 *
 * <p>即：默认并发下每条要 20–25s，**正好压在 20s 边界上**，于是表现为"每次都换几条失败"
 * 的非确定性；把 worker 池压到 4 之后降到约 13s/条，但相对 20s 仍只有 1.5 倍余量。
 * 60s 是相对"最坏观测值 25s"留 2.4 倍余量。
 *
 * <p>⚠️ **这一项是兜底，不是根治**。根因是 16 核机器上 vitest 默认起 ~16 个 worker、
 * 每个都在跑 jsdom + antd（全量累计用例耗时 1243s → 4 worker 时 623s，**并发减半反而更快**）。
 * 根治要动 `vite.config.ts`（加 `maxWorkers`，且**必须同时给 `minWorkers`**——
 * 只给 `maxWorkers` 会让 vitest 1.6 报 `options.minThreads and options.maxThreads must not conflict`
 * 并**一条用例都不跑**），那是一次影响全仓 80 个文件的独立提交，不在本批次授权内。
 */
export const HEAVY_RENDER_TEST_TIMEOUT = 60000
