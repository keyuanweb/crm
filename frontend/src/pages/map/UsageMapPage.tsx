import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Alert, Button, Card, Space, Tabs, Typography } from 'antd'
import {
  CalendarOutlined,
  CommentOutlined,
  CustomerServiceOutlined,
  FileTextOutlined,
  FundOutlined,
  TeamOutlined,
} from '@ant-design/icons'
import { Graph } from '@antv/g6'
import { ALL_FLOWS, MAIN_FLOW, QUICK_ACTIONS, STATE_FLOWS, type FlowDef, type QuickAction } from '../../types/usageMap'
import { useAuthStore } from '../../store/authStore'

const { Title, Paragraph } = Typography

/** 图标映射（QUICK_ACTIONS 用字符串标识避免 ReactNode 序列化问题）。 */
const ICON_MAP: Record<string, React.ReactNode> = {
  TeamOutlined: <TeamOutlined />,
  CommentOutlined: <CommentOutlined />,
  FundOutlined: <FundOutlined />,
  CustomerServiceOutlined: <CustomerServiceOutlined />,
  CalendarOutlined: <CalendarOutlined />,
  FileTextOutlined: <FileTextOutlined />,
}

export default function UsageMapPage() {
  const navigate = useNavigate()
  const user = useAuthStore((s) => s.user)
  const containerRef = useRef<HTMLDivElement>(null)
  const graphRef = useRef<Graph | null>(null)
  const [activeFlow, setActiveFlow] = useState<FlowDef>(MAIN_FLOW)
  const [viewTab, setViewTab] = useState<'process' | 'state'>('process')
  const [stateFlowId, setStateFlowId] = useState<string>(STATE_FLOWS[0].id)
  const [renderFailed, setRenderFailed] = useState(false)

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
          data: { label: n.title, desc: n.desc, color: n.color ?? '#1677ff', path: n.path, edgeLabel: n.desc },
        }))
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
              stroke: ((d: { data: { color: string } }) => d.data.color) as never,
              lineWidth: 2,
              radius: 8,
              labelText: ((d: { data: { label: string } }) => d.data.label) as never,
              labelFill: '#1f1f1f',
              labelFontWeight: 600,
              labelFontSize: 13,
              size: [125, 42],
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
        graph.on('node:click', ((evt: { target: { id?: string } }) => {
          const nodeId = evt.target?.id
          const node = activeDef.nodes.find((n) => n.id === nodeId)
          if (node?.path) {
            navigate(node.path)
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

  const quickActions: QuickAction[] = QUICK_ACTIONS.map((a) => ({
    ...a,
    icon: ICON_MAP[a.icon as string] ?? null,
  }))

  return (
    <div>
      <div style={{ marginBottom: 16 }}>
        <Title level={3} style={{ marginBottom: 4, fontWeight: 600 }}>
          🗺️ 员工使用地图
        </Title>
        <Paragraph type="secondary" style={{ marginBottom: 0, fontSize: 13 }}>
          可视化系统业务流程 · 点击节点直达对应模块 · 切换角色查看操作链
        </Paragraph>
      </div>

      {renderFailed && (
        <Alert
          type="warning"
          showIcon
          message="地图渲染失败，请刷新页面重试"
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
            label: '业务流程',
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
            label: '状态流转',
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

      <Card style={{ borderRadius: 12, boxShadow: '0 1px 2px rgba(0,0,0,0.04)' }} styles={{ body: { padding: 12 } }}>
        <div ref={containerRef} style={{ height: 200, width: '100%' }} />
        {renderFailed && (
          <Paragraph type="secondary" style={{ textAlign: 'center', marginTop: 8, marginBottom: 0 }}>
            当前视图不可用，可切换上方流程或使用下方快捷入口
          </Paragraph>
        )}
      </Card>

      {/* 高频操作快捷入口 */}
      <Card
        title={
          <Space>
            <span>⚡ 高频操作</span>
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              一键直达常用功能
            </Typography.Text>
          </Space>
        }
        style={{ borderRadius: 12, marginTop: 16, boxShadow: '0 1px 2px rgba(0,0,0,0.04)' }}
      >
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10 }}>
          {quickActions.map((a) => (
            <Button
              key={a.key}
              icon={a.icon as React.ReactNode}
              style={{ height: 40, minWidth: 120 }}
              onClick={() => navigate(a.path)}
            >
              {a.label}
            </Button>
          ))}
        </div>
      </Card>
    </div>
  )
}
