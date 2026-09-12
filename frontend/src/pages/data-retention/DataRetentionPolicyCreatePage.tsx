/** 数据保留策略创建页面（080-data-retention，US1）。 */

import { dataRetentionApi } from '../../services/api/dataRetentionApi';
import type { DataRetentionPolicyRequest } from '../../types/dataRetention';
import { ArrowLeftOutlined } from '@ant-design/icons';
import { Button, Card, Form, Input, Select, Space, Typography } from 'antd';
import React, { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';

const { Title } = Typography;

const { Option } = Select;

const DataRetentionPolicyCreatePage: React.FC = () => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);

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

  const onFinish = async (values: DataRetentionPolicyRequest) => {
    setLoading(true);
    try {
      await dataRetentionApi.createPolicy(values);
      navigate('/data-retention');
    } catch (error) {
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/data-retention')}>
          {t('pages.dataRetention.common.back')}
        </Button>
        <Title level={4}>{t('pages.dataRetention.create.title')}</Title>
      </Space>

      <Card>
        <Form<DataRetentionPolicyRequest>
          form={form}
          onFinish={onFinish}
          layout="vertical"
          initialValues={{
            actionType: 'ARCHIVE',
          }}
        >
          <Form.Item<DataRetentionPolicyRequest>
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

          <Form.Item<DataRetentionPolicyRequest>
            label={t('pages.dataRetention.common.retentionDaysLabel')}
            name="retentionDays"
            rules={[{ required: true, message: t('pages.dataRetention.common.requiredRetentionDays') }]}
          >
            <Input type="number" min={1} placeholder={t('pages.dataRetention.common.inputRetentionDays')} />
          </Form.Item>

          <Form.Item<DataRetentionPolicyRequest>
            label={t('pages.dataRetention.common.actionType')}
            name="actionType"
            rules={[{ required: true, message: t('pages.dataRetention.common.selectActionType') }]}
          >
            <Select>
              {Object.entries(actionTypeLabels).map(([key, label]) => (
                <Option key={key} value={key}>{label}</Option>
              ))}
            </Select>
          </Form.Item>

          <Form.Item>
            <Space>
              <Button onClick={() => navigate('/data-retention')}>{t('common.button.cancel')}</Button>
              <Button type="primary" htmlType="submit" loading={loading}>
                {t('pages.dataRetention.common.createPolicy')}
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Card>
    </div>
  );
};

export default DataRetentionPolicyCreatePage;
