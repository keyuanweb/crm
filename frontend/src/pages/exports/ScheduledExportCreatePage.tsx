/** 定时导出任务创建页面（079-scheduled-export，US1）。 */

import { scheduledExportApi } from '../../services/api/scheduledExportApi';
import type { ScheduledExportRequest } from '../../types/scheduledExport';
import { ENTITY_TYPE_LABELS, EXPORT_FORMAT_LABELS } from '../../types/scheduledExport';
import { ArrowLeftOutlined } from '@ant-design/icons';
import { Button, Card, Form, Input, Select, Space, Typography, Alert } from 'antd';
import React, { useState } from 'react';
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
  const navigate = useNavigate();
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [cronPreview, setCronPreview] = useState<string>('');

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
          返回
        </Button>
        <Title level={4}>创建定时导出任务</Title>
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
            label="导出实体"
            name="entityType"
            rules={[{ required: true, message: '请选择导出实体' }]}
          >
            <Select placeholder="请选择导出实体">
              {Object.entries(ENTITY_TYPE_LABELS).map(([key, label]) => (
                <Option key={key} value={key}>{label}</Option>
              ))}
            </Select>
          </Form.Item>

          <Form.Item<ScheduledExportRequest>
            label="筛选条件（JSON）"
            name="filterConditions"
            extra="可选，JSON 格式"
          >
            <Input.TextArea rows={3} placeholder='{"status": "active"}' />
          </Form.Item>

          <Form.Item<ScheduledExportRequest>
            label="导出格式"
            name="exportFormat"
            rules={[{ required: true, message: '请选择导出格式' }]}
          >
            <Select>
              {Object.entries(EXPORT_FORMAT_LABELS).map(([key, label]) => (
                <Option key={key} value={key}>{label}</Option>
              ))}
            </Select>
          </Form.Item>

          <Form.Item<ScheduledExportRequest>
            label="执行周期"
            name="periodType"
            rules={[{ required: true, message: '请选择执行周期' }]}
          >
            <Select onChange={handlePeriodChange}>
              <Option value="daily">每日</Option>
              <Option value="weekly">每周</Option>
              <Option value="monthly">每月</Option>
            </Select>
          </Form.Item>

          <Form.Item<ScheduledExportRequest>
            label="执行时间"
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
              <span>时</span>
              <Input
                type="number"
                min={0}
                max={59}
                style={{ width: 80 }}
                value={form.getFieldValue('minute')}
                onChange={(e) => { form.setFieldValue('minute', Number(e.target.value)); handleTimeChange(); }}
              />
              <span>分</span>
            </Space>
          </Form.Item>

          <Form.Item<ScheduledExportRequest>
            label="Cron 表达式预览"
          >
            <Alert message={cronPreview || '* * * * *'} type="info" showIcon />
          </Form.Item>

          <Form.Item>
            <Space>
              <Button onClick={() => navigate('/exports/scheduled')}>取消</Button>
              <Button type="primary" htmlType="submit" loading={loading}>
                创建任务
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Card>
    </div>
  );
};

export default ScheduledExportCreatePage;
