/** 配额对比分析页面（078-sales-quota，US4 - 配额对比分析）。 */

import { quotaApi, type TeamRankingItem as TeamRanking } from '../../services/api/quotaApi';
import { ArrowLeftOutlined, DownloadOutlined } from '@ant-design/icons';
import { Button, Card, Col, Flex, message, Progress, Row, Select, Space, Table, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import React, { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';

const { Title, Text } = Typography;

const QuotaComparisonPage: React.FC = () => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [year, setYear] = useState<number>(new Date().getFullYear());
  const [ranking, setRanking] = useState<TeamRanking[]>([]);
  const [loading, setLoading] = useState(false);

  // loadRanking 依赖 year：用 useCallback 固定引用后交给 effect 依赖。
  const loadRanking = useCallback(() => {
    setLoading(true);
    quotaApi
      .getTeamRanking(year)
      .then((data) => {
        setRanking(data);
      })
      .catch((err) => {
        console.error(err);
      })
      .finally(() => setLoading(false));
  }, [year]);

  useEffect(() => {
    loadRanking();
  }, [loadRanking]);

  const columns: ColumnsType<TeamRanking> = [
    {
      title: t('pages.quotaComparison.colRank'),
      width: 80,
      render: (_, __, index) => (
        <Text strong style={{ color: index < 3 ? '#faad14' : '#595959' }}>
          {index + 1}
        </Text>
      ),
    },
    {
      title: t('pages.quotaComparison.colTeam'),
      dataIndex: 'teamName',
      width: 200,
    },
    {
      title: t('pages.quotaComparison.colQuotaAmount'),
      dataIndex: 'quotaAmount',
      width: 150,
      sorter: (a, b) => a.quotaAmount - b.quotaAmount,
      render: (val) => val?.toFixed(2),
    },
    {
      title: t('pages.quotaComparison.colActualAmount'),
      dataIndex: 'actualAmount',
      width: 150,
      sorter: (a, b) => a.actualAmount - b.actualAmount,
      render: (val) => val?.toFixed(2),
    },
    {
      title: t('pages.quotaComparison.colAchievementRate'),
      dataIndex: 'achievementRate',
      width: 200,
      sorter: (a, b) => (a.achievementRate || 0) - (b.achievementRate || 0),
      render: (val) => (
        <Flex align="center" gap={8}>
          <Progress
            percent={Number(val?.toFixed(1)) || 0}
            size="small"
            status={
              (val || 0) >= 80
                ? 'success'
                : (val || 0) >= 60
                ? 'normal'
                : 'exception'
            }
            strokeColor={{
              '0%': (val || 0) >= 80 ? '#52c41a' : (val || 0) >= 60 ? '#faad14' : '#ff4d4f',
              '100%': (val || 0) >= 80 ? '#52c41a' : (val || 0) >= 60 ? '#faad14' : '#ff4d4f',
            }}
          />
          <Text strong style={{ color: (val || 0) >= 80 ? '#52c41a' : (val || 0) >= 60 ? '#faad14' : '#ff4d4f' }}>
            {val?.toFixed(1)}%
          </Text>
        </Flex>
      ),
    },
  ];

  const totalQuota = ranking.reduce((sum, r) => sum + (r.quotaAmount || 0), 0);
  const totalActual = ranking.reduce((sum, r) => sum + (r.actualAmount || 0), 0);
  const overallRate = totalQuota > 0 ? (totalActual / totalQuota) * 100 : 0;

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/quotas')}>
          {t('pages.quotaComparison.btnBack')}
        </Button>
        <Title level={4}>{t('pages.quotaComparison.title')}</Title>
      </Space>

      <Card style={{ marginBottom: 16 }}>
        <Flex gap={16} align="center">
          <Text>{t('pages.quotaComparison.labelYear')}</Text>
          <Select<number>
            value={year}
            onChange={setYear}
            style={{ width: 120 }}
            options={Array.from({ length: 5 }, (_, i) => ({
              label: String(new Date().getFullYear() - 2 + i),
              value: new Date().getFullYear() - 2 + i,
            }))}
          />
          <Button icon={<DownloadOutlined />} onClick={() => message.info(t('pages.quotaComparison.msgExportWip'))}>
            {t('pages.quotaComparison.btnExport')}
          </Button>
        </Flex>
      </Card>

      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col span={8}>
          <Card size="small" styles={{ body: { padding: '12px 16px' } }}>
            <Flex gap={8} align="center">
              <span style={{ fontSize: 24 }}>📊</span>
              <div>
                <div style={{ color: '#8c8c8c', fontSize: 12 }}>{t('pages.quotaComparison.statTotalQuota')}</div>
                <div style={{ fontSize: 20, fontWeight: 600 }}>{totalQuota.toFixed(2)} {t('pages.quotaComparison.unitWan')}</div>
              </div>
            </Flex>
          </Card>
        </Col>
        <Col span={8}>
          <Card size="small" styles={{ body: { padding: '12px 16px' } }}>
            <Flex gap={8} align="center">
              <span style={{ fontSize: 24 }}>💰</span>
              <div>
                <div style={{ color: '#8c8c8c', fontSize: 12 }}>{t('pages.quotaComparison.statTotalActual')}</div>
                <div style={{ fontSize: 20, fontWeight: 600 }}>{totalActual.toFixed(2)} {t('pages.quotaComparison.unitWan')}</div>
              </div>
            </Flex>
          </Card>
        </Col>
        <Col span={8}>
          <Card size="small" styles={{ body: { padding: '12px 16px' } }}>
            <Flex gap={8} align="center">
              <span style={{ fontSize: 24 }}>📈</span>
              <div>
                <div style={{ color: '#8c8c8c', fontSize: 12 }}>{t('pages.quotaComparison.statTotalRate')}</div>
                <div style={{ fontSize: 20, fontWeight: 600, color: overallRate >= 80 ? '#52c41a' : overallRate >= 60 ? '#faad14' : '#ff4d4f' }}>
                  {overallRate.toFixed(1)}%
                </div>
              </div>
            </Flex>
          </Card>
        </Col>
      </Row>

      <Card title={t('pages.quotaComparison.cardRanking')}>
        <Table<TeamRanking>
          columns={columns}
          dataSource={ranking}
          loading={loading}
          rowKey="teamId"
          pagination={false}
          size="middle"
        />
      </Card>
    </div>
  );
};

export default QuotaComparisonPage;
