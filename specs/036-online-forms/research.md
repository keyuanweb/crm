# 研究：在线表单

## R1 字段配置

**决策**: fields JSON：`[{ field, label, type: TEXT/TEL/EMAIL/TEXTAREA, required }]`。内置字段映射 lead：name（姓名）/company（公司）/phone（电话）/email（邮箱）。其余字段存 submission payload（快照）。

## R2 提交建线索

**决策**: 校验必填 + 长度（name ≤100/company ≤100/phone ≤30/email ≤100）→ 防重复（email 或 phone 已存在未删除 lead → 409 提示"已收到申请"）→ 建 lead（status=NEW，source=表单 source，默认 WEBSITE）→ form_submission 存 payload + IP + 时间。

## R3 频控

**决策**: 内存 ConcurrentHashMap<ip, Deque<timestamp>>，1 分钟窗口 ≤3 次，超限 429。防爬。

## R4 发布

**决策**: 外链 = `${baseUrl}/f/{id}`（前端公开页渲染表单提交）；内嵌代码 = `<script src=...>`（简化：提供 iframe 嵌入代码片段）。本期前端做 `/f/:id` 公开提交页。

## R5 前端

**决策**: 营销分组加"在线表单"页：表单列表（CRUD + 启用/停用 + 外链复制 + 提交记录数）+ 配置弹窗（字段行编辑器：field/label/type/required）+ 公开提交页 /f/:id（动态渲染字段 + 提交）。
