/** 数据保留执行历史页面（080-data-retention，US2）。 */

import { dataRetentionApi } from '../../services/api/dataRetentionApi';
import type { DataRetentionExecutionResponse, DataRetentionPolicyResponse } from '../../types/dataRetention';
import { ArrowLeftOutlined, PlayCircleOutlined } from '@ant-design/icons';
import { Button, Card, Descriptions, List, Space, Tag, Typography, Alert, message } from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate, useParams } from 'react-router-dom';
import { usePerms } from '../../hooks/usePerms';
import { PERMS } from '../../constants/permissions';

const { Title, Text } = Typography;

const DataRetentionExecutionHistoryPage: React.FC = () => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { id } = useParams<{ id: string }>();
  const [policy, setPolicy] = useState<DataRetentionPolicyResponse | null>(null);
  const [executions, setExecutions] = useState<DataRetentionExecutionResponse[]>([]);
  const [loading, setLoading] = useState(false);
  // 立即执行：POST /data-retention/execute 挂的是 retention:execute（DataRetentionPolicyController.java:78-79）。
  // 「返回」只 navigate、不发请求，不收口。
  const can = usePerms([PERMS.retentionExecute]);

  const entityTypeLabels: Record<string, string> = {
    CUSTOMER: t('pages.dataRetention.common.entityTypeLabels.CUSTOMER'),
    OPPORTUNITY: t('pages.dataRetention.common.entityTypeLabels.OPPORTUNITY'),
    CONTRACT: t('pages.dataRetention.common.entityTypeLabels.CONTRACT'),
    ORDER: t('pages.dataRetention.common.entityTypeLabels.ORDER'),
    AUDIT_LOG: t('pages.dataRetention.common.entityTypeLabels.AUDIT_LOG'),
  };
  const actionTypeLabels: Record<string, string> = {
    ARCHIVE: t('pages.dataRetention.common.actionTypeLabels.ARCHIVE'),
    DELETE: t('pages.dataRetention.common.actionTypeLabels.DELETE'),
  };
  const policyStatusLabels: Record<string, string> = {
    ACTIVE: t('pages.dataRetention.common.policyStatusLabels.ACTIVE'),
    INACTIVE: t('pages.dataRetention.common.policyStatusLabels.INACTIVE'),
  };
  const executionStatusLabels: Record<string, string> = {
    SUCCESS: t('pages.dataRetention.common.executionStatusLabels.SUCCESS'),
    FAILED: t('pages.dataRetention.common.executionStatusLabels.FAILED'),
    PARTIAL: t('pages.dataRetention.common.executionStatusLabels.PARTIAL'),
  };

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
      message.success(t('pages.dataRetention.history.msgExecuteSuccess'));
      loadDetail();
    } catch (error) {
      console.error(error);
      message.error(t('pages.dataRetention.history.msgExecuteFailed'));
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
          {t('pages.dataRetention.common.back')}
        </Button>
        <Title level={4}>{t('pages.dataRetention.common.historyTitle', { entity: entityTypeLabels[policy.entityType] ?? policy.entityType })}</Title>
        {can[PERMS.retentionExecute] && (
          <Button type="primary" icon={<PlayCircleOutlined />} onClick={handleExecute}>
            {t('pages.dataRetention.history.executeNow')}
          </Button>
        )}
      </Space>

      <Card title={t('pages.dataRetention.history.policyInfo')} style={{ marginBottom: 16 }}>
        <Descriptions bordered column={2} size="small">
          <Descriptions.Item label={t('pages.dataRetention.common.entity')}>{entityTypeLabels[policy.entityType] ?? policy.entityType}</Descriptions.Item>
          <Descriptions.Item label={t('pages.dataRetention.common.retentionPeriod')}>{t('pages.dataRetention.common.days', { count: policy.retentionDays })}</Descriptions.Item>
          <Descriptions.Item label={t('pages.dataRetention.common.actionType')}>{actionTypeLabels[policy.actionType] ?? policy.actionType}</Descriptions.Item>
          <Descriptions.Item label={t('pages.dataRetention.common.status')}>
            <Tag color={policy.status === 'ACTIVE' ? 'green' : 'default'}>
              {policyStatusLabels[policy.status]}
            </Tag>
          </Descriptions.Item>
        </Descriptions>
      </Card>

      <Card title={t('pages.dataRetention.common.historyCount', { count: executions.length })}>
        <List<DataRetentionExecutionResponse>
          dataSource={executions}
          locale={{ emptyText: t('pages.dataRetention.common.noExecutions') }}
          renderItem={(item) => (
            <List.Item>
              <Space style={{ width: '100%', justifyContent: 'space-between' }}>
                <Space>
                  <Tag color={getStatusColor(item.status)}>{executionStatusLabels[item.status]}</Tag>
                  <Text>{t('pages.dataRetention.common.executedAtValue', { time: new Date(item.executedAt).toLocaleString('zh-CN') })}</Text>
                  {item.processedCount !== null && item.processedCount !== undefined && (
                    <Text>{t('pages.dataRetention.common.processedCountValue', { count: item.processedCount })}</Text>
                  )}
                </Space>
                {item.errorMessage && (
                  <Alert message={t('pages.dataRetention.common.error')} description={item.errorMessage} type="error" showIcon style={{ maxWidth: 300 }} />
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
