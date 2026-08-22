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
      <Typography.Title level={4}>客户详情：{data.name}</Typography.Title>

      <Card title="基本信息" style={{ marginBottom: 16 }}>
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

      <Card title="关联商机" style={{ marginBottom: 16 }}>
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
