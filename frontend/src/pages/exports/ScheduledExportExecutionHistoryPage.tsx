/** 定时导出执行历史页面（079-scheduled-export，US3）。 */

import { scheduledExportApi } from '../../services/api/scheduledExportApi';
import type { ScheduledExportExecutionResponse, ScheduledExportResponse } from '../../types/scheduledExport';
import { ENTITY_TYPE_LABELS, EXPORT_FORMAT_LABELS, EXECUTION_STATUS_LABELS, TASK_STATUS_LABELS } from '../../types/scheduledExport';
import { ArrowLeftOutlined, ReloadOutlined } from '@ant-design/icons';
import { Button, Card, Descriptions, List, Space, Tag, Typography, Alert, message } from 'antd';
import React, { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';

const { Title, Text } = Typography;

const ScheduledExportExecutionHistoryPage: React.FC = () => {
  const navigate = useNavigate();
  const { id } = useParams<{ id: string }>();
  const [task, setTask] = useState<ScheduledExportResponse | null>(null);
  const [executions, setExecutions] = useState<ScheduledExportExecutionResponse[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (id) {
      loadDetail();
    }
  }, [id]);

  const loadDetail = async () => {
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
  };

  const handleExecuteNow = async () => {
    if (!id) return;
    try {
      await scheduledExportApi.executeNow(Number(id));
      message.success('立即执行成功');
      loadDetail();
    } catch (error) {
      console.error(error);
      message.error('立即执行失败');
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
          返回
        </Button>
        <Title level={4}>执行历史 - {ENTITY_TYPE_LABELS[task.entityType as keyof typeof ENTITY_TYPE_LABELS]}</Title>
        <Button icon={<ReloadOutlined />} onClick={loadDetail}>
          刷新
        </Button>
        <Button type="primary" icon={<ReloadOutlined />} onClick={handleExecuteNow}>
          立即执行
        </Button>
      </Space>

      <Card title="任务信息" style={{ marginBottom: 16 }}>
        <Descriptions bordered column={2} size="small">
          <Descriptions.Item label="实体">{ENTITY_TYPE_LABELS[task.entityType as keyof typeof ENTITY_TYPE_LABELS]}</Descriptions.Item>
          <Descriptions.Item label="格式">{EXPORT_FORMAT_LABELS[task.exportFormat as keyof typeof EXPORT_FORMAT_LABELS]}</Descriptions.Item>
          <Descriptions.Item label="状态">
            <Tag color={task.status === 'ACTIVE' ? 'green' : task.status === 'SUSPENDED' ? 'orange' : 'default'}>
              {TASK_STATUS_LABELS[task.status as keyof typeof TASK_STATUS_LABELS]}
            </Tag>
          </Descriptions.Item>
          <Descriptions.Item label="下次执行">{task.nextExecutionTime ? new Date(task.nextExecutionTime).toLocaleString('zh-CN') : '-'}</Descriptions.Item>
          <Descriptions.Item label="Cron" span={2}>{task.cronExpression}</Descriptions.Item>
        </Descriptions>
      </Card>

      <Card title={`执行历史（共 ${executions.length} 次`}>
        <List<ScheduledExportExecutionResponse>
          dataSource={executions}
          locale={{ emptyText: '暂无执行历史' }}
          renderItem={(item) => (
            <List.Item>
              <div style={{ display: 'flex', width: '100%', justifyContent: 'space-between' }}>
                <Space>
                  <Tag color={getStatusColor(item.status)}>{EXECUTION_STATUS_LABELS[item.status as keyof typeof EXECUTION_STATUS_LABELS]}</Tag>
                  <Text>执行时间：{new Date(item.executedAt).toLocaleString('zh-CN')}</Text>
                  {item.rowCount !== null && item.rowCount !== undefined && (
                    <Text>行数：{item.rowCount}</Text>
                  )}
                  {item.fileSize !== null && item.fileSize !== undefined && (
                    <Text>大小：{(item.fileSize / 1024).toFixed(2)} KB</Text>
                  )}
                </Space>
                {item.errorMessage && (
                  <Alert message="错误" description={item.errorMessage} type="error" showIcon style={{ maxWidth: 300 }} />
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
