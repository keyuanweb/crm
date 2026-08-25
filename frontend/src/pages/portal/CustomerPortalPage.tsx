import { useState } from 'react'
import { App, Button, Card, Descriptions, Form, Input, InputNumber, List, Modal, Select, Space, Tabs, Tag, Typography } from 'antd'
import { useQuery } from '@tanstack/react-query'
import {
  fetchPortalArticle,
  fetchPortalArticles,
  queryPortalTicket,
  submitPortalTicket,
} from '../../services/customerPortalService'
import { extractErrorMessage } from '../../services/apiClient'

/** 客户自助门户（050，公开访问）：知识库浏览/在线提单/进度查询。 */
export default function CustomerPortalPage() {
  const [keyword, setKeyword] = useState('')
  const [activeTab, setActiveTab] = useState('kb')

  const articles = useQuery({
    queryKey: ['portal-articles', keyword],
    queryFn: () => fetchPortalArticles(keyword || undefined, 1, 50),
  })

  return (
    <div style={{ maxWidth: 900, margin: '0 auto', padding: '24px 16px' }}>
      <Typography.Title level={3} style={{ textAlign: 'center' }}>
        客户自助服务中心
      </Typography.Title>
      <Typography.Paragraph type="secondary" style={{ textAlign: 'center' }}>
        浏览常见问题、在线提交服务请求、跟踪工单进度
      </Typography.Paragraph>
      <Tabs
        activeKey={activeTab}
        onChange={setActiveTab}
        items={[
          {
            key: 'kb',
            label: '知识库',
            children: <KnowledgeBaseTab keyword={keyword} setKeyword={setKeyword} data={articles.data?.items} loading={articles.isLoading} />,
          },
          { key: 'submit', label: '提交工单', children: <SubmitTicketTab /> },
          { key: 'track', label: '工单查询', children: <TrackTicketTab /> },
        ]}
      />
    </div>
  )
}

function KnowledgeBaseTab({
  keyword,
  setKeyword,
  data,
  loading,
}: {
  keyword: string
  setKeyword: (v: string) => void
  data?: { id: number; title: string; category: string }[]
  loading: boolean
}) {
  const { message } = App.useApp()
  const [article, setArticle] = useState<{ id: number; title: string; content?: string; category?: string } | null>(null)

  return (
    <Card>
      <Space style={{ marginBottom: 16 }}>
        <Input.Search
          placeholder="搜索常见问题"
          allowClear
          style={{ width: 320 }}
          onSearch={(v) => setKeyword(v)}
        />
        <Tag>{loading ? '加载中' : `${data?.length ?? 0} 篇文章`}</Tag>
      </Space>
      <List
        loading={loading}
        dataSource={data ?? []}
        locale={{ emptyText: keyword ? '未找到相关文章' : '暂无已发布文章' }}
        renderItem={(item) => (
          <List.Item
            actions={[
              <a
                key="view"
                onClick={() => {
                  void fetchPortalArticle(item.id)
                    .then((a) => setArticle(a))
                    .catch((e) => message.error(extractErrorMessage(e, '加载失败')))
                }}
              >
                查看
              </a>,
            ]}
          >
            <List.Item.Meta
              title={item.title}
              description={<Tag color="blue">{item.category}</Tag>}
            />
          </List.Item>
        )}
      />
      <Modal
        title={article?.title}
        open={!!article}
        onCancel={() => setArticle(null)}
        footer={null}
        width={640}
      >
        {article?.content ? (
          <Typography.Paragraph style={{ whiteSpace: 'pre-wrap' }}>{article.content}</Typography.Paragraph>
        ) : null}
      </Modal>
    </Card>
  )
}

