import { useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { AutoComplete, Input, Typography } from 'antd'
import { SearchOutlined } from '@ant-design/icons'
import { searchAll, type SearchResponse } from '../services/searchService'

/**
 * 全局搜索框（032-global-search）：Header 内，防抖 300ms 即时下拉分组结果，
 * 点击跳转详情，回车进入结果页。
 */
export default function GlobalSearch() {
  const navigate = useNavigate()
  const [value, setValue] = useState('')
  const [options, setOptions] = useState<{ value: string; label: React.ReactNode }[]>([])
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  const onSearch = async (kw: string) => {
    if (!kw.trim() || kw.trim().length < 2) {
      setOptions([])
      return
    }
    try {
      const resp: SearchResponse = await searchAll(kw.trim())
      const opts: { value: string; label: React.ReactNode }[] = []
      for (const g of resp.groups) {
        for (const item of g.items) {
          opts.push({
            value: item.path,
            label: (
              <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12 }}>
                <span>
                  <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                    [{g.label}]
                  </Typography.Text>{' '}
                  {item.title}
                </span>
                {item.subtitle ? (
                  <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                    {item.subtitle}
                  </Typography.Text>
                ) : null}
              </div>
            ),
          })
        }
      }
      setOptions(opts)
    } catch {
      setOptions([])
    }
  }

  const onChange = (v: string) => {
    setValue(v)
    if (timerRef.current) clearTimeout(timerRef.current)
    timerRef.current = setTimeout(() => void onSearch(v), 300)
  }

  const onSelect = (path: string) => {
    navigate(path)
    setValue('')
    setOptions([])
  }

  const onPressEnter = () => {
    if (value.trim()) {
      navigate(`/search?q=${encodeURIComponent(value.trim())}`)
      setOptions([])
    }
  }

  return (
    <AutoComplete
      style={{ width: 260 }}
      value={value}
      options={options}
      onChange={onChange}
      onSelect={onSelect}
      popupMatchSelectWidth={380}
    >
      <Input
        prefix={<SearchOutlined style={{ color: '#bfbfbf' }} />}
        placeholder="全局搜索客户/线索/工单…"
        allowClear
        onPressEnter={onPressEnter}
      />
    </AutoComplete>
  )
}
