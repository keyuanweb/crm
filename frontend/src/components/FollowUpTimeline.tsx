import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  createFollowUp,
  fetchFollowUps,
  updateFollowUp,
  type FollowUpPayload,
} from '../services/followUpService'
import { extractErrorMessage } from '../services/apiClient'
import { METHOD_LABELS, type FollowUp, type FollowUpMethod } from '../types/followUp'
import { useAuthStore } from '../store/authStore'

interface Props {
  customerId: number
}

const emptyForm: FollowUpPayload = {
  customerId: 0,
  method: 'PHONE',
  content: '',
  nextFollowUpAt: undefined,
}

export default function FollowUpTimeline({ customerId }: Props) {
  const [editing, setEditing] = useState<FollowUp | null>(null)
  const [form, setForm] = useState<FollowUpPayload>({ ...emptyForm, customerId })
  const [showForm, setShowForm] = useState(false)
  const [error, setError] = useState('')
  const queryClient = useQueryClient()
  const user = useAuthStore((s) => s.user)

  const { data, isLoading } = useQuery({
    queryKey: ['follow-ups', customerId],
    queryFn: () => fetchFollowUps({ customerId, page: 1, pageSize: 50 }),
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['follow-ups'] })

  const saveMutation = useMutation({
    mutationFn: (payload: FollowUpPayload) =>
      editing ? updateFollowUp(editing.id, payload) : createFollowUp(payload),
    onSuccess: () => {
      setShowForm(false)
      setEditing(null)
      setForm({ ...emptyForm, customerId })
      setError('')
      invalidate()
    },
    onError: (err) => setError(extractErrorMessage(err, '保存失败')),
  })

  const startEdit = (f: FollowUp) => {
    setEditing(f)
    setForm({
      customerId: f.customerId,
      opportunityId: f.opportunityId,
      method: f.method,
      content: f.content,
      nextFollowUpAt: f.nextFollowUpAt,
      version: f.version,
    })
    setError('')
    setShowForm(true)
  }

  const canEdit = (f: FollowUp) =>
    user?.role === 'ADMIN' || f.followUpBy === user?.id

  const inputClass =
    'w-full rounded border border-gray-300 px-3 py-2 focus:border-blue-500 focus:outline-none'

  return (
    <div className="rounded-lg bg-white p-6 shadow">
      <div className="mb-4 flex items-center justify-between">
        <h3 className="font-semibold text-gray-800">跟进记录</h3>
        {!showForm && (
          <button
            onClick={() => {
              setEditing(null)
              setForm({ ...emptyForm, customerId })
              setError('')
              setShowForm(true)
            }}
            className="rounded bg-blue-600 px-3 py-1.5 text-sm text-white hover:bg-blue-700"
          >
            添加跟进
          </button>
        )}
      </div>

      {error && (
        <div role="alert" className="mb-4 rounded bg-red-50 p-3 text-sm text-red-700">
          {error}
        </div>
      )}

      {showForm && (
        <form
          onSubmit={(e) => {
            e.preventDefault()
            if (!form.content.trim()) {
              setError('跟进内容不能为空')
              return
            }
            void saveMutation.mutateAsync({ ...form, customerId })
          }}
          className="mb-6 space-y-3 rounded border border-gray-200 p-4"
          aria-label="跟进记录表单"
        >
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-sm font-medium text-gray-700">方式</label>
              <select
                value={form.method}
                onChange={(e) => setForm((f) => ({ ...f, method: e.target.value as FollowUpMethod }))}
                className={inputClass}
              >
                {Object.entries(METHOD_LABELS).map(([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="mb-1 block text-sm font-medium text-gray-700">下次跟进</label>
              <input
                type="datetime-local"
                value={form.nextFollowUpAt ?? ''}
                onChange={(e) =>
                  setForm((f) => ({ ...f, nextFollowUpAt: e.target.value || undefined }))
                }
                className={inputClass}
              />
            </div>
          </div>
          <div>
            <label className="mb-1 block text-sm font-medium text-gray-700">内容 *</label>
            <textarea
              value={form.content}
              onChange={(e) => setForm((f) => ({ ...f, content: e.target.value }))}
              rows={3}
              className={inputClass}
            />
          </div>
          <div className="flex justify-end gap-2">
            <button
              type="button"
              onClick={() => {
                setShowForm(false)
                setEditing(null)
                setError('')
              }}
              className="rounded border border-gray-300 px-4 py-2 text-sm hover:bg-gray-100"
            >
              取消
            </button>
            <button
              type="submit"
              disabled={saveMutation.isPending}
              className="rounded bg-blue-600 px-4 py-2 text-sm text-white hover:bg-blue-700 disabled:opacity-50"
            >
              {saveMutation.isPending ? '保存中…' : '保存'}
            </button>
          </div>
        </form>
      )}

      {isLoading ? (
        <p className="py-4 text-center text-sm text-gray-400">加载中…</p>
      ) : (data?.items ?? []).length === 0 ? (
        <p className="py-4 text-center text-sm text-gray-400">暂无跟进记录</p>
      ) : (
        <ul className="space-y-3">
          {(data?.items ?? []).map((f) => (
            <li key={f.id} className="rounded border border-gray-100 p-3">
              <div className="mb-1 flex items-center justify-between text-xs text-gray-400">
                <span>
                  {METHOD_LABELS[f.method]} · {f.followUpByName ?? '未知'} ·{' '}
                  {f.createdAt ? f.createdAt.replace('T', ' ').slice(0, 16) : ''}
                  {f.nextFollowUpAt ? ` · 下次跟进 ${f.nextFollowUpAt.replace('T', ' ').slice(0, 16)}` : ''}
                </span>
                {canEdit(f) && (
                  <button onClick={() => startEdit(f)} className="text-blue-600 hover:underline">
                    编辑
                  </button>
                )}
              </div>
              <p className="text-sm text-gray-700">{f.content}</p>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
