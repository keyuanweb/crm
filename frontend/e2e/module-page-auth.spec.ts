/**
 * 配额／数据保留／定时导出三个模块的 14 个页面在浏览器中的鉴权验证
 * （083-engineering-consolidation / FR-G17、SC-G04）。
 *
 * 为什么这三组页面需要浏览器级用例：改造前它们的 API 客户端是三份**逐字重复的裸 fetch 封装**，
 * 读的是 `localStorage.getItem('token')`，而全站写入的键是 `accessToken` —— 于是每个请求都发
 * `Authorization: Bearer null`，后端一律 401，页面表现为**空表**（与"后端没数据"无法区分）。
 * 客户端侧的"发什么头"已由 `src/services/api/moduleApiClients.test.ts` 用自定义 adapter 钉死；
 * 这里补的是它替代不了的一层：真实浏览器里 localStorage 的真实令牌 → axios 拦截器 → vite 代理
 * 是否真的把令牌带到了后端。
 *
 * 与种子数据的关系：**不依赖任何业务种子数据**。CI 的 e2e 作业在空库上启动后端（仅由
 * DataInitializer 建出 admin/admin123），此时配额/数据保留/定时导出三张表都是空的 ——
 * 用例断言的是"请求已认证"（不得 401）与"页面不是白屏"，而不是"渲染出若干行"。
 * 定时导出详情页需要一条真实记录，故由 beforeAll 创建、afterAll 删除（净零副作用）。
 *
 * T073 补的那一层：改造前的断言只否定 401，于是列表接口回 **500 或 404 一样通过**——而
 * "页面空表"在历史上正是由 500 或 401 同样产生的，即该用例测不出它要防的症状。现在四个
 * 列表页额外断言两件事：① 列表接口**返回 2xx**；② 接口返回非空载荷时**页面必须渲染出行**。
 * 载荷为空时不反过来断言空状态（CI 在空库上跑，且各页空态标记不统一，写死一种会制造假红）；
 * 读不到响应体时**打印一行说明**而不是静默通过。
 */
import { expect, test, type Page } from '@playwright/test';
import { login } from './helpers/login';

interface Call {
  method: string;
  path: string;
  auth: string;
  status: number;
  /** 响应体里的条目数；`null` = 没读到（非 JSON、或响应还没回来）。 */
  items: number | null;
}

/**
 * 从列表响应里数出条目数，兼容本项目并存的两种形态：裸数组（数据保留模块）与
 * `{records:[…]}`/`{data:[…]}` 信封（MyBatis-Plus 分页与全站信封）。数不出来返回 `null`,
 * **不返回 0** —— 把"读不到"记成 0 会让下面的"接口非空、页面却空表"这条断言静默失效。
 */
function itemCount(json: unknown): number | null {
  if (Array.isArray(json)) return json.length;
  if (json && typeof json === 'object') {
    const o = json as Record<string, unknown>;
    for (const key of ['records', 'data', 'items', 'list']) {
      if (Array.isArray(o[key])) return (o[key] as unknown[]).length;
    }
    if (typeof o['total'] === 'number') return o['total'] as number;
  }
  return null;
}

/**
 * 记录该页面发出的所有 /api/v1 请求及其凭据、响应码与条目数。
 *
 * 回填的两条通道都必须**按请求对象**配对，不能按 URL 配对：dev 下 React 会双挂载、同一
 * URL 被请求两次，按 URL 建立 `Map<url, Call>` 会让先发的那条永远停在 -1（未匹配），而 -1
 * 既不等于 401 也不等于 200——一个 401 就能借此漏网（本条注释原有的事实保留在此）。
 * 故：① `statuses` 直接收响应的状态码，用于"不得出现 401"，不受回填影响；② 每条 `Call`
 * 经 `Map<Request, Call>` 由**它自己那条请求**的响应回填，重复请求各自拿到各自的码。
 */
function record(page: Page, calls: Call[], statuses: number[]) {
  const byRequest = new Map<Request, Call>();
  page.on('request', (req) => {
    if (!req.url().includes('/api/v1/')) return;
    const u = new URL(req.url());
    const c: Call = {
      method: req.method(),
      path: u.pathname + u.search,
      auth: req.headers()['authorization'] ?? '(缺少 Authorization 头)',
      status: -1,
      items: null,
    };
    calls.push(c);
    byRequest.set(req, c);
  });
  page.on('response', (resp) => {
    if (!resp.url().includes('/api/v1/')) return;
    statuses.push(resp.status());
    const c = byRequest.get(resp.request());
    if (!c) return;
    c.status = resp.status();
    // 只读列表类响应的体，用于"接口有数据、页面空表"的交叉核对；读不到就保持 null
    void resp
      .json()
      .then((json) => {
        c.items = itemCount(json);
      })
      .catch(() => {});
  });
}

