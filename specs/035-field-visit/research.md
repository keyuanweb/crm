# 研究：外勤拜访

## R1 计划状态机

**决策**: PLANNED（计划）→ 签到 → DONE（已完成）；取消 → CANCELED。拜访时间已过可补签到（标记补签 flag）。

## R2 签到

**决策**: 浏览器 Geolocation API 取经纬度；地址 = 坐标原文（或前端可选逆地址）；记录时间。防重复：同计划已 DONE 则拒绝（乐观 UPDATE WHERE status=PLANNED）。

## R3 小结转跟进

**决策**: 签到带小结 → 自动 insert follow_up（type=VISIT、content=小结、customer_id、created_by=签到人）→ 客户详情时间线可见（复用 FollowUpTimeline）。

## R4 提醒

**决策**: 计划时间前 15 分钟用 026 通知（创建计划时注册延迟任务——简化：签到当天首页提醒，不做定时器；本期跳过定时提醒，仅记录计划时间）。

## R5 统计

**决策**: 按销售（created_by）当月拜访数/完成率 + 按客户拜访次数。SQL group by。

## R6 前端

**决策**: 销售分组加"拜访"页：统计卡（本月/完成率）+ 列表（状态筛选 + 签到/取消操作 + 详情）+ PWA 移动端详情页"立即签到"（Geolocation）。
