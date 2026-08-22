import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { fetchAuditLogs } from '../../services/auditLogService'
import { ACTION_LABELS, ENTITY_LABELS, type AuditAction, type AuditEntityType } from '../../types/auditLog'

export default function AuditLogPage() {
  const [action, setAction] = useState('')
  const [entityType, setEntityType] = useState('')
  const [actorName, setActorName] = useState('')
  const [page, setPage] = useState(1)

  const { data, isLoading } = useQuery({
    queryKey: ['audit-logs', action, entityType, actorName, page],
    queryFn: () =>
      fetchAuditLogs({
        action: action || undefined,
        entityType: entityType || undefined,
        actorName: actorName || undefined,
        page,
        pageSize: 20,
      }),
  })

  const total = data?.total ?? 0
  const totalPages = Math.max(1, Math.ceil(total / 20))

  return (
    <div>
      <div className="mb-4">
        <h2 className="text-xl font-semibold text-gray-800">审计日志</h2>
        <p className="mt-1 text-sm text-gray-400">
          关键操作记录（创建/编辑/删除/导入/导出/关闭/密码变更），仅管理员可见，不含敏感明文。
        </p>
      </div>

      <div className="mb-4 flex flex-wrap items-center gap-2">
        <select
          value={action}
          onChange={(e) => {
            setAction(e.target.value)
            setPage(1)
          }}
          className="rounded border border-gray-300 px-3 py-2"
        >
          <option value="">全部动作</option>
          {Object.entries(ACTION_LABELS).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </select>
        <select
          value={entityType}
          onChange={(e) => {
            setEntityType(e.target.value)
            setPage(1)
          }}
          className="rounded border border-gray-300 px-3 py-2"
        >
          <option value="">全部对象</option>
          {Object.entries(ENTITY_LABELS).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </select>
        <input
          value={actorName}
          onChange={(e) => setActorName(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') setPage(1)
          }}
          placeholder="操作人"
          className="w-40 rounded border border-gray-300 px-3 py-2 focus:border-blue-500 focus:outline-none"
        />
        <button
          onClick={() => setPage(1)}
          className="rounded border border-gray-300 px-4 py-2 hover:bg-gray-100"
        >
          查询
        </button>
      </div>

      {isLoading ? (
        <p className="py-8 text-center text-gray-400">加载中…</p>
      ) : (
        <table className="w-full rounded-lg bg-white shadow">
          <thead>
            <tr className="border-b text-left text-sm text-gray-500">
              <th className="px-4 py-3">时间</th>
              <th className="px-4 py-3">操作人</th>
              <th className="px-4 py-3">动作</th>
              <th className="px-4 py-3">对象</th>
              <th className="px-4 py-3">对象 ID</th>
              <th className="px-4 py-3">说明</th>
            </tr>
          </thead>
          <tbody>
            {(data?.items ?? []).map((log) => (
              <tr key={log.id} className="border-b text-sm hover:bg-gray-50">
                <td className="px-4 py-3">
                  {log.createdAt ? log.createdAt.replace('T', ' ').slice(0, 19) : '-'}
                </td>
                <td className="px-4 py-3">{log.actorName ?? '-'}</td>
                <td className="px-4 py-3">
                  {ACTION_LABELS[log.action as AuditAction] ?? log.action}
                </td>
                <td className="px-4 py-3">
                  {ENTITY_LABELS[log.entityType as AuditEntityType] ?? log.entityType}
                </td>
                <td className="px-4 py-3">{log.entityId ?? '-'}</td>
                <td className="px-4 py-3 text-gray-600">{log.detail ?? '-'}</td>
              </tr>
            ))}
            {data?.items?.length === 0 && (
              <tr>
                <td colSpan={6} className="px-4 py-8 text-center text-gray-400">
                  暂无审计记录
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
    </div>
  )
}
