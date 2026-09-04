import { useEffect, useRef } from 'react'
import * as echarts from 'echarts'
import type { FunnelStage } from '../types'

interface FunnelChartProps {
  /** 漏斗阶段数据 */
  stages: FunnelStage[]
  /** 自定义样式 */
  style?: React.CSSProperties
}

const STAGE_COLORS = ['#1677ff', '#69b1ff', '#a0c4ff', '#d6e4ff', '#4da3ff', '#7dd3fc']
const STAGE_LABELS: Record<string, string> = {
  INITIAL_CONTACT: '初步接触',
  NEGOTIATING: '谈判中',
  CLOSED_WON: '已赢单',
  CLOSED_LOST: '已输单',
}

/**
 * 销售漏斗图组件
 * 使用 ECharts 横向条形图，带渐变填充和流光边框
 */
export default function FunnelChart({ stages, style }: FunnelChartProps) {
  const chartRef = useRef<HTMLDivElement>(null)
  const chartInstanceRef = useRef<echarts.ECharts | null>(null)

  useEffect(() => {
    if (!chartRef.current) return

    if (!chartInstanceRef.current) {
      chartInstanceRef.current = echarts.init(chartRef.current)
    }

    const maxAmount = Math.max(...stages.map((s) => s.amountTotal), 1)
    const seriesData = stages.map((s, i) => ({
      name: STAGE_LABELS[s.stage] || s.stage,
      value: s.amountTotal,
      itemStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
          { offset: 0, color: STAGE_COLORS[i % STAGE_COLORS.length] + '80' },
          { offset: 1, color: STAGE_COLORS[i % STAGE_COLORS.length] },
        ]),
      },
    }))

    const option = {
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' },
        formatter: (params: any) => {
          const data = params[0]
          const stage = stages[data.dataIndex]
          return `${data.name}<br/>金额：${(stage?.amountTotal || 0).toLocaleString()} 元<br/>数量：${stage?.count || 0} 个`
        },
      },
      grid: {
        left: '15%',
        right: '10%',
        top: '5%',
        bottom: '5%',
      },
      xAxis: {
        type: 'value',
        show: false,
        max: maxAmount,
      },
      yAxis: {
        type: 'category',
        data: stages.map((s) => STAGE_LABELS[s.stage] || s.stage).reverse(),
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: {
          color: '#7db4ff',
          fontSize: 12,
        },
      },
      series: [
        {
          name: '金额',
          type: 'bar',
          data: seriesData.reverse(),
          barWidth: 20,
          itemStyle: {
            borderRadius: [0, 10, 10, 0],
          },
          label: {
            show: true,
            position: 'right',
            formatter: (params: any) => {
              const stage = stages[params.dataIndex]
              return `${(stage?.amountTotal || 0).toLocaleString()}`
            },
            color: '#e6f0ff',
            fontSize: 11,
          },
          animationDuration: 1000,
          animationEasing: 'cubicOut',
        },
      ],
      background: 'transparent',
    }

    chartInstanceRef.current.setOption(option, { notMerge: true })

    return () => {
      // 不清毁实例，只清空 option
      chartInstanceRef.current?.setOption({ series: [{ data: [] }] })
    }
  }, [stages])

  useEffect(() => {
    const handleResize = () => {
      chartInstanceRef.current?.resize()
    }

    window.addEventListener('resize', handleResize)
    return () => window.removeEventListener('resize', handleResize)
  }, [])

  return (
    <div
      ref={chartRef}
      style={{
        width: '100%',
        height: '100%',
        minHeight: 200,
        ...style,
      }}
    />
  )
}
