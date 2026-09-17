import { describe, expect, it, vi, beforeEach } from 'vitest'
import { fireEvent, screen, within } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import MailSyncPage from './MailSyncPage'
import { useAuthStore } from '../../store/authStore'
import type { UserInfo } from '../../store/authStore'

vi.mock('../../services/mailService', () => ({
  fetchMailAccounts: vi.fn(),
  createMailAccount: vi.fn(),
  updateMailAccount: vi.fn(),
  deleteMailAccount: vi.fn(),
  fetchSyncRecords: vi.fn(),
  triggerSync: vi.fn(),
  deleteSyncRecord: vi.fn(),
}))

/**
 * 086 权限收口 · `MailSyncPage` 的**两个不同权限码 + 两层嵌套表格**的渲染测试。
 * 101 另加两条**状态渲染**用例（`SIMULATED` 与未知值）。
 *
 * <pre>
 *   删除邮箱账号      ← mail_account:manage（DELETE /mail-accounts/{id}）
 *   同步收件          ← mail_sync:manage   （POST /mail-accounts/{id}/sync）
 *   删除同步记录      ← mail_sync:manage   （DELETE /mail-accounts/{id}/records/{recordId}）
 * </pre>
 *
 * <p>三个控件挂两个码，且同步记录子表（`SyncRecordList`）是**账户表的展开行**——不点开
 * 那个展开箭头，子表根本不挂载，此时对「同步收件」的否定断言测的只是"没展开"，不是"没权限"。
 * 所以每个用例都先展开第一行，并以子表顶部那条 `demoDataNoticeTitle` 提示为**
 * 子表已挂载**的证据，再做否定断言。
 *
 * <p>两个码的差异用「只多一个码」的两个用户收敛到唯一变量：`salesWithAccountManage`
 * 应当只看得见账号删除，`salesWithSyncManage` 应当只看得见同步相关的两个控件。
 *
 * <p>负向用例**必须**用非 ADMIN：`hasPerm` 对 `role === 'ADMIN'` 直接短路
 * （`hooks/usePermission.ts:10`），管理员永远拿不到"看不见"的结论。
 */
const accountRow = {
  id: 1,
  email: 'sales@corp.com',
  displayName: '销售邮箱',
  imapHost: 'imap.corp.com',
  imapPort: 993,
  smtpHost: 'smtp.corp.com',
  smtpPort: 465,
  enabled: true,
  isDefaultSender: true,
}
/** 同步记录行；`syncStatus` 是唯一被参数化的字段（101 的状态渲染用例只动它）。 */
const syncRecordRow = (syncStatus: string) => ({
  id: 100,
  accountId: 1,
  direction: 'INBOUND',
  subject: '测试邮件',
  fromAddress: 'a@b.com',
  toAddress: 'c@d.com',
  syncStatus,
  syncTime: '2026-01-01T10:00:00',
})

const adminUser: UserInfo = { id: 1, username: 'admin', displayName: '系统管理员', role: 'ADMIN' }
/** 非 ADMIN 基线：零权限码。 */
const salesNoPerm: UserInfo = { id: 2, username: 'sales01', displayName: '销售一', role: 'SALES', permissions: [] }
/** 只多了 `mail_account:manage`。 */
const salesWithAccountManage: UserInfo = { ...salesNoPerm, permissions: ['mail_account:manage'] }
/** 只多了 `mail_sync:manage`。 */
const salesWithSyncManage: UserInfo = { ...salesNoPerm, permissions: ['mail_sync:manage'] }

/**
 * 账户表（外层）里的删除链接。
 *
 * <p>必须把展开行里的同名链接剔掉：展开后同步记录子表是**嵌套在同一份 DOM 里**的，
 * 子表的「删除」也是 `common.button.delete`，全局查会把两者混在一起——那样「有同步权限
 * 但无账户权限」的用例将永远测不出账号删除的缺失。
 */
const queryAccountDelete = () =>
  screen.queryAllByText('common.button.delete').filter((el) => !el.closest('.ant-table-expanded-row'))
/** 展开出来的同步记录子表。 */
const expandedRow = () => document.querySelector('.ant-table-expanded-row') as HTMLElement | null
const querySyncButton = () => screen.queryAllByText('pages.mail.btnSyncInbox')
const querySyncRecordDelete = () => {
  const row = expandedRow()
  return row ? within(row).queryAllByText('common.button.delete') : []
}

async function renderPage(user: UserInfo, syncStatus = 'SYNCED') {
  useAuthStore.setState({ user })
  const { fetchMailAccounts, fetchSyncRecords } = await import('../../services/mailService')
  vi.mocked(fetchMailAccounts).mockResolvedValue([accountRow] as never)
  vi.mocked(fetchSyncRecords).mockResolvedValue({
    items: [syncRecordRow(syncStatus)],
    total: 1,
    page: 1,
    pageSize: 10,
  } as never)

  renderWithProviders(<MailSyncPage />)
  // 等到账户表真的渲染出这一行，后续的否定断言才有意义。
  await screen.findByText('sales@corp.com')
}

