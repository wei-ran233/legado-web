# Legado 多用户书城服务器（魔改版）

将 legado 阅读改造为**多用户在线书城服务器**：

- 🔐 **用户体系**：管理员创建账号，普通用户搜索 / 加书架 / 阅读
- 🛡️ **鉴权**：JWT 访问令牌（15 分钟）+ 旋转式 Refresh Token（7 天）+ 登录限速防爆破
- 📱 **两步验证（2FA）**：TOTP（RFC 6238），兼容 Google Authenticator / Aegis / 1Password，含 10 个一次性恢复码
- 📚 **书源管理**：管理员导入 / 编辑 / **上架 / 下架** / 删除书源；下架源对普通用户完全不可见
- 🌐 **阅读链路**：多源聚合搜索（SSE 流式）→ 详情 → 目录 → 正文（正文磁盘缓存全局共享）
- 📱 **响应式 UI**：桌面侧栏 / 移动端底部 TabBar，阅读器支持字号 / 行距 / 页宽 / 4 套主题
- 🐳 **部署**：单 Docker 镜像 + SQLite（WAL），数据全部落盘 `data/` 目录

## 架构

```
Vue3 SPA (modules/web)
   │ Bearer JWT
   ▼
Ktor 服务器 (server/)          ← 新增
   ├─ Auth: JWT + Refresh + TOTP 2FA + 限速
   ├─ Library: 搜索 SSE / 书架 / 目录 / 正文 / 进度
   ├─ Admin: 书源上下架 / 用户管理 / 设置
   ▼
modules/engine (纯 JVM 书源引擎)  ← 自 app 模块移植
   ├─ AnalyzeRule / AnalyzeUrl / webBook（原版逻辑）
   ├─ Rhino JS 引擎（原版沙箱）
   └─ OkHttp 抓取 + 正文磁盘缓存
   ▼
SQLite (Exposed, WAL) + data/ 文件目录
```

> 本仓库仅维护 Web 前端、JVM 服务端和纯 JVM 的 `modules/engine`。
> Android 历史源码仅保留作参考，不再参与构建；继承的 Firebase 配置已移除。
> 保留 `io.legado.app` 包名以兼容移植的引擎代码。

## 快速开始（Docker）

```bash
# 1. 构建镜像
docker compose build

# 2. 修改 docker-compose.yml 中的 JWT_SECRET（必填）！

# 3. 启动
docker compose up -d

# 4. 查看首次生成的管理员密码
docker compose logs | grep -A3 "管理员账号"
```

浏览器打开 `http://<服务器IP>:8080` 即可使用。

## 快速开始（裸机运行）

依赖：JDK 17+（本机构建用 JDK 24 作为编译工具链）

```bash
# 构建后端（无需 Android SDK）
./gradlew :server:installDist

# 构建前端（产物放 data/webapp 供服务器托管）
cd modules/web && pnpm install && pnpm build-only && cd ..
mkdir -p data/webapp && cp -r modules/web/dist/* data/webapp/

# 启动
cd server/build/install/server/bin
JWT_SECRET=$(openssl rand -hex 32) \
ADMIN_PASSWORD=admin12345 \
DATA_DIR=$(pwd)/../../../data PORT=8080 \
./server
```

## 环境变量

| 变量 | 必填 | 说明 |
|---|---|---|
| `JWT_SECRET` | ✅ | JWT 签名密钥，`openssl rand -hex 32` 生成 |
| `PORT` |  | 监听端口，默认 8080 |
| `DATA_DIR` |  | 数据目录（SQLite + 正文缓存 + 图片缓存 + 前端），默认 `data` |
| `ADMIN_USERNAME` |  | 首次启动管理员用户名，默认 `admin` |
| `ADMIN_PASSWORD` |  | 首次启动管理员密码，不设则随机生成打印到日志 |

## API 概览（前缀 /api）

**认证**：`POST /auth/login`（密码 → `2FA_REQUIRED` 或双 token）→ `POST /auth/2fa/verify`（动态码/恢复码）
**个人**：`GET /me`、`PATCH /me/password`、`POST /me/2fa/setup|enable|disable`、`GET|DELETE /me/sessions`
**阅读**：`POST /search/stream`（SSE）、`GET|POST|DELETE /bookshelf`、`GET /book/{url}/toc`、
`GET /book/content?bookUrl&index`、`POST|GET /progress`、`GET /cover`、`GET /image`
**管理**：`/admin/sources`（import / enable=上架 / disable=下架 / delete）、`/admin/users`、`/admin/stats`

响应统一为 `{isSuccess, errorMsg, data}`；401/403 走 HTTP 状态码。

## 安全设计

- 密码 BCrypt（hutool）存储；密码策略 ≥8 位
- JWT 15 分钟 + `ver` 版本号：改密 / 禁用即全局踢出
- Refresh Token 旋转式：每次刷新作废旧值（重用检测即失效）
- 登录限速：(IP+用户名) 5 次失败锁 15 分钟
- TOTP 防重放：记录上次成功时间步，拒绝同窗口重复使用
- 恢复码 BCrypt 哈希存储、单次核销
- 响应头：nosniff / DENY iframe / no-referrer
- ⚠️ 生产必须经 HTTPS 反代（Caddy 最省事，自动签证书）：

```
legado.example.com {
    reverse_proxy 127.0.0.1:8080
}
```

## 备份

所有状态都在 `DATA_DIR` 一个目录里（SQLite 单文件 + WAL + 缓存）：

```bash
sqlite3 data/legado-server.db ".backup '/backup/legado-$(date +%F).db'"
```

## 多尺寸适配

- **桌面 >1024px**：左侧导航栏 + 多列封面网格 + 阅读器 42em 行宽
- **平板 768–1024px**：网格自适应 4–5 列
- **手机 <768px**：底部 TabBar、单列/两列大封面、全屏化阅读器、管理页表格横向滚动

阅读器设置（字号 14–32 / 行距 1.2–2.4 / 页宽 60–100% / 白·羊皮·护眼·夜间主题）保存在本地。

## 已知限制（路线图）

- 依赖 Android WebView 的书源（WebJs 规则 / useWebView）服务器不支持，会返回明确错误
- 本地书（txt/epub 上传）与音频/漫画源为 P2 路线图
- 书源在线调试 WebSocket 面板待接入新后端
- 旧版 `assets/web` 静态页与 `/chapter` 旧阅读页为遗留代码，已不入口化
