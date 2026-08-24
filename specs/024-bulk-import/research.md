# 研究：批量导入设计

## R1 复用 016 模式

**决策**: 复制 CustomerExcelService 的 POI 解析模式（WorkbookFactory 读 xlsx、逐行 Cell 取值、ImportResult 反馈），扩展 LeadExcelService / ContactExcelService。

## R2 线索导入列

**决策**: 模板列：姓名* / 公司* / 职位 / 电话 / 邮箱 / 来源 / 评分 / 备注。校验：姓名+公司必填；来源 ∈ WEBSITE/AD/EXHIBITION/REFERRAL/COLD_CALL/OTHER；评分 0-100（可选，导入后 019 自动评分覆盖）。created_by = 当前用户。

## R3 联系人导入列

**决策**: 模板列：姓名* / 客户名称* / 职位 / 电话 / 邮箱 / 角色 / 备注。校验：姓名+客户名称必填；客户按名称精确匹配（不区分大小写）customerId，不匹配该行失败"客户不存在"。created_by = 当前用户。

## R4 失败明细

**决策**: `GET /leads/import-failures` 返回导入失败明细（行号+原始数据+原因），用文本/xlsx 简单实现；或前端直接用页面结果（成功/失败数 + 失败行）展示，下载为补充。

## R5 审计

**决策**: 导入成功后在 AuditService 记录（"导入线索 N 条"），失败明细不单独审计。
