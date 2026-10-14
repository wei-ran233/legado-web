<div align="center">

# Legado Web 多用户在线书城与阅读服务器

<p>
  <a href="https://github.com/wei-ran233/legado-web"><img src="https://img.shields.io/badge/License-GPL--3.0-blue.svg" alt="License" /></a>
  <img src="https://img.shields.io/badge/Kotlin-2.0+-purple.svg" alt="Kotlin" />
  <img src="https://img.shields.io/badge/Vue-3.x-emerald.svg" alt="Vue 3" />
  <img src="https://img.shields.io/badge/Ktor-3.x-orange.svg" alt="Ktor" />
  <img src="https://img.shields.io/badge/Docker-Ready-2496ED.svg" alt="Docker" />
  <img src="https://img.shields.io/badge/Database-SQLite%20(WAL)-lightgrey.svg" alt="SQLite" />
  <a href="https://github.com/wei-ran233/legado-web"><img src="https://img.shields.io/github/stars/wei-ran233/legado-web?style=social" alt="Stars" /></a>
</p>

<p>
  基于 <a href="https://github.com/Luoyacheng/legado-E">Luoyacheng/legado-E</a> 与开源阅读 <b>Legado (Sigma)</b> 核心规则引擎深度重构与二次开发的<b>多用户在线书城服务器与现代化响应式 Web 阅读平台</b>。<br/>
  将强大的 Android 书源解析规则解耦为纯 JVM 引擎，结合现代 Ktor 异步服务与 Vue 3 前端，让您随时随地在任何设备（PC、手机、平板、墨水屏）享受极致的阅读与书源聚合体验。
</p>

</div>

---

## 🌟 核心特性

- 👥 **完备的多用户体系**：
  - 支持多用户独立书架、书籍收藏、阅读记录与历史进度云端同步。
  - 管理员中心化管理用户账号，支持启用/禁用、密码重置与权限管控。
- 🔐 **现代安全鉴权与 2FA 两步验证**：
  - **双 Token 体系**：15 分钟短效 Access Token + 7 天旋转式 Refresh Token，修改密码即刻全局踢出。
  - **TOTP 2FA（RFC 6238）**：兼容 Google Authenticator、1Password、Aegis、Microsoft Authenticator 等常用验证器。
  - **应急恢复机制**：支持生成 10 个 BCrypt 哈希加密的一次性恢复码，防止设备丢失失联。
  - **登录防爆破限速**：针对同一 IP 与用户名组合，错误达 5 次自动锁定 15 分钟。
- ⚡ **纯 JVM 书源规则引擎**：
  - 将原版 Legado 的 `AnalyzeRule`、`AnalyzeUrl`、`webBook` 及 Rhino JS 沙箱引擎完整移植为纯 JVM 模块（`modules/engine`），不依赖 Android SDK。
  - 完美兼容全网海量 Legado 第三方书源规则。
- 🔍 **多源聚合流式搜索 (SSE)**：
  - 后端多书源并发异步抓取，前端基于 **Server-Sent Events (SSE)** 实时流式渲染搜索结果，边搜边看，毫秒级响应。
- 📖 **全平台响应式阅读器**：
  - 桌面端现代化侧栏 + 移动端自适应底部导航栏。
  - 沉浸式小说阅读器：支持 4 套精选主题（白天白、护眼羊皮纸、深灰沉浸、暗夜黑）。
  - 支持快捷键翻页、字号/行距/内边距无极微调、目录秒级跳转与正文全局磁盘缓存。
- 📚 **书源上下架与隔离控制**：
  - 支持管理员批量导入、编辑、测试、删除书源。
  - **独创书源上架/下架管理**：管理员可将私有或敏感书源设为“下架”，下架源对普通用户完全不可见，既方便源的沉淀管理又兼顾对外展示合规。
- 🧹 **智能缓存生命周期管理**：
  - 正文与图片自动落盘缓存，支持配置历史缓存过期时间与后台定时巡检清理，磁盘空间无忧。
- 🐳 **多姿势极简部署**：
  - Docker / Docker Compose 一键起服。
  - Windows 提供一键极速启动与停止脚本（双击即用，自动拉起浏览器）。

