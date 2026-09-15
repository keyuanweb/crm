/** 数据保留策略编辑页面（080-data-retention，US4 - 编辑策略）。 */

import { dataRetentionApi } from '../../services/api/dataRetentionApi';
import type { DataRetentionPolicyRequest } from '../../types/dataRetention';
import { ArrowLeftOutlined } from '@ant-design/icons';
import { Button, Card, Form, Input, Select, Space, Typography, message } from 'antd';
import React, { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate, useParams } from 'react-router-dom';
import { FormGrid, VERTICAL_MIN_ITEM_WIDTH } from '../../components/ui';

const { Title } = Typography;

const { Option } = Select;

const DataRetentionPolicyEditPage: React.FC = () => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { id } = useParams<{ id: string }>();
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);

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

  useEffect(() => {
    if (!id) return;
    let cancelled = false;
    const load = async () => {
      setLoading(true);
      try {
        const policy = await dataRetentionApi.getPolicy(Number(id));
        if (cancelled) return;
        form.setFieldsValue({
          entityType: policy.entityType,
          retentionDays: policy.retentionDays,
          actionType: policy.actionType,
        });
      } catch (error) {
        console.error(error);
        message.error(t('pages.dataRetention.edit.loadFailed'));
      } finally {
        if (!cancelled) setLoading(false);
      }
    };
    load();
    return () => {
      cancelled = true;
    };
  }, [id, form, t]);

  const onFinish = async (values: DataRetentionPolicyRequest) => {
    if (!id) return;
    setSubmitting(true);
    try {
      await dataRetentionApi.updatePolicy(Number(id), values);
      message.success(t('pages.dataRetention.edit.msgUpdated'));
      navigate('/data-retention');
    } catch (error) {
      console.error(error);
      message.error(t('pages.dataRetention.edit.msgUpdateFailed'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/data-retention')}>
          {t('pages.dataRetention.common.back')}
        </Button>
        <Title level={4}>{t('pages.dataRetention.edit.title')}</Title>
      </Space>

      <Card loading={loading}>
        <Form<DataRetentionPolicyRequest>
          form={form}
          onFinish={onFinish}
          layout="vertical"
        >
          {/* 页面级容器约 980px，上限 3（088 T043 裁决；理由见 FormGrid 的 maxCols 注释）。 */}
          <FormGrid minItemWidth={VERTICAL_MIN_ITEM_WIDTH} maxCols={3}>
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
          </FormGrid>

          <Form.Item>
            <Space>
              <Button onClick={() => navigate('/data-retention')}>{t('common.button.cancel')}</Button>
              <Button type="primary" htmlType="submit" loading={submitting}>
                {t('pages.dataRetention.edit.submit')}
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Card>
    </div>
  );
};

export default DataRetentionPolicyEditPage;
