import { Link, useParams } from 'react-router-dom'
import { Button, Card, Descriptions, Result, Table, Tag, Typography } from 'antd'
import { ArrowLeftOutlined } from '@ant-design/icons'
import { useCustomerDetail } from '../../hooks/useCustomers'
import FollowUpTimeline from '../../components/FollowUpTimeline'
import type { OpportunityBrief } from '../../types/customer'

export default function CustomerDetailPage() {
  const { id } = useParams()
  const customerId = Number(id)
  const { data, isLoading, error } = useCustomerDetail(
    Number.isFinite(customerId) ? customerId : undefined,
  )

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

      <div style={{ marginBottom: 20 }}>
        <Typography.Title level={4} style={{ marginBottom: 4 }}>
          {data.name}
        </Typography.Title>
        <Typography.Text type="secondary" style={{ fontSize: 13 }}>
          {data.company} · {data.status === 'ACTIVE' ? '启用中' : '已停用'}
        </Typography.Text>
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

      <FollowUpTimeline customerId={customerId} />
    </div>
  )
}
