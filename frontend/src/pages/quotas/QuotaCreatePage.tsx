/** 配额创建页面（078-sales-quota，US1 - 创建年度/季度配额）。 */

import { quotaApi, type SalesQuotaRequest } from '../../services/api/quotaApi';
import { ArrowLeftOutlined } from '@ant-design/icons';
import type { Dayjs } from 'dayjs';
import { Button, Card, DatePicker, Form, InputNumber, Select, Space, Typography, message } from 'antd';
import React, { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';

const { Title } = Typography;
const { Option } = Select;

const QuotaCreatePage: React.FC = () => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [form] = Form.useForm();
  const [submitting, setSubmitting] = useState(false);

  const onFinish = async (values: {
    year: number;
    quarter?: number;
    teamId?: number;
    userId?: number;
    amount: number;
    periodRange: [Dayjs, Dayjs];
  }) => {
    const payload: SalesQuotaRequest = {
      year: values.year,
      quarter: values.quarter,
      teamId: values.teamId,
      userId: values.userId,
      amount: values.amount,
      periodStart: values.periodRange[0].format('YYYY-MM-DD'),
      periodEnd: values.periodRange[1].format('YYYY-MM-DD'),
    };
    setSubmitting(true);
    try {
      await quotaApi.create(payload);
      message.success(t('pages.quotaCreate.msgCreated'));
      navigate('/quotas');
    } catch (error) {
      console.error(error);
      message.error(t('pages.quotaCreate.msgCreateFailed'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/quotas')}>
          {t('pages.quotaCreate.btnBack')}
        </Button>
        <Title level={4}>{t('pages.quotaCreate.title')}</Title>
      </Space>

      <Card>
        <Form
          form={form}
          onFinish={onFinish}
          layout="vertical"
          initialValues={{ year: new Date().getFullYear() }}
        >
          <Form.Item
            label={t('pages.quotaCreate.formYear')}
            name="year"
            rules={[{ required: true, message: t('pages.quotaCreate.msgYearRequired') }]}
          >
            <InputNumber min={2000} max={2100} style={{ width: '100%' }} />
          </Form.Item>

          <Form.Item label={t('pages.quotaCreate.formQuarter')} name="quarter">
            <Select allowClear placeholder={t('pages.quotaCreate.phQuarter')}>
              <Option value={1}>Q1</Option>
              <Option value={2}>Q2</Option>
              <Option value={3}>Q3</Option>
              <Option value={4}>Q4</Option>
            </Select>
          </Form.Item>

          <Form.Item label={t('pages.quotaCreate.formTeamId')} name="teamId">
            <InputNumber min={1} style={{ width: '100%' }} placeholder={t('pages.quotaCreate.phTeamId')} />
          </Form.Item>

          <Form.Item label={t('pages.quotaCreate.formUserId')} name="userId">
            <InputNumber min={1} style={{ width: '100%' }} placeholder={t('pages.quotaCreate.phUserId')} />
          </Form.Item>

          <Form.Item
            label={t('pages.quotaCreate.formAmount')}
            name="amount"
            rules={[{ required: true, message: t('pages.quotaCreate.msgAmountRequired') }]}
          >
            <InputNumber min={0.01} step={0.01} style={{ width: '100%' }} />
          </Form.Item>

          <Form.Item
            label={t('pages.quotaCreate.formPeriod')}
            name="periodRange"
            rules={[{ required: true, message: t('pages.quotaCreate.msgPeriodRequired') }]}
          >
            <DatePicker.RangePicker style={{ width: '100%' }} />
          </Form.Item>

          <Form.Item>
            <Space>
              <Button onClick={() => navigate('/quotas')}>{t('common.button.cancel')}</Button>
              <Button type="primary" htmlType="submit" loading={submitting}>
                {t('pages.quotaCreate.btnCreate')}
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Card>
    </div>
  );
};

export default QuotaCreatePage;
