import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  createUser,
  fetchUsers,
  resetPassword,
  updateUser,
} from '../../services/userService'
import { extractErrorMessage } from '../../services/apiClient'
import { ROLE_LABELS, type User, type UserCreatePayload, type UserRole } from '../../types/user'
import { useAuthStore } from '../../store/authStore'

const emptyCreate: UserCreatePayload = {
  username: '',
  displayName: '',
  role: 'SALES',
  password: '',
}

export default function UserManagementPage() {
  const [keyword, setKeyword] = useState('')
  const [search, setSearch] = useState('')
  const [role, setRole] = useState('')
  const [page, setPage] = useState(1)
  const [modal, setModal] = useState<'create' | 'edit' | 'reset' | null>(null)
  const [editing, setEditing] = useState<User | null>(null)
  const [error, setError] = useState('')
  const [form, setForm] = useState<UserCreatePayload>(emptyCreate)
  const [newPassword, setNewPassword] = useState('')
  const queryClient = useQueryClient()
  const currentUser = useAuthStore((s) => s.user)

  const { data, isLoading } = useQuery({
    queryKey: ['users', search, role, page],
    queryFn: () => fetchUsers({ keyword: search || undefined, role: role || undefined, page, pageSize: 20 }),
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['users'] })

  const createMutation = useMutation({
    mutationFn: (payload: UserCreatePayload) => createUser(payload),
    onSuccess: () => {
      setModal(null)
      setError('')
      invalidate()
    },
    onError: (err) => setError(extractErrorMessage(err, '创建失败')),
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, payload }: { id: number; payload: Parameters<typeof updateUser>[1] }) =>
      updateUser(id, payload),
    onSuccess: () => {
      setModal(null)
      setError('')
      invalidate()
    },
    onError: (err) => setError(extractErrorMessage(err, '保存失败')),
  })

  const resetMutation = useMutation({
    mutationFn: ({ id, pwd }: { id: number; pwd: string }) => resetPassword(id, pwd),
    onSuccess: () => {
      setModal(null)
      setNewPassword('')
      setError('')
    },
    onError: (err) => setError(extractErrorMessage(err, '重置失败')),
  })

  const toggleEnabled = (u: User) => {
    if (u.id === currentUser?.id) {
      setError('不能停用当前登录账号')
      return
    }
    if (window.confirm(`确定${u.enabled ? '停用' : '启用'}账号「${u.username}」吗？`)) {
      void updateMutation.mutate({ id: u.id, payload: { enabled: !u.enabled, version: u.version } })
    }
  }

  const total = data?.total ?? 0
  const totalPages = Math.max(1, Math.ceil(total / 20))

  const inputClass =
    'w-full rounded border border-gray-300 px-3 py-2 focus:border-blue-500 focus:outline-none'

  return (
    <div>
      <div className="mb-4 flex items-center justify-between">
        <h2 className="text-xl font-semibold text-gray-800">用户管理</h2>
        <button
          onClick={() => {
            setForm(emptyCreate)
            setError('')
            setModal('create')
          }}
          className="rounded bg-blue-600 px-4 py-2 text-white hover:bg-blue-700"
        >
          新增用户
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
          placeholder="搜索用户名 / 显示名"
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
          value={role}
          onChange={(e) => {
            setRole(e.target.value)
            setPage(1)
          }}
          className="rounded border border-gray-300 px-3 py-2"
        >
          <option value="">全部角色</option>
          {(Object.keys(ROLE_LABELS) as UserRole[]).map((r) => (
            <option key={r} value={r}>
              {ROLE_LABELS[r]}
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
              <th className="px-4 py-3">用户名</th>
              <th className="px-4 py-3">显示名</th>
              <th className="px-4 py-3">角色</th>
              <th className="px-4 py-3">状态</th>
              <th className="px-4 py-3">最后登录</th>
              <th className="px-4 py-3">操作</th>
            </tr>
          </thead>
          <tbody>
            {(data?.items ?? []).map((u) => (
              <tr key={u.id} className="border-b text-sm hover:bg-gray-50">
                <td className="px-4 py-3 font-medium">{u.username}</td>
                <td className="px-4 py-3">{u.displayName}</td>
                <td className="px-4 py-3">{ROLE_LABELS[u.role]}</td>
                <td className="px-4 py-3">
                  <span
                    className={
                      u.enabled
                        ? 'rounded bg-green-100 px-2 py-1 text-xs text-green-700'
                        : 'rounded bg-gray-100 px-2 py-1 text-xs text-gray-600'
                    }
                  >
                    {u.enabled ? '启用' : '停用'}
                  </span>
                </td>
                <td className="px-4 py-3">
                  {u.lastLoginAt ? u.lastLoginAt.replace('T', ' ').slice(0, 19) : '-'}
                </td>
                <td className="px-4 py-3">
                  <button
                    onClick={() => {
                      setEditing(u)
                      setError('')
                      setModal('edit')
                    }}
                    className="mr-3 text-blue-600 hover:underline"
                  >
                    编辑
                  </button>
                  <button
                    onClick={() => {
                      setEditing(u)
                      setNewPassword('')
                      setError('')
                      setModal('reset')
                    }}
                    className="mr-3 text-amber-600 hover:underline"
                  >
                    重置密码
                  </button>
                  <button onClick={() => toggleEnabled(u)} className="text-red-600 hover:underline">
                    {u.enabled ? '停用' : '启用'}
                  </button>
                </td>
              </tr>
            ))}
            {data?.items?.length === 0 && (
              <tr>
                <td colSpan={6} className="px-4 py-8 text-center text-gray-400">
                  暂无用户
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

      {modal === 'create' && (
        <div className="fixed inset-0 flex items-center justify-center bg-black/30" onClick={() => setModal(null)}>
          <div className="w-full max-w-md rounded-lg bg-white p-6" onClick={(e) => e.stopPropagation()}>
            <h3 className="mb-4 text-lg font-semibold">新增用户</h3>
            <form
              onSubmit={(e) => {
                e.preventDefault()
                void createMutation.mutateAsync(form)
              }}
              className="space-y-4"
            >
              <div>
                <label htmlFor="user-username" className="mb-1 block text-sm font-medium text-gray-700">
                  用户名 *
                </label>
                <input
                  id="user-username"
                  value={form.username}
                  onChange={(e) => setForm((f) => ({ ...f, username: e.target.value }))}
                  required
                  pattern="[a-zA-Z0-9_]{3,50}"
                  className={inputClass}
                />
              </div>
              <div>
                <label htmlFor="user-displayname" className="mb-1 block text-sm font-medium text-gray-700">
                  显示名 *
                </label>
                <input
                  id="user-displayname"
                  value={form.displayName}
                  onChange={(e) => setForm((f) => ({ ...f, displayName: e.target.value }))}
                  required
                  className={inputClass}
                />
              </div>
              <div>
                <label htmlFor="user-role" className="mb-1 block text-sm font-medium text-gray-700">
                  角色 *
                </label>
                <select
                  id="user-role"
                  value={form.role}
                  onChange={(e) => setForm((f) => ({ ...f, role: e.target.value as UserRole }))}
                  className={inputClass}
                >
                  {(Object.keys(ROLE_LABELS) as UserRole[]).map((r) => (
                    <option key={r} value={r}>
                      {ROLE_LABELS[r]}
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label htmlFor="user-password" className="mb-1 block text-sm font-medium text-gray-700">
                  初始密码 *（8~64 位，含字母与数字）
                </label>
                <input
                  id="user-password"
                  type="password"
                  value={form.password}
                  onChange={(e) => setForm((f) => ({ ...f, password: e.target.value }))}
                  required
                  minLength={8}
                  className={inputClass}
                />
              </div>
              <div className="flex justify-end gap-2">
                <button
                  type="button"
                  onClick={() => setModal(null)}
                  className="rounded border border-gray-300 px-4 py-2 hover:bg-gray-100"
                >
                  取消
                </button>
                <button
                  type="submit"
                  disabled={createMutation.isPending}
                  className="rounded bg-blue-600 px-4 py-2 text-white hover:bg-blue-700 disabled:opacity-50"
                >
                  {createMutation.isPending ? '保存中…' : '创建'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {modal === 'edit' && editing && (
        <div className="fixed inset-0 flex items-center justify-center bg-black/30" onClick={() => setModal(null)}>
          <div className="w-full max-w-md rounded-lg bg-white p-6" onClick={(e) => e.stopPropagation()}>
            <h3 className="mb-4 text-lg font-semibold">编辑用户：{editing.username}</h3>
            <form
              onSubmit={(e) => {
                e.preventDefault()
                void updateMutation.mutate({
                  id: editing.id,
                  payload: {
                    displayName: editing.displayName,
                    role: editing.role,
                    version: editing.version,
                  },
                })
              }}
              className="space-y-4"
            >
              <div>
                <label htmlFor="user-edit-displayname" className="mb-1 block text-sm font-medium text-gray-700">
                  显示名
                </label>
                <input
                  id="user-edit-displayname"
                  value={editing.displayName}
                  onChange={(e) => setEditing({ ...editing, displayName: e.target.value })}
                  className={inputClass}
                />
              </div>
              <div>
                <label htmlFor="user-edit-role" className="mb-1 block text-sm font-medium text-gray-700">
                  角色
                </label>
                <select
                  id="user-edit-role"
                  value={editing.role}
                  onChange={(e) => setEditing({ ...editing, role: e.target.value as UserRole })}
                  className={inputClass}
                >
                  {(Object.keys(ROLE_LABELS) as UserRole[]).map((r) => (
                    <option key={r} value={r}>
                      {ROLE_LABELS[r]}
                    </option>
                  ))}
                </select>
              </div>
              <div className="flex justify-end gap-2">
                <button
                  type="button"
                  onClick={() => setModal(null)}
                  className="rounded border border-gray-300 px-4 py-2 hover:bg-gray-100"
                >
                  取消
                </button>
                <button
                  type="submit"
                  disabled={updateMutation.isPending}
                  className="rounded bg-blue-600 px-4 py-2 text-white hover:bg-blue-700 disabled:opacity-50"
                >
                  保存
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {modal === 'reset' && editing && (
        <div className="fixed inset-0 flex items-center justify-center bg-black/30" onClick={() => setModal(null)}>
          <div className="w-full max-w-md rounded-lg bg-white p-6" onClick={(e) => e.stopPropagation()}>
            <h3 className="mb-4 text-lg font-semibold">重置密码：{editing.username}</h3>
            <form
              onSubmit={(e) => {
                e.preventDefault()
                void resetMutation.mutate({ id: editing.id, pwd: newPassword })
              }}
              className="space-y-4"
            >
              <div>
                <label htmlFor="user-reset-password" className="mb-1 block text-sm font-medium text-gray-700">
                  新密码 *（8~64 位，含字母与数字；重置后旧令牌立即失效）
                </label>
                <input
                  id="user-reset-password"
                  type="password"
                  value={newPassword}
                  onChange={(e) => setNewPassword(e.target.value)}
                  required
                  minLength={8}
                  className={inputClass}
                />
              </div>
              <div className="flex justify-end gap-2">
                <button
                  type="button"
                  onClick={() => setModal(null)}
                  className="rounded border border-gray-300 px-4 py-2 hover:bg-gray-100"
                >
                  取消
                </button>
                <button
                  type="submit"
                  disabled={resetMutation.isPending}
                  className="rounded bg-amber-600 px-4 py-2 text-white hover:bg-amber-700 disabled:opacity-50"
                >
                  重置
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}
