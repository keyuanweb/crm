/** 配额达成页面（078-sales-quota，US2 - 达成率跟踪与可视化）。 */

import { quotaApi, type SalesQuotaAchievementResponse, type SalesQuotaResponse } from '../../services/api/quotaApi';
import { ArrowLeftOutlined } from '@ant-design/icons';
import { Button, Card, Progress, Row, Col, Space, Typography, Tag, Alert } from 'antd';
import React, { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate, useParams } from 'react-router-dom';

const { Title, Text } = Typography;

const QuotaAchievementPage: React.FC = () => {
  const { t } = useTranslation();
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [quota, setQuota] = useState<SalesQuotaResponse | null>(null);
  const [achievement, setAchievement] = useState<SalesQuotaAchievementResponse | null>(null);
  useEffect(() => {
    if (id) {
      Promise.all([quotaApi.detail(Number(id)), quotaApi.getAchievement(Number(id))])
        .then(([quotaData, achievementData]) => {
          setQuota(quotaData);
          setAchievement(achievementData);
        })
        .catch((err) => {
          console.error(err);
        });
    }
  }, [id]);

  if (!quota || !achievement) {
    return <div>Loading...</div>;
  }

  const getProgressStatus = (status: string): 'normal' | 'success' | 'exception' => {
    if (status === 'ON_TRACK') return 'success';
    if (status === 'AT_RISK') return 'normal';
    return 'exception';
  };

  const getProgressColor = (status: string): string => {
    if (status === 'ON_TRACK') return '#52c41a';
    if (status === 'AT_RISK') return '#faad14';
    return '#ff4d4f';
  };

  const getStatusTag = (status: string) => {
    if (status === 'ON_TRACK') return <Tag color="green">{t('pages.quotaAchievement.tagOnTrack')}</Tag>;
    if (status === 'AT_RISK') return <Tag color="orange">{t('pages.quotaAchievement.tagAtRisk')}</Tag>;
    return <Tag color="red">{t('pages.quotaAchievement.tagBehind')}</Tag>;
  };

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/quotas')}>
          {t('pages.quotaAchievement.btnBack')}
        </Button>
        <Title level={4}>
          {t('pages.quotaAchievement.title', {
            year: quota.year,
            period: quota.quarter ? `Q${quota.quarter}` : t('pages.quotaAchievement.annual'),
          })}
        </Title>
      </Space>

      {achievement.status === 'AT_RISK' && (
        <Alert
          message={t('pages.quotaAchievement.alertAtRiskTitle')}
          description={t('pages.quotaAchievement.alertAtRiskDesc')}
          type="warning"
          showIcon
          style={{ marginBottom: 16 }}
        />
      )}

      {achievement.status === 'BELOW_TARGET' && (
        <Alert
          message={t('pages.quotaAchievement.alertBelowTitle')}
          description={t('pages.quotaAchievement.alertBelowDesc')}
          type="error"
          showIcon
          style={{ marginBottom: 16 }}
        />
      )}

      <Row gutter={[16, 16]}>
        <Col span={8}>
          <Card size="small" styles={{ body: { padding: '16px' } }}>
            <Text type="secondary">{t('pages.quotaAchievement.cardQuotaAmount')}</Text>
            <div style={{ fontSize: 24, fontWeight: 600, marginTop: 8 }}>
              {quota.amount?.toFixed(2)} <Text type="secondary">{t('pages.quotaAchievement.unitWan')}</Text>
            </div>
          </Card>
        </Col>
        <Col span={8}>
          <Card size="small" styles={{ body: { padding: '16px' } }}>
            <Text type="secondary">{t('pages.quotaAchievement.cardActualAmount')}</Text>
            <div style={{ fontSize: 24, fontWeight: 600, marginTop: 8 }}>
              {achievement.actualAmount?.toFixed(2)} <Text type="secondary">{t('pages.quotaAchievement.unitWan')}</Text>
            </div>
          </Card>
        </Col>
        <Col span={8}>
          <Card size="small" styles={{ body: { padding: '16px' } }}>
            <Text type="secondary">{t('pages.quotaAchievement.cardAchievementRate')}</Text>
            <div style={{ fontSize: 24, fontWeight: 600, marginTop: 8, color: getProgressColor(achievement.status) }}>
              {achievement.achievementRate?.toFixed(1)}%
            </div>
          </Card>
        </Col>
      </Row>

      <Card title={t('pages.quotaAchievement.cardProgress')} style={{ marginTop: 16 }}>
        <Progress
          percent={Number(achievement.achievementRate?.toFixed(1)) || 0}
          status={getProgressStatus(achievement.status)}
          strokeColor={getProgressColor(achievement.status)}
          format={(percent) => `${percent}%`}
        />
        <div style={{ marginTop: 16 }}>
          <Space>
            <Text type="secondary">{t('pages.quotaAchievement.labelStatus')}</Text>
            {getStatusTag(achievement.status)}
            <Text type="secondary">{t('pages.quotaAchievement.labelCalculatedAt')}</Text>
            <Text>{new Date(achievement.calculatedAt).toLocaleString('zh-CN')}</Text>
          </Space>
        </div>
      </Card>

      <Card title={t('pages.quotaAchievement.cardDetail')} style={{ marginTop: 16 }}>
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <div>
            <Text type="secondary">{t('pages.quotaAchievement.labelPeriod')}</Text>
            <Text>{quota.periodStart} ~ {quota.periodEnd}</Text>
          </div>
          {quota.teamName && (
            <div>
              <Text type="secondary">{t('pages.quotaAchievement.labelTeam')}</Text>
              <Text>{quota.teamName}</Text>
            </div>
          )}
          {quota.userName && (
            <div>
              <Text type="secondary">{t('pages.quotaAchievement.labelUser')}</Text>
              <Text>{quota.userName}</Text>
            </div>
          )}
          <div>
            <Text type="secondary">{t('pages.quotaAchievement.labelStatus')}</Text>
            <Tag color={quota.status === 'ACTIVE' ? 'green' : quota.status === 'DRAFT' ? 'orange' : 'default'}>
              {quota.status}
            </Tag>
          </div>
        </Space>
      </Card>
    </div>
  );
};

export default QuotaAchievementPage;
