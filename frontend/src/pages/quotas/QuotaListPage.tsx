/** 配额列表页面（078-sales-quota，MVP - User Story 1）。
 *
 * 创建配额的 6 字段表单自 099 起**内联在本页的弹窗里**（共享原语 `FormModal`，`lg` 档 800px）——
 * 原先它独占一个路由页 `/quotas/create`（`QuotaCreatePage.tsx`，126 行 = 返回按钮 + 标题 +
 * 一张 Card + 一张表单），而全站主流写法是「列表页 + 表单弹窗」。099 删掉了那个页面与路由，
 * 字段、校验规则、范围与 i18n 键**逐字搬运**，只换容器。
 */

import { quotaApi, type SalesQuotaRequest, type SalesQuotaResponse, type SalesQuotaSummary } from '../../services/api/quotaApi';
import { PlusOutlined, TeamOutlined, UserOutlined, BarChartOutlined } from '@ant-design/icons';
import { ProTable } from '@ant-design/pro-components';
import { App, Button, Card, Col, DatePicker, Flex, Form, InputNumber, Row, Select } from 'antd';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import type { Dayjs } from 'dayjs';
import React, { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { FormGrid, FormModal, VERTICAL_MIN_ITEM_WIDTH } from '../../components/ui';

const { Option } = Select;

const QuotaListPage: React.FC = () => {
  const { t } = useTranslation();
  const { message } = App.useApp();
  const navigate = useNavigate();
  const actionRef = useRef<ActionType>();
  const [form] = Form.useForm();
  const [year] = useState(new Date().getFullYear());
  const [summary, setSummary] = useState<SalesQuotaSummary | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  // 提交成功后用它把 KPI 汇总重新取一次。**必须显式加通路**：下面那个 useEffect 的依赖只有
  // `[year]`，`actionRef.reload()` 只管表格、管不到三张汇总卡——少了它，新建完配额顶部数字会停在旧值上。
  const [refreshToken, setRefreshToken] = useState(0);

  useEffect(() => {
    quotaApi
      .getSummary(year)
      .then(setSummary)
      .catch((err) => console.error(err));
  }, [year, refreshToken]);

  const openCreate = () => {
    // 不需要 `form.resetFields()`：FormModal 默认 `destroyOnClose`，关闭时表单即被卸载，
    // 再次打开是重新挂载、吃下面 `initialValues` 的新值（照 CustomerListPage 的读法）。
    setModalOpen(true);
  };

  const onCreate = async () => {
    // 校验失败的 rejection 就地吃掉（同 CustomerListPage 的 onSave）：antd 已把错误显示在字段下方，
    // 再弹一条 message 只会重复；而放它逃出去会让 FormModal 的 handleOk 产生一个无人接管的
    // promise rejection（FormModal **刻意不吞异常**，见其文件头）。
    const values = await form.validateFields().catch(() => undefined);
    if (!values) return;
    const payload: SalesQuotaRequest = {
      year: values.year,
      quarter: values.quarter,
      teamId: values.teamId,
      userId: values.userId,
      amount: values.amount,
      periodStart: (values.periodRange as [Dayjs, Dayjs])[0].format('YYYY-MM-DD'),
      periodEnd: (values.periodRange as [Dayjs, Dayjs])[1].format('YYYY-MM-DD'),
    };
    try {
      await quotaApi.create(payload);
      message.success(t('pages.quotaCreate.msgCreated'));
      setModalOpen(false);
      actionRef.current?.reload();
      setRefreshToken((n) => n + 1);
    } catch (error) {
      console.error(error);
      message.error(t('pages.quotaCreate.msgCreateFailed'));
    }
  };

  const columns: ProColumns<SalesQuotaResponse>[] = [
    {
      title: t('pages.quotaList.colYear'),
      dataIndex: 'year',
      width: 80,
      search: false,
    },
    {
      title: t('pages.quotaList.colQuarter'),
      dataIndex: 'quarter',
      width: 80,
      search: false,
      render: (val) => val ? `Q${val}` : t('pages.quotaList.annual'),
    },
    {
      title: t('pages.quotaList.colTeam'),
      dataIndex: 'teamName',
      width: 120,
      search: false,
    },
    {
      title: t('pages.quotaList.colSales'),
      dataIndex: 'userName',
      width: 100,
      search: false,
    },
    {
      title: t('pages.quotaList.colQuotaAmount'),
      dataIndex: 'amount',
      width: 120,
      search: false,
      render: (val) => (typeof val === 'number' ? val.toFixed(2) : val),
    },
    {
      title: t('pages.quotaList.colActualAmount'),
      dataIndex: 'actualAmount',
      width: 120,
      search: false,
      render: (val) => (typeof val === 'number' ? val.toFixed(2) : '-'),
    },
    {
      title: t('pages.quotaList.colAchievementRate'),
      dataIndex: 'achievementRate',
      width: 100,
      search: false,
      render: (val) => (typeof val === 'number' ? `${val.toFixed(1)}%` : '-'),
    },
    {
      title: t('pages.quotaList.colStatus'),
      dataIndex: 'status',
      width: 100,
      valueEnum: {
        ACTIVE: { text: t('pages.quotaList.statusActive'), status: 'Success' },
        DRAFT: { text: t('common.status.draft'), status: 'Warning' },
        CLOSED: { text: t('common.status.closed'), status: 'Error' },
      },
    },
    {
      title: t('pages.quotaList.colCreatedAt'),
      dataIndex: 'createdAt',
      width: 160,
      search: false,
      valueType: 'dateTime',
    },
  ];

  return (
    <div>
      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col span={8}>
          <Card size="small" styles={{ body: { padding: '12px 16px' } }}>
            <Flex gap={8} align="center">
              <TeamOutlined style={{ fontSize: 24, color: '#1890ff' }} />
              <div>
                <div style={{ color: '#8c8c8c', fontSize: 12 }}>{t('pages.quotaList.statTotalQuota')}</div>
                <div style={{ fontSize: 20, fontWeight: 600 }}>
                  {summary ? summary.totalQuota.toFixed(2) : '--'} {t('pages.quotaList.unitWan')}
                </div>
              </div>
            </Flex>
          </Card>
        </Col>
        <Col span={8}>
          <Card size="small" styles={{ body: { padding: '12px 16px' } }}>
            <Flex gap={8} align="center">
              <UserOutlined style={{ fontSize: 24, color: '#52c41a' }} />
              <div>
                <div style={{ color: '#8c8c8c', fontSize: 12 }}>{t('pages.quotaList.statTotalActual')}</div>
                <div style={{ fontSize: 20, fontWeight: 600 }}>
                  {summary ? summary.totalActual.toFixed(2) : '--'} {t('pages.quotaList.unitWan')}
                </div>
              </div>
            </Flex>
          </Card>
        </Col>
        <Col span={8}>
          <Card size="small" styles={{ body: { padding: '12px 16px' } }}>
            <Flex gap={8} align="center">
              <span style={{ fontSize: 24 }}>📊</span>
              <div>
                <div style={{ color: '#8c8c8c', fontSize: 12 }}>{t('pages.quotaList.statTotalRate')}</div>
                <div style={{ fontSize: 20, fontWeight: 600 }}>
                  {summary ? summary.achievementRate.toFixed(1) : '--'}%
                </div>
              </div>
            </Flex>
          </Card>
        </Col>
      </Row>

      <ProTable<SalesQuotaResponse>
        actionRef={actionRef}
        headerTitle={t('pages.quotaList.title')}
        rowKey="id"
        columns={columns}
        request={async (params) => {
          const res = await quotaApi.list({
            page: params.current || 1,
            size: params.pageSize || 20,
            year,
            status: params.status,
          });
          return {
            data: res.records,
            total: res.total,
            success: true,
          };
        }}
        pagination={{
          defaultPageSize: 20,
          showSizeChanger: true,
          pageSizeOptions: ['10', '20', '50', '100'],
        }}
        search={{
          labelWidth: 'auto',
        }}
        dateFormatter="string"
        toolbar={{
          actions: [
            <Button
              key="comparison"
              icon={<BarChartOutlined />}
              onClick={() => navigate('/quotas/comparison')}
            >
              {t('pages.quotaList.btnComparison')}
            </Button>,
            <Button key="add" type="primary" icon={<PlusOutlined />} onClick={openCreate}>
              {t('pages.quotaList.btnCreate')}
            </Button>,
          ],
        }}
      />

      <FormModal
        size="lg"
        title={t('pages.quotaCreate.title')}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        okText={t('pages.quotaCreate.btnCreate')}
        onSubmit={onCreate}
      >
        <Form
          form={form}
          name="quotaCreateForm"
          layout="vertical"
          initialValues={{ year: new Date().getFullYear() }}
        >
          {/* 6 个字段进栅格，参数取自改前的 `QuotaCreatePage.tsx`，只去掉了 `maxCols={3}`：
              那是给**页面级宽容器**用的上限（见 `FormGrid.tsx` 文件头），而弹窗是 480–960 的窄容器，
              auto-fit 自己算出的列数天然合理。 */}
          <FormGrid minItemWidth={VERTICAL_MIN_ITEM_WIDTH}>
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
          </FormGrid>
        </Form>
      </FormModal>
    </div>
  );
};

export default QuotaListPage;
