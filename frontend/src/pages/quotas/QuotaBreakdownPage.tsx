/** 配额分解页面（078-sales-quota，MVP - User Story 1）。 */

import { quotaApi, type SalesQuotaBreakdownRequest, type SalesQuotaResponse } from '../../services/api/quotaApi';
import { ArrowLeftOutlined, CheckOutlined, CloseOutlined, PlusOutlined } from '@ant-design/icons';
import { Button, Card, message, Space, Table, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import React, { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate, useParams } from 'react-router-dom';

const { Title, Text } = Typography;

const QuotaBreakdownPage: React.FC = () => {
  const { t } = useTranslation();
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
      title: t('pages.quotaBreakdown.colQuarter'),
      dataIndex: 'quarter',
      width: 100,
      render: (val) => (val ? `Q${val}` : '-'),
    },
    {
      title: t('pages.quotaBreakdown.colTeam'),
      dataIndex: 'teamId',
      width: 100,
    },
    {
      title: t('pages.quotaBreakdown.colSales'),
      dataIndex: 'userId',
      width: 100,
    },
    {
      title: t('pages.quotaBreakdown.colAmount'),
      dataIndex: 'amount',
      width: 150,
      render: (val) => val?.toFixed(2),
    },
    {
      title: t('pages.quotaBreakdown.colAction'),
      width: 80,
      render: (_, __, index) => (
        <Button danger size="small" onClick={() => setBreakdowns(breakdowns.filter((_, i) => i !== index))}>
          {t('common.button.delete')}
        </Button>
      ),
    },
  ];

  const handleAdd = () => {
    setBreakdowns([...breakdowns, { quarter: undefined, teamId: undefined, userId: undefined, amount: 0 }]);
  };

  const handleConfirm = () => {
    if (!isBalanced) {
      message.error(t('pages.quotaBreakdown.msgUnbalanced', { diff: diff.toFixed(2) }));
      return;
    }
    setConfirmLoading(true);
    quotaApi
      .breakdown(Number(id), breakdowns)
      .then(() => {
        message.success(t('pages.quotaBreakdown.msgSuccess'));
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
          {t('pages.quotaBreakdown.btnBack')}
        </Button>
        <Title level={4}>
          {t('pages.quotaBreakdown.title', {
            year: quota.year,
            period: quota.quarter ? `Q${quota.quarter}` : t('pages.quotaBreakdown.annual'),
          })}
        </Title>
      </Space>

      <Card title={t('pages.quotaBreakdown.cardParent')} style={{ marginBottom: 16 }}>
        <Space size="large">
          <div>
            <Text type="secondary">{t('pages.quotaBreakdown.labelQuotaAmount')}</Text>
            <Text strong>{quota.amount?.toFixed(2)} {t('pages.quotaBreakdown.unitWan')}</Text>
          </div>
          <div>
            <Text type="secondary">{t('pages.quotaBreakdown.labelPeriod')}</Text>
            <Text>{quota.periodStart} ~ {quota.periodEnd}</Text>
          </div>
        </Space>
      </Card>

      <Card
        title={t('pages.quotaBreakdown.cardDetail')}
        extra={
          <Button type="dashed" icon={<PlusOutlined />} onClick={handleAdd}>
            {t('pages.quotaBreakdown.btnAdd')}
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

      <Card title={t('pages.quotaBreakdown.cardValidation')} style={{ marginBottom: 16 }}>
        <Space size="large">
          <div>
            <Text type="secondary">{t('pages.quotaBreakdown.labelTotalBreakdown')}</Text>
            <Text strong style={{ color: isBalanced ? '#52c41a' : '#ff4d4f' }}>
              {totalBreakdown.toFixed(2)} {t('pages.quotaBreakdown.unitWan')}
            </Text>
          </div>
          <div>
            <Text type="secondary">{t('pages.quotaBreakdown.labelDiff')}</Text>
            <Text strong style={{ color: isBalanced ? '#52c41a' : '#ff4d4f' }}>
              {diff.toFixed(2)} {t('pages.quotaBreakdown.unitWan')}
            </Text>
          </div>
          <div>
            <Text type="secondary">{t('pages.quotaBreakdown.labelStatus')}</Text>
            <Text strong style={{ color: isBalanced ? '#52c41a' : '#ff4d4f' }}>
              {isBalanced ? t('pages.quotaBreakdown.statusBalanced') : t('pages.quotaBreakdown.statusUnbalanced')}
            </Text>
          </div>
        </Space>
      </Card>

      <Card>
        <Space>
          <Button type="primary" loading={confirmLoading} disabled={!isBalanced || breakdowns.length === 0} icon={<CheckOutlined />} onClick={handleConfirm}>
            {t('pages.quotaBreakdown.btnConfirm')}
          </Button>
          <Button icon={<CloseOutlined />} onClick={() => navigate('/quotas')}>
            {t('common.button.cancel')}
          </Button>
        </Space>
      </Card>
    </div>
  );
};

export default QuotaBreakdownPage;
