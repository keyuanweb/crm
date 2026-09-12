import { useEffect, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import * as echarts from 'echarts'
import type { HealthDistribution } from '../types'

interface HealthChartProps {
  /** 客户健康度分布数据 */
  data: HealthDistribution
  /** 自定义样式 */
  style?: React.CSSProperties
}

/**
 * 客户健康度分布组件
 * 使用 ECharts 环形图，带脉冲动画
 */
export default function HealthChart({ data, style }: HealthChartProps) {
  const { t } = useTranslation()
  const chartRef = useRef<HTMLDivElement>(null)
  const chartInstanceRef = useRef<echarts.ECharts | null>(null)

  useEffect(() => {
    if (!chartRef.current) return

    if (!chartInstanceRef.current) {
      chartInstanceRef.current = echarts.init(chartRef.current)
    }

    const option = {
      tooltip: {
        trigger: 'item',
        formatter: '{b}: {c} ({d}%)',
      },
      legend: {
        orient: 'vertical',
        left: 'left',
        top: 'center',
        textStyle: {
          color: '#7db4ff',
          fontSize: 12,
        },
      },
      series: [
        {
          name: t('pages.dataVision.health.seriesName'),
          type: 'pie',
          radius: ['40%', '70%'],
          center: ['60%', '50%'],
          avoidLabelOverlap: false,
          itemStyle: {
            borderRadius: 10,
            borderColor: '#0a1830',
            borderWidth: 2,
          },
          label: {
            show: false,
          },
          emphasis: {
            label: {
              show: true,
              fontSize: 16,
              fontWeight: 'bold',
              color: '#e6f0ff',
            },
          },
          labelLine: {
            show: false,
          },
          data: [
            {
              value: data.green,
              name: t('pages.dataVision.health.green'),
              itemStyle: { color: '#52c41a' },
            },
            {
              value: data.yellow,
              name: t('pages.dataVision.health.yellow'),
              itemStyle: { color: '#faad14' },
            },
            {
              value: data.red,
              name: t('pages.dataVision.health.red'),
              itemStyle: { color: '#ff4d4f' },
            },
          ],
          animationType: 'scale',
          animationEasing: 'elasticOut',
          animationDelay: (idx: number) => idx * 200,
        },
      ],
      background: 'transparent',
    }

    chartInstanceRef.current.setOption(option, { notMerge: true })

    return () => {
      chartInstanceRef.current?.setOption({ series: [{ data: [] }] })
    }
  }, [data, t])

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
