/** 定时导出任务创建页面（079-scheduled-export，US1）。 */

import { scheduledExportApi } from '../../services/api/scheduledExportApi';
import type { ScheduledExportRequest } from '../../types/scheduledExport';
import { ArrowLeftOutlined } from '@ant-design/icons';
import { Button, Card, Form, Input, Select, Space, Typography, Alert } from 'antd';
import React, { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';

const { Title } = Typography;

const { Option } = Select;

// Cron 表达式生成器
const generateCron = (type: string, hour: number, minute: number, dayOfWeek?: number, dayOfMonth?: number): string => {
  if (type === 'daily') {
    return `${minute} ${hour} * * *`;
  }
  if (type === 'weekly') {
    return `${minute} ${hour} * * ${dayOfWeek || 1}`;
  }
  if (type === 'monthly') {
    return `${minute} ${hour} ${dayOfMonth || 1} * *`;
  }
  return `${minute} ${hour} * * *`;
};

const ScheduledExportCreatePage: React.FC = () => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [cronPreview, setCronPreview] = useState<string>('');

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

  const onFinish = async (values: ScheduledExportRequest) => {
    setLoading(true);
    try {
      await scheduledExportApi.create(values);
      navigate('/exports/scheduled');
    } catch (error) {
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  const handlePeriodChange = (value: string) => {
    const hour = form.getFieldValue('hour') || 9;
    const minute = form.getFieldValue('minute') || 0;
    const cron = generateCron(value, hour, minute);
    setCronPreview(cron);
  };

  const handleTimeChange = () => {
    const periodType = form.getFieldValue('periodType') || 'daily';
    const hour = form.getFieldValue('hour') || 9;
    const minute = form.getFieldValue('minute') || 0;
    const cron = generateCron(periodType, hour, minute);
    setCronPreview(cron);
  };

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/exports/scheduled')}>
          {t('pages.scheduledExport.common.back')}
        </Button>
        <Title level={4}>{t('pages.scheduledExport.create.title')}</Title>
      </Space>

      <Card>
        <Form<ScheduledExportRequest>
          form={form}
          onFinish={onFinish}
          layout="vertical"
          initialValues={{
            exportFormat: 'CSV',
            periodType: 'daily',
            hour: 9,
            minute: 0,
            dayOfWeek: 1,
            dayOfMonth: 1,
          }}
        >
          <Form.Item<ScheduledExportRequest>
            label={t('pages.scheduledExport.create.entityLabel')}
            name="entityType"
            rules={[{ required: true, message: t('pages.scheduledExport.create.selectEntity') }]}
          >
            <Select placeholder={t('pages.scheduledExport.create.selectEntity')}>
              {Object.entries(entityTypeLabels).map(([key, label]) => (
                <Option key={key} value={key}>{label}</Option>
              ))}
            </Select>
          </Form.Item>

          <Form.Item<ScheduledExportRequest>
            label={t('pages.scheduledExport.create.filterConditions')}
            name="filterConditions"
            extra={t('pages.scheduledExport.create.filterConditionsExtra')}
          >
            <Input.TextArea rows={3} placeholder='{"status": "active"}' />
          </Form.Item>

          <Form.Item<ScheduledExportRequest>
            label={t('pages.scheduledExport.create.exportFormat')}
            name="exportFormat"
            rules={[{ required: true, message: t('pages.scheduledExport.create.selectExportFormat') }]}
          >
            <Select>
              {Object.entries(exportFormatLabels).map(([key, label]) => (
                <Option key={key} value={key}>{label}</Option>
              ))}
            </Select>
          </Form.Item>

          <Form.Item<ScheduledExportRequest>
            label={t('pages.scheduledExport.create.periodType')}
            name="periodType"
            rules={[{ required: true, message: t('pages.scheduledExport.create.selectPeriod') }]}
          >
            <Select onChange={handlePeriodChange}>
              <Option value="daily">{t('pages.scheduledExport.create.periodDaily')}</Option>
              <Option value="weekly">{t('pages.scheduledExport.create.periodWeekly')}</Option>
              <Option value="monthly">{t('pages.scheduledExport.create.periodMonthly')}</Option>
            </Select>
          </Form.Item>

          <Form.Item<ScheduledExportRequest>
            label={t('pages.scheduledExport.create.executionTime')}
          >
            <Space>
              <Input
                type="number"
                min={0}
                max={23}
                style={{ width: 80 }}
                value={form.getFieldValue('hour')}
                onChange={(e) => { form.setFieldValue('hour', Number(e.target.value)); handleTimeChange(); }}
              />
              <span>{t('pages.scheduledExport.create.hour')}</span>
              <Input
                type="number"
                min={0}
                max={59}
                style={{ width: 80 }}
                value={form.getFieldValue('minute')}
                onChange={(e) => { form.setFieldValue('minute', Number(e.target.value)); handleTimeChange(); }}
              />
              <span>{t('pages.scheduledExport.create.minute')}</span>
            </Space>
          </Form.Item>

          <Form.Item<ScheduledExportRequest>
            label={t('pages.scheduledExport.create.cronPreview')}
          >
            <Alert message={cronPreview || '* * * * *'} type="info" showIcon />
          </Form.Item>

          <Form.Item>
            <Space>
              <Button onClick={() => navigate('/exports/scheduled')}>{t('common.button.cancel')}</Button>
              <Button type="primary" htmlType="submit" loading={loading}>
                {t('pages.scheduledExport.common.createTask')}
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Card>
    </div>
  );
};

export default ScheduledExportCreatePage;
