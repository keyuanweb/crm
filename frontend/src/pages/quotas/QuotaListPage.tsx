/** 配额列表页面（078-sales-quota，MVP - User Story 1）。 */

import { quotaApi, type SalesQuotaResponse } from '../../services/api/quotaApi';
import { PlusOutlined, TeamOutlined, UserOutlined, BarChartOutlined } from '@ant-design/icons';
import { ProTable } from '@ant-design/pro-components';
import { Button, Card, Col, Flex, Row } from 'antd';
import type { ProColumns } from '@ant-design/pro-components';
import React, { useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';

const QuotaListPage: React.FC = () => {
  const navigate = useNavigate();
  const actionRef = useRef<any>();
  const [year] = useState(new Date().getFullYear());

  const columns: ProColumns<SalesQuotaResponse>[] = [
    {
      title: '年份',
      dataIndex: 'year',
      width: 80,
      search: false,
    },
    {
      title: '季度',
      dataIndex: 'quarter',
      width: 80,
      search: false,
      render: (val) => val ? `Q${val}` : '年度',
    },
    {
      title: '团队',
      dataIndex: 'teamName',
      width: 120,
      search: false,
    },
    {
      title: '销售',
      dataIndex: 'userName',
      width: 100,
      search: false,
    },
    {
      title: '配额金额（万）',
      dataIndex: 'amount',
      width: 120,
      search: false,
      render: (val) => (typeof val === 'number' ? val.toFixed(2) : val),
    },
    {
      title: '实际销售额（万）',
      dataIndex: 'actualAmount',
      width: 120,
      search: false,
      render: (val) => (typeof val === 'number' ? val.toFixed(2) : '-'),
    },
    {
      title: '达成率',
      dataIndex: 'achievementRate',
      width: 100,
      search: false,
      render: (val) => (typeof val === 'number' ? `${val.toFixed(1)}%` : '-'),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      valueEnum: {
        ACTIVE: { text: '活跃', status: 'Success' },
        DRAFT: { text: '草稿', status: 'Warning' },
        CLOSED: { text: '已关闭', status: 'Error' },
      },
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      width: 160,
      search: false,
      valueType: 'dateTime',
    },
  ];

  return (
    <div>
      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col span={8}>
          <Card size="small" styles={{ body: { padding: '12px 16px' } }}>
            <Flex gap={8} align="center">
              <TeamOutlined style={{ fontSize: 24, color: '#1890ff' }} />
              <div>
                <div style={{ color: '#8c8c8c', fontSize: 12 }}>总配额</div>
                <div style={{ fontSize: 20, fontWeight: 600 }}>-- 万</div>
              </div>
            </Flex>
          </Card>
        </Col>
        <Col span={8}>
          <Card size="small" styles={{ body: { padding: '12px 16px' } }}>
            <Flex gap={8} align="center">
              <UserOutlined style={{ fontSize: 24, color: '#52c41a' }} />
              <div>
                <div style={{ color: '#8c8c8c', fontSize: 12 }}>总实际</div>
                <div style={{ fontSize: 20, fontWeight: 600 }}>-- 万</div>
              </div>
            </Flex>
          </Card>
        </Col>
        <Col span={8}>
          <Card size="small" styles={{ body: { padding: '12px 16px' } }}>
            <Flex gap={8} align="center">
              <span style={{ fontSize: 24 }}>📊</span>
              <div>
                <div style={{ color: '#8c8c8c', fontSize: 12 }}>总达成率</div>
                <div style={{ fontSize: 20, fontWeight: 600 }}>--%</div>
              </div>
            </Flex>
          </Card>
        </Col>
      </Row>

      <ProTable<SalesQuotaResponse>
        actionRef={actionRef}
        headerTitle="配额列表"
        rowKey="id"
        columns={columns}
        request={async (params) => {
          const res = await quotaApi.list({
            page: params.current ? Math.floor((params.current - 1) / (params.pageSize || 20)) : 0,
            size: params.pageSize || 20,
            year,
            status: params.status,
          });
          return {
            data: res.content,
            total: res.totalElements,
            success: true,
          };
        }}
        pagination={{
          defaultPageSize: 20,
          showSizeChanger: true,
          pageSizeOptions: ['10', '20', '50', '100'],
        }}
        search={{
          labelWidth: 'auto',
        }}
        dateFormatter="string"
        toolbar={{
          actions: [
            <Button
              key="comparison"
              icon={<BarChartOutlined />}
              onClick={() => navigate('/quotas/comparison')}
            >
              配额对比
            </Button>,
            <Button key="add" type="primary" icon={<PlusOutlined />}>
              创建配额
            </Button>,
          ],
        }}
      />
    </div>
  );
};

export default QuotaListPage;