---

## 🏛️ 系统架构

```
┌────────────────────────────────────────────────────────┐
│                   Vue 3 SPA 前端                        │
│   (桌面侧栏 / 移动端 TabBar / 阅读器 / 管理员控制台)    │
└───────────────────────────┬────────────────────────────┘
                            │ Bearer JWT (RESTful & SSE)
┌───────────────────────────▼────────────────────────────┐
│                  Ktor 异步服务器 (server/)              │
│  ├─ Auth 模块: JWT + Refresh + TOTP 2FA + 限速防爆破   │
│  ├─ Library 模块: 多源 SSE 搜索 / 书架 / 目录 / 正文    │
│  ├─ Admin 模块: 书源导入/上下架 / 用户管理 / 统计面板  │
│  └─ Service: 正文与封面磁盘缓存 / 自动过期清理守护      │
└───────────────────────────┬────────────────────────────┘
                            │
┌───────────────────────────▼────────────────────────────┐
│          modules/engine (纯 JVM 书源规则引擎)          │
│  ├─ AnalyzeRule / AnalyzeUrl / webBook 规则解析器      │
│  ├─ Rhino JS 脚本沙箱执行环境                           │
│  └─ OkHttp 异步网络抓取与反爬伪装                      │
└───────────────────────────┬────────────────────────────┘
                            │
┌───────────────────────────▼────────────────────────────┐
│                      持久化存储层                      │
│  ├─ SQLite 数据库 (Exposed ORM, WAL 高性能模式)        │
│  └─ data/ 目录: webapp 前端产物 / 正文缓存 / 图片缓存   │
└────────────────────────────────────────────────────────┘
```

---

## 🚀 快速开始

### 方式一：Docker Compose（推荐）

最简单快速的生产部署方式，免除一切环境配置：

1. **克隆代码并进入目录**：
   ```bash
   git clone https://github.com/wei-ran233/legado-web.git
   cd legado-web
   ```

2. **配置环境变量（可选）**：
   查看或编辑 `docker-compose.yml` 中的配置项（推荐修改 `JWT_SECRET` 为自定义随机串）。

3. **构建并启动容器**：
   ```bash
   docker compose up -d --build
   ```

4. **查看首次生成的管理员密码**：
   ```bash
   docker compose logs | grep -A3 "管理员账号"
   ```

5. **浏览器访问**：
   访问 `http://<服务器IP>:8080`，使用管理员账号登录即可！

---

### 方式二：Windows 本地一键启动（免配置）

项目中内置了 Windows 专用一键运维脚本：

1. 确保机器已安装 **JDK 17+**。
2. 双击运行根目录下的 **`start-server.bat`**。
   - 脚本将自动检测编译状态，若未打包则自动调用 Gradle 进行打包。
   - 自动检测并同步前端资源、清理历史缓存。
   - 服务启动完成后，**会自动在默认浏览器中打开** `http://127.0.0.1:9000`。
3. 若需停止服务，可双击运行 **`stop-server.bat`**。

---

### 方式三：源码编译与裸机部署

适合二次开发与定制人员：

#### 环境要求
- **JDK 17+**（推荐 JDK 21 或 JDK 24）
- **Node.js 20+** 与 **pnpm**

#### 步骤说明

1. **编译后端服务**：
   ```bash
   # SERVER_ONLY=1 跳过 Android 模块，无需配置 Android SDK
   SERVER_ONLY=1 ./gradlew :server:installDist
   ```

2. **构建前端并同步产物**：
   ```bash
   cd modules/web
   pnpm install
   pnpm build-only
   cd ../..
   # 创建数据目录并同步静态网页
   mkdir -p data/webapp
   cp -r modules/web/dist/* data/webapp/
   ```

3. **启动服务**：
   ```bash
   # 进入编译打包目录
   cd server/build/install/server/bin

   # Linux / macOS
   export JWT_SECRET="your-32-byte-hex-secret-key-123456"
   export PORT=8080
   export DATA_DIR="$(pwd)/../../../data"
   ./server

   # Windows (CMD)
   set JWT_SECRET=your-32-byte-hex-secret-key-123456
   set PORT=8080
   set DATA_DIR=..\..\..\data
   server.bat
   ```

