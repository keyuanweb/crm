import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { Empty, List, Space, Tabs, Tag, Typography } from 'antd'
import { useTranslation } from 'react-i18next'
import { searchFull, type SearchGroup, type SearchResponse } from '../../services/searchService'
// 095：本页原有的页内私有 `Highlight` 已提取为 `components/ui/Highlight.tsx`
// （一份实现两处用——部门树搜索更依赖它）。**行为逐字未变**：多词分词、正则转义、
// 色值 `#ffe58f` 全部保留。不要在此处再写第二份。
import { Highlight } from '../../components/ui'
export default function SearchResultPage() {
  const { t } = useTranslation()
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
        {t('title')}: {keyword}
      </Typography.Title>
      <Typography.Text type="secondary" style={{ fontSize: 13 }}>
        {t('totalResults', { count: resp.total })}
      </Typography.Text>

      {resp.groups.length > 1 && (
        <Tabs
          activeKey={activeType}
          onChange={setActiveType}
          style={{ marginTop: 8 }}
          items={[
            { key: 'ALL', label: '全部' },
            ...resp.groups.map((g) => ({ key: g.type, label: `${t(g.label.toLowerCase())}（${g.items.length}）` })),
          ]}
        />
      )}

      <div style={{ marginTop: 12 }}>
        {groups.length === 0 ? (
          <Empty description={t('noResults')} style={{ padding: 40 }} />
        ) : (
          <Space direction="vertical" style={{ width: '100%' }} size={16}>
            {groups.map((g) => (
              <div key={g.type}>
                <div style={{ marginBottom: 8 }}>
                  <Tag color="blue">{t(g.label.toLowerCase())}</Tag>
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
