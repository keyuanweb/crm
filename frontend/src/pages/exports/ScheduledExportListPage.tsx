/** 定时导出任务列表页面（079-scheduled-export，US1）。 */

import { scheduledExportApi } from '../../services/api/scheduledExportApi';
import type { ScheduledExportResponse } from '../../types/scheduledExport';
import { ENTITY_TYPE_LABELS, EXPORT_FORMAT_LABELS, TASK_STATUS_LABELS } from '../../types/scheduledExport';
import { PlusOutlined, PauseCircleOutlined, PlayCircleOutlined, DeleteOutlined } from '@ant-design/icons';
import { Button, Card, Popconfirm, Space, Table, Tag, message } from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuthStore } from '../../store/authStore';

const ScheduledExportListPage: React.FC = () => {
  const navigate = useNavigate();
  // 改造前这里写死 `useParams().id ?? 1`，而本页路由（`/exports/scheduled`）根本没有 `:id` 参数，
  // 于是**恒定请求 1 号用户**的列表。服务端已按登录身份限定范围并对不符的参数返回 403（FR-G16），
  // 故必须改传当前登录用户——写死 1 会让非 1 号用户看到 403 而非自己的任务。
  const user = useAuthStore((s) => s.user);
  const userId = user?.id;
  const [loading, setLoading] = useState(false);
  const [data, setData] = useState<ScheduledExportResponse[]>([]);

  // loadList 依赖 userId：用 useCallback 固定引用后交给 effect 依赖，避免 effect 依赖表不完整。
  const loadList = useCallback(async () => {
    // 登录信息尚未就绪时先不请求：带上 undefined 只会换来一个 400，不如等 effect 因 userId 变化重跑
    if (userId == null) return;
    setLoading(true);
    try {
      const result = await scheduledExportApi.list(userId);
      setData(result);
    } catch (error) {
      console.error(error);
    } finally {
      setLoading(false);
    }
  }, [userId]);

  useEffect(() => {
    void loadList();
  }, [loadList]);

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
      render: (_: unknown, record: ScheduledExportResponse) => [
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