/** 等页面把数据请求走完，返回可观测的渲染结果。 */
async function settle(page: Page) {
  await page.waitForLoadState('domcontentloaded').catch(() => {});
  await page.waitForTimeout(2500);
  return {
    rows: await page.locator('.ant-table-tbody tr.ant-table-row').count(),
    empty: (await page.locator('.ant-empty').count()) > 0,
    bodyLen: (await page.locator('body').innerText()).length,
  };
}

/**
 * 列表页的额外口径。给了 `listApi` 就启用两条通用断言替代不了的检查：
 * ① 该页的列表请求必须存在且**返回 2xx**；② 接口返回了非空载荷时，页面必须渲染出行。
 *
 * 为什么需要它：本用例改造前只否定 401，于是列表接口回 **500 或 404 一样通过**——而
 * `/quotas` 的"空表"在历史上正是由 500 或 401 同样产生的，用例测不出它要防的症状。
 */
interface ProbeOptions {
  /** 该列表页的数据接口路径（正则，匹配 `path`，含 query） */
  listApi?: RegExp;
}

async function probe(page: Page, path: string, opts: ProbeOptions = {}) {
  const calls: Call[] = [];
  const statuses: number[] = [];
  record(page, calls, statuses);
  await page.goto(path);
  const render = await settle(page);

  // 认证/通知类请求属于全站既有链路，非本用例的人质，打印时略去
  const moduleCalls = calls.filter((c) => !/^\/(auth|notifications)\//.test(c.path));
  console.log(`\n=== ${path} ===`);
  for (const c of moduleCalls) {
    console.log(`  ${c.status} ${c.auth.slice(0, 24)}… ${c.method} ${c.path}`);
  }
  console.log(`  渲染：行数=${render.rows} 空状态=${render.empty} 文本长度=${render.bodyLen}`);
  console.log(`  全部状态码：${statuses.join(',')}`);

  // 断言 1（本用例的核心）：每个请求都必须带**真实**令牌。
  // 改造前发出去的是字面量 "Bearer null"，故这两条一起把"读错凭据键"钉死。
  for (const c of calls) {
    expect(c.auth, `${path} 的 ${c.method} ${c.path} 未带真实令牌，实为「${c.auth}」`).toMatch(
      /^Bearer .+/,
    );
    expect(c.auth, `${path} 发出了 Bearer null`).not.toContain('null');
  }

  // 断言 2：不得出现 401 —— 401 正是改造前的表现，也是"页面空表"的来由。
  expect(statuses.filter((s) => s === 401), `${path} 出现 401`).toEqual([]);

  // 说明：纯表单页（/quotas/create 等）加载时不发模块请求，"没有 401"对它而言是空真，
  // 故这里只提示、不断言 —— 断言会逼着人去造一个假请求来满足它。
  // ⚠️ 2026-09-16（099）订正：上句举的例子 `/quotas/create` **已不再是纯表单页** —— 该路由与
  // `QuotaCreatePage.tsx` 已由 099 删除（创建配额改成 `/quotas` 列表页内的 `FormModal` 弹窗）。
  // **上句原文逐字保留、不删改**（写下时它是事实）；现存的纯表单页见下方 `FORM_PAGES`。
  if (moduleCalls.length === 0) console.log('  注意：该页加载时未发出模块接口请求（纯表单页）');

  // 断言 3：页面不能是白屏。
  expect(render.bodyLen, `${path} 渲染为空`).toBeGreaterThan(0);

  // 断言 4（仅列表页）：接口 2xx，且"接口有数据、页面空表"必须被抓出来。
  if (opts.listApi) {
    const listCalls = calls.filter((c) => opts.listApi!.test(c.path));
    expect(
      listCalls.map((c) => `${c.status} ${c.method} ${c.path}`),
      `${path} 未发出列表请求（期望匹配 ${opts.listApi}），实际发出：${calls.map((c) => c.path).join(' | ') || '（无）'}`,
    ).not.toEqual([]);

    // 500/404 与"库里没数据"在页面上一模一样，只能在这一层区分；这正是本页症状的本质
    const notOk = listCalls.filter((c) => c.status < 200 || c.status >= 300);
    expect(
      notOk.map((c) => `${c.status} ${c.method} ${c.path}`),
      `${path} 的列表请求未返回 2xx（各自对应一个请求）`,
    ).toEqual([]);

    const readable = listCalls.filter((c) => c.items !== null);
    if (readable.length === 0) {
      console.log('  [列表页] 未读到列表响应体，本次不做"载荷-渲染"交叉核对');
    } else {
      const payloadItems = Math.max(...readable.map((c) => c.items as number));
      if (payloadItems > 0) {
        expect(
          render.rows,
          `${path} 的列表接口返回了 ${payloadItems} 条，页面却渲染出 0 行——请求成功而内容为空，正是"空表"症状`,
        ).toBeGreaterThan(0);
      } else {
        // 不反过来断言"空载荷必须渲染空状态"：CI 的 e2e 在**空库**上跑，那时"空"是正确结果，
        // 而本项目各页的空状态标记并不统一（有的用 antd 默认空态、有的自定义 emptyText），
        // 写死一种会把空库上的正确渲染判成失败。
        console.log('  [列表页] 列表接口载荷为空，本用例不断言渲染形态（空库上"空"是正确结果）');
      }
    }
  }

  return render;
}

let scheduledExportId: number | null = null;

// 详情页需要一条属于当前用户的真实记录；空库上只能现造，用后即删（见 afterAll）
test.beforeAll(async ({ browser }) => {
  const page = await browser.newPage();
  await login(page);
  const created = await page.evaluate(async () => {
    const token = localStorage.getItem('accessToken');
    const resp = await fetch('/api/v1/scheduled-exports', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      body: JSON.stringify({
        entityType: 'CUSTOMER',
        exportFormat: 'CSV',
        // 5 段式：后端 @Pattern 是 ^([0-9*,/-]+\s){4}[0-9*,/-]+$，不接受 6 段与 "?"
        cronExpression: '0 3 * * *',
        periodType: 'DAILY',
        hour: 3,
        minute: 0,
      }),
    });
    return { status: resp.status, body: await resp.text() };
  });
  console.log(`\n[前置] 创建定时导出任务：${created.status} ${created.body.slice(0, 200)}`);
  if (created.status === 200 || created.status === 201) {
    scheduledExportId = JSON.parse(created.body).id;
  }
  await page.close();
});

