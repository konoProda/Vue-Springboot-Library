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

### Database

```bash
# Initialize: create the springboot-vue database and 4 base tables
mysql -u root -p < sql/springboot-vue.sql

# Apply migrations in order (if upgrading from original schema):
mysql -u root -p springboot-vue < sql/migration_multicopy.sql
mysql -u root -p springboot-vue < sql/migration_add_id.sql
mysql -u root -p springboot-vue < sql/migration_operation_log.sql
mysql -u root -p springboot-vue < sql/migration_bookwithuser_unique.sql
mysql -u root -p springboot-vue < sql/migration_lend_record_pk.sql
mysql -u root -p springboot-vue < sql/migration_add_book_version.sql
```

### Quick-start (local dev)

1. Run `sql/springboot-vue.sql` to create the `springboot-vue` MySQL database, then apply all 6 migration scripts
2. Copy `SpringBoot/src/main/resources/application.properties.example` to `application.properties` and set your MySQL password
3. Start backend: `cd SpringBoot && mvn spring-boot:run` (runs on port 9090)
4. Start frontend: `cd vue && npm run serve` (runs on port 9876)
5. Open `http://localhost:9876`
6. Test accounts must be inserted directly into the `user` table (no UI registration for admin)

## Architecture

### Backend (`SpringBoot/`)

**Package**: `com.example.demo` | **Group/Artifact**: `com.example` / `demo`

The backend was originally a flat architecture (controllers → mappers). After refactoring, it now has a **Service layer** for core business logic while some simple CRUD controllers still inject mappers directly:

```
config/
├── WebMvcConfig.java              — registers JwtInterceptor on all paths, whitelists /user/login, /user/register, /dashboard, /error

interceptor/
├── JwtInterceptor.java            — JWT auth interceptor. Validates token on all non-whitelist requests (also passes GET /book without auth).
│                                    Extracts token from Authorization: Bearer or token header. Sets userId, role, username as request attributes.
│                                    Provides static requireAdmin(HttpServletRequest) helper that controllers call for role checks.

controller/
├── BookController.java            /book            — CRUD for books. GET is public; add/edit/delete are admin-only with operation logging.
├── BookWithUserController.java    /bookwithuser    — active borrows. Delegates borrow/renew to BorrowService. Admin edit requires admin role.
├── DashboardController.java       /dashboard       — aggregate counts + /trend (7-day borrow trend) + /top-books (top 5). Public.
├── LendRecordController.java      /LendRecord      — lending history CRUD. Admin-only delete/edit. Uses LendRecordService for multi-table sync.
├── LendRecordController1.java     /LendRecord1     — return book (delegates to BorrowService.returnBook())
├── OperationLogController.java    /operation-logs  — paginated search by type, username, time range. Admin-only.
└── UserController.java            /user            — login, register, password update. Admin-only user CRUD with operation logging.

service/
├── BookService.java / impl/BookServiceImpl.java       — book CRUD wrapper
├── BorrowService.java                                 — core borrow/return/renew logic with @Transactional. Configurable via @Value:
│                                                        max-borrow-count (default 5), borrow-duration-days (30), renew-duration-days (30),
│                                                        max-renew-count (1). Validates: book exists, available > 0, under borrow limit, no duplicate.
├── LendRecordService.java / impl/LendRecordServiceImpl.java — lend record CRUD with @Transactional cross-table sync
└── OperationLogService.java                           — async (@Async) audit log writer

entity/              — MyBatis-Plus entities: Book, User, LendRecord, BookWithUser, OperationLog
mapper/              — MyBatis-Plus BaseMapper interfaces (5 mappers, no custom SQL/XML)
commom/
├── MybatisPlusConfig.java        — pagination plugin + OptimisticLockerInnerInterceptor + @MapperScan
└── Result.java                   — unified API response wrapper {code, msg, data}, Result.success() / Result.error()
utils/
├── TokenUtils.java               — JWT token generation/parsing. Uses @PostConstruct static hack to inject UserMapper.
│                                   genToken(user): creates 1-day token signed with user's password hash.
│                                   getUserFromToken(token): parses token, queries DB, returns User (null if invalid/expired).
└── QueryUtils.java               — likeIfNotBlank() / eqIfNotBlank() helpers to reduce repetitive LambdaQueryWrapper code
```

Key patterns:
- **Auth flow**: `WebMvcConfig` → `JwtInterceptor.preHandle()` (validates token, sets request attrs) → Controllers call `JwtInterceptor.requireAdmin(request)` for admin-only operations
- **Token**: Frontend sends `Authorization: Bearer <token>` header. Backend also accepts `token` header for backward compatibility.
- **Roles**: `user.role` — `1` = admin, `2` = reader. Admin-only endpoints return `Result.error("403", "无权限操作")`.
- **API wrapper**: All responses use `Result<T>` with code `"0"` for success, `"401"` for unauthenticated, `"403"` for forbidden.
- **Pagination**: List endpoints accept `pageNum`/`pageSize` params, using MyBatis-Plus `Page<T>` + `LambdaQueryWrapper`.
- **Multi-copy books**: `book.total_copies` / `book.available_copies` replace the old single `status` field. `available_copies` is protected by `@Version` optimistic locking (decremented on borrow, incremented on return).
- **Visit counter**: `LoginUser.visitCount` uses `AtomicInteger` for thread safety.
- **Table naming**: snake_case DB columns, camelCase Java fields (MyBatis-Plus auto-maps via `map-underscore-to-camel-case: true`).

