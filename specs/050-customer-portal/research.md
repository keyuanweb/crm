# Research: 客户自助门户模块

**Branch**: `050-customer-portal` | **Date**: 2026-08-25

## 1. 门户形态

**Decision**: 与主系统同源部署的公开前端路由（/portal），后端独立 `/api/v1/portal/**` 公开接口。门户三块：知识库浏览（PUBLISHED 文章搜索/详情）、在线提单（手机/邮箱识别客户 + 工单号查询码）、进度查询（工单号+手机/邮箱双验证）。

**Rationale**: 同源部署零额外运维；公开接口独立命名空间便于白名单；双验证防枚举（章程原则三）。

**Alternatives considered**: 独立子域名/新应用——部署复杂度高；复用内部工单接口——暴露内部字段与权限，否。

## 2. 客户识别

**Decision**: 提单按手机号或邮箱匹配联系人（Contact）→ 得 customerId；未匹配 → 422（提示联系销售注册，不自动建客户）。

**Rationale**: 自动建客户易产生垃圾数据；联系人关联客户是既有模型，识别链路最短。

## 3. 工单创建复用

**Decision**: 门户提单调用 TicketService.create 的既有逻辑（SLA 计算/状态 INIT），但入口 DTO 为轻量字段（phone/email/title/description/priority），Service 层识别客户后转内部 TicketRequest。

**Rationale**: 复用 SLA/状态机逻辑；门户 DTO 不暴露内部字段（FR-P08）。
