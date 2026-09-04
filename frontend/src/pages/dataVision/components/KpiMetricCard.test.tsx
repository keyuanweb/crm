import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import KpiMetricCard from './KpiMetricCard'

describe('KpiMetricCard', () => {
  it('renders label and value correctly', () => {
    render(<KpiMetricCard label="测试指标" value={12345} color="#4da3ff" />)
    expect(screen.getByText('测试指标')).toBeInTheDocument()
    expect(screen.getByText('12345')).toBeInTheDocument()
  })

  it('renders with unit', () => {
    render(<KpiMetricCard label="金额" value={1000000} unit="元" color="#52c41a" />)
    // CountUp 组件渲染 value 本身，单位追加在后面
    expect(screen.getByText('1000000')).toBeInTheDocument()
    expect(screen.getByText('元')).toBeInTheDocument()
  })

  it('renders with decimals', () => {
    render(<KpiMetricCard label="平均值" value={123.456} color="#faad14" decimals={2} />)
    expect(screen.getByText('123.46')).toBeInTheDocument()
  })
})
