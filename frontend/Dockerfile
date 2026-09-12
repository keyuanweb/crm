# 前端镜像：在**构建期**产出静态产物，再由 nginx 提供（FR-G20）。
#
# 改造前编排里没有这个文件：`crm-frontend` 直接用 `nginx:alpine` 并把宿主机
# `./frontend/dist` 挂进容器。而 `dist` 既不随仓库交付（构建产物被 .gitignore/.dockerignore 排除），
# 也不被任何编排步骤生成 —— 干净检出一键启动只会得到**空白页**，且失败是静默的
# （nginx 正常起来、正常返回 404，看不出是"没构建"还是"配错了"）。

# ==================== 构建阶段 ====================
FROM node:20-alpine AS builder

WORKDIR /app

# pnpm 由 corepack 按 package.json 的 packageManager 字段就地启用，
# 避免镜像里预装的 pnpm 与 pnpm-lock.yaml 的锁格式版本不匹配而拒绝 --frozen-lockfile。
RUN corepack enable

# 先只复制依赖清单：源码改动不会使依赖安装那一层缓存失效
COPY frontend/package.json frontend/pnpm-lock.yaml frontend/pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile

# 再复制源码并构建。`pnpm build` = `tsc --noEmit && vite build`：
# 类型检查在构建期一并执行，镜像不会带出"能打包但类型不过"的产物。
COPY frontend/ ./
RUN pnpm build

# ==================== 运行阶段 ====================
FROM nginx:alpine

# 只搬运产物：node 工具链、源码与 devDependencies 都不进入运行镜像
COPY --from=builder /app/dist /usr/share/nginx/html

EXPOSE 80
