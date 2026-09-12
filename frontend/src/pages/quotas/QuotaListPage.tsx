/** 配额列表页面（078-sales-quota，MVP - User Story 1）。 */

import { quotaApi, type SalesQuotaResponse, type SalesQuotaSummary } from '../../services/api/quotaApi';
import { PlusOutlined, TeamOutlined, UserOutlined, BarChartOutlined } from '@ant-design/icons';
import { ProTable } from '@ant-design/pro-components';
import { Button, Card, Col, Flex, Row } from 'antd';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import React, { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';

const QuotaListPage: React.FC = () => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const actionRef = useRef<ActionType>();
  const [year] = useState(new Date().getFullYear());
  const [summary, setSummary] = useState<SalesQuotaSummary | null>(null);

  useEffect(() => {
    quotaApi
      .getSummary(year)
      .then(setSummary)
      .catch((err) => console.error(err));
  }, [year]);

  const columns: ProColumns<SalesQuotaResponse>[] = [
    {
      title: t('pages.quotaList.colYear'),
      dataIndex: 'year',
      width: 80,
      search: false,
    },
    {
      title: t('pages.quotaList.colQuarter'),
      dataIndex: 'quarter',
      width: 80,
      search: false,
      render: (val) => val ? `Q${val}` : t('pages.quotaList.annual'),
    },
    {
      title: t('pages.quotaList.colTeam'),
      dataIndex: 'teamName',
      width: 120,
      search: false,
    },
    {
      title: t('pages.quotaList.colSales'),
      dataIndex: 'userName',
      width: 100,
      search: false,
    },
    {
      title: t('pages.quotaList.colQuotaAmount'),
      dataIndex: 'amount',
      width: 120,
      search: false,
      render: (val) => (typeof val === 'number' ? val.toFixed(2) : val),
    },
    {
      title: t('pages.quotaList.colActualAmount'),
      dataIndex: 'actualAmount',
      width: 120,
      search: false,
      render: (val) => (typeof val === 'number' ? val.toFixed(2) : '-'),
    },
    {
      title: t('pages.quotaList.colAchievementRate'),
      dataIndex: 'achievementRate',
      width: 100,
      search: false,
      render: (val) => (typeof val === 'number' ? `${val.toFixed(1)}%` : '-'),
    },
    {
      title: t('pages.quotaList.colStatus'),
      dataIndex: 'status',
      width: 100,
      valueEnum: {
        ACTIVE: { text: t('pages.quotaList.statusActive'), status: 'Success' },
        DRAFT: { text: t('common.status.draft'), status: 'Warning' },
        CLOSED: { text: t('common.status.closed'), status: 'Error' },
      },
    },
    {
      title: t('pages.quotaList.colCreatedAt'),
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
                <div style={{ color: '#8c8c8c', fontSize: 12 }}>{t('pages.quotaList.statTotalQuota')}</div>
                <div style={{ fontSize: 20, fontWeight: 600 }}>
                  {summary ? summary.totalQuota.toFixed(2) : '--'} {t('pages.quotaList.unitWan')}
                </div>
              </div>
            </Flex>
          </Card>
        </Col>
        <Col span={8}>
          <Card size="small" styles={{ body: { padding: '12px 16px' } }}>
            <Flex gap={8} align="center">
              <UserOutlined style={{ fontSize: 24, color: '#52c41a' }} />
              <div>
                <div style={{ color: '#8c8c8c', fontSize: 12 }}>{t('pages.quotaList.statTotalActual')}</div>
                <div style={{ fontSize: 20, fontWeight: 600 }}>
                  {summary ? summary.totalActual.toFixed(2) : '--'} {t('pages.quotaList.unitWan')}
                </div>
              </div>
            </Flex>
          </Card>
        </Col>
        <Col span={8}>
          <Card size="small" styles={{ body: { padding: '12px 16px' } }}>
            <Flex gap={8} align="center">
              <span style={{ fontSize: 24 }}>📊</span>
              <div>
                <div style={{ color: '#8c8c8c', fontSize: 12 }}>{t('pages.quotaList.statTotalRate')}</div>
                <div style={{ fontSize: 20, fontWeight: 600 }}>
                  {summary ? summary.achievementRate.toFixed(1) : '--'}%
                </div>
              </div>
            </Flex>
          </Card>
        </Col>
      </Row>

      <ProTable<SalesQuotaResponse>
        actionRef={actionRef}
        headerTitle={t('pages.quotaList.title')}
        rowKey="id"
        columns={columns}
        request={async (params) => {
          const res = await quotaApi.list({
            page: params.current || 1,
            size: params.pageSize || 20,
            year,
            status: params.status,
          });
          return {
            data: res.records,
            total: res.total,
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
              {t('pages.quotaList.btnComparison')}
            </Button>,
            <Button key="add" type="primary" icon={<PlusOutlined />} onClick={() => navigate('/quotas/create')}>
              {t('pages.quotaList.btnCreate')}
            </Button>,
          ],
        }}
      />
    </div>
  );
};

export default QuotaListPage;
