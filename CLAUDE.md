# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Library Management System (图书馆管理系统) — a full-stack web app with Spring Boot backend and Vue 3 frontend, supporting two roles: admin (图书管理、读者管理、借阅管理) and reader (图书查询、借阅/归还、查看借阅记录).

## Commands

### Backend (Spring Boot)

```bash
# Compile and package (produces jar in SpringBoot/target/)
cd SpringBoot && mvn clean package -DskipTests

# Run Spring Boot (requires application.properties with DB credentials first)
cd SpringBoot && mvn spring-boot:run

# Run tests
cd SpringBoot && mvn test

# Run a single test class
cd SpringBoot && mvn test -Dtest=YourTestClass
```

### Frontend (Vue 3)

```bash
cd vue

# Install dependencies
npm install

# Dev server with hot-reload (port 9876, proxies /api to localhost:9090)
npm run serve

# Production build (output goes to ../SpringBoot/src/main/resources/static/)
npm run build
```

### Quick-start (local dev)

1. Run `sql/springboot-vue.sql` to create the `springboot-vue` MySQL database
2. Copy `SpringBoot/src/main/resources/application.properties.example` to `application.properties` and set your MySQL password
3. Start backend: `cd SpringBoot && mvn spring-boot:run` (runs on port 9090)
4. Start frontend: `cd vue && npm run serve` (runs on port 9876)
5. Open `http://localhost:9876`
6. Test accounts must be inserted directly into the `user` table (no UI registration for admin)

## Architecture

### Backend (`SpringBoot/`)

**Package**: `com.example.demo` | **Group/Artifact**: `com.example` / `demo`

The backend uses a **flat architecture** — controllers directly inject mappers (no Service layer):

```
controller/          — REST controllers (inject mappers directly)
├── BookController.java          /book          — CRUD for books
├── BookWithUserController.java  /bookwithuser  — tracking which reader has which book + due dates
├── DashboardController.java     /dashboard     — aggregate counts (books, users, lend records, visits)
├── LendRecordController.java    /LendRecord    — lending history CRUD
├── LendRecordController1.java   /LendRecord1   — return book action (update status + returnTime)
└── UserController.java          /user          — user CRUD, register, login

entity/              — JPA/MyBatis-Plus entities (Book, User, LendRecord, BookWithUser)
mapper/              — MyBatis-Plus BaseMapper interfaces (no custom SQL needed)
commom/
├── MybatisPlusConfig.java       — pagination plugin + @MapperScan
└── Result.java                  — unified API response wrapper {code, msg, data}
utils/
└── TokenUtils.java              — JWT token generation/parsing (header: "token")
```

Key patterns:
- **No Service layer** — Controllers call `XxxMapper` directly via `@Resource`
- **Pagination**: All list endpoints accept `pageNum`/`pageSize` params, using MyBatis-Plus `Page<T>` + `LambdaQueryWrapper`
- **Auth**: JWT-based. `TokenUtils.genToken(user)` creates a 1-day token signed with the user's password hash. `TokenUtils.getUser()` reads the token from the `token` request header and returns the User object. There is no Spring Security or filter/interceptor — auth is checked per-endpoint.
- **Roles**: `user.role` — `1` = admin, `2` = reader
- **API wrapper**: All responses use `Result<T>` with code `"0"` for success
- **Table naming**: snake_case DB columns, camelCase Java fields (MyBatis-Plus auto-maps via `map-underscore-to-camel-case: true`)

### Frontend (`vue/`)

```
src/
├── main.js              — app entry, global Element Plus + icon registration
├── App.vue
├── router/index.js      — Vue Router (all routes under Layout, + /login, /register)
├── store/index.js       — Vuex store (currently empty — state lives in sessionStorage)
├── utils/request.js     — Axios instance (baseURL: /api, timeout: 5s, request interceptor)
├── layout/Layout.vue    — Header + Aside sidebar + <router-view>
├── components/
│   ├── Header.vue       — top bar with user name dropdown + logout
│   ├── Aside.vue        — sidebar menu (role-based visibility via v-if="user.role == N")
│   └── Validate.vue     — CAPTCHA component for login
└── views/
    ├── Login.vue / Register.vue
    ├── Dashboard.vue
    ├── Book.vue / BookWithUser.vue
    ├── User.vue / Person.vue
    ├── LendRecord.vue
    └── Password.vue
```

Key patterns:
- **State**: User info stored in `sessionStorage` under key `"user"` (not in Vuex). Logout clears it.
- **Request interceptor**: Redirects to `/login` if no user in sessionStorage (no token is actually sent in headers despite the commented-out line).
- **Proxy**: Vue dev server proxies `/api/*` → `http://localhost:9090/*` (path rewritten to strip `/api` prefix)
- **Role-based UI**: `Aside.vue` shows admin menu items only when `user.role == 1`, reader items when `user.role == 2`
- **Lazy loading**: All view components are dynamically imported in the router

### Database

MySQL database: `springboot-vue` with 4 tables:

| Table | Purpose |
|-------|---------|
| `user` | Users (admin + readers), role=1 admin, role=2 reader |
| `book` | Book catalog (isbn, name, author, price, publisher, status, borrownum) |
| `lend_record` | Lending history (reader_id, isbn, bookname, lend_time, return_time, status) |
| `bookwithuser` | Active borrows — which reader currently has which book, with due date and renew count |

### Deployment

The Vue production build outputs to `SpringBoot/src/main/resources/static/`, so the single Spring Boot jar serves both the API and frontend static files. The `run/start.cmd` is a Windows batch script to launch the jar directly.

## 工具触发规则（自动加载）

当用户提出以下意图时，请自动匹配对应的工具：

1. **重构/清理/简化代码** → 优先调用 `code-simplifier` 插件（Refactor Cleaner）
2. **派发子任务/后台执行/批量修改** → 启动 `GKD` 插件
3. **分析代码库/查重复/死代码/架构违规** → 加载 `codebase-analysis` 技能
4. **如果上述工具未安装或不可用** → 回退到常规对话式代码分析
5. **所有工具执行前** → 需向用户确认操作范围和目标文件

## Playwright MCP Server
- 已配置 Playwright MCP Server，Claude 可通过 `browser_navigate`、`browser_click` 等工具控制浏览器[reference:7]
- 测试目标：`http://localhost:9876`
- 使用 `browser_snapshot` 获取页面结构，`browser_take_screenshot` 截图[reference:8]
