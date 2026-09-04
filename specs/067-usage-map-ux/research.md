# Research: 使用地图视觉样式优化

## G6 v5 节点样式支持

### 阴影效果

G6 v5 支持通过 `shadow` 配置节点阴影：

```typescript
node: {
  style: {
    shadow: '0 2px 8px rgba(0,0,0,0.15)',
    borderRadius: 12,
    // ...
  }
}
```

### 悬停交互

G6 v5 支持节点事件监听：

```typescript
graph.on('node:mouseenter', (evt) => {
  const node = evt.item
  graph.updateItem(node, {
    style: {
      shadow: '0 4px 12px rgba(0,0,0,0.25)',
      lineWidth: 3,
    }
  })
})

graph.on('node:mouseleave', (evt) => {
  const node = evt.item
  graph.updateItem(node, {
    style: {
      shadow: '0 2px 8px rgba(0,0,0,0.15)',
      lineWidth: 2,
    }
  })
})
```

### 节点缩放

G6 v5 支持通过 `scale` 属性实现节点缩放：

```typescript
graph.on('node:mouseenter', (evt) => {
  const node = evt.item
  graph.updateItem(node, {
    style: {
      scaleX: 1.05,
      scaleY: 1.05,
    }
  })
})
```

## Ant Design 5 主题变量

Ant Design 5 使用 Token 机制，支持自定义主题：

```typescript
import { ConfigProvider } from 'antd'

<ConfigProvider
  theme={{
    token: {
      colorPrimary: '#1677ff',
      borderRadius: 12,
      // ...
    }
  }}
>
  {/* ... */}
</ConfigProvider>
```

### 响应式断点

使用 `Grid.useBreakpoint()` 获取断点信息：

```typescript
const screens = Grid.useBreakpoint()
const isMobile = !screens.lg
```

## Modal vs Drawer

| 特性 | Modal | Drawer |
|---|---|---|
| 位置 | 页面中心 | 右侧/底部滑出 |
| 移动端适配 | 需调整宽度 | 天然适配（底部滑出） |
| 内容容量 | 中等 | 较大 |
| 实现复杂度 | 低 | 中 |

**Decision**: 桌面端使用 Modal（中心弹出），移动端使用 Drawer（底部滑出），通过 `Grid.useBreakpoint()` 判断。

## 快捷按钮悬停效果

方案 1：CSS `:hover`（推荐）

```css
.quick-button:hover {
  background: #e6f4ff !important;
  color: #1677ff !important;
}
```

方案 2：Ant Design `styles` API

```typescript
<Button
  style={{ background: '#f5f5f5' }}
  styles={{ body: { ':hover': { background: '#e6f4ff' } } }}
>
```

**Decision**: 使用方案 1（CSS `:hover`），更简单且兼容性好。

## 风险评估

### G6 节点样式兼容性

- **风险**: G6 v5 不支持某些 CSS 样式属性（如 `shadow`）
- **缓解**: 测试 G6 v5 文档支持的样式属性，降级到兼容方案（如使用 `lineWidth` 模拟阴影）

### 弹窗遮挡流程图

- **风险**: Modal 遮挡流程图节点
- **缓解**: 调整 Modal 位置（右滑出）或使用 Drawer（右侧滑出）

### 移动端性能

- **风险**: 移动端动画导致性能问题
- **缓解**: 使用 CSS `transform`（GPU 加速），限制动画复杂度

## 参考资源

- [G6 v5 文档](https://g6.antv.antgroup.com/)
- [Ant Design 5 主题配置](https://ant.design/components/theme-cn)
- [Ant Design Grid 响应式](https://ant.design/components/grid-cn)
