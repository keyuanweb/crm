/**
 * 配额／数据保留／定时导出三个模块的 API 客户端（FR-G17，083-engineering-consolidation）。
 *
 * <p>为什么这几个客户端值得单独测：它们改造前是**三份逐字重复的裸 `fetch` 封装**，各有两处系统性缺陷，
 * 而两处的表现（页面空表）与"后端没数据"无法区分，靠页面级测试很难定位：
 *
 * <ol>
 *   <li>读错凭据键（`localStorage.getItem('token')`，而全站写入的是 `accessToken`）→ 请求头是
 *       `Authorization: Bearer null`，后端一律 401。
 *   <li>解包层次抄错（这三个模块返回**裸响应体**而非全站信封）→ 多解一层得到 `undefined`，
 *       页面从"401 空表"变成"解析失败"——**这是本故事的主要风险点**（T043、research.md §9）。
 * </ol>
 *
 * 两条都不是类型能拦住的（`as` 一写就过），故在此以运行时可观测的形态钉死：发出什么头、解出什么值。
 */
import type { InternalAxiosRequestConfig } from 'axios';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { apiClient } from '../apiClient';
import { dataRetentionApi } from './dataRetentionApi';
import { quotaApi } from './quotaApi';
import { scheduledExportApi } from './scheduledExportApi';

const ORIGINAL_ADAPTER = apiClient.defaults.adapter;

let captured: InternalAxiosRequestConfig[] = [];
let nextBody: unknown = {};

beforeEach(() => {
  captured = [];
  nextBody = {};
  localStorage.clear();
  // 用自定义 adapter 顶替传输层：本文件要断言的是"发出去的请求长什么样"与"回来的响应体如何解包"，
  // 不需要真实网络（且 jsdom 的 XHR 已被 test/setup.ts 置为 noop，走真实适配器只会静默挂起）。
  apiClient.defaults.adapter = async (config) => {
    captured.push(config);
    return { data: nextBody, status: 200, statusText: 'OK', headers: {}, config } as never;
  };
});

afterEach(() => {
  apiClient.defaults.adapter = ORIGINAL_ADAPTER;
  localStorage.clear();
});

/**
 * 读 Authorization 头。
 *
 * 两种读法并用：axios 的 AxiosHeaders 对"经 set() 写入"的键走 `get()`，而请求拦截器里
 * `config.headers.Authorization = …` 这类直接赋值在部分版本里只保留为实例属性 —— 只取其一
 * 会在升级 axios 后让本用例静默失效（读不到头却仍然通过），那正是本文件要防的那类假绿。
 */
function authHeader(config: InternalAxiosRequestConfig): string {
  const headers = config.headers as Record<string, unknown> & {
    get?: (name: string) => unknown;
  };
  const value = headers.Authorization ?? headers.authorization ?? headers.get?.('Authorization');
  return value == null ? '' : String(value);
}

describe('凭据注入（FR-G17：改用全站统一的凭据键）', () => {
  it('三个客户端都带上 accessToken 对应的真实令牌', async () => {
    localStorage.setItem('accessToken', 'tk-real-token');

    await scheduledExportApi.list(7);
    await dataRetentionApi.getAllPolicies();
    await quotaApi.getSummary(2026);

    expect(captured).toHaveLength(3);
    for (const config of captured) {
      expect(authHeader(config)).toBe('Bearer tk-real-token');
    }
  });

  it('全站凭据键下不再发出 Bearer null', async () => {
    // 只写改造前那个错误的键：若客户端又退回读 'token'，这里会拿到空令牌，
    // 而旧实现会把空令牌拼成字面量 "Bearer null" 发出去（正是 401 的来由）
    localStorage.setItem('token', 'tk-from-wrong-key');
    localStorage.setItem('accessToken', 'tk-real-token');

    await scheduledExportApi.list(7);

    expect(authHeader(captured[0])).not.toContain('null');
    expect(authHeader(captured[0])).not.toContain('tk-from-wrong-key');
  });
});

describe('解包层次（裸响应体 vs 全站信封）', () => {
  it('裸响应体只解一层，返回的就是响应体本身', async () => {
    const bare = { id: 3, entityType: 'CUSTOMER' };

    nextBody = bare;
    await expect(scheduledExportApi.detail(3)).resolves.toEqual(bare);

    nextBody = [bare];
    await expect(dataRetentionApi.getAllPolicies()).resolves.toEqual([bare]);

    nextBody = bare;
    await expect(quotaApi.getSummary(2026)).resolves.toEqual(bare);
  });

  it('多解一层会返回 undefined —— 用反例固定"只解一层"这个结论', async () => {
    // 说明为何不能像 announcementService 那样写 data.data：这些端点的响应体就是业务对象，
    // 它没有 data 字段，再取一层即 undefined
    nextBody = { id: 3, entityType: 'CUSTOMER' };
    await expect(scheduledExportApi.detail(3)).resolves.not.toBeUndefined();
  });
});

describe('变更类方法对空响应体的处理', () => {
  it('后端返回 200 空体时不抛错', async () => {
    // 改造前这类反序列化对空体抛 `Unexpected end of JSON input`，于是"操作成功了却弹失败提示"
    nextBody = '';

    await expect(scheduledExportApi.delete(1)).resolves.toBeUndefined();
    await expect(scheduledExportApi.updateStatus(1, 'SUSPENDED')).resolves.toBeUndefined();
    await expect(dataRetentionApi.deletePolicy(1)).resolves.toBeUndefined();
    await expect(quotaApi.breakdown(1, [{ amount: 100 }])).resolves.toBeUndefined();
  });
});

describe('查询串形状不变（保守形，避免顺手改动已发出的请求）', () => {
  it('未传的筛选项不出现在查询串里', async () => {
    await quotaApi.list({ page: 1, size: 20, year: 2026, status: undefined });

    const url = String(captured[0].url);
    expect(url).toContain('page=1');
    expect(url).toContain('size=20');
    expect(url).toContain('year=2026');
    // 空/未定义项必须被跳过，否则会发出 `status=undefined` 这类字面量参数
    expect(url).not.toContain('status');
    expect(url).not.toContain('undefined');
  });
});
