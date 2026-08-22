import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import {
  createCustomer,
  deleteCustomer,
  downloadTemplate,
  exportCustomers,
  importCustomers,
  updateCustomer,
  type CustomerPayload,
  type ImportResult,
} from '../../services/customerService'
import { extractErrorMessage } from '../../services/apiClient'
import { useAuthStore } from '../../store/authStore'
import { useCustomerList } from '../../hooks/useCustomers'
import type { Customer } from '../../types/customer'
import CustomerForm from './CustomerForm'

export default function CustomerListPage() {
  const [keyword, setKeyword] = useState('')
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState('')
  const [page, setPage] = useState(1)
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<Customer | null>(null)
  const [error, setError] = useState('')
  const [importResult, setImportResult] = useState<ImportResult | null>(null)
  const queryClient = useQueryClient()
  const user = useAuthStore((s) => s.user)
  const isAdmin = user?.role === 'ADMIN'

  const { data, isLoading } = useCustomerList({
    keyword: search || undefined,
    status: status || undefined,
    page,
    pageSize: 20,
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['customers'] })

  const saveMutation = useMutation({
    mutationFn: (payload: CustomerPayload) =>
      editing ? updateCustomer(editing.id, payload) : createCustomer(payload),
    onSuccess: () => {
      setModalOpen(false)
      setEditing(null)
      setError('')
      invalidate()
    },
    onError: (err) => setError(extractErrorMessage(err, '保存失败')),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => deleteCustomer(id),
    onSuccess: () => invalidate(),
    onError: (err) => setError(extractErrorMessage(err, '删除失败')),
  })

  const openCreate = () => {
    setEditing(null)
    setError('')
    setModalOpen(true)
  }

  const openEdit = (c: Customer) => {
    setEditing(c)
    setError('')
    setModalOpen(true)
  }

  const confirmDelete = (c: Customer) => {
    if (window.confirm(`确定删除客户「${c.name}」吗？（逻辑删除，可恢复）`)) {
      void deleteMutation.mutate(c.id)
    }
  }

  const total = data?.total ?? 0
  const totalPages = Math.max(1, Math.ceil(total / 20))

  return (
    <div>
      <div className="mb-4 flex items-center justify-between">
        <h2 className="text-xl font-semibold text-gray-800">客户管理</h2>
        <div className="flex items-center gap-2">
          {isAdmin && (
            <>
              <button
                onClick={() => {
                  setImportResult(null)
                  document.getElementById('import-file-input')?.click()
                }}
                className="rounded border border-gray-300 px-4 py-2 hover:bg-gray-100"
              >
                导入
              </button>
              <input
                id="import-file-input"
                type="file"
                accept=".xlsx"
                className="hidden"
                onChange={async (e) => {
                  const file = e.target.files?.[0]
                  e.target.value = ''
                  if (!file) return
                  try {
                    const result = await importCustomers(file)
                    setImportResult(result)
                    invalidate()
                  } catch (err) {
                    setError(extractErrorMessage(err, '导入失败'))
                  }
                }}
              />
              <button
                onClick={() => void downloadTemplate()}
                className="rounded border border-gray-300 px-4 py-2 hover:bg-gray-100"
              >
                下载模板
              </button>
              <button
                onClick={() =>
                  void exportCustomers({ keyword: search || undefined, status: status || undefined })
                }
                className="rounded border border-gray-300 px-4 py-2 hover:bg-gray-100"
              >
                导出
              </button>
            </>
          )}
          <button onClick={openCreate} className="rounded bg-blue-600 px-4 py-2 text-white hover:bg-blue-700">
            新增客户
          </button>
        </div>
      </div>

      {error && (
        <div role="alert" className="mb-4 rounded bg-red-50 p-3 text-sm text-red-700">
          {error}
        </div>
      )}

      {importResult && (
        <div
          role="status"
          className="mb-4 rounded border border-green-200 bg-green-50 p-3 text-sm text-green-800"
        >
          导入完成：成功 {importResult.successCount} 条，失败 {importResult.failureCount} 条。
          {importResult.failures.length > 0 && (
            <ul className="mt-1 list-inside list-disc text-xs text-green-700">
              {importResult.failures.slice(0, 10).map((f) => (
                <li key={f.row}>
                  第 {f.row} 行：{f.message}
                </li>
              ))}
            </ul>
          )}
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
          placeholder="搜索名称 / 公司 / 联系人 / 电话"
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
        <select
          value={status}
          onChange={(e) => {
            setStatus(e.target.value)
            setPage(1)
          }}
          className="rounded border border-gray-300 px-3 py-2"
        >
          <option value="">全部状态</option>
          <option value="ACTIVE">启用</option>
          <option value="INACTIVE">停用</option>
        </select>
      </div>

      {isLoading ? (
        <p className="py-8 text-center text-gray-400">加载中…</p>
      ) : (
        <table className="w-full rounded-lg bg-white shadow">
          <thead>
            <tr className="border-b text-left text-sm text-gray-500">
              <th className="px-4 py-3">名称</th>
              <th className="px-4 py-3">公司</th>
              <th className="px-4 py-3">联系人</th>
              <th className="px-4 py-3">电话</th>
              <th className="px-4 py-3">邮箱</th>
              <th className="px-4 py-3">状态</th>
              <th className="px-4 py-3">操作</th>
            </tr>
          </thead>
          <tbody>
            {(data?.items ?? []).map((c) => (
              <tr key={c.id} className="border-b text-sm hover:bg-gray-50">
                <td className="px-4 py-3">
                  <Link to={`/customers/${c.id}`} className="text-blue-600 hover:underline">
                    {c.name}
                  </Link>
                </td>
                <td className="px-4 py-3">{c.company}</td>
                <td className="px-4 py-3">{c.contactPerson ?? '-'}</td>
                <td className="px-4 py-3">{c.phone ?? '-'}</td>
                <td className="px-4 py-3">{c.email ?? '-'}</td>
                <td className="px-4 py-3">{c.status === 'ACTIVE' ? '启用' : '停用'}</td>
                <td className="px-4 py-3">
                  <button onClick={() => openEdit(c)} className="mr-3 text-blue-600 hover:underline">
                    编辑
                  </button>
                  <button onClick={() => confirmDelete(c)} className="text-red-600 hover:underline">
                    删除
                  </button>
                </td>
              </tr>
            ))}
            {data?.items?.length === 0 && (
              <tr>
                <td colSpan={7} className="px-4 py-8 text-center text-gray-400">
                  暂无客户数据
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
            <h3 className="mb-4 text-lg font-semibold">{editing ? '编辑客户' : '新增客户'}</h3>
            <CustomerForm
              initial={editing ?? undefined}
              onSubmit={(payload) => saveMutation.mutateAsync(payload)}
              onCancel={() => setModalOpen(false)}
              submitting={saveMutation.isPending}
            />
          </div>
        </div>
      )}
    </div>
  )
}
