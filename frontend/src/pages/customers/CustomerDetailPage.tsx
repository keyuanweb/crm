import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { App, Button, Card, Descriptions, Form, Modal, Result, Select, Table, Tag, Typography } from 'antd'
import { ArrowLeftOutlined, ShareAltOutlined } from '@ant-design/icons'
import { useCustomerDetail } from '../../hooks/useCustomers'
import { shareCustomer } from '../../services/customerShareService'
import { fetchUsers } from '../../services/userService'
import { extractErrorMessage } from '../../services/apiClient'
import { useAuthStore } from '../../store/authStore'
import FollowUpTimeline from '../../components/FollowUpTimeline'
import ContactsCard from '../../components/ContactsCard'
import type { OpportunityBrief } from '../../types/customer'

export default function CustomerDetailPage() {
  const { id } = useParams()
  const customerId = Number(id)
  const { message } = App.useApp()
  const user = useAuthStore((s) => s.user)
  const isAdmin = user?.role === 'ADMIN'
  const [shareOpen, setShareOpen] = useState(false)
  const [userOptions, setUserOptions] = useState<{ value: number; label: string }[]>([])
  const [shareForm] = Form.useForm<{ sharedToUserId: number }>()
  const { data, isLoading, error } = useCustomerDetail(
    Number.isFinite(customerId) ? customerId : undefined,
  )

  const openShare = async () => {
    const users = await fetchUsers({ page: 1, pageSize: 100 })
    setUserOptions(
      users.items
        .filter((u) => u.role !== 'SUPPORT' && u.id !== user?.id)
        .map((u) => ({ value: u.id, label: u.displayName || u.username })),
    )
    shareForm.resetFields()
    setShareOpen(true)
  }

  const onShare = async () => {
    const values = await shareForm.validateFields()
    try {
      await shareCustomer(customerId, values.sharedToUserId)
      message.success('已共享')
      setShareOpen(false)
    } catch (err) {
      message.error(extractErrorMessage(err, '共享失败'))
    }
  }

  const canShare = data && (isAdmin || data.ownerId === user?.id)

  if (isLoading) {
    return (
      <Card loading style={{ minHeight: 300 }} />
    )
  }
  if (error || !data) {
    return (
      <Result
        status="404"
        title="客户不存在或已被删除"
        extra={
          <Link to="/customers">
            <Button type="primary" icon={<ArrowLeftOutlined />}>
              返回客户列表
            </Button>
          </Link>
        }
      />
    )
  }

  const opportunityColumns = [
    { title: '商机名称', dataIndex: 'name' },
    {
      title: '状态',
      dataIndex: 'status',
      render: (s: string) => (s === 'ACTIVE' ? <Tag color="blue">进行中</Tag> : <Tag>已归档</Tag>),
    },
    { title: '销售机会数', dataIndex: 'salesOpportunityCount' },
  ]

  return (
    <div>
      <Link to="/customers" style={{ marginBottom: 16, display: 'inline-block' }}>
        <Button type="link" icon={<ArrowLeftOutlined />}>
          返回客户列表
        </Button>
      </Link>

      <div style={{ marginBottom: 20, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <Typography.Title level={4} style={{ marginBottom: 4 }}>
            {data.name}
          </Typography.Title>
          <Typography.Text type="secondary" style={{ fontSize: 13 }}>
            {data.company} · {data.status === 'ACTIVE' ? '启用中' : '已停用'}
            {data.ownerName ? ` · 归属：${data.ownerName}` : ' · 公海'}
          </Typography.Text>
        </div>
        {canShare && (
          <Button icon={<ShareAltOutlined />} onClick={() => void openShare()}>
            共享给...
          </Button>
        )}
      </div>

      <Card
        title="基本信息"
        style={{ marginBottom: 16, borderRadius: 10 }}
        headStyle={{ borderBottom: '1px solid #f0f0f0' }}
      >
        <Descriptions column={2} bordered size="small">
          <Descriptions.Item label="客户名称">{data.name}</Descriptions.Item>
          <Descriptions.Item label="公司">{data.company}</Descriptions.Item>
          <Descriptions.Item label="联系人">{data.contactPerson ?? '-'}</Descriptions.Item>
          <Descriptions.Item label="电话">{data.phone ?? '-'}</Descriptions.Item>
          <Descriptions.Item label="邮箱">{data.email ?? '-'}</Descriptions.Item>
          <Descriptions.Item label="地址">{data.address ?? '-'}</Descriptions.Item>
          <Descriptions.Item label="备注" span={2}>
            {data.remark ?? '-'}
          </Descriptions.Item>
          <Descriptions.Item label="状态">
            {data.status === 'ACTIVE' ? <Tag color="green">启用</Tag> : <Tag>停用</Tag>}
          </Descriptions.Item>
        </Descriptions>
      </Card>

      <Card
        title="关联商机"
        style={{ marginBottom: 16, borderRadius: 10 }}
        headStyle={{ borderBottom: '1px solid #f0f0f0' }}
        bodyStyle={{ padding: 0 }}
      >
        <Table<OpportunityBrief>
          rowKey="id"
          size="small"
          dataSource={data.opportunities}
          columns={opportunityColumns as never}
          pagination={false}
          locale={{ emptyText: '暂无关联商机' }}
        />
      </Card>

      <ContactsCard customerId={customerId} />

      <FollowUpTimeline customerId={customerId} />

      <Modal
        title={`共享客户「${data.name}」`}
        open={shareOpen}
        onOk={() => void onShare()}
        onCancel={() => setShareOpen(false)}
        okText="共享"
        destroyOnClose
      >
        <Form form={shareForm} name="shareForm" layout="vertical">
          <Form.Item
            name="sharedToUserId"
            label="共享给"
            rules={[{ required: true, message: '请选择用户' }]}
          >
            <Select showSearch optionFilterProp="label" placeholder="选择用户" options={userOptions} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
