import { useEffect, useState } from 'react'
import type { ResponsiveConfig } from '../types'

/**
 * 响应式布局 Hook
 * @returns 响应式布局配置
 */
export function useResponsiveGrid(): ResponsiveConfig {
  const [config, setConfig] = useState<ResponsiveConfig>({
    columns1080: 3,
    columns1440: 4,
    columns2160: 5,
    columns: 3,
    fontSizeMultiplier: 1,
  })

  useEffect(() => {
    const updateConfig = () => {
      const height = window.innerHeight
      const width = window.innerWidth

      let columns: number
      let fontSizeMultiplier: number

      if (height >= 2160 || width >= 3840) {
        // 4K 分辨率
        columns = 5
        fontSizeMultiplier = 1.3
      } else if (height >= 1440 || width >= 2560) {
        // 2K 分辨率
        columns = 4
        fontSizeMultiplier = 1.15
      } else {
        // 1080p 或更低
        columns = 3
        fontSizeMultiplier = 1
      }

      setConfig({
        columns1080: 3,
        columns1440: 4,
        columns2160: 5,
        columns,
        fontSizeMultiplier,
      })
    }

    // 初始计算
    updateConfig()

    // 监听窗口大小变化
    window.addEventListener('resize', updateConfig)
    return () => window.removeEventListener('resize', updateConfig)
  }, [])

  return config
}
