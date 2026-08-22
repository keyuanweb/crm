import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { changeOwnPassword } from '../../services/userService'
import { extractErrorMessage } from '../../services/apiClient'
import { useAuthStore } from '../../store/authStore'

export default function ChangePasswordPage() {
  const [oldPassword, setOldPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [loading, setLoading] = useState(false)
  const navigate = useNavigate()
  const clear = useAuthStore((s) => s.clear)

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')
    setSuccess('')
    if (newPassword !== confirm) {
      setError('两次输入的新密码不一致')
      return
    }
    setLoading(true)
    try {
      await changeOwnPassword(oldPassword, newPassword)
      // 密码已变更，旧令牌失效，要求重新登录
      clear()
      navigate('/login', { replace: true })
    } catch (err) {
      setError(extractErrorMessage(err, '修改失败'))
    } finally {
      setLoading(false)
    }
  }

  const inputClass =
    'w-full rounded border border-gray-300 px-3 py-2 focus:border-blue-500 focus:outline-none'

  return (
    <div className="mx-auto max-w-md">
      <h2 className="mb-4 text-xl font-semibold text-gray-800">修改密码</h2>
      {error && (
        <div role="alert" className="mb-4 rounded bg-red-50 p-3 text-sm text-red-700">
          {error}
        </div>
      )}
      {success && (
        <div className="mb-4 rounded bg-green-50 p-3 text-sm text-green-700">{success}</div>
      )}
      <form onSubmit={onSubmit} className="space-y-4 rounded-lg bg-white p-6 shadow">
        <div>
          <label htmlFor="cp-old" className="mb-1 block text-sm font-medium text-gray-700">
            旧密码 *
          </label>
          <input
            id="cp-old"
            type="password"
            value={oldPassword}
            onChange={(e) => setOldPassword(e.target.value)}
            required
            className={inputClass}
          />
        </div>
        <div>
          <label htmlFor="cp-new" className="mb-1 block text-sm font-medium text-gray-700">
            新密码 *（8~64 位，含字母与数字）
          </label>
          <input
            id="cp-new"
            type="password"
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
            required
            minLength={8}
            className={inputClass}
          />
        </div>
        <div>
          <label htmlFor="cp-confirm" className="mb-1 block text-sm font-medium text-gray-700">
            确认新密码 *
          </label>
          <input
            id="cp-confirm"
            type="password"
            value={confirm}
            onChange={(e) => setConfirm(e.target.value)}
            required
            minLength={8}
            className={inputClass}
          />
        </div>
        <p className="text-xs text-gray-400">修改成功后需要重新登录（旧访问令牌立即失效）。</p>
        <button
          type="submit"
          disabled={loading}
          className="w-full rounded bg-blue-600 py-2 text-white hover:bg-blue-700 disabled:opacity-50"
        >
          {loading ? '提交中…' : '确认修改'}
        </button>
      </form>
    </div>
  )
}
