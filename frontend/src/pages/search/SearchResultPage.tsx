import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { Empty, List, Space, Tabs, Tag, Typography } from 'antd'
import { searchFull, type SearchGroup, type SearchResponse } from '../../services/searchService'

/** 高亮关键字。 */
function Highlight({ text, keyword }: { text: string; keyword: string }) {
  if (!text || !keyword) return <>{text}</>
  const parts = text.split(new RegExp(`(${keyword.split(' ').map((k) => k.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')).join('|')})`, 'gi'))
  return (
    <>
      {parts.map((p, i) =>
        keyword.toLowerCase().split(' ').some((k) => k && p.toLowerCase() === k) ? (
          <mark key={i} style={{ background: '#ffe58f', padding: '0 2px', borderRadius: 3 }}>
            {p}
          </mark>
        ) : (
          <span key={i}>{p}</span>
        ),
      )}
    </>
  )
}

export default function SearchResultPage() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const keyword = params.get('q') ?? ''
  const [resp, setResp] = useState<SearchResponse>({ keyword: '', groups: [], total: 0 })
  const [activeType, setActiveType] = useState('ALL')
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    if (!keyword.trim()) return
    setLoading(true)
    void searchFull(keyword.trim(), activeType === 'ALL' ? undefined : activeType)
      .then(setResp)
      .finally(() => setLoading(false))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [keyword, activeType])

  const groups: SearchGroup[] = activeType === 'ALL' ? resp.groups : resp.groups.filter((g) => g.type === activeType)

  return (
    <div>
      <Typography.Title level={4} style={{ marginBottom: 4 }}>
        搜索：{keyword}
      </Typography.Title>
      <Typography.Text type="secondary" style={{ fontSize: 13 }}>
        共 {resp.total} 条结果
      </Typography.Text>

      {resp.groups.length > 1 && (
        <Tabs
          activeKey={activeType}
          onChange={setActiveType}
          style={{ marginTop: 8 }}
          items={[
            { key: 'ALL', label: '全部' },
            ...resp.groups.map((g) => ({ key: g.type, label: `${g.label}（${g.items.length}）` })),
          ]}
        />
      )}

      <div style={{ marginTop: 12 }}>
        {groups.length === 0 ? (
          <Empty description="未找到匹配结果" style={{ padding: 40 }} />
        ) : (
          <Space direction="vertical" style={{ width: '100%' }} size={16}>
            {groups.map((g) => (
              <div key={g.type}>
                <div style={{ marginBottom: 8 }}>
                  <Tag color="blue">{g.label}</Tag>
                </div>
                <List
                  size="small"
                  bordered
                  dataSource={g.items}
                  loading={loading}
                  renderItem={(item) => (
                    <List.Item
                      style={{ cursor: 'pointer' }}
                      onClick={() => navigate(item.path)}
                    >
                      <List.Item.Meta
                        title={<Highlight text={item.title} keyword={keyword} />}
                        description={
                          item.subtitle ? <Highlight text={item.subtitle} keyword={keyword} /> : null
                        }
                      />
                    </List.Item>
                  )}
                />
              </div>
            ))}
          </Space>
        )}
      </div>
    </div>
  )
}
