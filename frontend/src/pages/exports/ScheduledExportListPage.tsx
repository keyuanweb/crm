/** 定时导出任务列表页面（079-scheduled-export，US1）。 */

import { scheduledExportApi } from '../../services/api/scheduledExportApi';
import type { ScheduledExportResponse } from '../../types/scheduledExport';
import { PlusOutlined, PauseCircleOutlined, PlayCircleOutlined, DeleteOutlined } from '@ant-design/icons';
import { Button, Card, Popconfirm, Space, Table, Tag, message } from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { useAuthStore } from '../../store/authStore';

const ScheduledExportListPage: React.FC = () => {
  const { t } = useTranslation();
  const navigate = useNavigate();
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
      message.success(t('pages.scheduledExport.list.msgStatusUpdated'));
      loadList();
    } catch (error) {
      console.error(error);
      message.error(t('pages.scheduledExport.list.msgStatusUpdateFailed'));
    }
  };

  const handleDelete = async (id: number) => {
    try {
      await scheduledExportApi.delete(id);
      message.success(t('pages.scheduledExport.list.msgDeleted'));
      loadList();
    } catch (error) {
      console.error(error);
      message.error(t('pages.scheduledExport.list.msgDeleteFailed'));
    }
  };

  const columns = [
    {
      title: t('pages.scheduledExport.common.entity'),
      dataIndex: 'entityType',
      width: 100,
      render: (text: string) => entityTypeLabels[text] || text,
    },
    {
      title: t('pages.scheduledExport.common.format'),
      dataIndex: 'exportFormat',
      width: 80,
      render: (text: string) => exportFormatLabels[text] || text,
    },
    {
      title: t('pages.scheduledExport.common.status'),
      dataIndex: 'status',
      width: 100,
      render: (text: string) => {
        const color = text === 'ACTIVE' ? 'green' : text === 'SUSPENDED' ? 'orange' : 'default';
        return <Tag color={color}>{taskStatusLabels[text]}</Tag>;
      },
    },
    {
      title: t('pages.scheduledExport.common.nextExecution'),
      dataIndex: 'nextExecutionTime',
      width: 180,
      render: (text: string) => text ? new Date(text).toLocaleString('zh-CN') : '-',
    },
    {
      title: t('pages.scheduledExport.common.createdAt'),
      dataIndex: 'createdAt',
      width: 180,
      render: (text: string) => text ? new Date(text).toLocaleString('zh-CN') : '-',
    },
    {
      title: t('pages.scheduledExport.common.action'),
      width: 300,
      render: (_: unknown, record: ScheduledExportResponse) => [
        <a key="executions" onClick={() => navigate(`/exports/scheduled/${record.id}/executions`)}>
          {t('pages.scheduledExport.common.executionHistory')}
        </a>,
        record.status === 'ACTIVE' ? (
          <Popconfirm key="pause" title={t('pages.scheduledExport.list.pauseConfirm')} onConfirm={() => handleStatusChange(record.id, 'SUSPENDED')}>
            <a><PauseCircleOutlined /> {t('pages.scheduledExport.list.pause')}</a>
          </Popconfirm>
        ) : record.status === 'SUSPENDED' ? (
          <Popconfirm key="resume" title={t('pages.scheduledExport.list.resumeConfirm')} onConfirm={() => handleStatusChange(record.id, 'ACTIVE')}>
            <a><PlayCircleOutlined /> {t('pages.scheduledExport.list.resume')}</a>
          </Popconfirm>
        ) : null,
        <Popconfirm key="delete" title={t('pages.scheduledExport.list.deleteConfirm')} onConfirm={() => handleDelete(record.id)}>
          <a style={{ color: '#ff4d4f' }}><DeleteOutlined /> {t('common.button.delete')}</a>
        </Popconfirm>,
      ],
    },
  ];

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Card title={t('pages.scheduledExport.list.title')} style={{ flex: 1 }} />
        <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/exports/scheduled/create')}>
          {t('pages.scheduledExport.common.createTask')}
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
