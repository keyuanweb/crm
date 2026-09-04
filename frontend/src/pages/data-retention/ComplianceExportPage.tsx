/** 合规导出页面（080-data-retention，US3 - 合规导出）。 */

import { ENTITY_TYPE_LABELS } from '../../types/dataRetention';
import { ArrowLeftOutlined, DownloadOutlined } from '@ant-design/icons';
import { Button, Card, Form, Input, Select, Space, Typography, message } from 'antd';
import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';

const { Title } = Typography;

const { Option } = Select;

interface ExportValues {
  entityType: string;
  dateRange: string;
  exportFormat: string;
  userId: string;
}

const onFinish = async (values: ExportValues, setLoading: (v: boolean) => void) => {
  try {
    setLoading(true);
    const res = await axios.post('/api/v1/data-retention/compliance-export', null, {
      params: {
        entityType: values.entityType,
        userId: values.userId || '1',
        exportFormat: values.exportFormat,
      },
    });
    message.success('合规导出完成');
    console.log('Export file:', res.data.data.filePath);
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : '导出失败';
    message.error(msg);
  } finally {
    setLoading(false);
  }
};

const ComplianceExportPage: React.FC = () => {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/data-retention')}>
          返回
        </Button>
        <Title level={4}>合规导出（DSAR）</Title>
      </Space>

      <Card>
        <Form
          onFinish={(values) => onFinish(values, setLoading)}
          layout="vertical"
          initialValues={{
            exportFormat: 'CSV',
            userId: '1',
          }}
        >
          <Form.Item
            label="实体类型"
            name="entityType"
            rules={[{ required: true, message: '请选择实体类型' }]}
          >
            <Select placeholder="请选择实体类型">
              {Object.entries(ENTITY_TYPE_LABELS).map(([key, label]) => (
                <Option key={key} value={key}>{label}</Option>
              ))}
            </Select>
          </Form.Item>

          <Form.Item
            label="用户 ID"
            name="userId"
            rules={[{ required: true, message: '请输入用户 ID' }]}
          >
            <Input placeholder="请输入用户 ID" />
          </Form.Item>

          <Form.Item
            label="导出格式"
            name="exportFormat"
            rules={[{ required: true, message: '请选择导出格式' }]}
          >
            <Select>
              <Option value="CSV">CSV</Option>
              <Option value="XLSX">Excel</Option>
            </Select>
          </Form.Item>

          <Form.Item>
            <Space>
              <Button onClick={() => navigate('/data-retention')}>取消</Button>
              <Button type="primary" htmlType="submit" icon={<DownloadOutlined />} loading={loading}>
                导出
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Card>
    </div>
  );
};

export default ComplianceExportPage;
