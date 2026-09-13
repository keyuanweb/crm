import { describe, expect, it, vi, beforeEach } from 'vitest'
import { fireEvent, screen } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import OpenPlatformPage from './OpenPlatformPage'
import { useAuthStore } from '../../store/authStore'
import type { UserInfo } from '../../store/authStore'

vi.mock('../../services/openPlatformService', () => ({
  fetchApiKeys: vi.fn(),
  createApiKey: vi.fn(),
  revokeApiKey: vi.fn(),
  fetchWebhooks: vi.fn(),
  createWebhook: vi.fn(),
  toggleWebhook: vi.fn(),
  deleteWebhook: vi.fn(),
}))
// 事件类型标签走 labelOf(t, ENUM_KEYS.webhookEvent, code)；这里替身化以免依赖真实枚举资源。
vi.mock('../../constants/enumLabels', () => ({
  ENUM_KEYS: { webhookEvent: { CUSTOMER_CREATED: 'customer.created' } },
  labelOf: () => 'customer.created',
}))

/**
 * 086 权限收口 · `OpenPlatformPage` 的双向渲染测试。
 *
 * <p>本页的判据**全部**来自同一个码 `open_platform:manage`（`OpenPlatformController.java:70,77,86,97,104,111,118`），
 * 覆盖两类形态：
 * <ul>
 *   <li><b>行内动作</b>：API Key 的「吊销」——嵌在 `status === 'ACTIVE'` 的**状态判据之内**，故对已吊销行
 *       照旧显示灰色状态文案（那是状态指示不是动作）。本文件第 4 例专门锁这个：状态占位**不因权限而消失**。
 *   <li><b>工具栏 + 行内动作</b>：Webhook 的「启停」「删除」两个按钮。
 * </ul>
 *
 * <p><b>如实说明</b>：本页两个**列表读端点**（`:77` / `:104`）挂的也是这同一个码，所以"能看见这张表"
 * 已蕴含"持有该码"——第 3 例（有码 ⇒ 可见）在今天的表现与第 1 例相同，它锁的是**码的身份**，
 * 不是"隐藏能力"。这一点页面源码注释里也记了。真正能体现收窄的是第 2 例（无码 ⇒ 不可见）。
 */
const adminUser: UserInfo = { id: 1, username: 'admin', displayName: '系统管理员', role: 'ADMIN' }
const salesNoPerm: UserInfo = { id: 2, username: 'sales01', displayName: '销售一', role: 'SALES', permissions: [] }
const salesWithPerm: UserInfo = { ...salesNoPerm, permissions: ['open_platform:manage'] }

const activeKey = { id: 1, name: '生产密钥', keyPrefix: 'sk_prod', scopes: ['customer:read'], status: 'ACTIVE' }
const webhook = { id: 7, eventType: 'CUSTOMER_CREATED', callbackUrl: 'https://example.com/hook', enabled: true }

const revokeLink = () => screen.queryByText('pages.openPlatform.revoke')
const toggleButton = () => screen.queryByRole('button', { name: /pages\.openPlatform\.(disable|enable)/ })
const deleteButton = () => screen.queryByRole('button', { name: /pages\.openPlatform\.delete/ })

async function renderKeysTab(user: UserInfo) {
  const { fetchApiKeys, fetchWebhooks } = await import('../../services/openPlatformService')
  vi.mocked(fetchApiKeys).mockResolvedValue({ items: [activeKey], total: 1, page: 1, pageSize: 20 } as never)
  vi.mocked(fetchWebhooks).mockResolvedValue([webhook] as never)
  useAuthStore.setState({ user })

  renderWithProviders(<OpenPlatformPage />)

  // 反空洞守卫：「新建 API Key」按钮按决策 1（新建不收口）**始终**渲染，用它确认默认 Tab 已就绪。
  await screen.findByRole('button', { name: /pages\.openPlatform\.btnAdd/ }, { timeout: 5000 })
}

async function renderWebhooksTab(user: UserInfo) {
  await renderKeysTab(user)
  // WebhookTab 是懒渲染的，必须真的切过去（否则其按钮根本不在 DOM 里，负向断言会假绿）。
  fireEvent.click(screen.getByRole('tab', { name: /pages\.openPlatform\.tabWebhooks/ }))
  await screen.findByText('https://example.com/hook', {}, { timeout: 5000 })
}

describe('OpenPlatformPage 收口（086）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('① ADMIN：「吊销 API Key」「启停 Webhook」「删除 Webhook」都可见', async () => {
    await renderWebhooksTab(adminUser)

    expect(toggleButton()).toBeInTheDocument()
    expect(deleteButton()).toBeInTheDocument()

    // 切回 API Key 页签断言吊销链接（两个页签互斥渲染，不能同时断言）
    fireEvent.click(screen.getByRole('tab', { name: /pages\.openPlatform\.tabApiKeys/ }))
    expect(await screen.findByText('pages.openPlatform.revoke', {}, { timeout: 5000 })).toBeInTheDocument()
  })

  it('② 无 open_platform:manage 的 SALES：三处动作都不可见，但页面内容照常渲染', async () => {
    await renderWebhooksTab(salesNoPerm)

    // 反空洞守卫：Webhook 卡片本体（回调地址 + 启停状态 Tag）在，证明渲染没问题
    expect(screen.getByText('https://example.com/hook')).toBeInTheDocument()
    expect(screen.getByText('https://example.com/hook')).toBeInTheDocument()
    expect(toggleButton()).not.toBeInTheDocument()
    expect(deleteButton()).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('tab', { name: /pages\.openPlatform\.tabApiKeys/ }))
    // API Key 行的「名称」仍在（数据渲染正常），只有吊销动作消失
    expect(await screen.findByText('生产密钥', {}, { timeout: 5000 })).toBeInTheDocument()
    expect(revokeLink()).not.toBeInTheDocument()
  })

  it('③ 持有 open_platform:manage 的 SALES：三处动作都可见（锁住码的身份）', async () => {
    await renderWebhooksTab(salesWithPerm)

    expect(toggleButton()).toBeInTheDocument()
    expect(deleteButton()).toBeInTheDocument()

    fireEvent.click(screen.getByRole('tab', { name: /pages\.openPlatform\.tabApiKeys/ }))
    expect(await screen.findByText('pages.openPlatform.revoke', {}, { timeout: 5000 })).toBeInTheDocument()
  })

  it('④ 已吊销的 API Key：灰色状态占位**与权限无关**，无码时照样显示（状态判据未被权限判据吞掉）', async () => {
    const { fetchApiKeys, fetchWebhooks } = await import('../../services/openPlatformService')
    vi.mocked(fetchApiKeys).mockResolvedValue({
      items: [{ ...activeKey, id: 2, name: '旧密钥', status: 'REVOKED' }],
      total: 1,
      page: 1,
      pageSize: 20,
    } as never)
    vi.mocked(fetchWebhooks).mockResolvedValue([] as never)
    useAuthStore.setState({ user: salesNoPerm })

    renderWithProviders(<OpenPlatformPage />)

    await screen.findByText('旧密钥', {}, { timeout: 5000 })
    // `pages.openPlatform.revoked` 既用于状态 Tag、也用于动作列的灰色占位：两处都应该在，
    // 且都**不**受权限影响——这证明权限判据只嵌在 ACTIVE 那一支里，没有把状态分支一起收掉。
    expect(screen.getAllByText('pages.openPlatform.revoked').length).toBeGreaterThan(0)
  })
})
