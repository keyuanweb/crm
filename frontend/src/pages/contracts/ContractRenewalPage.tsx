import { useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components'
import { App, Button, Tag } from 'antd'
import { fetchRenewalOverview } from '../../services/contractService'
import { extractErrorMessage } from '../../services/apiClient'
import {
  CONTRACT_STATUS_COLORS,
  RENEWAL_GROUP_LABELS,
  type Contract,
  type RenewalGroup,
} from '../../types/contract'
import { formatAmount } from '../../types/opportunity'

export default function ContractRenewalPage() {
  const { t } = useTranslation()
  const actionRef = useRef<ActionType>()
  const { message } = App.useApp()
  const [group, setGroup] = useState<RenewalGroup>('EXPIRING_SOON')

  const switchGroup = (g: RenewalGroup) => {
    setGroup(g)
    actionRef.current?.reload()
  }

  const columns: ProColumns<Contract>[] = [
    {
      title: t('pages.contractRenewal.colContractNo'),
      dataIndex: 'contractNo',
      render: (_, row) => <Link to={`/contracts/${row.id}`}>{row.contractNo}</Link>,
    },
    { title: t('pages.contractRenewal.colTitle'), dataIndex: 'title' },
    { title: t('pages.contractRenewal.colCustomer'), dataIndex: 'customerName', search: false },
    { title: t('pages.contractRenewal.colAmount'), dataIndex: 'amount', search: false, render: (_, row) => formatAmount(row.amount) },
    {
      title: t('pages.contractRenewal.colEndDate'),
      dataIndex: 'endDate',
      search: false,
      render: (_, row) => (row.endDate ? row.endDate.replace(/-/g, '/') : '-'),
    },
    {
      title: t('pages.contractRenewal.colStatus'),
      dataIndex: 'status',
      search: false,
      render: (_, row) => (
        <Tag color={CONTRACT_STATUS_COLORS[row.status]}>{t(`pages.contractRenewal.status${row.status}`)}</Tag>
      ),
    },
    {
      title: t('pages.contractRenewal.colRenewedFrom'),
      dataIndex: 'renewedFromNo',
      search: false,
      render: (_, row) => (row.renewedFromNo ? `#${row.renewedFromNo}` : '-'),
    },
    {
      title: t('pages.contractRenewal.colRenewedTo'),
      dataIndex: 'renewedBy',
      search: false,
      render: (_, row) =>
        row.renewedBy?.length
          ? row.renewedBy.map((r) => <Tag key={r.id} color="blue">{r.contractNo}</Tag>)
          : '-',
    },
  ]

  return (
    <>
      <div style={{ marginBottom: 16, display: 'flex', gap: 8 }}>
        {(Object.keys(RENEWAL_GROUP_LABELS) as RenewalGroup[]).map((g) => (
          <Button
            key={g}
            type={group === g ? 'primary' : 'default'}
            onClick={() => switchGroup(g)}
          >
            {t(`pages.contractRenewal.group${g}`)}
          </Button>
        ))}
      </div>
      <ProTable<Contract>
        headerTitle={`${t('pages.contractRenewal.headerTitle')} · ${t(`pages.contractRenewal.group${group}`)}`}
        rowKey="id"
        actionRef={actionRef}
        columns={columns}
        search={{ labelWidth: 'auto' }}
        pagination={{ defaultPageSize: 20 }}
        request={async (params) => {
          try {
            const res = await fetchRenewalOverview(
              group,
              params.keyword,
              params.current ?? 1,
              params.pageSize ?? 20,
            )
            return { data: res.items, success: true, total: res.total }
          } catch (err) {
            message.error(extractErrorMessage(err, t('pages.contractRenewal.msgLoadFailed')))
            return { data: [], success: false, total: 0 }
          }
        }}
      />
    </>
  )
}
