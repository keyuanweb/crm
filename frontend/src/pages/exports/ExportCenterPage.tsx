import { useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components'
import { App, Button, Popconfirm, Select, Tag } from 'antd'
import { DownloadOutlined, PlusOutlined } from '@ant-design/icons'
import {
  createExportJob,
  downloadExportJob,
  fetchExportJobs,
} from '../../services/exportService'
import { extractErrorMessage } from '../../services/apiClient'
import { ENUM_KEYS, labelOf } from '../../constants/enumLabels'
import { type ExportJob, type ExportType } from '../../types/export'

export default function ExportCenterPage() {
  const { t } = useTranslation()
  const actionRef = useRef<ActionType>()
  const { message } = App.useApp()
  const [exportType, setExportType] = useState<ExportType>('LEAD')

  const onExport = async () => {
    try {
      await createExportJob({ exportType })
      message.success(t('pages.exportCenter.msgExported'))
      actionRef.current?.reload()
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.exportCenter.msgCreateFailed')))
    }
  }

  const onDownload = async (row: ExportJob) => {
    try {
      await downloadExportJob(row.id)
    } catch (err) {
      message.error(extractErrorMessage(err, t('pages.exportCenter.msgDownloadFailed')))
    }
  }

  const statusColor = (status: string) =>
    status === 'DONE' ? 'green' : status === 'FAILED' ? 'red' : 'processing'

  const columns: ProColumns<ExportJob>[] = [
    {
      title: t('pages.exportCenter.colType'),
      dataIndex: 'exportType',
      search: false,
      render: (_, row) => labelOf(t, ENUM_KEYS.exportType, row.exportType),
    },
    {
      title: t('pages.exportCenter.colStatus'),
      dataIndex: 'status',
      search: false,
      render: (_, row) => (
        <Tag color={statusColor(row.status)}>{labelOf(t, ENUM_KEYS.exportStatus, row.status)}</Tag>
      ),
    },
    { title: t('pages.exportCenter.colRowCount'), dataIndex: 'rowCount', search: false },
    { title: t('pages.exportCenter.colFileName'), dataIndex: 'fileName', search: false },
    {
      title: t('pages.exportCenter.colCreated'),
      dataIndex: 'createdAt',
      search: false,
      valueType: 'dateTime',
    },
    {
      title: t('pages.exportCenter.colAction'),
      valueType: 'option',
      width: 100,
      render: (_, row) => [
        row.status === 'DONE' ? (
          <a key="download" onClick={() => void onDownload(row)}>
            <DownloadOutlined /> {t('pages.exportCenter.btnDownload')}
          </a>
        ) : row.status === 'FAILED' ? (
          <span key="failed" style={{ color: '#ff4d4f' }}>
            {row.errorMessage ?? t('pages.exportCenter.statusFailed')}
          </span>
        ) : (
          <span key="pending">{t('pages.exportCenter.statusPending')}</span>
        ),
      ],
    },
  ]

  return (
    <>
      <div style={{ marginBottom: 16, display: 'flex', gap: 8, alignItems: 'center' }}>
        <Select
          style={{ width: 180 }}
          value={exportType}
          onChange={setExportType}
          options={Object.keys(ENUM_KEYS.exportType).map((code) => ({ value: code, label: labelOf(t, ENUM_KEYS.exportType, code) }))}
        />
        <Button type="primary" icon={<PlusOutlined />} onClick={() => void onExport()}>
          {t('pages.exportCenter.btnExport')}
        </Button>
      </div>
      <ProTable<ExportJob>
        size="small"
        headerTitle={t('pages.exportCenter.title')}
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        search={false}
        pagination={{ defaultPageSize: 20 }}
        request={async (params) => {
          const res = await fetchExportJobs(params.current ?? 1, params.pageSize ?? 20)
          return { data: res.items, success: true, total: res.total }
        }}
        toolBarRender={() => [
          <Popconfirm
            key="refresh"
            title={t('pages.exportCenter.confirmRefresh')}
            onConfirm={() => actionRef.current?.reload()}
          >
            <Button>{t('pages.exportCenter.btnRefresh')}</Button>
          </Popconfirm>,
        ]}
      />
    </>
  )
}
