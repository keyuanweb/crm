import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  closeSalesOpportunity,
  createSalesOpportunity,
  fetchOpportunities,
  fetchSalesOpportunities,
  type SalesOpportunityPayload,
} from '../../services/opportunityService'
import { extractErrorMessage } from '../../services/apiClient'
import { ACTIVE_STAGES, formatAmount, STAGE_LABELS, type OpportunityStage } from '../../types/opportunity'

export default function SalesOpportunityListPage() {
  const [stage, setStage] = useState('')
  const [page, setPage] = useState(1)
  const [modalOpen, setModalOpen] = useState(false)
  const [error, setError] = useState('')
  const [form, setForm] = useState<SalesOpportunityPayload>({
    opportunityId: 0,
    amount: undefined,
    stage: 'INITIAL_CONTACT',
    expectedCloseDate: undefined,
  })
  const queryClient = useQueryClient()

  const { data, isLoading } = useQuery({
    queryKey: ['sales-opportunities', stage, page],
    queryFn: () => fetchSalesOpportunities({ stage: stage || undefined, page, pageSize: 20 }),
  })

  const opportunities = useQuery({
    queryKey: ['opportunities-options'],
    queryFn: () => fetchOpportunities({ page: 1, pageSize: 100 }),
    enabled: modalOpen,
  })

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['sales-opportunities'] })
    queryClient.invalidateQueries({ queryKey: ['opportunities'] })
  }

  const saveMutation = useMutation({
    mutationFn: (payload: SalesOpportunityPayload) => createSalesOpportunity(payload),
    onSuccess: () => {
      setModalOpen(false)
      setError('')
      invalidate()
    },
    onError: (err) => setError(extractErrorMessage(err, '创建失败')),
  })

  const closeMutation = useMutation({
    mutationFn: ({ id, result, version }: { id: number; result: 'WON' | 'LOST'; version: number }) =>
      closeSalesOpportunity(id, result, version),
    onSuccess: () => invalidate(),
    onError: (err) => setError(extractErrorMessage(err, '关闭失败')),
  })

  const total = data?.total ?? 0
  const totalPages = Math.max(1, Math.ceil(total / 20))

  return (
    <div>
      <div className="mb-4 flex items-center justify-between">
        <h2 className="text-xl font-semibold text-gray-800">销售机会管道</h2>
        <button
          onClick={() => {
            setForm({ opportunityId: 0, amount: undefined, stage: 'INITIAL_CONTACT', expectedCloseDate: undefined })
            setError('')
            setModalOpen(true)
          }}
          className="rounded bg-blue-600 px-4 py-2 text-white hover:bg-blue-700"
        >
          新增销售机会
        </button>
      </div>

      {error && (
        <div role="alert" className="mb-4 rounded bg-red-50 p-3 text-sm text-red-700">
          {error}
        </div>
      )}

      <div className="mb-4">
        <select
          value={stage}
          onChange={(e) => {
            setStage(e.target.value)
            setPage(1)
          }}
          className="rounded border border-gray-300 px-3 py-2"
        >
          <option value="">全部阶段</option>
          {Object.entries(STAGE_LABELS).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </select>
      </div>

      {isLoading ? (
        <p className="py-8 text-center text-gray-400">加载中…</p>
      ) : (
        <table className="w-full rounded-lg bg-white shadow">
          <thead>
            <tr className="border-b text-left text-sm text-gray-500">
              <th className="px-4 py-3">所属商机</th>
              <th className="px-4 py-3">关联客户</th>
              <th className="px-4 py-3">金额（元）</th>
              <th className="px-4 py-3">阶段</th>
              <th className="px-4 py-3">预计成交</th>
              <th className="px-4 py-3">操作</th>
            </tr>
          </thead>
          <tbody>
            {(data?.items ?? []).map((so) => (
              <tr key={so.id} className="border-b text-sm hover:bg-gray-50">
                <td className="px-4 py-3">{so.opportunityName ?? so.opportunityId}</td>
                <td className="px-4 py-3">{so.customerName ?? '-'}</td>
                <td className="px-4 py-3">{formatAmount(so.amount)}</td>
                <td className="px-4 py-3">
                  <span
                    className={
                      so.stage === 'CLOSED_WON'
                        ? 'rounded bg-green-100 px-2 py-1 text-xs text-green-700'
                        : so.stage === 'CLOSED_LOST'
                          ? 'rounded bg-gray-100 px-2 py-1 text-xs text-gray-600'
                          : 'rounded bg-blue-100 px-2 py-1 text-xs text-blue-700'
                    }
                  >
                    {STAGE_LABELS[so.stage]}
                  </span>
                </td>
                <td className="px-4 py-3">{so.expectedCloseDate ?? '-'}</td>
                <td className="px-4 py-3">
                  {ACTIVE_STAGES.includes(so.stage) ? (
                    <>
                      <button
                        onClick={() =>
                          window.confirm('确定将该机会关闭为赢单吗？') &&
                          void closeMutation.mutate({ id: so.id, result: 'WON', version: so.version })
                        }
                        className="mr-3 text-green-600 hover:underline"
                      >
                        赢单
                      </button>
                      <button
                        onClick={() =>
                          window.confirm('确定将该机会关闭为输单吗？') &&
                          void closeMutation.mutate({ id: so.id, result: 'LOST', version: so.version })
                        }
                        className="text-red-600 hover:underline"
                      >
                        输单
                      </button>
                    </>
                  ) : (
                    <span className="text-gray-400">
                      {so.closeResult === 'WON' ? '已赢单' : '已输单'}
                    </span>
                  )}
                </td>
              </tr>
            ))}
            {data?.items?.length === 0 && (
              <tr>
                <td colSpan={6} className="px-4 py-8 text-center text-gray-400">
                  暂无销售机会
                </td>
              </tr>
            )}
          </tbody>
        </table>
      )}

      <div className="mt-4 flex items-center justify-between text-sm text-gray-500">
        <span>
          共 {total} 条，第 {page} / {totalPages} 页
        </span>
        <div className="flex gap-2">
          <button
            disabled={page <= 1}
            onClick={() => setPage((p) => p - 1)}
            className="rounded border border-gray-300 px-3 py-1 disabled:opacity-40"
          >
            上一页
          </button>
          <button
            disabled={page >= totalPages}
            onClick={() => setPage((p) => p + 1)}
            className="rounded border border-gray-300 px-3 py-1 disabled:opacity-40"
          >
            下一页
          </button>
        </div>
      </div>

      {modalOpen && (
        <div className="fixed inset-0 flex items-center justify-center bg-black/30" onClick={() => setModalOpen(false)}>
          <div className="w-full max-w-lg rounded-lg bg-white p-6" onClick={(e) => e.stopPropagation()}>
            <h3 className="mb-4 text-lg font-semibold">新增销售机会</h3>
            <form
              onSubmit={(e) => {
                e.preventDefault()
                if (!form.opportunityId) {
                  setError('请选择所属商机')
                  return
                }
                void saveMutation.mutateAsync(form)
              }}
              className="space-y-4"
            >
              <div>
                <label className="mb-1 block text-sm font-medium text-gray-700">所属商机 *</label>
                <select
                  value={form.opportunityId}
                  onChange={(e) => setForm((f) => ({ ...f, opportunityId: Number(e.target.value) }))}
                  className="w-full rounded border border-gray-300 px-3 py-2"
                >
                  <option value={0}>请选择商机</option>
                  {(opportunities.data?.items ?? []).map((o) => (
                    <option key={o.id} value={o.id}>
                      {o.name}（{o.customerName ?? o.customerId}）
                    </option>
                  ))}
                </select>
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="mb-1 block text-sm font-medium text-gray-700">金额（元）</label>
                  <input
                    type="number"
                    min={0}
                    value={form.amount ? form.amount / 100 : ''}
                    onChange={(e) =>
                      setForm((f) => ({ ...f, amount: e.target.value ? Number(e.target.value) * 100 : undefined }))
                    }
                    className="w-full rounded border border-gray-300 px-3 py-2"
                  />
                </div>
                <div>
                  <label className="mb-1 block text-sm font-medium text-gray-700">阶段 *</label>
                  <select
                    value={form.stage}
                    onChange={(e) =>
                      setForm((f) => ({ ...f, stage: e.target.value as OpportunityStage }))
                    }
                    className="w-full rounded border border-gray-300 px-3 py-2"
                  >
                    {ACTIVE_STAGES.map((s) => (
                      <option key={s} value={s}>
                        {STAGE_LABELS[s]}
                      </option>
                    ))}
                  </select>
                </div>
              </div>
              <div>
                <label className="mb-1 block text-sm font-medium text-gray-700">预计成交日期</label>
                <input
                  type="date"
                  value={form.expectedCloseDate ?? ''}
                  onChange={(e) => setForm((f) => ({ ...f, expectedCloseDate: e.target.value || undefined }))}
                  className="w-full rounded border border-gray-300 px-3 py-2"
                />
              </div>
              {error && (
                <div role="alert" className="rounded bg-red-50 p-3 text-sm text-red-700">
                  {error}
                </div>
              )}
              <div className="flex justify-end gap-2">
                <button
                  type="button"
                  onClick={() => setModalOpen(false)}
                  className="rounded border border-gray-300 px-4 py-2 hover:bg-gray-100"
                >
                  取消
                </button>
                <button
                  type="submit"
                  disabled={saveMutation.isPending}
                  className="rounded bg-blue-600 px-4 py-2 text-white hover:bg-blue-700 disabled:opacity-50"
                >
                  {saveMutation.isPending ? '保存中…' : '保存'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}