/** 展开第一个账户行，让同步记录子表真的挂载。 */
async function expandFirstAccount() {
  const icon = document.querySelector('.ant-table-row-expand-icon') as HTMLElement | null
  expect(icon, '账户表应渲染出展开图标，否则同步记录子表根本没挂载，对它的一切否定断言都是假绿').not.toBeNull()
  fireEvent.click(icon as HTMLElement)
  // 子表挂载的证据：`SyncRecordList` 顶部那条「演示数据」提示（无条件渲染）+ 记录行
  await screen.findByText('pages.mail.demoDataNoticeTitle')
}

/** 反空洞守卫：外层账户行 + 展开出来的子表内容都真的渲染了。 */
async function expectBothTablesRendered() {
  expect(screen.getByText('sales@corp.com')).toBeInTheDocument()
  expect(screen.getByText('销售邮箱')).toBeInTheDocument()
  expect(expandedRow(), '同步记录子表未挂载').not.toBeNull()
  expect(screen.getByText('pages.mail.demoDataNoticeTitle')).toBeInTheDocument()
  // 子表自己的 request 是异步的，等它把记录行渲染出来才算「子表真的渲染好了」
  await screen.findByText('测试邮件')
}

describe('MailSyncPage 权限收口（086）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('ADMIN 看得见账号「删除」、「同步收件」与同步记录「删除」', async () => {
    await renderPage(adminUser)
    await expandFirstAccount()

    await expectBothTablesRendered()
    expect(queryAccountDelete()).toHaveLength(1)
    expect(querySyncButton()).toHaveLength(1)
    expect(querySyncRecordDelete()).toHaveLength(1)
  })

  it('零权限的 SALES：两张表都渲染了，但三个控件都不在', async () => {
    await renderPage(salesNoPerm)
    await expandFirstAccount()

    await expectBothTablesRendered()
    expect(queryAccountDelete()).toHaveLength(0)
    expect(querySyncButton()).toHaveLength(0)
    expect(querySyncRecordDelete()).toHaveLength(0)
  })

  it('只持有 mail_account:manage 的 SALES：看得见账号「删除」，看不见同步的两个控件', async () => {
    await renderPage(salesWithAccountManage)
    await expandFirstAccount()

    await expectBothTablesRendered()
    expect(queryAccountDelete()).toHaveLength(1)
    expect(querySyncButton()).toHaveLength(0)
    expect(querySyncRecordDelete()).toHaveLength(0)
  })

  it('只持有 mail_sync:manage 的 SALES：看得见「同步收件」与同步记录「删除」，看不见账号「删除」', async () => {
    await renderPage(salesWithSyncManage)
    await expandFirstAccount()

    await expectBothTablesRendered()
    expect(querySyncButton()).toHaveLength(1)
    expect(querySyncRecordDelete()).toHaveLength(1)
    expect(queryAccountDelete()).toHaveLength(0)
  })
})

/**
 * 101 加在状态列上的两条：三向渲染与未知值兜底。
 *
 * <p>它们与权限面无关（用 ADMIN，只关心渲染），所以另起一组——混进上面那组会让「授权收口」与
 * 「诚实化」两类判据共用同一份 setup，一处坏了分不清是哪一类。
 *
 * <p>⚠️ 这两条**替代不了**后端那三条：它们只证明"界面不再把演示记录显示成已同步"，
 * 证不了"后端默认拒绝且不写记录"。前端能做的只有不撒谎。
 */
describe('MailSyncPage 收信状态渲染（101）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('SIMULATED 渲染成橙色「模拟」——既不是绿色「已同步」，也不是红色「失败」', async () => {
    await renderPage(adminUser, 'SIMULATED')
    await expandFirstAccount()
    await expectBothTablesRendered()

    expect(screen.queryAllByText('pages.mail.tagSimulated')).toHaveLength(1)
    expect(screen.queryAllByText('pages.mail.tagSynced')).toHaveLength(0)
    // 三向少写一支（SIMULATED 落到兜底）时这条会红：把演示记录显示成「失败」同样是假话。
    expect(screen.queryAllByText('pages.mail.tagFailed')).toHaveLength(0)
  })

  it('未知状态仍兜底为红色「失败」，原值不外泄给用户', async () => {
    await renderPage(adminUser, 'SOMETHING_NEW')
    await expandFirstAccount()
    await expectBothTablesRendered()

    expect(screen.queryAllByText('pages.mail.tagFailed')).toHaveLength(1)
    expect(screen.queryAllByText('pages.mail.tagSynced')).toHaveLength(0)
    expect(screen.queryAllByText('pages.mail.tagSimulated')).toHaveLength(0)
    expect(screen.queryAllByText('SOMETHING_NEW')).toHaveLength(0)
  })
})