test.afterAll(async ({ browser }) => {
  if (scheduledExportId == null) return;
  const page = await browser.newPage();
  await login(page);
  const del = await page.evaluate(async (id) => {
    const token = localStorage.getItem('accessToken');
    const resp = await fetch(`/api/v1/scheduled-exports/${id}`, {
      method: 'DELETE',
      headers: { Authorization: `Bearer ${token}` },
    });
    return resp.status;
  }, scheduledExportId);
  console.log(`\n[清理] 删除前置创建的定时导出任务 id=${scheduledExportId} → ${del}`);
  await page.close();
});

test.describe('模块页面鉴权（FR-G17、SC-G04）', () => {
  // 每个用例是独立的浏览器上下文，登录态不共享，故逐个登录
  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  // 列表页：**接口成功**与**页面非空**一起断言（T073）。`/quotas/comparison` 不在原任务
  // 点名的三个列表页里，但它是同一形态——`loadRanking()` 把失败 `catch` 成空表，与
  // `/quotas` 的"空表"同源，故一并纳入。
  const LIST_PAGES: Array<{ path: string; listApi: RegExp }> = [
    { path: '/quotas', listApi: /^\/api\/v1\/sales-quota\?/ },
    { path: '/data-retention', listApi: /^\/api\/v1\/data-retention\/policies$/ },
    { path: '/exports/scheduled', listApi: /^\/api\/v1\/scheduled-exports\?/ },
    { path: '/quotas/comparison', listApi: /^\/api\/v1\/sales-quota\/ranking\?/ },
  ];

  // 纯表单页：加载时不发模块请求，只走 probe 的通用断言（"没有 401"对它们是空真）
  // ⚠️ 2026-09-16（099）：`'/quotas/create'` 已从本清单**摘除** —— 该路由由 099 删除（创建配额改成
  // `/quotas` 列表页内的弹窗）。**摘它的理由是空真**：删路由后 `goto('/quotas/create')` 会落到
  // `NotFoundPage`，而 probe 的通用断言（无 401、非白屏、非空渲染）在 404 页上**照样成立** ⇒
  // 这条用例会**一直绿**，留着就是拿空真当证据。
  const FORM_PAGES = [
    '/data-retention/create',
    '/data-retention/compliance-export',
    '/exports/scheduled/create',
  ];

  for (const { path, listApi } of LIST_PAGES) {
    test(`列表页鉴权与数据加载：${path}`, async ({ page }) => {
      await probe(page, path, { listApi });
    });
  }

  for (const path of FORM_PAGES) {
    test(`页面鉴权：${path}`, async ({ page }) => {
      await probe(page, path);
    });
  }

  // 详情类：id 是否存在取决于种子数据。**用不存在的 id 反而是更严格的检查**——
  // 后端会回 404（已认证、对象不存在），而不是 401（未认证）。改造前这里必是 401。
  for (const path of [
    '/quotas/1/breakdown',
    '/quotas/1/achievement',
    '/quotas/1/versions',
    '/data-retention/1/edit',
    '/data-retention/1/executions',
  ]) {
    test(`页面鉴权：${path}`, async ({ page }) => {
      await probe(page, path);
    });
  }

  test('页面鉴权：/exports/scheduled/:id/executions（用前置创建的任务）', async ({ page }) => {
    expect(scheduledExportId, '前置创建定时导出任务失败，详情页无从验证').not.toBeNull();
    await probe(page, `/exports/scheduled/${scheduledExportId}/executions`);
  });
});