function SubmitTicketTab() {
  const { message } = App.useApp()
  const [form] = Form.useForm()
  const [saving, setSaving] = useState(false)
  const [result, setResult] = useState<{ ticketId: number; status: string } | null>(null)

  const submit = async () => {
    const values = await form.validateFields()
    setSaving(true)
    try {
      const res = await submitPortalTicket({
        phone: values.phone,
        email: values.email,
        title: values.title.trim(),
        description: values.description,
        priority: values.priority ?? 'MEDIUM',
      })
      setResult(res)
      message.success('工单已提交')
    } catch (err) {
      message.error(extractErrorMessage(err, '提交失败'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Card>
      {result ? (
        <Descriptions column={1} bordered size="small">
          <Descriptions.Item label="工单号">{result.ticketId}</Descriptions.Item>
          <Descriptions.Item label="状态">
            <Tag color="processing">{result.status}</Tag>
          </Descriptions.Item>
          <Descriptions.Item label="下一步">
            请记录工单号，前往「工单查询」标签跟踪进度。
          </Descriptions.Item>
        </Descriptions>
      ) : (
        <Form form={form} layout="vertical" style={{ maxWidth: 520 }}>
          <Form.Item name="phone" label="手机号" rules={[{ required: true, message: '请填写手机号' }]}>
            <Input placeholder="与销售登记的手机号一致" maxLength={20} />
          </Form.Item>
          <Form.Item name="email" label="邮箱">
            <Input placeholder="可选" />
          </Form.Item>
          <Form.Item name="title" label="问题标题" rules={[{ required: true, message: '请填写标题' }]}>
            <Input maxLength={200} />
          </Form.Item>
          <Form.Item name="description" label="问题描述">
            <Input.TextArea rows={3} maxLength={2000} />
          </Form.Item>
          <Form.Item name="priority" label="优先级" initialValue="MEDIUM">
            <Select
              options={[
                { value: 'LOW', label: '低' },
                { value: 'MEDIUM', label: '中' },
                { value: 'HIGH', label: '高' },
                { value: 'URGENT', label: '紧急' },
              ]}
            />
          </Form.Item>
          <Button type="primary" loading={saving} onClick={() => void submit()}>
            提交工单
          </Button>
        </Form>
      )}
    </Card>
  )
}

function TrackTicketTab() {
  const { message } = App.useApp()
  const [form] = Form.useForm()
  const [querying, setQuerying] = useState(false)
  const [status, setStatus] = useState<{
    ticketId: number
    status: string
    priority: string
    slaStatus?: string
    createdAt: string
    replies: { content: string; createdAt: string }[]
  } | null>(null)

  const query = async () => {
    const values = await form.validateFields()
    setQuerying(true)
    try {
      const res = await queryPortalTicket({
        ticketId: values.ticketId,
        phone: values.phone,
        email: values.email,
      })
      setStatus(res)
    } catch (err) {
      setStatus(null)
      message.error(extractErrorMessage(err, '查询失败'))
    } finally {
      setQuerying(false)
    }
  }

  return (
    <Card>
      <Form form={form} layout="inline" style={{ marginBottom: 16 }}>
        <Form.Item name="ticketId" label="工单号" rules={[{ required: true, message: '请输入工单号' }]}>
          <InputNumber min={1} style={{ width: 160 }} />
        </Form.Item>
        <Form.Item name="phone" label="手机号" rules={[{ required: true, message: '请输入手机号' }]}>
          <Input placeholder="提交时的手机号" maxLength={20} />
        </Form.Item>
        <Button type="primary" loading={querying} onClick={() => void query()}>
          查询
        </Button>
      </Form>
      {status && (
        <>
          <Descriptions column={3} bordered size="small">
            <Descriptions.Item label="工单号">{status.ticketId}</Descriptions.Item>
            <Descriptions.Item label="状态">
              <Tag color={status.status === 'CLOSED' ? 'success' : 'processing'}>{status.status}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="SLA">{status.slaStatus ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="优先级">{status.priority}</Descriptions.Item>
            <Descriptions.Item label="提交时间" span={2}>
              {status.createdAt.replace('T', ' ').slice(0, 19)}
            </Descriptions.Item>
          </Descriptions>
          <Typography.Title level={5} style={{ marginTop: 16 }}>
            处理记录
          </Typography.Title>
          <List
            dataSource={status.replies}
            locale={{ emptyText: '暂无处理记录' }}
            renderItem={(r) => (
              <List.Item>
                <List.Item.Meta
                  title={r.createdAt.replace('T', ' ').slice(0, 19)}
                  description={r.content}
                />
              </List.Item>
            )}
          />
        </>
      )}
    </Card>
  )
}
