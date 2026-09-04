# Contract: 部门管理 API

## 现有接口（复用，无需修改）

### 1. 获取部门树

**Endpoint**: `GET /api/v1/departments/tree`

**Response**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "departments": [
      {
        "id": 1,
        "name": "技术部",
        "parentId": null,
        "version": 0,
        "createdAt": "2024-01-01T00:00:00",
        "children": [
          {
            "id": 2,
            "name": "前端组",
            "parentId": 1,
            "version": 0,
            "createdAt": "2024-01-01T00:00:00",
            "children": []
          }
        ]
      }
    ]
  }
}
```

**权限**: ADMIN

---

### 2. 创建部门

**Endpoint**: `POST /api/v1/departments`

**Request**:
```json
{
  "name": "测试部门",
  "parentId": 1,
  "version": 0
}
```

**Response**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 3,
    "name": "测试部门",
    "parentId": 1,
    "version": 0,
    "createdAt": "2024-01-01T00:00:00",
    "children": []
  }
}
```

**权限**: ADMIN

**错误响应**:
- `400`: 部门名称已存在
- `400`: 上级部门不存在
- `400`: 部门树超过最大深度

---

### 3. 更新部门

**Endpoint**: `PUT /api/v1/departments/{id}`

**Path Parameters**:
- `id`: 部门 ID

**Request**:
```json
{
  "name": "更新后的名称",
  "parentId": 1,
  "version": 0
}
```

**Response**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 3,
    "name": "更新后的名称",
    "parentId": 1,
    "version": 1,
    "createdAt": "2024-01-01T00:00:00",
    "children": []
  }
}
```

**权限**: ADMIN

**错误响应**:
- `400`: 部门名称已存在
- `400`: 上级部门不存在
- `400`: 部门树超过最大深度
- `409`: 版本冲突

---

### 4. 删除部门

**Endpoint**: `DELETE /api/v1/departments/{id}`

**Path Parameters**:
- `id`: 部门 ID

**Response**:
```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

**权限**: ADMIN

**错误响应**:
- `400`: 该部门存在子部门，无法删除
- `400`: 该部门存在成员，无法删除
- `404`: 部门不存在

## 新增接口（可选，用于优化）

### 5. 搜索部门（可选）

**Endpoint**: `GET /api/v1/departments/search?keyword={keyword}`

**Query Parameters**:
- `keyword`: 搜索关键字（部门名称模糊匹配）

**Response**: 同"获取部门树"，但仅返回匹配节点及其父节点路径

**权限**: ADMIN

**说明**: 如果采用前端搜索，此接口不需要实现

---

### 6. 部门详情（可选）

**Endpoint**: `GET /api/v1/departments/{id}`

**Path Parameters**:
- `id`: 部门 ID

**Response**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 3,
    "name": "测试部门",
    "parentId": 1,
    "description": "测试用",
    "sortOrder": 10,
    "version": 0,
    "createdAt": "2024-01-01T00:00:00",
    "createdBy": "admin",
    "memberCount": 5,
    "childCount": 2,
    "children": []
  }
}
```

**权限**: ADMIN

**说明**: 如果详情数据可以通过树接口获取，此接口不需要实现

## 约束

1. 所有接口必须经过 JWT 认证
2. 仅 ADMIN 角色可访问
3. 统一响应格式
4. 错误信息使用中文
5. 部门名称唯一性在相同 parent_id 下校验
6. 防止部门环（parent_id 不能是自身的子孙）
7. 部门树最大深度 5 层
