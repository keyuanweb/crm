import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { App, Alert, Card, Empty, Tag, Typography } from 'antd'
import {
  DndContext,
  DragOverlay,
  PointerSensor,
  useDraggable,
  useDroppable,
  useSensor,
  useSensors,
  type DragEndEvent,
  type DragStartEvent,
} from '@dnd-kit/core'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { fetchSalesOpportunities, updateSalesOpportunity } from '../../services/opportunityService'
import { extractErrorMessage, isVersionConflict, type PageResult } from '../../services/apiClient'
import { formatAmount, type SalesOpportunity } from '../../types/opportunity'
import { useOpportunityStages } from '../../hooks/useOpportunityStages'

/**
 * 看板一次拉取的条数。
 *
 * <p>看板与列表不同——分页的看板没有意义（一列翻到第 2 页、别的列却停在第 1 页）。故一次拉到底，超过这个条数就
 * **明确告知用户被截断**，而不是默默少显示几张卡片：少掉的卡片看起来就像「这笔商机不见了」。
 */
const BOARD_PAGE_SIZE = 200

const BOARD_QUERY_KEY = ['sales-opportunities-board'] as const

/** 单张卡片。用 `useDraggable` 而不是 `useSortable`：看板只按列分组，列内不需要排序语义。 */
function StageCard({ row }: { row: SalesOpportunity }) {
  const { attributes, listeners, setNodeRef, transform, isDragging } = useDraggable({
    id: String(row.id),
  })

  return (
    <div
      ref={setNodeRef}
      {...listeners}
      {...attributes}
      style={{
        // 用 translate3d 而不是改 top/left：拖动时走合成层，不掉帧
        transform: transform ? `translate3d(${transform.x}px, ${transform.y}px, 0)` : undefined,
        // 被拖的那张原位留一个淡出的占位，列高度不会因为拖走而塌陷
        opacity: isDragging ? 0.4 : 1,
        cursor: 'grab',
        marginBottom: 8,
      }}
    >
      <Card size="small" styles={{ body: { padding: 10 } }} hoverable>
        <Typography.Text strong style={{ fontSize: 13 }}>
          {row.opportunityName ?? `#${row.opportunityId}`}
        </Typography.Text>
        <div style={{ marginTop: 4 }}>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {row.customerName ?? '-'}
          </Typography.Text>
        </div>
        <div
          style={{
            marginTop: 6,
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
          }}
        >
          <Typography.Text style={{ fontSize: 13 }}>¥{formatAmount(row.amount)}</Typography.Text>
          {row.expectedCloseDate ? (
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              {row.expectedCloseDate}
            </Typography.Text>
          ) : null}
        </div>
      </Card>
    </div>
  )
}

/**
 * 一列。整列是放置区——落在列内任意位置都算落到这一列，不需要精确命中卡片之间的间隙。
 *
 * @param code 阶段编码——**放置区的 id 必须是它**，不能用展示名：自建阶段的展示名与编码不同，用名字会让
 *     `onDragEnd` 拿到的「落点」既不是编码也没法还原，拖拽直接静默失效
 * @param title 已解析好的展示名（由调用方用 `stageLabel` 解析，本组件不碰 i18n 细节）
 * @param retired 已停用：只读列，不接收拖放
 */
