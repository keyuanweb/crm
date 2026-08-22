import { useRef, useState } from 'react'
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components'
import { App, Button, Popconfirm, Select, Tag } from 'antd'
import { DownloadOutlined, PlusOutlined } from '@ant-design/icons'
import {
  createExportJob,
  downloadExportJob,
  fetchExportJobs,
} from '../../services/exportService'
import { extractErrorMessage } from '../../services/apiClient'
import {
  EXPORT_STATUS_LABELS,
  EXPORT_TYPE_LABELS,
  type ExportJob,
  type ExportType,
} from '../../types/export'

export default function ExportCenterPage() {
  const actionRef = useRef<ActionType>()
  const { message } = App.useApp()
  const [exportType, setExportType] = useState<ExportType>('LEAD')

  const onExport = async () => {
    try {
      await createExportJob({ exportType })
      message.success('导出任务已创建，稍后可在列表下载')
      actionRef.current?.reload()
    } catch (err) {
      message.error(extractErrorMessage(err, '创建导出任务失败'))
    }
  }

  const onDownload = async (row: ExportJob) => {
    try {
      await downloadExportJob(row.id)
    } catch (err) {
      message.error(extractErrorMessage(err, '下载失败'))
    }
  }

  const statusColor = (status: string) =>
    status === 'DONE' ? 'green' : status === 'FAILED' ? 'red' : 'processing'

  const columns: ProColumns<ExportJob>[] = [
    {
      title: '导出类型',
      dataIndex: 'exportType',
      search: false,
      render: (_, row) => EXPORT_TYPE_LABELS[row.exportType as ExportType],
    },
    {
      title: '状态',
      dataIndex: 'status',
      search: false,
      render: (_, row) => (
        <Tag color={statusColor(row.status)}>{EXPORT_STATUS_LABELS[row.status as ExportJob['status']]}</Tag>
      ),
    },
    { title: '行数', dataIndex: 'rowCount', search: false },
    { title: '文件名', dataIndex: 'fileName', search: false },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      search: false,
      valueType: 'dateTime',
    },
    {
      title: '操作',
      valueType: 'option',
      width: 100,
      render: (_, row) => [
        row.status === 'DONE' ? (
          <a key="download" onClick={() => void onDownload(row)}>
            <DownloadOutlined /> 下载
          </a>
        ) : row.status === 'FAILED' ? (
          <span key="failed" style={{ color: '#ff4d4f' }}>
            {row.errorMessage ?? '失败'}
          </span>
        ) : (
          <span key="pending">生成中...</span>
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
          options={Object.entries(EXPORT_TYPE_LABELS).map(([value, label]) => ({ value, label }))}
        />
        <Button type="primary" icon={<PlusOutlined />} onClick={() => void onExport()}>
          发起导出
        </Button>
      </div>
      <ProTable<ExportJob>
        headerTitle="导出记录"
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
            title="刷新状态？"
            onConfirm={() => actionRef.current?.reload()}
          >
            <Button>刷新</Button>
          </Popconfirm>,
        ]}
      />
    </>
  )
}
