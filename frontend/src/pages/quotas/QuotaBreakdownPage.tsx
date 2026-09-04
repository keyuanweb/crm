/** 配额分解页面（078-sales-quota，MVP - User Story 1）。 */

import { quotaApi, type SalesQuotaBreakdownRequest, type SalesQuotaResponse } from '../../services/api/quotaApi';
import { ArrowLeftOutlined, CheckOutlined, CloseOutlined, PlusOutlined } from '@ant-design/icons';
import { Button, Card, message, Space, Table, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import React, { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';

const { Title, Text } = Typography;

const QuotaBreakdownPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [quota, setQuota] = useState<SalesQuotaResponse | null>(null);
  const [breakdowns, setBreakdowns] = useState<SalesQuotaBreakdownRequest[]>([]);
  const [confirmLoading, setConfirmLoading] = useState(false);

  useEffect(() => {
    if (id) {
      quotaApi
        .detail(Number(id))
        .then((data) => {
          setQuota(data);
        })
        .catch((err) => {
          message.error(err.message);
        });
    }
  }, [id]);

  const totalBreakdown = breakdowns.reduce((sum, b) => sum + (b.amount || 0), 0);
  const diff = quota ? totalBreakdown - (quota.amount || 0) : 0;
  const isBalanced = Math.abs(diff) <= 0.01;

  const columns: ColumnsType<SalesQuotaBreakdownRequest> = [
    {
      title: '季度',
      dataIndex: 'quarter',
      width: 100,
      render: (val) => (val ? `Q${val}` : '-'),
    },
    {
      title: '团队',
      dataIndex: 'teamId',
      width: 100,
    },
    {
      title: '销售',
      dataIndex: 'userId',
      width: 100,
    },
    {
      title: '分解金额（万）',
      dataIndex: 'amount',
      width: 150,
      render: (val) => val?.toFixed(2),
    },
    {
      title: '操作',
      width: 80,
      render: (_, __, index) => (
        <Button danger size="small" onClick={() => setBreakdowns(breakdowns.filter((_, i) => i !== index))}>
          删除
        </Button>
      ),
    },
  ];

  const handleAdd = () => {
    setBreakdowns([...breakdowns, { quarter: undefined, teamId: undefined, userId: undefined, amount: 0 }]);
  };

  const handleConfirm = () => {
    if (!isBalanced) {
      message.error(`分解总和与上级配额不一致（偏差：${diff.toFixed(2)} 万）`);
      return;
    }
    setConfirmLoading(true);
    quotaApi
      .breakdown(Number(id), breakdowns)
      .then(() => {
        message.success('分解成功');
        navigate('/quotas');
      })
      .catch((err) => {
        message.error(err.message);
      })
      .finally(() => setConfirmLoading(false));
  };

  if (!quota) {
    return <div>Loading...</div>;
  }

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/quotas')}>
          返回
        </Button>
        <Title level={4}>配额分解 - {quota.year}年 {quota.quarter ? `Q${quota.quarter}` : '年度'}</Title>
      </Space>

      <Card title="上级配额" style={{ marginBottom: 16 }}>
        <Space size="large">
          <div>
            <Text type="secondary">配额金额：</Text>
            <Text strong>{quota.amount?.toFixed(2)} 万</Text>
          </div>
          <div>
            <Text type="secondary">期间：</Text>
            <Text>{quota.periodStart} ~ {quota.periodEnd}</Text>
          </div>
        </Space>
      </Card>

      <Card
        title="分解明细"
        extra={
          <Button type="dashed" icon={<PlusOutlined />} onClick={handleAdd}>
            添加分解
          </Button>
        }
        style={{ marginBottom: 16 }}
      >
        <Table<SalesQuotaBreakdownRequest>
          columns={columns}
          dataSource={breakdowns}
          rowKey={(_, index) => String(index)}
          pagination={false}
          size="small"
        />
      </Card>

      <Card title="汇总校验" style={{ marginBottom: 16 }}>
        <Space size="large">
          <div>
            <Text type="secondary">分解总和：</Text>
            <Text strong style={{ color: isBalanced ? '#52c41a' : '#ff4d4f' }}>
              {totalBreakdown.toFixed(2)} 万
            </Text>
          </div>
          <div>
            <Text type="secondary">偏差：</Text>
            <Text strong style={{ color: isBalanced ? '#52c41a' : '#ff4d4f' }}>
              {diff.toFixed(2)} 万
            </Text>
          </div>
          <div>
            <Text type="secondary">状态：</Text>
            <Text strong style={{ color: isBalanced ? '#52c41a' : '#ff4d4f' }}>
              {isBalanced ? '平衡' : '不平衡'}
            </Text>
          </div>
        </Space>
      </Card>

      <Card>
        <Space>
          <Button type="primary" loading={confirmLoading} disabled={!isBalanced || breakdowns.length === 0} icon={<CheckOutlined />} onClick={handleConfirm}>
            确认分解
          </Button>
          <Button icon={<CloseOutlined />} onClick={() => navigate('/quotas')}>
            取消
          </Button>
        </Space>
      </Card>
    </div>
  );
};

export default QuotaBreakdownPage;