function StageColumn({
  code,
  title,
  retired,
  rows,
}: {
  code: string
  title: string
  retired: boolean
  rows: SalesOpportunity[]
}) {
  const { t } = useTranslation()
  // 已停用的列不接受拖放（服务端也会拒，这里只是不让用户白拖一次）
  const { setNodeRef, isOver } = useDroppable({
    id: code,
    disabled: retired,
  })

  const amountTotal = rows.reduce((sum, r) => sum + (r.amount ?? 0), 0)

  return (
    <div
      ref={setNodeRef}
      style={{
        flex: '0 0 280px',
        background: isOver && !retired ? '#e6f4ff' : '#f5f5f5',
        borderRadius: 8,
        padding: 10,
        transition: 'background 0.15s',
        minHeight: 200,
      }}
    >
      <div style={{ marginBottom: 8 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Typography.Text strong>{title}</Typography.Text>
          <Tag color={retired ? 'default' : 'blue'} style={{ marginInlineEnd: 0 }}>
            {rows.length}
          </Tag>
        </div>
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
          ¥{formatAmount(amountTotal)}
          {retired ? ` · ${t('pages.salesOpportunity.stageRetired')}` : ''}
        </Typography.Text>
      </div>
      {rows.length === 0 ? (
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description={
            <span style={{ fontSize: 12 }}>{t('pages.salesOpportunity.boardEmptyColumn')}</span>
          }
        />
      ) : (
        rows.map((row) => <StageCard key={row.id} row={row} />)
      )}
    </div>
  )
}

/**
 * 商机看板（1.2）。
 *
 * <p><b>拖拽提交复用既有的乐观锁</b>：`PUT /sales-opportunities/{id}` 带 `version`，服务端版本不符返回 409。
 * 这是本组件唯一的写路径，没有为看板另开一个「只改阶段」的接口——多一条写路径就多一处要同步维护的阶段校验。
 */
export default function OpportunityBoard() {
  const { t } = useTranslation()
  const { message } = App.useApp()
  const queryClient = useQueryClient()
  const { activeStages, stageLabel, isTerminal, isLoading: stagesLoading } = useOpportunityStages()
  const [dragging, setDragging] = useState<SalesOpportunity | null>(null)

  const { data, isLoading: rowsLoading } = useQuery({
    queryKey: BOARD_QUERY_KEY,
    queryFn: () => fetchSalesOpportunities({ page: 1, pageSize: BOARD_PAGE_SIZE }),
  })

  const sensors = useSensors(
    // 移动超过 4px 才算拖拽：否则卡片上的普通点击会被当成拖拽起点，卡片「粘」在鼠标上
    useSensor(PointerSensor, { activationConstraint: { distance: 4 } }),
  )

  const rows = useMemo(() => data?.items ?? [], [data])
  const byStage = useMemo(() => {
    const grouped = new Map<string, SalesOpportunity[]>()
    for (const stage of activeStages) grouped.set(stage.code, [])
    for (const row of rows) grouped.get(row.stage)?.push(row)
    return grouped
  }, [rows, activeStages])

  // 终态商机不在看板上——队列视图的语义就是「进行中」。但不能默默不显示：条数写在提示条里，用户知道去哪找。
  const closedCount = rows.filter((r) => isTerminal(r.stage)).length
  const total = data?.total ?? rows.length

  const move = useMutation({
    mutationFn: (vars: { row: SalesOpportunity; stage: string }) =>
      // 必须整对象回传（后端 PUT 是全量替换语义）：只发 stage 的话 amount / expectedCloseDate 会被清空
      updateSalesOpportunity(vars.row.id, {
        opportunityId: vars.row.opportunityId,
        amount: vars.row.amount,
        stage: vars.stage,
        expectedCloseDate: vars.row.expectedCloseDate,
        version: vars.row.version,
      }),
    onMutate: async ({ row, stage }) => {
      // 先取消在途的 refetch，否则它回来会把乐观改动冲掉
      await queryClient.cancelQueries({ queryKey: BOARD_QUERY_KEY })
      const previous = queryClient.getQueryData<PageResult<SalesOpportunity>>(BOARD_QUERY_KEY)
      queryClient.setQueryData<PageResult<SalesOpportunity>>(BOARD_QUERY_KEY, (old) =>
        old
          ? { ...old, items: old.items.map((i) => (i.id === row.id ? { ...i, stage } : i)) }
          : old,
      )
      return { previous }
    },
    onError: (err, _vars, ctx) => {
      if (ctx?.previous) queryClient.setQueryData(BOARD_QUERY_KEY, ctx.previous)
      message.error(
        isVersionConflict(err)
          ? t('pages.salesOpportunity.msgVersionConflict')
          : extractErrorMessage(err, t('pages.salesOpportunity.msgStageChangeFailed')),
      )
    },
    // 成败都重拉：成功要拿到递增后的 version，失败要拿到「别人的最新值」
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: BOARD_QUERY_KEY })
    },
  })

  const onDragEnd = (event: DragEndEvent) => {
    setDragging(null)
    const target = event.over?.id
    if (target === undefined) return
    const row = rows.find((r) => r.id === Number(event.active.id))
    const stage = String(target)
    if (!row || row.stage === stage) return
    move.mutate({ row, stage })
  }

  if (rowsLoading || stagesLoading) {
    return <Card loading bordered={false} style={{ borderRadius: 10 }} />
  }

  return (
    <>
      {total > rows.length ? (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 12 }}
          message={t('pages.salesOpportunity.boardTruncated', { shown: rows.length, total })}
        />
      ) : null}
      {closedCount > 0 ? (
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 12 }}
          message={t('pages.salesOpportunity.boardClosedCount', { count: closedCount })}
        />
      ) : null}
      <DndContext
        sensors={sensors}
        onDragStart={(e: DragStartEvent) =>
          setDragging(rows.find((r) => r.id === Number(e.active.id)) ?? null)
        }
        onDragEnd={onDragEnd}
        onDragCancel={() => setDragging(null)}
      >
        <div style={{ display: 'flex', gap: 12, overflowX: 'auto', paddingBottom: 8 }}>
          {activeStages.map((stage) => (
            <StageColumn
              key={stage.code}
              code={stage.code}
              title={stageLabel(stage.code)}
              retired={stage.enabled !== 1}
              rows={byStage.get(stage.code) ?? []}
            />
          ))}
        </div>
        <DragOverlay>{dragging ? <StageCard row={dragging} /> : null}</DragOverlay>
      </DndContext>
    </>
  )
}
