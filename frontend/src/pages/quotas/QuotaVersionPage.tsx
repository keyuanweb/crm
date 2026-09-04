/** 配额版本历史页面（078-sales-quota，US3 - 配额调整与版本管理）。 */

import { quotaApi, type SalesQuotaResponse } from '../../services/api/quotaApi';
import { ArrowLeftOutlined } from '@ant-design/icons';
import { Button, Card, List, Space, Typography, Tag } from 'antd';
import React, { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';

const { Title, Text } = Typography;

interface VersionRecord {
  id: number;
  quotaId: number;
  oldAmount: number;
  newAmount: number;
  changedBy: number;
  changedAt: string;
  changeReason: string;
  versionNumber: number;
}

const QuotaVersionPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [quota, setQuota] = useState<SalesQuotaResponse | null>(null);
  const [versions, setVersions] = useState<VersionRecord[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (id) {
      setLoading(true);
      Promise.all([quotaApi.detail(Number(id)), quotaApi.getVersions(Number(id))])
        .then(([quotaData, versionsData]) => {
          setQuota(quotaData);
          setVersions(versionsData);
        })
        .catch((err) => {
          console.error(err);
        })
        .finally(() => setLoading(false));
    }
  }, [id]);

  if (!quota || loading) {
    return <div>Loading...</div>;
  }

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/quotas')}>
          返回
        </Button>
        <Title level={4}>版本历史 - {quota.year}年 {quota.quarter ? `Q${quota.quarter}` : '年度'}</Title>
      </Space>

      <Card title="当前配额" style={{ marginBottom: 16 }}>
        <Space size="large">
          <div>
            <Text type="secondary">当前金额：</Text>
            <Text strong style={{ fontSize: 18 }}>{quota.amount?.toFixed(2)} 万</Text>
          </div>
          <div>
            <Text type="secondary">状态：</Text>
            <Tag color={quota.status === 'ACTIVE' ? 'green' : quota.status === 'DRAFT' ? 'orange' : 'default'}>
              {quota.status}
            </Tag>
          </div>
        </Space>
      </Card>

      <Card title={`版本历史（共 ${versions.length} 个版本`}>
        <List<VersionRecord>
          dataSource={versions}
          locale={{ emptyText: '暂无版本历史' }}
          renderItem={(item) => (
            <List.Item>
              <div style={{ display: 'flex', width: '100%', justifyContent: 'space-between' }}>
                <Space>
                  <Tag color="blue">V{item.versionNumber}</Tag>
                  <Text>
                    <Text type="secondary">{item.oldAmount.toFixed(2)} 万</Text>
                    <span style={{ margin: '0 8px', color: '#8c8c8c' }}>→</span>
                    <Text strong style={{ color: '#1890ff' }}>{item.newAmount.toFixed(2)} 万</Text>
                  </Text>
                </Space>
                <Space direction="vertical" size={0}>
                  <Text type="secondary">{new Date(item.changedAt).toLocaleString('zh-CN')}</Text>
                  {item.changeReason && <Text style={{ fontSize: 12 }}>{item.changeReason}</Text>}
                </Space>
               </div>
            </List.Item>
          )}
        />
      </Card>
    </div>
  );
};

export default QuotaVersionPage;
