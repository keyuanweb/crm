# Implementation Plan: 公告与内部协作

**Branch**: `037-announcements` | **Date**: 2026-08-23 | **Spec**: [spec.md](./spec.md)

## Summary

新增团队公告（announcement + announcement_read）+ 业务记录评论协作（comment 多实体 + @提及 → 026 通知）；`AnnouncementService`（公告 CRUD + 已读标记 + 未读角标）+ `CommentService`（评论 CRUD + @提及解析通知）；前端公告管理页 + 首页公告卡 + 详情页评论区（客户/线索/商机/工单）。

## Technical Context

**Language/Version**: Java 17（后端）+ TypeScript/React 18（前端）

**Primary Dependencies**: MyBatis-Plus、026 NotificationService、antd（Card/Timeline/Avatar）

**Storage**: 3 表 + Flyway V52

**Testing**: JUnit 5 + Mockito（公告 CRUD/已读/@提及解析）、集成（AnnouncementIT）、前端（公告卡 + 评论区渲染）

**Target Platform**: Web

**Project Type**: Web 应用

**Performance Goals**: 公告/评论操作 ≤ 100ms；未读角标查询 ≤ 200ms

**Constraints**: 公告置顶/过期自动隐藏；已读幂等；@提及正则解析（@用户名 → userId）→ 026 通知带跳转

**Scale/Scope**: 3 表 + 1 迁移 + 2 Service + 2 Controller + 前端公告页/首页卡/评论区

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 章程条款 | 门禁要求 | 状态 |
|---|---|---|
| 原则一：契约优先的 API 设计 | 端点契约先于实现 | ✅ 满足（contracts/announcements.md） |
| 原则二：分层架构与关注点分离 | Controller→Service→Repository | ✅ 满足（Announcement/Comment 分离） |
| 原则三：数据完整性、安全与校验 | 权限/防注入 | ✅ 满足（announcement:manage + 评论正文校验 + @提及白名单） |
| 原则四：测试优先与质量门禁 | 测试先于实现 | ✅ 满足（AnnouncementServiceTest/CommentServiceTest/AnnouncementIT + 前端测试） |
| 原则五：简洁、可维护与可观测 | 避免过度设计 | ✅ 满足（正则 @提及 + 通知复用） |

**结论**: 无门禁违规。

## Project Structure

### Source Code

```text
backend/src/main/java/com/crm/
├── entity/Announcement.java / AnnouncementRead.java / Comment.java
├── repository/3 Mapper
├── dto/announcement/AnnouncementRequest.java / AnnouncementResponse.java / CommentRequest.java / CommentResponse.java
├── service/AnnouncementService.java   # 公告 CRUD + 置顶/过期 + 已读标记 + 未读列表
├── service/CommentService.java        # 评论 CRUD + @提及解析 → 026 通知
├── controller/AnnouncementController.java # /api/v1/announcements
├── controller/CommentController.java      # /api/v1/comments
└── security/RequirePermission           # announcement:manage

backend/src/main/resources/db/migration/V52__announcements.sql
backend/src/test/java/com/crm/
├── service/AnnouncementServiceTest.java
├── service/CommentServiceTest.java
└── integration/AnnouncementIT.java

frontend/src/
├── types/announcement.ts / services/announcementService.ts / commentService.ts
├── components/CommentSection.tsx       # 通用评论区（客户/线索/商机/工单详情复用）
├── components/AnnouncementCard.tsx     # 首页公告卡（未读角标）
├── pages/announcements/AnnouncementPage.tsx # 公告列表/管理
└── App.tsx                             # 路由（系统管理）
```

**Structure Decision**: 公告：pinned 置顶排序 + expiresAt 过期隐藏（查询过滤）；已读用 announcement_read（user × announcement，幂等 insert ignore）。评论：comment 表多实体通用（entity_type/entity_id），@提及用正则 `@用户名` 匹配已启用用户 → 026 通知（entityType=COMMENT + path 跳转）；评论删除仅作者/管理员（逻辑删）。前端 CommentSection 复用组件嵌入 4 个详情页 + 首页公告卡。

## Complexity Tracking

> 无违规，本表留空。
