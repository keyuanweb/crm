# 研究：客户查重合并

## R1 查重规则

**决策**: 名称归一化（去空格/大小写/全半角）后：
- 名称归一化完全相同 + 公司相同 → 相似度 100（精确重复）
- 名称归一化包含关系（A 含 B 或 B 含 A）+ 公司相同 → 相似度 90（高度相似）
- 电话/邮箱精确相同 → 相似度 85（联系方式重复）
分组扫描（分批 500），组内两两成对。

## R2 关联数据转移

**决策**: 合并时批量 UPDATE 关联表 customer_id → 主记录：
sales_order / opportunity / contact / follow_up / ticket / customer_tag（去重）/ customer_share（去重）。

## R3 字段冲突

**决策**: 主记录字段非空保留；主空则取从记录值（name/company/phone/email/address/contact_person/remark）。

## R4 从记录处理

**决策**: 从记录逻辑删除（deleted=1）+ 进回收站（025 RecycleBinService，业务类型 CUSTOMER，可恢复）；恢复时数据已转移主记录，从记录仅恢复自身。

## R5 前端

**决策**: 客户管理分组加"查重合并"页：扫描按钮 → 重复对列表（主/从 + 相似度 + 关联计数 + 合并按钮）→ 确认弹窗（字段冲突提示）→ 合并成功刷新。
