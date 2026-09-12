/** 数据保留执行历史页面（080-data-retention，US2）。 */

import { dataRetentionApi } from '../../services/api/dataRetentionApi';
import type { DataRetentionExecutionResponse, DataRetentionPolicyResponse } from '../../types/dataRetention';
import { ENTITY_TYPE_LABELS, ACTION_TYPE_LABELS, POLICY_STATUS_LABELS, EXECUTION_STATUS_LABELS } from '../../types/dataRetention';
import { ArrowLeftOutlined, PlayCircleOutlined } from '@ant-design/icons';
import { Button, Card, Descriptions, List, Space, Tag, Typography, Alert, message } from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';

const { Title, Text } = Typography;

const DataRetentionExecutionHistoryPage: React.FC = () => {
  const navigate = useNavigate();
  const { id } = useParams<{ id: string }>();
  const [policy, setPolicy] = useState<DataRetentionPolicyResponse | null>(null);
  const [executions, setExecutions] = useState<DataRetentionExecutionResponse[]>([]);
  const [loading, setLoading] = useState(false);

  // loadDetail 依赖 id：用 useCallback 固定引用后由 effect 依赖它，
  // 这样「id 变化时重新加载」的语义由 hooks 自身保证，而不是靠 effect 少写一个依赖项。
  const loadDetail = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    try {
      const [policyData, executionsData] = await Promise.all([
        dataRetentionApi.getPolicy(Number(id)),
        dataRetentionApi.getExecutions(Number(id)),
      ]);
      setPolicy(policyData);
      setExecutions(executionsData);
    } catch (error) {
      console.error(error);
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    void loadDetail();
  }, [loadDetail]);

  const handleExecute = async () => {
    if (!id) return;
    try {
      await dataRetentionApi.executeArchival();
      message.success('归档执行成功');
      loadDetail();
    } catch (error) {
      console.error(error);
      message.error('归档执行失败');
    }
  };

  if (!policy || loading) {
    return <div>Loading...</div>;
  }

  const getStatusColor = (status: string) => {
    if (status === 'SUCCESS') return 'green';
    if (status === 'FAILED') return 'red';
    return 'orange';
  };

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/data-retention')}>
          返回
        </Button>
        <Title level={4}>执行历史 - {ENTITY_TYPE_LABELS[policy.entityType as keyof typeof ENTITY_TYPE_LABELS]}</Title>
        <Button type="primary" icon={<PlayCircleOutlined />} onClick={handleExecute}>
          立即执行归档
        </Button>
      </Space>

      <Card title="策略信息" style={{ marginBottom: 16 }}>
        <Descriptions bordered column={2} size="small">
          <Descriptions.Item label="实体">{ENTITY_TYPE_LABELS[policy.entityType as keyof typeof ENTITY_TYPE_LABELS]}</Descriptions.Item>
          <Descriptions.Item label="保留期限">{policy.retentionDays} 天</Descriptions.Item>
          <Descriptions.Item label="归档方式">{ACTION_TYPE_LABELS[policy.actionType as keyof typeof ACTION_TYPE_LABELS]}</Descriptions.Item>
          <Descriptions.Item label="状态">
            <Tag color={policy.status === 'ACTIVE' ? 'green' : 'default'}>
              {POLICY_STATUS_LABELS[policy.status as keyof typeof POLICY_STATUS_LABELS]}
            </Tag>
          </Descriptions.Item>
        </Descriptions>
      </Card>

      <Card title={`执行历史（共 ${executions.length} 次`}>
        <List<DataRetentionExecutionResponse>
          dataSource={executions}
          locale={{ emptyText: '暂无执行历史' }}
          renderItem={(item) => (
            <List.Item>
              <Space style={{ width: '100%', justifyContent: 'space-between' }}>
                <Space>
                  <Tag color={getStatusColor(item.status)}>{EXECUTION_STATUS_LABELS[item.status as keyof typeof EXECUTION_STATUS_LABELS]}</Tag>
                  <Text>执行时间：{new Date(item.executedAt).toLocaleString('zh-CN')}</Text>
                  {item.processedCount !== null && item.processedCount !== undefined && (
                    <Text>处理数量：{item.processedCount}</Text>
                  )}
                </Space>
                {item.errorMessage && (
                  <Alert message="错误" description={item.errorMessage} type="error" showIcon style={{ maxWidth: 300 }} />
                )}
              </Space>
            </List.Item>
          )}
        />
      </Card>
    </div>
  );
};

export default DataRetentionExecutionHistoryPage;
