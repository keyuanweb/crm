import { AxiosError } from 'axios'
import { describe, expect, it } from 'vitest'
import { REQUEST_TIMEOUT_MS, apiClient, extractErrorMessage } from './apiClient'

/**
 * 全局请求超时的行为层证据（2026-09-13）。
 *
 * <p>背景：用户报「渠道 ROI 页面加载数据有问题」，排查结论是**后端进程楔住**——TCP 连接建得起来、
 * 响应一个字节都不回（实测 6s/75s 皆无状态行）。而 axios 的 `timeout` 默认是 `0`，
 * 意思是**永不超时**：于是页面的 loading 永不结束，用户看到的只有转圈，既没有报错也拿不到任何
 * 线索去区分「慢」和「死」。同代码的新实例毫秒级应答，证明不是代码问题——但**页面本来就无法表达
 * 这件事**，这才是需要修的那半。
 *
 * <p>本文件钉三件事：
 * ① 实例的默认超时**是个有限值**（它若被改回 0，本条立刻转红）；
 * ② 超时/连不上时 `extractErrorMessage` **回落到调用方文案**——axios 自己的 message 是英文的
 * （`timeout of 30000ms exceeded` / `Network Error`），那是给开发者看的，不是给用户看的；
 * ③ 后端有响应体时**仍然优先用后端的文案**（这条是防止修 ② 时把优先级弄反）。
 *
 * <p>纯文件传输（下载导出件/报表/附件/PDF、上传导入件）**刻意不受**这个超时约束，
 * 各调用点显式传 `timeout: 0`——理由见 `apiClient.REQUEST_TIMEOUT_MS` 的注释：
 * 那里的耗时由文件大小决定，超时会把「其实成功了」报成失败，导入尤其危险（用户重试即重复导入）。
 */
describe('apiClient 的全局超时', () => {
  it('实例默认超时是有限值（0 = 永不超时，正是"一直转圈不报错"的成因）', () => {
    expect(apiClient.defaults.timeout).toBe(REQUEST_TIMEOUT_MS)
    expect(REQUEST_TIMEOUT_MS).toBeGreaterThan(0)
  })
})

describe('extractErrorMessage 的回落规则', () => {
  /** 有响应体：后端文案优先。 */
  it('后端有响应体时用后端文案', () => {
    const err = new AxiosError('Request failed with status code 400', 'ERR_BAD_REQUEST', undefined, undefined, {
      data: { error: { message: '预算不能为负数' } },
    } as never)
    expect(extractErrorMessage(err, '兜底文案')).toBe('预算不能为负数')
  })

  /** 超时：没有响应体，axios 的 message 是英文的，用户看不懂。 */
  it('超时（无响应体）回落到调用方文案', () => {
    const err = new AxiosError('timeout of 30000ms exceeded', 'ECONNABORTED')
    expect(extractErrorMessage(err, '加载失败，请稍后重试')).toBe('加载失败，请稍后重试')
  })

  /** 连不上：同上，且这是"后端没起"时页面上真正会走的那条路。 */
  it('连不上后端（无响应体）同样回落到调用方文案', () => {
    const err = new AxiosError('Network Error', 'ERR_NETWORK')
    expect(extractErrorMessage(err, '加载失败，请稍后重试')).toBe('加载失败，请稍后重试')
  })

  /** 代码自己抛的 Error（不是 axios 错误）：落到函数默认文案。 */
  it('非 axios 错误回落到默认文案', () => {
    expect(extractErrorMessage(new Error('boom'))).toBe('请求失败，请稍后重试')
  })
})
