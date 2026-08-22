import { Link, useParams } from 'react-router-dom'
import { useCustomerDetail } from '../../hooks/useCustomers'
import FollowUpTimeline from '../../components/FollowUpTimeline'

export default function CustomerDetailPage() {
  const { id } = useParams()
  const customerId = Number(id)

  const { data, isLoading, error } = useCustomerDetail(
    Number.isFinite(customerId) ? customerId : undefined,
  )

  if (isLoading) {
    return <p className="py-8 text-center text-gray-400">加载中…</p>
  }
  if (error || !data) {
    return (
      <div className="rounded bg-red-50 p-4 text-red-700">
        客户不存在或已被删除。{' '}
        <Link to="/customers" className="underline">
          返回客户列表
        </Link>
      </div>
    )
  }

  const rows: [string, string][] = [
    ['客户名称', data.name],
    ['公司', data.company],
    ['联系人', data.contactPerson ?? '-'],
    ['电话', data.phone ?? '-'],
    ['邮箱', data.email ?? '-'],
    ['地址', data.address ?? '-'],
    ['备注', data.remark ?? '-'],
    ['状态', data.status === 'ACTIVE' ? '启用' : '停用'],
  ]

  return (
    <div>
      <div className="mb-4">
        <Link to="/customers" className="text-blue-600 hover:underline">
          ← 返回客户列表
        </Link>
      </div>
      <h2 className="mb-4 text-xl font-semibold text-gray-800">客户详情：{data.name}</h2>

      <div className="mb-6 grid grid-cols-2 gap-4 rounded-lg bg-white p-6 shadow md:grid-cols-4">
        {rows.map(([label, value]) => (
          <div key={label}>
            <div className="text-xs text-gray-400">{label}</div>
            <div className="text-sm text-gray-800">{value}</div>
          </div>
        ))}
      </div>

      <div className="mb-6 rounded-lg bg-white p-6 shadow">
        <div className="mb-3 flex items-center justify-between">
          <h3 className="font-semibold text-gray-800">关联商机</h3>
          <Link to="/opportunities" className="text-sm text-blue-600 hover:underline">
            前往商机管理
          </Link>
        </div>
        {data.opportunities.length === 0 ? (
          <p className="text-sm text-gray-400">暂无关联商机</p>
        ) : (
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b text-left text-gray-500">
                <th className="py-2">商机名称</th>
                <th className="py-2">状态</th>
                <th className="py-2">销售机会数</th>
              </tr>
            </thead>
            <tbody>
              {data.opportunities.map((o) => (
                <tr key={o.id} className="border-b">
                  <td className="py-2">{o.name}</td>
                  <td className="py-2">{o.status === 'ACTIVE' ? '进行中' : '已归档'}</td>
                  <td className="py-2">{o.salesOpportunityCount}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <FollowUpTimeline customerId={customerId} />
    </div>
  )
}