---

## ⚙️ 环境变量配置

所有配置项均支持通过环境变量传入：

| 环境变量 | 是否必填 | 默认值 | 说明 |
|---|---|---|---|
| `JWT_SECRET` | 建议配置 | `smoke-test-secret...` | JWT 签名密钥，生产环境请使用 `openssl rand -hex 32` 生成 |
| `PORT` | 否 | `8080` (Docker) / `9000` (Bat) | 服务监听端口 |
| `DATA_DIR` | 否 | `data` | 数据持久化根目录（存放 SQLite 数据库、正文缓存、前端产物） |
| `ADMIN_USERNAME` | 否 | `admin` | 初始化管理员用户名 |
| `ADMIN_PASSWORD` | 否 | 随机生成打印至控制台 | 初始化管理员初始密码（如已存在数据库则忽略） |
| `CACHE_EXPIRE_DAYS` | 否 | `7` | 正文与图片磁盘缓存的有效天数，超出将被自动清理 |

---

## 📡 RESTful API 概览

统一 API 前缀为 `/api`，接口响应遵循统一格式：
```json
{
  "isSuccess": true,
  "errorMsg": "",
  "data": { ... }
}
```

### 核心接口列表

- **身份认证 (`/api/auth`)**
  - `POST /auth/login` - 账号密码登录（若开启 2FA 则返回 `2FA_REQUIRED` 状态）
  - `POST /auth/2fa/verify` - 验证 TOTP 动态验证码或应急恢复码
  - `POST /auth/refresh` - 使用 Refresh Token 轮换刷新 Access Token
- **个人中心与安全 (`/api/me`)**
  - `GET /me` - 获取当前登录用户信息
  - `PATCH /me/password` - 修改个人密码
  - `POST /me/2fa/setup` - 发起 2FA 绑定流程（生成 TOTP 二维码与密钥）
  - `POST /me/2fa/enable` - 验证并正式启用 2FA（返回 10 个一次性恢复码）
  - `POST /me/2fa/disable` - 停用 2FA
- **阅读与书架 (`/api/library`)**
  - `POST /search/stream` - 多源聚合流式搜索（SSE 协议）
  - `GET /bookshelf` - 获取用户个人书架
  - `POST /bookshelf` - 添加小说至书架
  - `DELETE /bookshelf` - 从书架移除小说
  - `GET /book/{url}/toc` - 获取小说章节目录
  - `GET /book/content` - 获取指定章节正文（优先读取磁盘缓存）
  - `GET /progress` & `POST /progress` - 获取 / 更新小说阅读进度
- **管理后台 (`/api/admin`)**
  - `GET /admin/users` & `POST /admin/users` - 用户列表与创建新用户
  - `PATCH /admin/users/{id}/status` - 启用/禁用用户
  - `GET /admin/sources` - 管理员查看全部书源列表
  - `POST /admin/sources/import` - 导入网络/本地书源 JSON
  - `POST /admin/sources/{id}/enable` - 上架书源（对所有普通用户公开）
  - `POST /admin/sources/{id}/disable` - 下架书源（对普通用户隐身）
  - `DELETE /admin/sources/{id}` - 删除书源

---

## 🛡️ 安全合规规范

1. **密码安全**：基于 BCrypt 算法加盐哈希存储，严格要求密码强度。
2. **令牌安全性**：短生命周期 Access Token（15 分钟）与版本号绑定，任何密码修改或禁用操作均会触发版本号递增，立即使旧 Token 失效。
3. **2FA 防重放**：严格校验时间步，拒绝同时间窗口内的重放请求；应急恢复码仅支持单次核销。
4. **防暴力破解**：内置 IP + 用户名维度的滑动窗口限速拦截器。
5. **安全 HTTP 标头**：预置 `X-Content-Type-Options: nosniff`、`X-Frame-Options: DENY`、`Referrer-Policy: no-referrer`。

---

## 🤝 致谢

