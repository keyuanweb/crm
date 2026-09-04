/** 定时导出任务列表页面（079-scheduled-export，US1）。 */

import { scheduledExportApi } from '../../services/api/scheduledExportApi';
import type { ScheduledExportResponse } from '../../types/scheduledExport';
import { ENTITY_TYPE_LABELS, EXPORT_FORMAT_LABELS, TASK_STATUS_LABELS } from '../../types/scheduledExport';
import { PlusOutlined, PauseCircleOutlined, PlayCircleOutlined, DeleteOutlined } from '@ant-design/icons';
import { Button, Card, Popconfirm, Space, Table, Tag, message } from 'antd';
import React, { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';



const ScheduledExportListPage: React.FC = () => {
  const navigate = useNavigate();
  const { id } = useParams<{ id: string }>();
  const userId = id ? Number(id) : 1;
  const [loading, setLoading] = useState(false);
  const [data, setData] = useState<ScheduledExportResponse[]>([]);

  React.useEffect(() => {
    loadList();
  }, [userId]);

  const loadList = async () => {
    setLoading(true);
    try {
      const result = await scheduledExportApi.list(userId);
      setData(result);
    } catch (error) {
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  const handleStatusChange = async (id: number, status: string) => {
    try {
      await scheduledExportApi.updateStatus(id, status);
      message.success('状态更新成功');
      loadList();
    } catch (error) {
      console.error(error);
      message.error('状态更新失败');
    }
  };

  const handleDelete = async (id: number) => {
    try {
      await scheduledExportApi.delete(id);
      message.success('删除成功');
      loadList();
    } catch (error) {
      console.error(error);
      message.error('删除失败');
    }
  };

  const columns = [
    {
      title: '实体',
      dataIndex: 'entityType',
      width: 100,
      render: (text: string) => ENTITY_TYPE_LABELS[text as keyof typeof ENTITY_TYPE_LABELS] || text,
    },
    {
      title: '格式',
      dataIndex: 'exportFormat',
      width: 80,
      render: (text: string) => EXPORT_FORMAT_LABELS[text as keyof typeof EXPORT_FORMAT_LABELS] || text,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (text: string) => {
        const color = text === 'ACTIVE' ? 'green' : text === 'SUSPENDED' ? 'orange' : 'default';
        return <Tag color={color}>{TASK_STATUS_LABELS[text as keyof typeof TASK_STATUS_LABELS]}</Tag>;
      },
    },
    {
      title: '下次执行',
      dataIndex: 'nextExecutionTime',
      width: 180,
      render: (text: string) => text ? new Date(text).toLocaleString('zh-CN') : '-',
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      width: 180,
      render: (text: string) => text ? new Date(text).toLocaleString('zh-CN') : '-',
    },
    {
      title: '操作',
      width: 300,
      render: (_: any, record: ScheduledExportResponse) => [
        <a key="executions" onClick={() => navigate(`/exports/scheduled/${record.id}/executions`)}>
          执行历史
        </a>,
        record.status === 'ACTIVE' ? (
          <Popconfirm key="pause" title="暂停此任务？" onConfirm={() => handleStatusChange(record.id, 'SUSPENDED')}>
            <a><PauseCircleOutlined /> 暂停</a>
          </Popconfirm>
        ) : record.status === 'SUSPENDED' ? (
          <Popconfirm key="resume" title="恢复此任务？" onConfirm={() => handleStatusChange(record.id, 'ACTIVE')}>
            <a><PlayCircleOutlined /> 恢复</a>
          </Popconfirm>
        ) : null,
        <Popconfirm key="delete" title="删除此任务？" onConfirm={() => handleDelete(record.id)}>
          <a style={{ color: '#ff4d4f' }}><DeleteOutlined /> 删除</a>
        </Popconfirm>,
      ],
    },
  ];

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Card title="定时导出订阅" style={{ flex: 1 }} />
        <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/exports/scheduled/create')}>
          创建任务
        </Button>
      </Space>

      <Table<ScheduledExportResponse>
        columns={columns}
        dataSource={data}
        loading={loading}
        rowKey="id"
        pagination={{
          pageSize: 10,
          showSizeChanger: true,
        }}
        scroll={{ x: 800 }}
      />
    </div>
  );
};

export default ScheduledExportListPage;
