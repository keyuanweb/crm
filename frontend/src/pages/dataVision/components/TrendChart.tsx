import { useEffect, useRef } from 'react'
import * as echarts from 'echarts'
import type { TrendPoint } from '../types'

interface TrendChartProps {
  data: TrendPoint[]
  style?: React.CSSProperties
}

export default function TrendChart({ data, style }: TrendChartProps) {
  const chartRef = useRef<HTMLDivElement>(null)
  const chartInstanceRef = useRef<echarts.ECharts | null>(null)

  useEffect(() => {
    if (!chartRef.current) return
    if (!chartInstanceRef.current) {
      chartInstanceRef.current = echarts.init(chartRef.current)
    }
    const dates = data.map((d) => d.date)
    const amounts = data.map((d) => d.amount)
    const option = {
      tooltip: { trigger: 'axis', formatter: (params: any) => `${params[0].name}<br/>金额：${params[0].value.toLocaleString()} 元` },
      grid: { left: '10%', right: '5%', top: '10%', bottom: '15%' },
      xAxis: { type: 'category', data: dates, boundaryGap: false, axisLine: { lineStyle: { color: '#5a7cb8' } }, axisLabel: { color: '#7db4ff', fontSize: 10, interval: Math.floor(dates.length / 10) } },
      yAxis: { type: 'value', axisLine: { show: false }, axisTick: { show: false }, splitLine: { lineStyle: { color: 'rgba(90, 124, 184, 0.1)' } }, axisLabel: { color: '#7db4ff', fontSize: 10, formatter: (v: number) => v >= 1000000 ? (v / 1000000).toFixed(1) + 'M' : v >= 10000 ? (v / 10000).toFixed(1) + 'W' : v.toString() } },
      series: [{ name: '商机金额', type: 'line', data: amounts, smooth: true, symbol: 'circle', symbolSize: 6, lineStyle: { width: 3, color: '#52c41a' }, itemStyle: { color: '#52c41a', borderColor: '#0a1830', borderWidth: 2 }, areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: 'rgba(82, 196, 26, 0.3)' }, { offset: 1, color: 'rgba(82, 196, 26, 0.05)' }]) }, emphasis: { focus: 'series' }, animationDuration: 1500, animationEasing: 'cubicOut' }],
      background: 'transparent',
    }
    chartInstanceRef.current.setOption(option, { notMerge: true })
    return () => { chartInstanceRef.current?.setOption({ series: [{ data: [] }] }) }
  }, [data])

  useEffect(() => {
    const handleResize = () => { chartInstanceRef.current?.resize() }
    window.addEventListener('resize', handleResize)
    return () => window.removeEventListener('resize', handleResize)
  }, [])

  return <div ref={chartRef} style={{ width: '100%', height: '100%', minHeight: 200, ...style }} />
}
