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
 */
import { expect, test, type Page } from '@playwright/test';
import { login } from './helpers/login';

interface Call {
  method: string;
  path: string;
  auth: string;
  status: number;
}

/**
 * 记录该页面发出的所有 /api/v1 请求及其凭据与响应码。
 *
 * 同时**独立**记录响应码：只靠请求对象回填响应码时，同一 URL 被请求两次会让先前的记录
 * 永远停在 -1（未匹配），而 -1 既不等于 401 也不等于 200 —— 一个 401 就能借此漏网。
 * 故 401 的判定直接读响应事件，不受回填是否成功影响。
 */
function record(page: Page, calls: Call[], statuses: number[]) {
  const byUrl = new Map<string, Call>();
  page.on('request', (req) => {
    if (!req.url().includes('/api/v1/')) return;
    const u = new URL(req.url());
    const c: Call = {
      method: req.method(),
      path: u.pathname + u.search,
      auth: req.headers()['authorization'] ?? '(缺少 Authorization 头)',
      status: -1,
    };
    calls.push(c);
    byUrl.set(req.url(), c);
  });
  page.on('response', (resp) => {
    if (!resp.url().includes('/api/v1/')) return;
    statuses.push(resp.status());
    const c = byUrl.get(resp.url());
    if (c) c.status = resp.status();
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

async function probe(page: Page, path: string) {
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
  if (moduleCalls.length === 0) console.log('  注意：该页加载时未发出模块接口请求（纯表单页）');

  // 断言 3：页面不能是白屏。
  expect(render.bodyLen, `${path} 渲染为空`).toBeGreaterThan(0);

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

  const STATIC_PAGES = [
    '/quotas',
    '/quotas/create',
    '/quotas/comparison',
    '/data-retention',
    '/data-retention/create',
    '/data-retention/compliance-export',
    '/exports/scheduled',
    '/exports/scheduled/create',
  ];

  for (const path of STATIC_PAGES) {
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
