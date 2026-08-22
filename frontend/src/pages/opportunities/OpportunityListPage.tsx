import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  createOpportunity,
  deleteOpportunity,
  fetchOpportunities,
  updateOpportunity,
  type OpportunityPayload,
} from '../../services/opportunityService'
import { fetchCustomers } from '../../services/customerService'
import { extractErrorMessage } from '../../services/apiClient'
import { formatAmount, type Opportunity } from '../../types/opportunity'

export default function OpportunityListPage() {
  const [keyword, setKeyword] = useState('')
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(1)
  const [modalOpen, setModalOpen] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [error, setError] = useState('')
  const [form, setForm] = useState<OpportunityPayload>({
    customerId: 0,
    name: '',
    expectedAmountMin: undefined,
    expectedAmountMax: undefined,
    remark: '',
  })
  const queryClient = useQueryClient()

  const { data, isLoading } = useQuery({
    queryKey: ['opportunities', search, page],
    queryFn: () =>
      fetchOpportunities({ keyword: search || undefined, page, pageSize: 20 }),
  })

  const customers = useQuery({
    queryKey: ['customers-options'],
    queryFn: () => fetchCustomers({ page: 1, pageSize: 100 }),
    enabled: modalOpen,
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['opportunities'] })

  const saveMutation = useMutation({
    mutationFn: (payload: OpportunityPayload) =>
      editingId ? updateOpportunity(editingId, payload) : createOpportunity(payload),
    onSuccess: () => {
      setModalOpen(false)
      setError('')
      invalidate()
    },
    onError: (err) => setError(extractErrorMessage(err, '保存失败')),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => deleteOpportunity(id),
    onSuccess: () => invalidate(),
    onError: (err) => setError(extractErrorMessage(err, '删除失败')),
  })

  const openCreate = () => {
    setEditingId(null)
    setForm({ customerId: 0, name: '', expectedAmountMin: undefined, expectedAmountMax: undefined, remark: '' })
    setError('')
    setModalOpen(true)
  }

  const openEdit = (o: Opportunity) => {
    setEditingId(o.id)
    setForm({
      customerId: o.customerId,
      name: o.name,
      expectedAmountMin: o.expectedAmountMin,
      expectedAmountMax: o.expectedAmountMax,
      remark: o.remark ?? '',
      status: o.status,
      version: o.version,
    })
    setError('')
    setModalOpen(true)
  }

  const total = data?.total ?? 0
  const totalPages = Math.max(1, Math.ceil(total / 20))

  return (
    <div>
      <div className="mb-4 flex items-center justify-between">
        <h2 className="text-xl font-semibold text-gray-800">商机管理</h2>
        <button onClick={openCreate} className="rounded bg-blue-600 px-4 py-2 text-white hover:bg-blue-700">
          新增商机
        </button>
      </div>

      {error && (
        <div role="alert" className="mb-4 rounded bg-red-50 p-3 text-sm text-red-700">
          {error}
        </div>
      )}

      <div className="mb-4 flex gap-2">
        <input
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              setSearch(keyword)
              setPage(1)
            }
          }}
          placeholder="搜索商机名称"
          className="w-72 rounded border border-gray-300 px-3 py-2 focus:border-blue-500 focus:outline-none"
        />
        <button
          onClick={() => {
            setSearch(keyword)
            setPage(1)
          }}
          className="rounded border border-gray-300 px-4 py-2 hover:bg-gray-100"
        >
          搜索
        </button>
      </div>

      {isLoading ? (
        <p className="py-8 text-center text-gray-400">加载中…</p>
      ) : (
        <table className="w-full rounded-lg bg-white shadow">
          <thead>
            <tr className="border-b text-left text-sm text-gray-500">
              <th className="px-4 py-3">商机名称</th>
              <th className="px-4 py-3">关联客户</th>
              <th className="px-4 py-3">预期金额（元）</th>
              <th className="px-4 py-3">销售机会数</th>
              <th className="px-4 py-3">状态</th>
              <th className="px-4 py-3">操作</th>
            </tr>
          </thead>
          <tbody>
            {(data?.items ?? []).map((o) => (
              <tr key={o.id} className="border-b text-sm hover:bg-gray-50">
                <td className="px-4 py-3 font-medium">{o.name}</td>
                <td className="px-4 py-3">{o.customerName ?? o.customerId}</td>
                <td className="px-4 py-3">
                  {formatAmount(o.expectedAmountMin)} ~ {formatAmount(o.expectedAmountMax)}
                </td>
                <td className="px-4 py-3">{o.salesOpportunityCount}</td>
                <td className="px-4 py-3">{o.status === 'ACTIVE' ? '进行中' : '已归档'}</td>
                <td className="px-4 py-3">
                  <button onClick={() => openEdit(o)} className="mr-3 text-blue-600 hover:underline">
                    编辑
                  </button>
                  <button
                    onClick={() => {
                      if (window.confirm(`确定删除商机「${o.name}」及其销售机会吗？`)) {
                        void deleteMutation.mutate(o.id)
                      }
                    }}
                    className="text-red-600 hover:underline"
                  >
                    删除
                  </button>
                </td>
              </tr>
            ))}
            {data?.items?.length === 0 && (
              <tr>
                <td colSpan={6} className="px-4 py-8 text-center text-gray-400">
                  暂无商机数据
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
            <h3 className="mb-4 text-lg font-semibold">{editingId ? '编辑商机' : '新增商机'}</h3>
            <form
              onSubmit={(e) => {
                e.preventDefault()
                if (!form.customerId) {
                  setError('请选择关联客户')
                  return
                }
                void saveMutation.mutateAsync(form)
              }}
              className="space-y-4"
            >
              <div>
                <label className="mb-1 block text-sm font-medium text-gray-700">关联客户 *</label>
                <select
                  value={form.customerId}
                  onChange={(e) => setForm((f) => ({ ...f, customerId: Number(e.target.value) }))}
                  className="w-full rounded border border-gray-300 px-3 py-2"
                >
                  <option value={0}>请选择客户</option>
                  {(customers.data?.items ?? []).map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.name}（{c.company}）
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label className="mb-1 block text-sm font-medium text-gray-700">商机名称 *</label>
                <input
                  value={form.name}
                  onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
                  required
                  className="w-full rounded border border-gray-300 px-3 py-2"
                />
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="mb-1 block text-sm font-medium text-gray-700">预期金额下限（元）</label>
                  <input
                    type="number"
                    min={0}
                    value={form.expectedAmountMin ?? ''}
                    onChange={(e) =>
                      setForm((f) => ({ ...f, expectedAmountMin: e.target.value ? Number(e.target.value) * 100 : undefined }))
                    }
                    className="w-full rounded border border-gray-300 px-3 py-2"
                  />
                </div>
                <div>
                  <label className="mb-1 block text-sm font-medium text-gray-700">预期金额上限（元）</label>
                  <input
                    type="number"
                    min={0}
                    value={form.expectedAmountMax ?? ''}
                    onChange={(e) =>
                      setForm((f) => ({ ...f, expectedAmountMax: e.target.value ? Number(e.target.value) * 100 : undefined }))
                    }
                    className="w-full rounded border border-gray-300 px-3 py-2"
                  />
                </div>
              </div>
              <div>
                <label className="mb-1 block text-sm font-medium text-gray-700">备注</label>
                <input
                  value={form.remark ?? ''}
                  onChange={(e) => setForm((f) => ({ ...f, remark: e.target.value }))}
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
