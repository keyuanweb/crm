import { beforeEach, describe, expect, it, vi } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useCustomFieldFilterColumns } from './useCustomFieldFilters'
import type { CustomField } from '../types/customField'

vi.mock('../services/customFieldService', () => ({
  fetchFieldDefinitions: vi.fn(),
}))

/**
 * 103 · 自定义字段**筛选列**消费字段权限标记。
 *
 * <p>为什么要有这个文件：HIDDEN 字段此前照样生成 `cf_<fieldId>` 筛选列，被 4 个列表页
 * （客户/线索/商机/工单）消费 ⇒ 工具栏里出现一个用户看不见、列表里也不下发值的筛选条件，
 * 筛出来的结果无法解释。这条侧信道<b>没有任何后端用例看着</b>。
 *
 * <p>两个方向都要钉：HIDDEN **不产**列；而 `permission` 缺席时**照旧产**列——`/custom-fields/definitions`
 * 之外的取数形态（分页端点）不带这个字段，把它当成 HIDDEN 会让整排筛选列凭空消失，
 * 那与后端「未配置即 EDITABLE」的 fail-open 默认正好相反。
 */
const HIDDEN_ID = 911
const VISIBLE_ID = 912
const NO_PERMISSION_ID = 913
const DISABLED_ID = 914

function def(
  id: number,
  permission: CustomField['permission'],
  enabled = true,
): CustomField {
  return {
    id,
    entityType: 'LEAD',
    name: `字段${id}`,
    fieldType: 'TEXT',
    required: false,
    enabled,
    version: 0,
    permission,
  }
}

const definitions = [
  def(HIDDEN_ID, { hidden: true, readOnly: false }),
  def(VISIBLE_ID, { hidden: false, readOnly: true }),
  def(NO_PERMISSION_ID, undefined),
  def(DISABLED_ID, { hidden: false, readOnly: false }, false),
]

// 单例 client：`wrapper` 每次渲染都会执行，在里面 new 一个会让 Provider 每次换实例。
// 代价是缓存跨用例存活 ⇒ beforeEach 里清一次。
const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
function wrapper({ children }: { children: ReactNode }) {
  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
}

async function renderColumns(): Promise<string[]> {
  const svc = await import('../services/customFieldService')
  vi.mocked(svc.fetchFieldDefinitions).mockResolvedValue(definitions as never)
  const { result } = renderHook(() => useCustomFieldFilterColumns('LEAD'), { wrapper })
  await waitFor(() => expect(result.current.length).toBeGreaterThan(0))
  return result.current.map((c) => String(c.dataIndex))
}

describe('useCustomFieldFilterColumns 的字段权限标记（103）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    queryClient.clear()
  })

  it('HIDDEN 字段不产筛选列（其余字段照旧产，反空洞）', async () => {
    const dataIndexes = await renderColumns()

    expect(dataIndexes).not.toContain(`cf_${HIDDEN_ID}`)
    expect(dataIndexes).toContain(`cf_${VISIBLE_ID}`)
  })

  it('permission 缺席时照旧产列（缺标记 = 当可编辑，与后端 fail-open 同向）', async () => {
    const dataIndexes = await renderColumns()

    expect(dataIndexes).toContain(`cf_${NO_PERMISSION_ID}`)
  })

  it('enabled=false 的字段照旧不产列', async () => {
    const dataIndexes = await renderColumns()

    expect(dataIndexes).not.toContain(`cf_${DISABLED_ID}`)
  })
})
