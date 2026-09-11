/** 数据保留策略编辑页面（080-data-retention，US4 - 编辑策略）。 */

import { dataRetentionApi } from '../../services/api/dataRetentionApi';
import type { DataRetentionPolicyRequest } from '../../types/dataRetention';
import { ENTITY_TYPE_LABELS, ACTION_TYPE_LABELS } from '../../types/dataRetention';
import { ArrowLeftOutlined } from '@ant-design/icons';
import { Button, Card, Form, Input, Select, Space, Typography, message } from 'antd';
import React, { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';

const { Title } = Typography;

const { Option } = Select;

const DataRetentionPolicyEditPage: React.FC = () => {
  const navigate = useNavigate();
  const { id } = useParams<{ id: string }>();
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);

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
        message.error('加载策略失败');
      } finally {
        if (!cancelled) setLoading(false);
      }
    };
    load();
    return () => {
      cancelled = true;
    };
  }, [id, form]);

  const onFinish = async (values: DataRetentionPolicyRequest) => {
    if (!id) return;
    setSubmitting(true);
    try {
      await dataRetentionApi.updatePolicy(Number(id), values);
      message.success('更新成功');
      navigate('/data-retention');
    } catch (error) {
      console.error(error);
      message.error('更新失败');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/data-retention')}>
          返回
        </Button>
        <Title level={4}>编辑数据保留策略</Title>
      </Space>

      <Card loading={loading}>
        <Form<DataRetentionPolicyRequest>
          form={form}
          onFinish={onFinish}
          layout="vertical"
        >
          <Form.Item<DataRetentionPolicyRequest>
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

          <Form.Item<DataRetentionPolicyRequest>
            label="保留期限（天）"
            name="retentionDays"
            rules={[{ required: true, message: '请输入保留期限' }]}
          >
            <Input type="number" min={1} placeholder="请输入保留天数" />
          </Form.Item>

          <Form.Item<DataRetentionPolicyRequest>
            label="归档方式"
            name="actionType"
            rules={[{ required: true, message: '请选择归档方式' }]}
          >
            <Select>
              {Object.entries(ACTION_TYPE_LABELS).map(([key, label]) => (
                <Option key={key} value={key}>{label}</Option>
              ))}
            </Select>
          </Form.Item>

          <Form.Item>
            <Space>
              <Button onClick={() => navigate('/data-retention')}>取消</Button>
              <Button type="primary" htmlType="submit" loading={submitting}>
                保存修改
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Card>
    </div>
  );
};

export default DataRetentionPolicyEditPage;
