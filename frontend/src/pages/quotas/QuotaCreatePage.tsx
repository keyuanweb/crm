/** 配额创建页面（078-sales-quota，US1 - 创建年度/季度配额）。 */

import { quotaApi, type SalesQuotaRequest } from '../../services/api/quotaApi';
import { ArrowLeftOutlined } from '@ant-design/icons';
import type { Dayjs } from 'dayjs';
import { Button, Card, DatePicker, Form, InputNumber, Select, Space, Typography, message } from 'antd';
import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';

const { Title } = Typography;
const { Option } = Select;

const QuotaCreatePage: React.FC = () => {
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
      message.success('创建成功');
      navigate('/quotas');
    } catch (error) {
      console.error(error);
      message.error('创建失败');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/quotas')}>
          返回
        </Button>
        <Title level={4}>创建配额</Title>
      </Space>

      <Card>
        <Form
          form={form}
          onFinish={onFinish}
          layout="vertical"
          initialValues={{ year: new Date().getFullYear() }}
        >
          <Form.Item label="年份" name="year" rules={[{ required: true, message: '请输入年份' }]}>
            <InputNumber min={2000} max={2100} style={{ width: '100%' }} />
          </Form.Item>

          <Form.Item label="季度" name="quarter">
            <Select allowClear placeholder="年度配额可不选">
              <Option value={1}>Q1</Option>
              <Option value={2}>Q2</Option>
              <Option value={3}>Q3</Option>
              <Option value={4}>Q4</Option>
            </Select>
          </Form.Item>

          <Form.Item label="团队 ID" name="teamId">
            <InputNumber min={1} style={{ width: '100%' }} placeholder="个人/年度配额可不填" />
          </Form.Item>

          <Form.Item label="销售 ID" name="userId">
            <InputNumber min={1} style={{ width: '100%' }} placeholder="团队/年度配额可不填" />
          </Form.Item>

          <Form.Item
            label="配额金额（万元）"
            name="amount"
            rules={[{ required: true, message: '请输入配额金额' }]}
          >
            <InputNumber min={0.01} step={0.01} style={{ width: '100%' }} />
          </Form.Item>

          <Form.Item
            label="期间"
            name="periodRange"
            rules={[{ required: true, message: '请选择期间' }]}
          >
            <DatePicker.RangePicker style={{ width: '100%' }} />
          </Form.Item>

          <Form.Item>
            <Space>
              <Button onClick={() => navigate('/quotas')}>取消</Button>
              <Button type="primary" htmlType="submit" loading={submitting}>
                创建配额
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Card>
    </div>
  );
};

export default QuotaCreatePage;
