/**
 * 部门详情（095 T030）。
 *
 * ## 为什么它必须是一个组件，而不是「抽一下更干净」
 *
 * `068` 的 **T035 明文要求 `components/DepartmentDetail.test.tsx`**，而抽取之前
 * 这份详情体是**写在页面文件里的内联 JSX** —— 那个测试文件**没有被测对象**。
 * T030 与 T035 是**硬依赖**，不是风格偏好。（本项只抽这一个：`068` 的 T011
 * 搜索框、T012 树节点**不抽** —— 没有任何任务硬依赖它们。）
 *
 * ## 落位与形态
 *
 * - 放 `components/` 顶层而**不是** `components/ui/`：它懂 `Department`，
 *   是**业务组件**；放进那个 barrel 会稀释 088 建立的设计系统语义。
 * - **纯展示**：props 只有 `department` 与已解析好的 `parentLabel`，
 *   不取数、不含业务规则、不持有状态。
 * - **维持**逐字段 `<div>` 形态，**不**改用 `<Descriptions>`：后者会立刻撞门禁
 *   **R8**（`column` 写死为大于 1 的字面量即命中），而本项不属列数治理范围。
 *
 * 门禁 **R7** 要求 `components/**` 下的组件被产品文件引用 —— 由
 * `pages/departments/DepartmentListPage.tsx` 的 import 满足。
 */

import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import type { Department } from '../types/department'

export interface DepartmentDetailProps {
  /** 要展示的部门。取的是列表里已加载的那一行的**同引用**，故无加载/失败窗口。 */
  department: Department
  /**
   * **已解析好的**上级部门名（含缩进前缀）。由调用方从部门树拍平的选项里查得。
   * 不传即视为顶级部门 —— 页面只在 `parentId` 存在时才传值。
   */
  parentLabel?: ReactNode
}

export default function DepartmentDetail({ department, parentLabel }: DepartmentDetailProps) {
  const { t } = useTranslation()

  const row = (label: string, value: ReactNode) => (
    <div style={{ marginBottom: 16 }}>
      <strong>{label}：</strong>
      {value}
    </div>
  )

  return (
    <div>
      {row(t('pages.departmentList.detailName'), department.name)}
      {row(t('pages.departmentList.detailId'), department.id)}
      {row(t('pages.departmentList.detailParent'), parentLabel ?? t('pages.departmentList.detailTopLevel'))}
      {row(t('pages.departmentList.detailDesc'), department.description || '-')}
      {row(t('pages.departmentList.detailSort'), department.sortOrder ?? 0)}
      {row(
        t('pages.departmentList.detailCreatedAt'),
        department.createdAt ? new Date(department.createdAt).toLocaleString('zh-CN') : '-',
      )}
      {row(t('pages.departmentList.detailCreatedBy'), department.createdBy || '-')}
      {row(t('pages.departmentList.detailMembers'), department.memberCount ?? 0)}
      {row(t('pages.departmentList.detailChildren'), department.childCount ?? 0)}
    </div>
  )
}