- 本项目基于 [Luoyacheng/legado-E](https://github.com/Luoyacheng/legado-E) 与 [gedoor/legado](https://github.com/gedoor/legado) 延续二次开发与深度重构而来。
- 核心书源规则解析逻辑保留了原作者团队的精湛实现，特此向 Legado 原作团队（gedoor）及 Legado-E 作者（Luoyacheng）致以崇高的敬意与感谢！

---

## ⚖️ 法律声明与版权合规须知

> [!IMPORTANT]
> 本项目是开源阅读技术工具，不提供对第三方作品或网站的使用授权。使用、部署、运营本项目时，请遵守适用法律及第三方合法权益。本声明不排除开发者或部署运营者依法应承担的责任。

1. **项目定位及上游成果**：
   - Legado Web 基于 [Luoyacheng/legado-E](https://github.com/Luoyacheng/legado-E) 与 [gedoor/legado](https://github.com/gedoor/legado) 的开源成果二次开发，提供规则解析、多用户阅读和缓存等软件功能。
   - 项目的技术研究、开源或非营利属性不等于任何未经授权的获取、复制或传播行为合法。

2. **第三方书源与版权内容**：
   - 书源是第三方资源的访问与解析规则，书源可用不代表内容已获授权。请仅访问、导入或分享依法可以使用的书源和作品。
   - 项目具备书源导入及管理员上架、网络搜索、正文与图片磁盘缓存和多用户阅读功能。**本项目不能笼统保证所有部署环境均不存储或传输第三方内容**；具体内容由实际部署、配置和使用情况决定。
   - 请勿利用本项目绕过付费或技术保护措施，或未经授权复制、传播他人的作品。

3. **用户与部署运营者责任**：
   - 自行部署或运营服务的用户应核验内容来源、授权范围及适用法律，妥善设置书源访问权限、缓存策略和侵权内容处理机制。
   - 用户、部署运营者和开发者的责任应结合各自具体行为、实际控制能力、过错及适用法律认定；本声明不以约定方式免除任何依法不得免除的责任。
   - 面向公众提供服务时，部署运营者应特别注意公开书源、多人共享内容、服务器缓存及传播行为可能产生的版权和其他合规义务。

4. **版权投诉与处理**：
   - 如认为本仓库中的代码、文档、示例或项目维护者实际提供的内容侵犯您的合法权益，请通过 [GitHub Issues](https://github.com/wei-ran233/legado-web/issues) 或邮件 **[liweimin@wrcode.online](mailto:liweimin@wrcode.online)** 联系维护者。
   - 为便于核查，请提供权利人及联系方式、权属或授权依据、涉嫌侵权的具体仓库路径或链接、问题说明及您希望采取的措施。请勿在公开 Issue 中发布身份证件、私人地址等敏感资料。
   - 维护者收到有效通知后，将结合可核实信息和适用法律及时评估，并在必要时采取更正、移除或其他适当措施。对于独立第三方部署的服务，请同时联系该服务的实际运营者。

5. **GPLv3 开源许可与合法使用**：
   - 本项目中依据 [GNU GPLv3](LICENSE) 授权的代码，使用、修改、复制和分发权利依该许可证确定，包括 GPLv3 允许的商业使用；本声明不另行设置与 GPLv3 冲突的全面商业使用禁令。
   - GPLv3 对软件代码的授权不等于对第三方小说、图书、图片、书源内容或商标的授权。任何商业或非商业活动均须遵守相关法律并取得必要授权。

6. **无担保与责任限制**：
   - 在适用法律允许的范围内，软件按照 GNU GPLv3 所载条件以“原样”（AS IS）提供，不作适销性、特定用途适用性等保证。具体担保与责任限制以许可证条款和适用法律为准。

---

## 📄 开源许可证与版权声明

本项目遵循 [GNU General Public License v3.0 (GPL-3.0)](LICENSE) 开源协议发布。

```
Copyright (C) 2026 wei-ran233 <https://github.com/wei-ran233/legado-web>
Copyright (C) 2024-2026 Luoyacheng <https://github.com/Luoyacheng/legado-E>
Copyright (C) 2019-2023 gedoor <https://github.com/gedoor/legado>
```
