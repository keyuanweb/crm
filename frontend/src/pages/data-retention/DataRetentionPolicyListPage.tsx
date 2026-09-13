/** 数据保留策略列表页面（080-data-retention，US1+US4）。 */

import { dataRetentionApi } from '../../services/api/dataRetentionApi';
import type { DataRetentionPolicyResponse } from '../../types/dataRetention';
import { PlusOutlined, DeleteOutlined, EditOutlined, FileProtectOutlined } from '@ant-design/icons';
import { Button, Card, Popconfirm, Space, Table, Tag, message } from 'antd';
import React, { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';

const DataRetentionPolicyListPage: React.FC = () => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);
  const [data, setData] = useState<DataRetentionPolicyResponse[]>([]);

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

  useEffect(() => {
    loadList();
  }, []);

  const loadList = async () => {
    setLoading(true);
    try {
      const result = await dataRetentionApi.getAllPolicies();
      setData(result);
    } catch (error) {
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async (id: number) => {
    try {
      await dataRetentionApi.deletePolicy(id);
      message.success(t('pages.dataRetention.list.msgDeleted'));
      loadList();
    } catch (error) {
      console.error(error);
      message.error(t('pages.dataRetention.list.msgDeleteFailed'));
    }
  };

  const columns = [
    {
      title: t('pages.dataRetention.common.entity'),
      dataIndex: 'entityType',
      width: 120,
      render: (text: string) => entityTypeLabels[text] || text,
    },
    {
      title: t('pages.dataRetention.common.retentionPeriod'),
      dataIndex: 'retentionDays',
      width: 100,
      render: (text: number) => text ? t('pages.dataRetention.common.days', { count: text }) : '-',
    },
    {
      title: t('pages.dataRetention.common.actionType'),
      dataIndex: 'actionType',
      width: 100,
      render: (text: string) => actionTypeLabels[text] || text,
    },
    {
      title: t('pages.dataRetention.common.status'),
      dataIndex: 'status',
      width: 100,
      render: (text: string) => {
        const color = text === 'ACTIVE' ? 'green' : 'default';
        return <Tag color={color}>{policyStatusLabels[text]}</Tag>;
      },
    },
    {
      title: t('pages.dataRetention.common.createdAt'),
      dataIndex: 'createdAt',
      width: 180,
      render: (text: string) => text ? new Date(text).toLocaleString('zh-CN') : '-',
    },
    {
      title: t('pages.dataRetention.common.action'),
      width: 200,
      render: (_: unknown, record: DataRetentionPolicyResponse) => [
        <a key="executions" onClick={() => navigate(`/data-retention/${record.id}/executions`)}>
          {t('pages.dataRetention.common.executionHistory')}
        </a>,
        <a key="edit" onClick={() => navigate(`/data-retention/${record.id}/edit`)}>
          <EditOutlined /> {t('common.button.edit')}
        </a>,
        <Popconfirm key="delete" title={t('pages.dataRetention.list.deleteConfirm')} onConfirm={() => handleDelete(record.id)}>
          <a style={{ color: '#ff4d4f' }}><DeleteOutlined /> {t('common.button.delete')}</a>
        </Popconfirm>,
      ],
    },
  ];

  // 标题与操作按钮放在同一张 Card 上：改造前这里是一个**没有孩子的** `<Card title={…} />` 塞在 `Space` 里，
  // 渲染出来只是「一条只写着标题的空边框」贴在两个按钮左边（`style={{flex:1}}` 落在 antd 的
  // `ant-space-item` 包装层里面，也不起作用）。同页族的详情/历史页（本目录另三个页面、
  // `DepartmentListPage`）都是「Card 标题 + `extra` 放按钮」，此处照此收口。
  return (
    <Card
      title={t('pages.dataRetention.list.title')}
      extra={
        <Space>
          <Button icon={<FileProtectOutlined />} onClick={() => navigate('/data-retention/compliance-export')}>
            {t('pages.dataRetention.list.complianceExport')}
          </Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/data-retention/create')}>
            {t('pages.dataRetention.common.createPolicy')}
          </Button>
        </Space>
      }
    >
      <Table<DataRetentionPolicyResponse>
        columns={columns}
        dataSource={data}
        loading={loading}
        rowKey="id"
        pagination={{ pageSize: 10 }}
        scroll={{ x: 800 }}
      />
    </Card>
  );
};

export default DataRetentionPolicyListPage;
