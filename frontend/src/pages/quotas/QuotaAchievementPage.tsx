/** 配额达成页面（078-sales-quota，US2 - 达成率跟踪与可视化）。 */

import { quotaApi, type SalesQuotaAchievementResponse, type SalesQuotaResponse } from '../../services/api/quotaApi';
import { ArrowLeftOutlined } from '@ant-design/icons';
import { Button, Card, Progress, Row, Col, Space, Typography, Tag, Alert } from 'antd';
import React, { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';

const { Title, Text } = Typography;

const QuotaAchievementPage: React.FC = () => {
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
    if (status === 'ON_TRACK') return <Tag color="green">正常</Tag>;
    if (status === 'AT_RISK') return <Tag color="orange">预警</Tag>;
    return <Tag color="red">落后</Tag>;
  };

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/quotas')}>
          返回
        </Button>
        <Title level={4}>配额达成 - {quota.year}年 {quota.quarter ? `Q${quota.quarter}` : '年度'}</Title>
      </Space>

      {achievement.status === 'AT_RISK' && (
        <Alert
          message="达成率预警"
          description="当前达成率在 60%-80% 之间，建议关注销售进度。"
          type="warning"
          showIcon
          style={{ marginBottom: 16 }}
        />
      )}

      {achievement.status === 'BELOW_TARGET' && (
        <Alert
          message="达成率落后"
          description="当前达成率低于 60%，需要立即采取措施。"
          type="error"
          showIcon
          style={{ marginBottom: 16 }}
        />
      )}

      <Row gutter={[16, 16]}>
        <Col span={8}>
          <Card size="small" styles={{ body: { padding: '16px' } }}>
            <Text type="secondary">配额金额</Text>
            <div style={{ fontSize: 24, fontWeight: 600, marginTop: 8 }}>
              {quota.amount?.toFixed(2)} <Text type="secondary">万</Text>
            </div>
          </Card>
        </Col>
        <Col span={8}>
          <Card size="small" styles={{ body: { padding: '16px' } }}>
            <Text type="secondary">实际销售额</Text>
            <div style={{ fontSize: 24, fontWeight: 600, marginTop: 8 }}>
              {achievement.actualAmount?.toFixed(2)} <Text type="secondary">万</Text>
            </div>
          </Card>
        </Col>
        <Col span={8}>
          <Card size="small" styles={{ body: { padding: '16px' } }}>
            <Text type="secondary">达成率</Text>
            <div style={{ fontSize: 24, fontWeight: 600, marginTop: 8, color: getProgressColor(achievement.status) }}>
              {achievement.achievementRate?.toFixed(1)}%
            </div>
          </Card>
        </Col>
      </Row>

      <Card title="达成进度" style={{ marginTop: 16 }}>
        <Progress
          percent={Number(achievement.achievementRate?.toFixed(1)) || 0}
          status={getProgressStatus(achievement.status)}
          strokeColor={getProgressColor(achievement.status)}
          format={(percent) => `${percent}%`}
        />
        <div style={{ marginTop: 16 }}>
          <Space>
            <Text type="secondary">状态：</Text>
            {getStatusTag(achievement.status)}
            <Text type="secondary">计算时间：</Text>
            <Text>{new Date(achievement.calculatedAt).toLocaleString('zh-CN')}</Text>
          </Space>
        </div>
      </Card>

      <Card title="配额详情" style={{ marginTop: 16 }}>
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <div>
            <Text type="secondary">期间：</Text>
            <Text>{quota.periodStart} ~ {quota.periodEnd}</Text>
          </div>
          {quota.teamName && (
            <div>
              <Text type="secondary">团队：</Text>
              <Text>{quota.teamName}</Text>
            </div>
          )}
          {quota.userName && (
            <div>
              <Text type="secondary">销售：</Text>
              <Text>{quota.userName}</Text>
            </div>
          )}
          <div>
            <Text type="secondary">状态：</Text>
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
