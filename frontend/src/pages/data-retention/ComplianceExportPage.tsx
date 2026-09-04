/** 合规导出页面（080-data-retention，US3 - 合规导出）。 */

import { ENTITY_TYPE_LABELS } from '../../types/dataRetention';
import { ArrowLeftOutlined, DownloadOutlined } from '@ant-design/icons';
import { Button, Card, Form, Input, Select, Space, Typography, message } from 'antd';
import React from 'react';
import { useNavigate } from 'react-router-dom';

const { Title } = Typography;

const { Option } = Select;

// eslint-disable-next-line @typescript-eslint/no-explicit-any
const onFinish = (_values: any) => {
  // TODO: 实现合规导出逻辑
  message.info('合规导出功能开发中');
};

const ComplianceExportPage: React.FC = () => {
  const navigate = useNavigate();

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
          onFinish={onFinish}
          layout="vertical"
          initialValues={{
            exportFormat: 'CSV',
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
            label="时间范围"
            name="dateRange"
            rules={[{ required: true, message: '请选择时间范围' }]}
          >
            <Input placeholder="请选择时间范围" />
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
              <Button type="primary" htmlType="submit" icon={<DownloadOutlined />}>
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
