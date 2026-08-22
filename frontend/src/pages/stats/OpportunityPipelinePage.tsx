import { useQuery } from '@tanstack/react-query'
import { fetchPipelineStats } from '../../services/statsService'
import { STAGE_LABELS, formatAmount } from '../../types/opportunity'

export default function OpportunityPipelinePage() {
  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['pipeline-stats'],
    queryFn: fetchPipelineStats,
  })

  if (isLoading) {
    return <p className="py-8 text-center text-gray-400">加载中…</p>
  }
  if (error || !data) {
    return (
      <div className="rounded bg-red-50 p-4 text-red-700">
        统计数据加载失败。
        <button onClick={() => void refetch()} className="ml-2 underline">
          重试
        </button>
      </div>
    )
  }

  return (
    <div>
      <div className="mb-4 flex items-center justify-between">
        <h2 className="text-xl font-semibold text-gray-800">商机管道统计</h2>
        <span className="text-xs text-gray-400">
          生成时间：{data.generatedAt.replace('T', ' ').slice(0, 19)}
        </span>
      </div>

      <div className="rounded-lg bg-white shadow">
        <table className="w-full">
          <thead>
            <tr className="border-b text-left text-sm text-gray-500">
              <th className="px-4 py-3">阶段</th>
              <th className="px-4 py-3">商机数量</th>
              <th className="px-4 py-3">金额合计（元）</th>
            </tr>
          </thead>
          <tbody>
            {data.stages.map((s) => (
              <tr key={s.stage} className="border-b text-sm">
                <td className="px-4 py-3">{STAGE_LABELS[s.stage as keyof typeof STAGE_LABELS] ?? s.stage}</td>
                <td className="px-4 py-3">{s.count}</td>
                <td className="px-4 py-3">{formatAmount(s.amountTotal)}</td>
              </tr>
            ))}
            <tr className="bg-gray-50 text-sm font-semibold">
              <td className="px-4 py-3">合计</td>
              <td className="px-4 py-3">{data.grandTotal.count}</td>
              <td className="px-4 py-3">{formatAmount(data.grandTotal.amountTotal)}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  )
}
