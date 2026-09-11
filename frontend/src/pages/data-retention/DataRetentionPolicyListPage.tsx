/** 数据保留策略列表页面（080-data-retention，US1+US4）。 */

import { dataRetentionApi } from '../../services/api/dataRetentionApi';
import type { DataRetentionPolicyResponse } from '../../types/dataRetention';
import { ENTITY_TYPE_LABELS, ACTION_TYPE_LABELS, POLICY_STATUS_LABELS } from '../../types/dataRetention';
import { PlusOutlined, DeleteOutlined, EditOutlined, FileProtectOutlined } from '@ant-design/icons';
import { Button, Card, Popconfirm, Space, Table, Tag, message } from 'antd';
import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';

const DataRetentionPolicyListPage: React.FC = () => {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);
  const [data, setData] = useState<DataRetentionPolicyResponse[]>([]);

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
      width: 120,
      render: (text: string) => ENTITY_TYPE_LABELS[text as keyof typeof ENTITY_TYPE_LABELS] || text,
    },
    {
      title: '保留期限',
      dataIndex: 'retentionDays',
      width: 100,
      render: (text: number) => text ? `${text} 天` : '-',
    },
    {
      title: '归档方式',
      dataIndex: 'actionType',
      width: 100,
      render: (text: string) => ACTION_TYPE_LABELS[text as keyof typeof ACTION_TYPE_LABELS] || text,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (text: string) => {
        const color = text === 'ACTIVE' ? 'green' : 'default';
        return <Tag color={color}>{POLICY_STATUS_LABELS[text as keyof typeof POLICY_STATUS_LABELS]}</Tag>;
      },
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      width: 180,
      render: (text: string) => text ? new Date(text).toLocaleString('zh-CN') : '-',
    },
    {
      title: '操作',
      width: 200,
      render: (_: any, record: DataRetentionPolicyResponse) => [
        <a key="executions" onClick={() => navigate(`/data-retention/${record.id}/executions`)}>
          执行历史
        </a>,
        <a key="edit" onClick={() => navigate(`/data-retention/${record.id}/edit`)}>
          <EditOutlined /> 编辑
        </a>,
        <Popconfirm key="delete" title="删除此策略？" onConfirm={() => handleDelete(record.id)}>
          <a style={{ color: '#ff4d4f' }}><DeleteOutlined /> 删除</a>
        </Popconfirm>,
      ],
    },
  ];

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Card title="数据保留策略" style={{ flex: 1 }} />
        <Button icon={<FileProtectOutlined />} onClick={() => navigate('/data-retention/compliance-export')}>
          合规导出
        </Button>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/data-retention/create')}>
          创建策略
        </Button>
      </Space>

      <Table<DataRetentionPolicyResponse>
        columns={columns}
        dataSource={data}
        loading={loading}
        rowKey="id"
        pagination={{ pageSize: 10 }}
        scroll={{ x: 800 }}
      />
    </div>
  );
};

export default DataRetentionPolicyListPage;
