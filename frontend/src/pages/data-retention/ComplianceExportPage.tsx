/** 合规导出页面（080-data-retention，US3 - 合规导出）。 */

import { apiClient, extractErrorMessage, type ApiEnvelope } from '../../services/apiClient';
import { ArrowLeftOutlined, DownloadOutlined } from '@ant-design/icons';
import { Button, Card, Form, Input, Select, Space, Typography, message } from 'antd';
import React, { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';

const { Title } = Typography;

const { Option } = Select;

interface ExportValues {
  entityType: string;
  dateRange: string;
  exportFormat: string;
  userId: string;
}

// t 由调用方传入：模块级函数拿不到 hook（与 CustomObjectRecordPage 的 renderFieldInput(f, t) 同例）。
const onFinish = async (
  values: ExportValues,
  setLoading: (v: boolean) => void,
  t: (key: string, params?: Record<string, unknown>) => string,
) => {
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
    message.success(t('pages.dataRetention.complianceExport.msgSuccess'));
    console.log('Export file:', res.data.data.filePath);
  } catch (err: unknown) {
    // 用全站提取器而非 `err.message`：axios 的 message 是 "Request failed with status code 403"，
    // 拿不到后端给出的具体理由（如权限不足），排查时等于没有信息。
    message.error(extractErrorMessage(err, t('pages.dataRetention.complianceExport.msgFailed')));
  } finally {
    setLoading(false);
  }
};

const ComplianceExportPage: React.FC = () => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);

  const entityTypeLabels: Record<string, string> = {
    CUSTOMER: t('pages.dataRetention.common.entityTypeLabels.CUSTOMER'),
    OPPORTUNITY: t('pages.dataRetention.common.entityTypeLabels.OPPORTUNITY'),
    CONTRACT: t('pages.dataRetention.common.entityTypeLabels.CONTRACT'),
    ORDER: t('pages.dataRetention.common.entityTypeLabels.ORDER'),
    AUDIT_LOG: t('pages.dataRetention.common.entityTypeLabels.AUDIT_LOG'),
  };

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/data-retention')}>
          {t('pages.dataRetention.common.back')}
        </Button>
        <Title level={4}>{t('pages.dataRetention.complianceExport.title')}</Title>
      </Space>

      <Card>
        <Form
          onFinish={(values) => onFinish(values, setLoading, t)}
          layout="vertical"
          initialValues={{
            exportFormat: 'CSV',
            userId: '1',
          }}
        >
          <Form.Item
            label={t('pages.dataRetention.common.entityType')}
            name="entityType"
            rules={[{ required: true, message: t('pages.dataRetention.common.selectEntityType') }]}
          >
            <Select placeholder={t('pages.dataRetention.common.selectEntityType')}>
              {Object.entries(entityTypeLabels).map(([key, label]) => (
                <Option key={key} value={key}>{label}</Option>
              ))}
            </Select>
          </Form.Item>

          <Form.Item
            label={t('pages.dataRetention.complianceExport.userId')}
            name="userId"
            rules={[{ required: true, message: t('pages.dataRetention.complianceExport.inputUserId') }]}
          >
            <Input placeholder={t('pages.dataRetention.complianceExport.inputUserId')} />
          </Form.Item>

          <Form.Item
            label={t('pages.dataRetention.complianceExport.exportFormat')}
            name="exportFormat"
            rules={[{ required: true, message: t('pages.dataRetention.complianceExport.selectExportFormat') }]}
          >
            <Select>
              <Option value="CSV">CSV</Option>
              <Option value="XLSX">Excel</Option>
            </Select>
          </Form.Item>

          <Form.Item>
            <Space>
              <Button onClick={() => navigate('/data-retention')}>{t('common.button.cancel')}</Button>
              <Button type="primary" htmlType="submit" icon={<DownloadOutlined />} loading={loading}>
                {t('common.button.export')}
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Card>
    </div>
  );
};

export default ComplianceExportPage;
