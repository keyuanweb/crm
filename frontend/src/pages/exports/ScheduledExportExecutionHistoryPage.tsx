/** 定时导出执行历史页面（079-scheduled-export，US3）。 */

import { scheduledExportApi } from '../../services/api/scheduledExportApi';
import type { ScheduledExportExecutionResponse, ScheduledExportResponse } from '../../types/scheduledExport';
import { ArrowLeftOutlined, ReloadOutlined } from '@ant-design/icons';
import { Button, Card, Descriptions, List, Space, Tag, Typography, Alert, message } from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate, useParams } from 'react-router-dom';

const { Title, Text } = Typography;

const ScheduledExportExecutionHistoryPage: React.FC = () => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { id } = useParams<{ id: string }>();
  const [task, setTask] = useState<ScheduledExportResponse | null>(null);
  const [executions, setExecutions] = useState<ScheduledExportExecutionResponse[]>([]);
  const [loading, setLoading] = useState(false);

  const entityTypeLabels: Record<string, string> = {
    CUSTOMER: t('pages.scheduledExport.common.entityTypeLabels.CUSTOMER'),
    OPPORTUNITY: t('pages.scheduledExport.common.entityTypeLabels.OPPORTUNITY'),
    CONTRACT: t('pages.scheduledExport.common.entityTypeLabels.CONTRACT'),
    ORDER: t('pages.scheduledExport.common.entityTypeLabels.ORDER'),
    INVOICE: t('pages.scheduledExport.common.entityTypeLabels.INVOICE'),
  };
  const exportFormatLabels: Record<string, string> = {
    CSV: t('pages.scheduledExport.common.exportFormatLabels.CSV'),
    XLSX: t('pages.scheduledExport.common.exportFormatLabels.XLSX'),
  };
  const taskStatusLabels: Record<string, string> = {
    ACTIVE: t('pages.scheduledExport.common.taskStatusLabels.ACTIVE'),
    SUSPENDED: t('pages.scheduledExport.common.taskStatusLabels.SUSPENDED'),
    DELETED: t('pages.scheduledExport.common.taskStatusLabels.DELETED'),
  };
  const executionStatusLabels: Record<string, string> = {
    SUCCESS: t('pages.scheduledExport.common.executionStatusLabels.SUCCESS'),
    FAILED: t('pages.scheduledExport.common.executionStatusLabels.FAILED'),
    EMAIL_SENT: t('pages.scheduledExport.common.executionStatusLabels.EMAIL_SENT'),
    EMAIL_FAILED: t('pages.scheduledExport.common.executionStatusLabels.EMAIL_FAILED'),
  };

  // 同 DataRetentionExecutionHistoryPage：loadDetail 依赖 id，用 useCallback 固定引用后交给 effect 依赖。
  const loadDetail = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    try {
      const [taskData, executionsData] = await Promise.all([
        scheduledExportApi.detail(Number(id)),
        scheduledExportApi.getExecutions(Number(id)),
      ]);
      setTask(taskData);
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

  const handleExecuteNow = async () => {
    if (!id) return;
    try {
      await scheduledExportApi.executeNow(Number(id));
      message.success(t('pages.scheduledExport.history.msgExecuteSuccess'));
      loadDetail();
    } catch (error) {
      console.error(error);
      message.error(t('pages.scheduledExport.history.msgExecuteFailed'));
    }
  };

  if (!task || loading) {
    return <div>Loading...</div>;
  }

  const getStatusColor = (status: string) => {
    if (status === 'SUCCESS' || status === 'EMAIL_SENT') return 'green';
    if (status === 'FAILED' || status === 'EMAIL_FAILED') return 'red';
    return 'default';
  };

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/exports/scheduled')}>
          {t('pages.scheduledExport.common.back')}
        </Button>
        <Title level={4}>{t('pages.scheduledExport.common.historyTitle', { entity: entityTypeLabels[task.entityType] ?? task.entityType })}</Title>
        <Button icon={<ReloadOutlined />} onClick={loadDetail}>
          {t('pages.scheduledExport.history.refresh')}
        </Button>
        <Button type="primary" icon={<ReloadOutlined />} onClick={handleExecuteNow}>
          {t('pages.scheduledExport.history.executeNow')}
        </Button>
      </Space>

      <Card title={t('pages.scheduledExport.history.taskInfo')} style={{ marginBottom: 16 }}>
        <Descriptions bordered column={2} size="small">
          <Descriptions.Item label={t('pages.scheduledExport.common.entity')}>{entityTypeLabels[task.entityType] ?? task.entityType}</Descriptions.Item>
          <Descriptions.Item label={t('pages.scheduledExport.common.format')}>{exportFormatLabels[task.exportFormat] ?? task.exportFormat}</Descriptions.Item>
          <Descriptions.Item label={t('pages.scheduledExport.common.status')}>
            <Tag color={task.status === 'ACTIVE' ? 'green' : task.status === 'SUSPENDED' ? 'orange' : 'default'}>
              {taskStatusLabels[task.status]}
            </Tag>
          </Descriptions.Item>
          <Descriptions.Item label={t('pages.scheduledExport.common.nextExecution')}>{task.nextExecutionTime ? new Date(task.nextExecutionTime).toLocaleString('zh-CN') : '-'}</Descriptions.Item>
          <Descriptions.Item label="Cron" span={2}>{task.cronExpression}</Descriptions.Item>
        </Descriptions>
      </Card>

      <Card title={t('pages.scheduledExport.common.historyCount', { count: executions.length })}>
        <List<ScheduledExportExecutionResponse>
          dataSource={executions}
          locale={{ emptyText: t('pages.scheduledExport.common.noExecutions') }}
          renderItem={(item) => (
            <List.Item>
              <div style={{ display: 'flex', width: '100%', justifyContent: 'space-between' }}>
                <Space>
                  <Tag color={getStatusColor(item.status)}>{executionStatusLabels[item.status]}</Tag>
                  <Text>{t('pages.scheduledExport.common.executedAtValue', { time: new Date(item.executedAt).toLocaleString('zh-CN') })}</Text>
                  {item.rowCount !== null && item.rowCount !== undefined && (
                    <Text>{t('pages.scheduledExport.common.rowCountValue', { count: item.rowCount })}</Text>
                  )}
                  {item.fileSize !== null && item.fileSize !== undefined && (
                    <Text>{t('pages.scheduledExport.common.fileSizeValue', { size: (item.fileSize / 1024).toFixed(2) })}</Text>
                  )}
                </Space>
                {item.errorMessage && (
                  <Alert message={t('pages.scheduledExport.common.error')} description={item.errorMessage} type="error" showIcon style={{ maxWidth: 300 }} />
                )}
               </div>
            </List.Item>
          )}
        />
      </Card>
    </div>
  );
};

export default ScheduledExportExecutionHistoryPage;