### Frontend (`vue/`)

```
src/
├── main.js              — app entry, global Element Plus (zh locale) + all Element Plus icons
├── App.vue
├── router/index.js      — Vue Router with lazy-loaded routes: Layout wrapper (8 child routes) + /login + /register
├── store/index.js       — Vuex store (currently empty — state lives in sessionStorage)
├── utils/request.js     — Axios instance (baseURL: /api, timeout: 5s). Request interceptor attaches Authorization: Bearer <token>.
│                          Response interceptor: handles 401 by clearing sessionStorage and redirecting to /login.
├── layout/Layout.vue    — Header + Aside sidebar + <router-view>
├── components/
│   ├── Header.vue       — top bar: system title, dark/light mode toggle (per-user in localStorage), user dropdown with logout
│   ├── Aside.vue        — sidebar menu (role-based visibility via v-if="user.role == N")
│   └── Validate.vue     — CAPTCHA component (random chars, colors, rotation) on Login page
├── views/
│   ├── Login.vue / Register.vue
│   ├── Dashboard.vue    — stats cards + borrow trend chart (ECharts) + top 5 books ranking
│   ├── Book.vue / BookWithUser.vue
│   ├── User.vue / Person.vue / Password.vue
│   ├── LendRecord.vue
│   └── Log.vue          — operation log viewer with filters (type, username, date range)
└── assets/
    ├── css/global.css   — reset + comprehensive dark mode CSS variable overrides for Element Plus
    └── css/style.css    — table headers, pagination float, transition classes
```

Key patterns:
- **State**: User info stored in `sessionStorage` under key `"user"` (includes `token` field). Logout clears it. Vuex store is unused.
- **Auth**: Token IS sent in `Authorization: Bearer` header via request interceptor. 401 responses trigger automatic logout redirect.
- **Proxy**: Vue dev server proxies `/api/*` → `http://localhost:9090/*` (path rewritten to strip `/api` prefix).
- **Role-based UI**: `Aside.vue` shows admin menu items only when `user.role == 1`, reader items when `user.role == 2`.
- **Dark mode**: CSS variable-based theme, toggle in `Header.vue`. User preference stored in `localStorage` keyed by username.
- **Lazy loading**: All view components are dynamically imported in the router.

### Database

MySQL database: `springboot-vue` with 5 tables:

| Table | Purpose |
|-------|---------|
| `user` | Users (admin + readers), role=1 admin, role=2 reader |
| `book` | Book catalog: isbn, name, author, price, publisher, total_copies, available_copies, borrownum, version (optimistic lock). No `status` column (replaced by multi-copy model). |
| `lend_record` | Lending history: id (AUTO PK), reader_id, isbn, bookname, lend_time, return_time, status, borrownum |
| `bookwithuser` | Active borrows: id (AUTO PK), user_id, isbn (unique per user via uq_user_isbn), book_name, nick_name, lendtime, deadtime, prolong |
| `operation_log` | Audit trail: user_id, username, user_role, operation_type, detail (TEXT), create_time. Indexed on user_id, operation_type, create_time. |

**Migration scripts** in `sql/` must be applied in order after the base schema:
1. `migration_multicopy.sql` — adds `total_copies`/`available_copies`, drops `status`
2. `migration_add_id.sql` — fixes `bookwithuser` PK (adds AUTO_INCREMENT id)
3. `migration_operation_log.sql` — creates `operation_log` table
4. `migration_bookwithuser_unique.sql` — adds unique index on (user_id, isbn)
5. `migration_lend_record_pk.sql` — adds AUTO_INCREMENT id to `lend_record`
6. `migration_add_book_version.sql` — adds `version` column for optimistic locking

### Deployment

The Vue production build outputs to `SpringBoot/src/main/resources/static/`, so the single Spring Boot jar serves both the API and frontend static files.

## Operation Logging

All admin write operations are asynchronously logged to `operation_log` via `OperationLogService` (`@Async`):
- Operation types: BORROW, RETURN, RENEW, ADD_BOOK, EDIT_BOOK, DELETE_BOOK, ADD_USER, EDIT_USER, DELETE_USER, EDIT_LEND_RECORD, DELETE_LEND_RECORD, EDIT_BOOKWITHUSER
- Logs include operator username + role, and a human-readable detail message (e.g., "删除图书《xxx》", "编辑借阅记录: 读者 xxx 借阅《xxx》的状态从 0 改为 1")
- Borrow/return/renew logs include borrower info in the detail text (reader name + book name)

## 工具触发规则（自动加载）

当用户提出以下意图时，请自动匹配对应的工具：

1. **重构/清理/简化代码** → 优先调用 `code-simplifier` 插件（Refactor Cleaner）
2. **派发子任务/后台执行/批量修改** → 启动 `GKD` 插件
3. **分析代码库/查重复/死代码/架构违规** → 加载 `codebase-analysis` 技能
4. **如果上述工具未安装或不可用** → 回退到常规对话式代码分析
5. **所有工具执行前** → 需向用户确认操作范围和目标文件

## Playwright MCP Server

- 已配置 Playwright MCP Server，Claude 可通过 `browser_navigate`、`browser_click` 等工具控制浏览器
- 测试目标：`http://localhost:9876`
- 使用 `browser_snapshot` 获取页面结构，`browser_take_screenshot` 截图
