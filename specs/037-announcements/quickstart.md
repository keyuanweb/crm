# 快速开始：公告与内部协作

## 后端

1. Flyway V52：announcement/announcement_read/comment 三表。
2. 实体/Mapper。
3. `AnnouncementService`（CRUD + 置顶/过期 + 已读）+ `CommentService`（CRUD + @提及通知）。
4. 2 Controller。
5. 测试：AnnouncementServiceTest + CommentServiceTest + AnnouncementIT。

## 前端

1. `types/announcement.ts` + 2 services。
2. `CommentSection`（通用评论区）+ `AnnouncementCard`（首页公告卡）。
3. `AnnouncementPage`（公告管理）。
4. 路由注册。

## 验证

- 后端：`mvn test`；前端：`pnpm run typecheck/lint/test`。
- 手动：发公告 → 首页卡未读角标 → 已读 → 详情页评论 @提及 → 对方收到通知。
