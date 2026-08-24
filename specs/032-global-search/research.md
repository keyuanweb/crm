# 研究：全局搜索

## R1 搜索范围

**决策**: 6 实体：
- 客户：name/company/contact_person/phone
- 线索：name/company
- 联系人：name/phone
- 商机：name（含客户名）
- 工单：title
- 产品：name/code

## R2 数据权限

**决策**: ADMIN 全量查询；其他角色按 created_by/owner_id（SALES 本人）过滤。客户按 created_by；线索按 owner_id/created_by；联系人按 created_by；商机按 owner_id；工单按 assignee/created_by。

## R3 响应结构

**决策**: `SearchResponse { keyword, groups: [{ type: 'CUSTOMER'|..., label: '客户', items: [{ id, title, subtitle, path }] }] }`。下拉 Top 5/实体；结果页 `full` 返回单实体分页（type 过滤）。

## R4 多关键字

**决策**: keyword 按空格拆分，各词 AND（LIKE %词%）。

## R5 前端

**决策**: Header 搜索框（AutoComplete）防抖 300ms 调 searchAll；下拉分组展示；回车 navigate(`/search?q=${kw}`)；结果页 SearchResultPage（Tabs 分组 + 高亮关键字 + 分页）。
