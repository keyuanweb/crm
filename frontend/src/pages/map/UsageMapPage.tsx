import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { Alert, Button, Card, Drawer, Modal, Space, Tabs, Typography } from 'antd'
import { Grid } from 'antd'
import {
  FileTextOutlined,
} from '@ant-design/icons'
import { Graph } from '@antv/g6'
import { ALL_FLOWS, MAIN_FLOW, QUICK_ACTIONS, STATE_FLOWS, type FlowDef, type FlowNode } from '../../types/usageMap'
import { useAuthStore } from '../../store/authStore'

const { Title, Paragraph } = Typography

export default function UsageMapPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const user = useAuthStore((s) => s.user)
  const containerRef = useRef<HTMLDivElement>(null)
  const graphRef = useRef<Graph | null>(null)
  const [activeFlow, setActiveFlow] = useState<FlowDef>(MAIN_FLOW)
  const [viewTab, setViewTab] = useState<'process' | 'state'>('process')
  const [stateFlowId, setStateFlowId] = useState<string>(STATE_FLOWS[0].id)
  const [renderFailed, setRenderFailed] = useState(false)
  
  // 节点详情弹窗状态
  const [modalOpen, setModalOpen] = useState(false)
  const [selectedNode, setSelectedNode] = useState<FlowNode | null>(null)
  const screens = Grid.useBreakpoint()
  const isMobile = !screens.lg

  // 当前渲染的流程（业务流程 or 状态流转）
  const activeDef = viewTab === 'state' ? STATE_FLOWS.find((f) => f.id === stateFlowId) ?? STATE_FLOWS[0] : activeFlow

  // 当前用户角色 → 默认流程（SALES→销售链、SUPPORT→客服链、ADMIN→主流程）
  const defaultFlow = () => {
    const role = user?.role
    if (role) {
      const matched = ALL_FLOWS.find((f) => f.role === role)
      if (matched) return matched
    }
    return MAIN_FLOW
  }

  useEffect(() => {
    setActiveFlow(defaultFlow())
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    if (!containerRef.current) return
    setRenderFailed(false)
    // StrictMode dev 双挂载防护：延迟创建，首次挂载的 timer 会在 cleanup 后被取消
    let cancelled = false
    const timer = window.setTimeout(() => {
      if (cancelled || !containerRef.current) return
      try {
        graphRef.current?.destroy()
        const nodes = activeDef.nodes.map((n) => ({
          id: n.id,
          data: {
            label: n.title,
            desc: n.desc,
            color: n.color ?? '#1677ff',
            path: n.path,
            edgeLabel: n.desc,
            warning: n.warning,
          },
        }))
        // 移动端适配：节点尺寸和文字大小
        const nodeSize = isMobile ? [110, 38] as [number, number] : [130, 46] as [number, number]
        const labelFontSize = isMobile ? 12 : 14
        const edges = activeDef.edges.map((e) => ({
          source: e.source,
          target: e.target,
          data: { label: e.label ?? '', curve: 1 },
        }))
        // 反向边（如 待审批↔已驳回）分配相反曲率，避免两条线重叠
        const reverseCurve = new Map<string, number>()
        for (let i = 0; i < edges.length; i++) {
          const key = `${edges[i].source}|${edges[i].target}`
          const revKey = `${edges[i].target}|${edges[i].source}`
          if (reverseCurve.has(revKey)) {
            edges[i].data.curve = -(reverseCurve.get(revKey) ?? 1)
          }
          reverseCurve.set(key, edges[i].data.curve)
        }
        // 自然大小布局（不 fitView 放大/缩小）；animation:false 禁用布局/平移动画，图一开始即最终位置
        const graph = new Graph({
          container: containerRef.current,
          data: { nodes, edges },
          animation: false,
          layout: { type: 'dagre', rankdir: 'LR', nodesep: 24, ranksep: 48 },
          node: {
            style: {
              fill: '#ffffff',
              stroke: ((d: { data: { color: string; warning?: boolean } }) =>
                d.data.warning ? '#cf1322' : (d.data.color ?? '#1677ff')) as never,
              lineWidth: ((d: { data: { warning?: boolean } }) => (d.data.warning ? 3 : 2)) as never,
              radius: 12,
              shadow: '0 2px 8px rgba(0,0,0,0.15)',
              labelText: ((d: { data: { label: string } }) => d.data.label) as never,
              labelFill: '#1f1f1f',
              labelFontWeight: 600,
              labelFontSize: labelFontSize,
              size: nodeSize,
            },
          },
          edge: {
            type: 'cubic',
            style: {
              stroke: ((d: { data: { label: string } }) => {
                const l = d.data.label
                if (l && /驳回|失败|废弃|终止|退回|无效/.test(l)) return '#cf1322'
                if (l && /通过|成交|转化|结项|关闭|完成/.test(l)) return '#52c41a'
                return '#bfbfbf'
              }) as never,
              lineDash: ((d: { data: { label: string } }) => {
                const l = d.data.label
                if (l && /驳回|失败|废弃|退回|无效/.test(l)) return [5, 4]
                return undefined
              }) as never,
              // cubic 曲线：反向边（curve=-1）用相反曲率分离，避免重叠
              curveOffset: ((d: { data: { curve?: number } }) =>
                22 * (d.data.curve ?? 1)) as never,
              lineWidth: 1.8,
              endArrow: true,
              labelText: ((d: { data: { label: string } }) => d.data.label) as never,
              labelFill: '#595959',
              labelFontSize: 11,
              labelBackground: true,
              labelBackgroundFill: '#ffffff',
              labelBackgroundRadius: 4,
              labelPadding: [2, 4],
            },
          },
        })
        // 事件注册（render 前）
        // 悬停效果：阴影加深 + 边框高亮（无尺寸变化）
        //
        // 注：原实现用 graph.find()/graph.updateItem()，那是 G6 v4 的 API，v5.1.1 的 Graph 上没有这两个方法
        // （Graph extends EventEmitter，方法清单见 node_modules/@antv/g6/lib/runtime/graph.js）。原先靠
        // `as any` 掩盖，运行时一旦悬停即抛 TypeError，高亮从未生效。此处改用 v5 的 updateNodeData
        // ——传 { id, style } 局部数据即可，且节点是否存在由 hasNode 判定。
        graph.on('node:mouseenter', ((evt: { target: { id?: string } }) => {
          const node = activeDef.nodes.find((n) => n.id === evt.target?.id)
          if (!node || !graph.hasNode(node.id)) return
          graph.updateNodeData([
            { id: node.id, style: { shadow: '0 4px 12px rgba(0,0,0,0.25)', lineWidth: 3 } },
          ])
        }) as never)
        graph.on('node:mouseleave', ((evt: { target: { id?: string } }) => {
          const node = activeDef.nodes.find((n) => n.id === evt.target?.id)
          if (!node || !graph.hasNode(node.id)) return
          // 恢复初始样式：lineWidth 与 node.style.stroke 一样由 warning 决定，不能一律恢复成 2
          graph.updateNodeData([
            { id: node.id, style: { shadow: '0 2px 8px rgba(0,0,0,0.15)', lineWidth: node.warning ? 3 : 2 } },
          ])
        }) as never)
        graph.on('node:click', ((evt: { target: { id?: string } }) => {
          const nodeId = evt.target?.id
          const node = activeDef.nodes.find((n) => n.id === nodeId)
          if (node) {
            setSelectedNode(node)
            setModalOpen(true)
          }
        }) as never)
        graphRef.current = graph
        // G6 v5 render 异步绘制：完成后将图平移居中（横向+垂直居中）
        graph.render().then(() => {
          if (graphRef.current !== graph) return
          try {
            // 读节点坐标计算包围盒 → 平移到视口中心（保持原缩放，不放大）
            const c = containerRef.current
            if (c) {
              const data = graph.getData() as unknown as {
                nodes: { id: string; style?: { x?: number; y?: number; size?: [number, number] } }[]
              }
              if (data.nodes?.length) {
                let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity
                for (const n of data.nodes) {
                  const s = n.style ?? {}
                  const sx = s.size?.[0] ?? 125
                  const sy = s.size?.[1] ?? 42
                  const nx = s.x ?? 0
                  const ny = s.y ?? 0
                  minX = Math.min(minX, nx - sx / 2)
                  maxX = Math.max(maxX, nx + sx / 2)
                  minY = Math.min(minY, ny - sy / 2)
                  maxY = Math.max(maxY, ny + sy / 2)
                }
                const cw = c.clientWidth
                const ch = c.clientHeight
                const dx = cw / 2 - (minX + maxX) / 2
                const dy = ch / 2 - (minY + maxY) / 2
                graph.translateBy([dx, dy])
              }
            }
          } catch {
            // 居中失败不影响已渲染图形
          }
        })
      } catch (err) {
        console.error('G6 render failed:', err)
        setRenderFailed(true)
      }
    }, 30)
    return () => {
      cancelled = true
      window.clearTimeout(timer)
      try {
        graphRef.current?.destroy()
      } catch {
        // destroy 已失效实例时忽略
      }
      graphRef.current = null
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeDef])

  const onFlowChange = (key: string) => {
    const flow = ALL_FLOWS.find((f) => f.id === key)
    if (flow) setActiveFlow(flow)
  }

  return (
    <div>
      <div style={{ marginBottom: 16 }}>
        <Title level={3} style={{ marginBottom: 4, fontWeight: 600 }}>
          🗺️ {t('pages.usageMap.pageTitle')}
        </Title>
        <Paragraph type="secondary" style={{ marginBottom: 0, fontSize: 13 }}>
          {t('pages.usageMap.subtitle')}
        </Paragraph>
      </div>

      {renderFailed && (
        <Alert
          type="warning"
          showIcon
          message={t('pages.usageMap.renderFailed')}
          style={{ marginBottom: 16 }}
        />
      )}

      {/* 一级：业务流程图 / 状态流转 */}
      <Tabs
        activeKey={viewTab}
        onChange={(k) => setViewTab(k as 'process' | 'state')}
        items={[
          {
            key: 'process',
            label: t('pages.usageMap.tabProcess'),
            children: (
              <Tabs
                activeKey={activeFlow.id}
                onChange={onFlowChange}
                size="small"
                items={ALL_FLOWS.map((f) => ({ key: f.id, label: f.title }))}
              />
            ),
          },
          {
            key: 'state',
            label: t('pages.usageMap.tabState'),
            children: (
              <Tabs
                activeKey={stateFlowId}
                onChange={setStateFlowId}
                size="small"
                items={STATE_FLOWS.map((f) => ({ key: f.id, label: f.title }))}
              />
            ),
          },
        ]}
      />

      <Card style={{ boxShadow: '0 1px 2px rgba(0,0,0,0.04)' }} styles={{ body: { padding: 12 } }}>
        <div ref={containerRef} style={{ height: 200, width: '100%' }} />
        {renderFailed && (
          <Paragraph type="secondary" style={{ textAlign: 'center', marginTop: 8, marginBottom: 0 }}>
            {t('pages.usageMap.viewUnavailable')}
          </Paragraph>
        )}
      </Card>

      {/* 高频操作快捷入口 */}
      <Card
        title={
          <Space>
            <span>⚡ {t('pages.usageMap.quickActionsTitle')}</span>
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              {t('pages.usageMap.quickActionsSubtitle')}
            </Typography.Text>
          </Space>
        }
        style={{ marginTop: 16, boxShadow: '0 1px 2px rgba(0,0,0,0.04)' }}
      >
        <div style={{
          display: 'grid',
          gridTemplateColumns: isMobile ? 'repeat(2, 1fr)' : 'repeat(auto-fill, minmax(140px, 1fr))',
          gap: 12,
        }}>
          {QUICK_ACTIONS.map((a) => (
            <Button
              key={a.key}
              icon={a.icon}
              style={{
                height: isMobile ? 44 : 48,
                borderRadius: 'var(--radius-md)',
                background: '#f5f5f5',
                borderColor: '#f5f5f5',
              }}
              onMouseEnter={(e) => {
                e.currentTarget.style.background = '#e6f4ff'
                e.currentTarget.style.borderColor = '#1677ff'
                e.currentTarget.style.color = '#1677ff'
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.background = '#f5f5f5'
                e.currentTarget.style.borderColor = '#f5f5f5'
                e.currentTarget.style.color = 'rgba(0,0,0,0.88)'
              }}
              onClick={() => navigate(a.path)}
            >
              {a.label}
            </Button>
          ))}
        </div>
      </Card>

      {/* 节点详情弹窗（桌面端 Modal）*/}
      <Modal
        title={selectedNode?.title}
        open={modalOpen && !isMobile}
        onCancel={() => setModalOpen(false)}
        footer={[
          selectedNode?.path && (
            <Button
              key="jump"
              type="primary"
              icon={<FileTextOutlined />}
              onClick={() => {
                navigate(selectedNode!.path!)
                setModalOpen(false)
              }}
            >
              {t('pages.usageMap.gotoModule')}
            </Button>
          ),
          <Button key="close" onClick={() => setModalOpen(false)}>
            {t('common.button.close')}
          </Button>,
        ]}
      >
        <p>{selectedNode?.desc}</p>
      </Modal>

      {/* 节点详情抽屉（移动端 Drawer）*/}
      <Drawer
        title={selectedNode?.title}
        open={modalOpen && isMobile}
        onClose={() => setModalOpen(false)}
        placement="bottom"
        height={'auto'}
        styles={{ body: { paddingBottom: 20 } }}
      >
        <p>{selectedNode?.desc}</p>
        {selectedNode?.path && (
          <Button
            type="primary"
            icon={<FileTextOutlined />}
            block
            onClick={() => {
              navigate(selectedNode!.path!)
              setModalOpen(false)
            }}
            style={{ marginTop: 16 }}
          >
            {t('pages.usageMap.gotoModule')}
          </Button>
        )}
      </Drawer>
    </div>
  )
}
