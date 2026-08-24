# Research: 菜单对标成熟 CRM 调整与规划模块

**Branch**: `044-menu-crm-alignment` | **Date**: 2026-08-24

## 1. 命名与分类对标

**Decision**: 基于 043 差距分析与成熟 CRM（Salesforce Sales/Marketing/Service Cloud、纷享销客业务域）命名习惯：
- "商机"与"销售机会"保留双入口但分属商机(父)/销售机会(子)——沿用既有业务语义，不强行合并；
- "外勤拜访"归销售管理（销售动作）；"客户360/健康度"并入客户管理（客户域）；
- 营销中心聚合获客手段（活动/邮件/表单/标签）；服务协作聚合服务与内部协作（工单/知识库/公告/审批）；
- 新增占位项按 043 建议归组：Playbook/签署入销售、自动化/落地页入营销、门户/调查/SLA日历入服务、开放平台/字段权限/多币种入系统管理。

**Rationale**: 命名与分组服务于"找得到"——沿用成熟 CRM 的业务域划分，减少认知成本。

## 2. 占位项实现

**Decision**: 路由分组数组项增加可选 `planned?: boolean` 标志；`toItems` 渲染时 `planned` → `{ disabled: true, label: '名称（规划中）' }`，不注册路由（点击 disabled 无响应，防 404）。

**Rationale**: disabled 是 antd Menu 原生能力，无需路由占位；"规划中"后缀显式区分已实现/未实现，未来实现后移除 planned 标志即"点亮"。

**Alternatives considered**: 创建空路由页面——多余且可能误入；仅改菜单文案——无法呈现完整地图。占位 disabled 最简洁。
