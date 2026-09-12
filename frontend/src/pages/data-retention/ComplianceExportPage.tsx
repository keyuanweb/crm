/** 合规导出页面（080-data-retention，US3 - 合规导出）。 */

import { ENTITY_TYPE_LABELS } from '../../types/dataRetention';
import { apiClient, extractErrorMessage, type ApiEnvelope } from '../../services/apiClient';
import { ArrowLeftOutlined, DownloadOutlined } from '@ant-design/icons';
import { Button, Card, Form, Input, Select, Space, Typography, message } from 'antd';
import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';

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
    // 走全站客户端而非裸 axios：改造前这次调用**完全没有携带凭据**（`axios` 裸实例不带任何请求头，
    // 也没有 401 跳转），是本页无法导出的独立根因（FR-G18）。
    //
    // ⚠️ 本端点是**全站信封**（响应类型为 `ApiResponse<Map<String,String>>`），与同模块的
    // dataRetentionApi 各端点（裸响应体）**不同**——故这里的解包层次与那些方法不能互抄：
    // 这里必须 `data.data`，那里只能 `data`。类型参数写成 ApiEnvelope 就是为了让这个层次由类型系统钉住。
    const res = await apiClient.post<ApiEnvelope<{ filePath: string }>>(
      '/data-retention/compliance-export',
      null,
      {
        params: {
          entityType: values.entityType,
          userId: values.userId || '1',
          exportFormat: values.exportFormat,
        },
      },
    );
    message.success('合规导出完成');
    console.log('Export file:', res.data.data.filePath);
  } catch (err: unknown) {
    // 用全站提取器而非 `err.message`：axios 的 message 是 "Request failed with status code 403"，
    // 拿不到后端给出的具体理由（如权限不足），排查时等于没有信息。
    message.error(extractErrorMessage(err, '导出失败'));
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
