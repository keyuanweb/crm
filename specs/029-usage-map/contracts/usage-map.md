# 契约：使用地图流程定义

## 业务主流程（main）

```
线索管理 → 客户转化 → 客户建档 → 商机推进 → 报价单 → 合同审批 → 订单回款 → 客户服务
```

节点：leads(/leads)、customer(/customers)、opportunity(/opportunities)、quote(/quotes)、contract(/contracts)、order(/orders)、service(/tickets)

## 角色流程

| 流程 | 角色 | 节点链 |
|---|---|---|
| 销售流程 | SALES | 线索认领→客户跟进→商机推进→报价→成交→回款 |
| 客服流程 | SUPPORT | 工单接收→处理→回复→关闭→知识库 |
| 管理流程 | ADMIN | 用户/角色→工作流→报表→系统维护→审计 |

## 高频操作（QuickAction）

- 创建客户 /customers（打开新增）
- 记跟进 /customers（跟进入口）
- 新增商机 /opportunities
- 新建工单 /tickets
- 创建任务 /tasks
- 新建报价 /quotes

## 备注

- 节点 path 与现有前端路由对齐；跳转受后端既有权限控制。
- 无后端 API 变更。
