import { useEffect, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import * as echarts from 'echarts'
import type { DefaultLabelFormatterCallbackParams, TooltipComponentFormatterCallbackParams } from 'echarts'
import type { FunnelStage } from '../types'
import { useOpportunityStages } from '../../../hooks/useOpportunityStages'

interface FunnelChartProps {
  /** 漏斗阶段数据 */
  stages: FunnelStage[]
  /** 自定义样式 */
  style?: React.CSSProperties
}

const STAGE_COLORS = ['#1677ff', '#69b1ff', '#a0c4ff', '#d6e4ff', '#4da3ff', '#7dd3fc']

/**
 * 销售漏斗图组件
 * 使用 ECharts 横向条形图，带渐变填充和流光边框
 */
export default function FunnelChart({ stages, style }: FunnelChartProps) {
  const { t } = useTranslation()
  // 阶段名走字典：漏斗的折/柱是服务端按字典顺序下发的，页面自己维护一份编码→中文映射，
  // 管理员新增阶段后这里就会把编码当名字显示（而看板、商机列表早已显示正确的中文）
  const { stageLabel } = useOpportunityStages()
  const chartRef = useRef<HTMLDivElement>(null)
  const chartInstanceRef = useRef<echarts.ECharts | null>(null)

  useEffect(() => {
    if (!chartRef.current) return

    if (!chartInstanceRef.current) {
      chartInstanceRef.current = echarts.init(chartRef.current)
    }

    const maxAmount = Math.max(...stages.map((s) => s.amountTotal), 1)
    const seriesData = stages.map((s, i) => ({
      name: stageLabel(s.stage),
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
        formatter: (params: TooltipComponentFormatterCallbackParams) => {
          const data = Array.isArray(params) ? params[0] : params
          const stage = stages[data.dataIndex]
          return t('pages.dataVision.funnel.tooltip', {
            name: data.name,
            amount: (stage?.amountTotal || 0).toLocaleString(),
            count: stage?.count || 0,
          })
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
        data: stages.map((s) => stageLabel(s.stage)).reverse(),
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: {
          color: '#7db4ff',
          fontSize: 12,
        },
      },
      series: [
        {
          name: t('pages.dataVision.funnel.seriesAmount'),
          type: 'bar',
          data: seriesData.reverse(),
          barWidth: 20,
          itemStyle: {
            borderRadius: [0, 10, 10, 0],
          },
          label: {
            show: true,
            position: 'right',
            formatter: (params: DefaultLabelFormatterCallbackParams) => {
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
    // stageLabel 必须进依赖：字典是异步到的，不重跑这个 effect 就永远停在「首次渲染时那批名字」
  }, [stages, t, stageLabel])

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
