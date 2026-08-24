# 研究：公告与内部协作

## R1 公告

**决策**: announcement 表：title/content(富文本)/pinned(置顶)/expires_at(过期)/created_by。列表查询：未过期 + 置顶优先 + 时间倒序；已读用 announcement_read（幂等）。过期公告自动隐藏（查询过滤 expires_at > now 或 IS NULL）。

## R2 已读

**决策**: 标记已读 insert（user×announcement 唯一，幂等）；未读角标 = 未过期公告 - 已读数。

## R3 评论

**决策**: comment 表通用（entity_type/entity_id/content/author_id）；实体：CUSTOMER/LEAD/OPPORTUNITY/TICKET。删除仅作者/管理员（逻辑删）。@提及：正则 `@([\u4e00-\u9fa5A-Za-z0-9_]+)` 匹配用户 username/displayName → 通知（026，entityType=COMMENT + path）。

## R4 通知跳转

**决策**: 026 notify(entityType="COMMENT", entityId=实体 id, path="/customers/{id}")——前端通知点击跳详情并定位评论。

## R5 前端

**决策**: 通用 CommentSection 组件（Timeline 评论列表 + 输入框 + @提及提示）嵌入客户/线索/商机/工单详情；首页公告卡（最新 5 条 + 未读角标 + 已读操作）；公告管理页（系统管理：CRUD + 置顶 + 过期时间）。
