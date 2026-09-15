import { useEffect, useState, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { App, Button, Card, Drawer, Form, Grid, Input, Modal, Popconfirm, Select, Space, Tag, InputNumber, Tree } from 'antd'
import { DeleteOutlined, EditOutlined, PlusOutlined, ReloadOutlined, SearchOutlined, EyeOutlined } from '@ant-design/icons'
import {
  createDepartment,
  deleteDepartment,
  fetchDepartmentTree,
  updateDepartment,
  type DepartmentPayload,
} from '../../services/departmentService'
import { extractErrorMessage } from '../../services/apiClient'
import { usePerms } from '../../hooks/usePerms'
import { PERMS } from '../../constants/permissions'
import type { Department } from '../../types/department'
import { FormGrid, Highlight, PageState, VERTICAL_MIN_ITEM_WIDTH } from '../../components/ui'
import DepartmentDetail from '../../components/DepartmentDetail'
import { useDebouncedValue } from '../../hooks/useDebouncedValue'

interface FormValues {
  name: string
  parentId?: number
  description?: string
  sortOrder?: number
}

export default function DepartmentListPage() {
  const { t } = useTranslation()
  const { message: messageApi } = App.useApp()
  const [treeData, setTreeData] = useState<Department[]>([])
  const [loading, setLoading] = useState(false)
  const [modalOpen, setModalOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [editing, setEditing] = useState<Department | null>(null)
  const [detailOpen, setDetailOpen] = useState(false)
  const [detailData, setDetailData] = useState<Department | null>(null)
  const [form] = Form.useForm<FormValues>()
  const [flatOptions, setFlatOptions] = useState<{ value: number; label: string }[]>([])
  const [searchValue, setSearchValue] = useState('')
  // 095 T015：输入框仍逐字符受控（受控值必须是实时的，否则打字会卡），
  // 但**真正参与过滤与高亮的是防抖后的 `q`**。两处必须同源——
  // 若过滤用 `q` 而高亮用 `searchValue`，会出现「节点被留下、却没标出命中在哪」。
  const q = useDebouncedValue(searchValue, 300)
  // 095 追加订正（单测实测，非推断）：展开键**必须**是字符串。
  //
  // React 会把元素的 `key` 强制转成字符串，故 `Tree.TreeNode key={node.id}` 在 rc-tree 内部
  // 的键是 `'1'`；而展开态是拿 `node.id`（number）去比。rc-tree 5.10 用
  // `expandedKeys.includes(key)` 精确比较 ⇒ **1 !== '1'** ⇒ 数字键恒不生效。
  // 后果：`load()` 的 `allKeys` 与下面 effect 的「展开全部」**都是空操作**，
  // 树永远收起 —— 这正是 T008 要修的那个现象的**第二层原因**（第一层是 else 分支写成了 `[]`）。
  // 实测（`zz-probe` 临时探针，用后即删）：同一棵两节点树，
  // `expandedKeys={[1]}` 不展开、`expandedKeys={['1']}` 展开。
  //
  // 故此处把状态收紧成 `string[]`，并在唯一的外部入口 `onExpand` 上显式 `String()` ——
  // 让类型挡住「哪天又有人塞进数字」。
  const [expandedKeys, setExpandedKeys] = useState<string[]>([])
  // 086：删除部门挂 `department:manage`（DepartmentController.java:71-73）。
  //
  // <p>**当下这道判据是冗余的，如实说明**：本页取数的 `GET /departments/tree`（`:48-50`）挂的是**同一个**
  // `department:manage`（1.5 把类级 `hasRole('ADMIN')` 换成了它，并把该码授给持有「部门管理」菜单的五个角色
  // ——`DepartmentController.java:26-28` 记这是本项唯一一处实质扩权）。于是"能渲染出这棵树"本身就蕴含
  // "持有该码"，而删除端点挂的也是它 ⇒ 对任何加载得出本页的人，判据恒真，**不会真的隐藏任何按钮**。
  //
  // 仍然挂上的理由：码与端点**逐字对应**（DELETE /departments/{id}），且一旦后端把读与写拆成两个码
  // （`department:read` / `department:manage`），这里的判据立刻变成有效的收窄——不挂则会在那时静默漏出。
  // 这与「读码挂在读端点上 ⇒ 不收口」（见 `QuoteDetailPage` 导出 PDF）**不是**同一种形态，故不豁免。
  //
  // <p>顺带记录一处**与权限无关的既有缺陷**（本次刻意不修，避免超出 086 范围）：本文件 `load()` 里
  // `const t = await fetchDepartmentTree()` **遮蔽了 i18n 的 `t`**，故其 catch 分支的
  // `t('pages.departmentList.msgLoadFailed')` 会对数组调用函数而抛 TypeError——加载失败时用户看不到任何提示。
  // 同一函数里 `walk(n.children, …)` 也未防 `children` 为空，`children` 缺失时同样抛错。
  const canManage = usePerms([PERMS.departmentManage])

  // 095 T032：断点二择，照抄仓内唯一范式（pages/map/UsageMapPage.tsx）。
  // ⚠️ 测试环境里 `setup.ts` 把 `matchMedia` **恒桩成 `matches: false`** ⇒ `screens.lg` 恒假
  // ⇒ `isMobile` **恒真** ⇒ **桌面分支在默认桩下跑不到**。要断桌面分支的用例必须在
  // **本测试文件内**覆盖 `matchMedia`（范式见 UsageMapPage.test.tsx），且不得改全局桩。
  const screens = Grid.useBreakpoint()
  const isMobile = !screens.lg

  const load = async () => {
    setLoading(true)
    try {
      const t = await fetchDepartmentTree()
      setTreeData(t)
      const options: { value: number; label: string }[] = []
      const allKeys: string[] = []
      const walk = (nodes: Department[], prefix: string) => {
        for (const n of nodes) {
          options.push({ value: n.id, label: prefix + n.name })
          if (n.children && n.children.length > 0) {
            allKeys.push(String(n.id))
          }
          walk(n.children, prefix + '  ')
        }
      }
      walk(t, '')
      setFlatOptions(options)
      setExpandedKeys(allKeys)
    } catch (err) {
      messageApi.error(extractErrorMessage(err, t('pages.departmentList.msgLoadFailed')))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const filteredTreeData = useMemo(() => {
    if (!q.trim()) {
      return treeData
    }

    const filterTree = (nodes: Department[]): Department[] => {
      return nodes.reduce<Department[]>((acc, node) => {
        const matchesSearch = node.name.toLowerCase().includes(q.toLowerCase())
        const filteredChildren = filterTree(node.children)

        if (matchesSearch || filteredChildren.length > 0) {
          acc.push({
            ...node,
            children: filteredChildren.length > 0 ? filteredChildren : node.children,
          })
        }

        return acc
      }, [])
    }

    return filterTree(treeData)
  }, [treeData, q])

  // 使用过滤后的树数据
  const displayTreeData = filteredTreeData

  // 095 T008：清空关键词时要恢复的「全部展开」键。与 `load()` 里收集 `allKeys` 是**同一判据**
  // （有子节点 ⇒ 可展开），此处由 `treeData` 派生，故保存/删除触发 `load()` 后自动跟随。
  const expandAllKeys = useMemo(() => {
    const keys: string[] = []
    const collect = (nodes: Department[]) => {
      for (const node of nodes) {
        if (node.children && node.children.length > 0) {
          keys.push(String(node.id))
        }
        collect(node.children)
      }
    }
    collect(treeData)
    return keys
  }, [treeData])

  // 自动展开匹配搜索的节点
  //
  // 095 T008 订正（**有意的可见行为变化**）：`else` 分支原为 `setExpandedKeys([])`，
  // 与 `load()` 里「收集 allKeys 并 setExpandedKeys」的意图**相反**——`load()` 完成后
  // `treeData` 变化即触发本 effect，于是每次加载后展开态都被无条件抹掉、`allKeys` 白算。
  // 现改为恢复「全部展开」。首次加载与每次保存/删除刷新后，树由「恒收起」变「展开全部」。
  // ⚠️ 但只改这一行**还不够** —— 展开键的类型不一致会让两处都静默失效，
  // 见 `expandedKeys` 声明处的追加订正。单测（`DepartmentListPage.test.tsx`）同时守着这两层。
  useEffect(() => {
    if (q.trim()) {
      const keys: string[] = []
      const findKeys = (nodes: Department[], parentKey?: number) => {
        for (const node of nodes) {
          if (node.name.toLowerCase().includes(q.toLowerCase())) {
            if (parentKey) keys.push(String(parentKey))
          }
          findKeys(node.children, node.id)
        }
      }
      findKeys(treeData)
      setExpandedKeys(keys)
    } else {
      setExpandedKeys(expandAllKeys)
    }
  }, [q, treeData, expandAllKeys])

  const openCreate = () => {
    setEditing(null)
    form.resetFields()
    setModalOpen(true)
  }

  const openEdit = (row: Department) => {
    setEditing(row)
    form.setFieldsValue({ 
      name: row.name, 
      parentId: row.parentId,
      description: row.description,
      sortOrder: row.sortOrder,
    })
    setModalOpen(true)
  }

  const openDetail = (row: Department) => {
    setDetailData(row)
    setDetailOpen(true)
  }

  const onSave = async () => {
    const values = await form.validateFields()
    const payload: DepartmentPayload = { 
      name: values.name.trim(), 
      parentId: values.parentId,
      description: values.description,
      sortOrder: values.sortOrder,
    }
    setSaving(true)
    try {
      if (editing) {
        await updateDepartment(editing.id, { ...payload, version: editing.version })
        messageApi.success(t('pages.departmentList.msgSaved'))
      } else {
        await createDepartment(payload)
        messageApi.success(t('pages.departmentList.msgCreated'))
      }
      setModalOpen(false)
      void load()
    } catch (err) {
      messageApi.error(extractErrorMessage(err, t('pages.departmentList.msgSaveFailed')))
    } finally {
      setSaving(false)
    }
  }

  const onDelete = async (row: Department) => {
    try {
      await deleteDepartment(row.id)
      messageApi.success(t('pages.departmentList.msgDeleted'))
      void load()
    } catch (err) {
      messageApi.error(extractErrorMessage(err, t('pages.departmentList.msgDeleteFailed')))
    }
  }

  // 095 T030：上级部门名的解析留在页面（它依赖本页持有的 `flatOptions`），
  // `DepartmentDetail` 只接收**已解析好的**结果 ⇒ 组件保持纯展示、可独立测。
  // 未命中时回落到原始 id，与抽取前的行为逐字一致。
  const detailParentLabel = detailData?.parentId
    ? flatOptions.find((o) => o.value === detailData.parentId)?.label || String(detailData.parentId)
    : undefined

  const renderTreeNode = (node: Department): React.ReactNode => (
    <Tree.TreeNode
      key={node.id}
      title={
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <span>
            {(node.childCount ?? 0) > 0 ? '📁' : '📄'} <Highlight text={node.name} keyword={q} />
            <Tag style={{ marginLeft: 8 }}>{node.id}</Tag>
            {(node.memberCount ?? 0) > 0 && <Tag color="blue" style={{ marginLeft: 4 }}>{node.memberCount} {t('pages.departmentList.colMembers')}</Tag>}
          </span>
          <Space>
            <Button size="small" type="link" icon={<EyeOutlined />} onClick={(e) => { e.stopPropagation(); openDetail(node) }}>
              {t('pages.departmentList.btnDetail')}
            </Button>
            <Button size="small" type="link" icon={<EditOutlined />} onClick={(e) => { e.stopPropagation(); openEdit(node) }}>
              {t('pages.departmentList.btnEdit')}
            </Button>
            {canManage[PERMS.departmentManage] && (
              <Popconfirm
                title={t('pages.departmentList.confirmDelete', { name: node.name })}
                // 095 T025：风险提示用**该节点自己的**数字现算，不用通用警告。
                // 后端确会拦截（`DepartmentService.delete` 对「有子部门」与「有成员」
                // 抛同一码 DEPARTMENT_HAS_CHILDREN_OR_MEMBERS），故这两句是**告知**、
                // 不是前端自己推断的规则。措辞**不得**写成「不可恢复」——本系统是
                // 逻辑删除 + 回收站，那句在这里不成立，写上去就是新的名实不符。
                description={t('pages.departmentList.confirmDeleteRisk', {
                  children: node.childCount ?? 0,
                  members: node.memberCount ?? 0,
                })}
                onConfirm={(e) => { if (e) onDelete(node) }}
              >
                <Button size="small" type="link" danger icon={<DeleteOutlined />}>
                  {t('pages.departmentList.btnDelete')}
                </Button>
              </Popconfirm>
            )}
          </Space>
        </div>
      }
    >
      {node.children && node.children.map(child => renderTreeNode(child))}
    </Tree.TreeNode>
  )

  return (
    <Card
      title={t('pages.departmentList.title')}
      loading={loading}
      extra={
        <Space>
          <Button icon={<ReloadOutlined />} onClick={() => void load()}>
            {t('pages.departmentList.btnRefresh')}
          </Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            {t('pages.departmentList.btnAdd')}
          </Button>
        </Space>
      }
    >
      <div style={{ marginBottom: 16 }}>
        <Input
          // 095 T037：无障碍名。**必须经 `t()`** —— 门禁 R6 抓裸 `placeholder`/`aria-label`
          // 字面量，其白名单是**冻结台账**，写死字面量会迫使台账增长、`ui:check` 直接红。
          aria-label={t('pages.departmentList.ariaSearch')}
          placeholder={t('pages.departmentList.placeholderSearch')}
          prefix={<SearchOutlined />}
          value={searchValue}
          onChange={(e) => setSearchValue(e.target.value)}
          allowClear
          style={{ maxWidth: 300 }}
        />
      </div>

      {/* 095 T016：树无数据时给明确空态。
          **不**用 `<PageState state="loading" />` 顶替上面的 `<Card loading>`——
          PageState 的文件头明文禁止：列表类页面已有自己的 Card 骨架，两层占位会打架。
          已知的语义边界（如实记，不在本项消解）：默认文案「暂无数据」与同屏搜索框并置时，
          「库里没有部门」与「搜不到」两种含义会含混。 */}
      {displayTreeData.length === 0 ? (
        <PageState state="empty" />
      ) : (
        <Tree
          // 095 T037：树容器的可朗读名称。⚠️ **结构前提 ≠ 键盘行为已验**：
          // antd `Tree` 自带 `role="tree"`/`treeitem` 与方向键处理，但 jsdom 下
          // **没有真实焦点模型** ⇒ 本项只断言「role 与 aria-label 在场」，
          // **不声称验证了方向键行为**（见 quickstart.md 的口径边界）。
          aria-label={t('pages.departmentList.ariaTree')}
          expandedKeys={expandedKeys}
          // 唯一的外部入口：rc-tree 回传的键已是字符串（它自己从元素 key 取的），
          // 这里显式 `String()` 是为了让「展开态一律是字符串」这条约定在类型上封闭。
          onExpand={(keys) => setExpandedKeys(keys.map(String))}
          showLine
          style={{ minHeight: 200, width: '100%' }}
        >
          {displayTreeData.map(node => renderTreeNode(node))}
        </Tree>
      )}

      <Modal
        title={editing ? t('pages.departmentList.modalEditTitle') : t('pages.departmentList.modalAddTitle')}
        open={modalOpen}
        onOk={() => void onSave()}
        confirmLoading={saving}
        onCancel={() => setModalOpen(false)}
        okText={t('pages.departmentList.btnSave')}
        destroyOnClose
        width={640}
      >
        <Form form={form} name="departmentForm" layout="vertical">
          <FormGrid minItemWidth={VERTICAL_MIN_ITEM_WIDTH}>
            <Form.Item name="name" label={t('pages.departmentList.formNameLabel')} rules={[{ required: true, message: t('pages.departmentList.formNameRequired') }]}>
              <Input placeholder={t('pages.departmentList.formNamePlaceholder')} />
            </Form.Item>
            <Form.Item name="parentId" label={t('pages.departmentList.formParentLabel')}>
              <Select
                allowClear
                placeholder={t('pages.departmentList.formParentPlaceholder')}
                options={flatOptions.filter((o) => o.value !== editing?.id)}
              />
            </Form.Item>
          </FormGrid>
          <Form.Item name="description" label={t('pages.departmentList.formDescLabel')}>
            <Input.TextArea rows={3} placeholder={t('pages.departmentList.formDescPlaceholder')} maxLength={500} showCount />
          </Form.Item>
          <Form.Item name="sortOrder" label={t('pages.departmentList.formSortLabel')}>
            <InputNumber min={0} max={9999} placeholder={t('pages.departmentList.formSortPlaceholder')} style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>

      {/* 095 T030/T032：详情体已抽成 `components/DepartmentDetail`（纯展示）。
          容器按断点二择：窄屏走底部抽屉、否则走对话框。两个容器**都渲染**，
          靠 `open` 二择 —— 这样两个分支在任何环境下都存在，不依赖条件渲染的分支裁剪。 */}
      <Modal
        title={t('pages.departmentList.modalDetailTitle')}
        open={detailOpen && !isMobile}
        onCancel={() => setDetailOpen(false)}
        footer={null}
        destroyOnClose
        width={600}
      >
        {detailData && <DepartmentDetail department={detailData} parentLabel={detailParentLabel} />}
      </Modal>

      <Drawer
        title={t('pages.departmentList.modalDetailTitle')}
        open={detailOpen && isMobile}
        onClose={() => setDetailOpen(false)}
        placement="bottom"
        height="auto"
      >
        {detailData && <DepartmentDetail department={detailData} parentLabel={detailParentLabel} />}
      </Drawer>
    </Card>
  )
}
