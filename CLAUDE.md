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

### Verification Scripts

```bash
# Borrow/return/renew flow black-box test (6 cases, zero dependencies)
bash docs/backend-flow-refactor/verify-borrow-flow.sh [BASE_URL]

# User CRUD API test (6 cases)
bash docs/test-reports/user-crud-api-test-20260715.sh [BASE_URL]
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

```
config/
├── WebMvcConfig.java              — registers JwtInterceptor on all paths, whitelists /user/login, /user/register, /dashboard, /error

interceptor/
├── JwtInterceptor.java            — JWT auth interceptor. Validates token on all non-whitelist requests (also passes GET /book without auth).
│                                    Extracts token from Authorization: Bearer or token header. Sets userId, role, username as request attributes.
│                                    Provides static requireAdmin(HttpServletRequest) helper that controllers call for role checks.

controller/
├── BorrowController.java          /borrow, /return, /renew — clean business endpoints (POST), userId from JWT, request body only {isbn}
├── BookController.java            /book            — CRUD for books. GET is public; add/edit/delete are admin-only with operation logging.
│                                                    Delete checks active borrows before proceeding (avoids orphan data).
├── BookWithUserController.java    /bookwithuser    — active borrows. Delegates borrow/renew to BorrowService. Admin edit only.
│                                                    findPage supports overdueFilter (1=逾期, 2=未逾期), computes status + overdueDays.
│                                                    deleteRecords rejects direct deletion, guides user to return flow.
├── DashboardController.java       /dashboard       — aggregate counts + /trend (7-day borrow trend) + /top-books (top 5). Public.
├── LendRecordController.java      /LendRecord      — lending history CRUD. Admin-only delete/edit. Uses LendRecordService for multi-table sync.
│                                                    findPage supports overdueFilter (1=逾期未还, 2=已归还, 3=未归还).
│                                                    Default sort: unreturned first by lendTime ASC, returned by returnTime DESC.
├── OperationLogController.java    /operation-logs  — paginated search by type, username, time range. Admin-only.
└── UserController.java            /user            — login, register, password update. Admin user CRUD with duplicate check.
│                                                    PUT /user: admins can edit anyone, readers can only edit themselves.
│                                                    DELETE /user/{id}: checks bookwithuser before deleting (avoids orphan data).

service/
├── BookService.java / impl/BookServiceImpl.java
├── BorrowService.java             — core borrow/return/renew logic with @Transactional. Configurable via @Value:
│                                    max-borrow-count (5), borrow-duration-days (30), renew-duration-days (30), max-renew-count (1).
│                                    Validates: book exists/available, overdue check, borrow limit, duplicate borrow, user has book check on return.
├── LendRecordService.java / impl/LendRecordServiceImpl.java — @Transactional cross-table sync
└── OperationLogService.java       — async (@Async) audit log writer

entity/              — MyBatis-Plus entities: Book, User, LendRecord, BookWithUser, OperationLog
mapper/              — MyBatis-Plus BaseMapper interfaces (5 mappers, no custom SQL/XML)
commom/
├── MybatisPlusConfig.java        — pagination plugin + OptimisticLockerInnerInterceptor + @MapperScan
└── Result.java                   — unified API response wrapper {code, msg, data}
utils/
├── TokenUtils.java               — JWT generation/parsing. @PostConstruct static hack injects UserMapper.
└── QueryUtils.java               — likeIfNotBlank() / eqIfNotBlank() helpers
```

Key patterns:
- **Auth**: `WebMvcConfig` → `JwtInterceptor.preHandle()` (validates token, sets request attrs) → Controllers call `JwtInterceptor.requireAdmin(request)` 
- **Token**: `Authorization: Bearer <token>` header. Backend also accepts `token` header for backward compatibility.
- **Roles**: `user.role` — `1` = admin, `2` = reader. Admin-only endpoints return `Result.error("403", "无权限操作")`.
- **API**: All responses use `Result<T>` with code `"0"` for success, `"401"` for unauthenticated.
- **Multi-copy**: `book.total_copies` / `book.available_copies` with `@Version` optimistic locking.
- **Orphan prevention**: Book delete → checks `bookwithuser`; User delete → checks `bookwithuser`; BookWithUser delete → rejected (guide to return flow).

### Frontend (`vue/`)

```
src/
├── main.js              — Vue 3 app entry: Element Plus + vue-i18n (legacy:true) + global icons
├── App.vue              — wraps <router-view> in <el-config-provider :locale> for EP locale sync
├── router/index.js      — lazy-loaded routes: Layout wrapper (8 children) + /login + /register
├── store/index.js       — Vuex (empty, state in sessionStorage)
├── utils/request.js     — Axios: baseURL /api, timeout 5s, Bearer token interceptor, 401→/login redirect
├── layout/Layout.vue    — Header only, full-width <router-view> (Aside removed)
├── components/
│   ├── Header.vue       — Two-row top nav: Row1(logo+dark+lang+user), Row2(horizontal menu)
│   └── Validate.vue     — CAPTCHA component
├── locales/
│   ├── zh.json          — 10 sections (~130 keys): header/login/register/person/password/nav/dashboard/book/borrow/lendRecord/user/log/common
│   └── en.json          — English equivalents
└── views/
    ├── Login.vue         — white topbar (logo+lang switch), login-bg.png cover, form right-aligned
    ├── Register.vue      — same layout as Login, full i18n
    ├── Dashboard.vue     — search bar (title→author fallback), stats cards (computed), ECharts trends (dark-adaptive via MutationObserver + $watch locale)
    ├── Book.vue          — left sidebar search panel (22%), table, borrow-btn--disabled CSS class
    ├── BookWithUser.vue  — left sidebar, status tags (overdue/due-soon/normal), overdue filter
    ├── LendRecord.vue    — left sidebar, status filter dropdown, reader-only auto-filter (search3=user.id)
    ├── User.vue          — left sidebar, sex column translation, dynamic dialog title
    ├── Person.vue        — full i18n, "change password" button → /password
    ├── Password.vue      — full i18n, label-width 180px for EN
    └── Log.vue           — left sidebar, operation type dropdown (12 options), typeLabel via $t('log.types.xxx')
```

Key patterns:
- **State**: User in `sessionStorage["user"]` with `token` field. Vuex unused.
- **i18n**: `vue-i18n` legacy mode, `$t()` in templates, `computed` for validation rules (data() can't use $t). Language preference in `localStorage["lang"]`.
- **EP locale sync**: `App.vue` uses `<el-config-provider :locale="elLocale">` with `$watch('$i18n.locale')` for pagination etc.
- **Layout**: All data pages use `.page-layout` flex container (sidebar 22% + content flex:1), independent scrolling, collapsible <1024px, action buttons `flex-wrap:nowrap`.
- **Dark mode**: `global.css` CSS variables, Header toggle, `html.dark` selectors in each component.
- **Login/Register**: Shared style — white topbar 50px, `login-bg.png` cover in `.login-body` below topbar, form `flex-end` + `center`.
- **Hook order**: `created()` MUST parse `sessionStorage` user BEFORE calling `this.load()`, otherwise role-based filters fail on first render.
- **API calls**: `/borrow`, `/return`, `/renew` take only `{isbn}`, userId from JWT. Frontend no longer sends computed dates/status.

### Database

MySQL database: `springboot-vue` with 5 tables (see Quick-start for full migration order):

| Table | Key fields |
|-------|-----------|
| `user` | id, username, password, nick_name, role (1=admin, 2=reader) |
| `book` | id, isbn, name, total_copies, available_copies, version (@Version) |
| `lend_record` | id (AUTO PK), reader_id, isbn, bookname, lend_time, return_time, status |
| `bookwithuser` | id (AUTO PK), user_id, isbn (uq_user_isbn), book_name, deadtime, prolong |
| `operation_log` | user_id, username, operation_type, detail (TEXT), create_time |

### Deployment

Vue production build → `SpringBoot/src/main/resources/static/`. Single jar serves both API and frontend.

## Operation Logging

Async logging via `OperationLogService` (`@Async`) for: BORROW, RETURN, RENEW, ADD_BOOK, EDIT_BOOK, DELETE_BOOK, ADD_USER, EDIT_USER, DELETE_USER, EDIT_LEND_RECORD, DELETE_LEND_RECORD, EDIT_BOOKWITHUSER.

## 工具触发规则

1. **需求分析/解构** → 优先加载 `requirement-analyzer` 技能
2. **测试用例/测试脚本** → 优先加载 `test-automation` 技能
3. **重构/清理/简化代码** → 优先调用 `code-simplifier` 插件
4. **派发子任务/后台执行/批量修改** → 启动 `GKD` 插件
5. **所有工具执行前** → 需向用户确认操作范围和目标文件

## Playwright MCP Server

- 测试目标: `http://localhost:9876`
- `browser_snapshot` 获取页面结构, `browser_take_screenshot` 截图

## Common Gotchas

- `0 || 1 === 1` in JS — never use `||` for numeric fallback, use `!= null ? x : default`
- `data()` cannot access `this.$t()` — use `computed` for i18n validation rules
- `created()`: parse `sessionStorage` user BEFORE `this.load()`
- Disabled `<button>` prevents all DOM events including click — use CSS class `pointer-events:none` + opacity instead of `:disabled` for clickable "disabled" buttons
- `el-input-number :min="1"` prevents entering 0 — use frontend validation fallback
- Chinese curly quotes `"` `"` break Java string compilation
- `set -e` in shell scripts: prefer manual error handling for curl+pipes
- Element Plus 1.2.x: locale switching requires `<el-config-provider :locale>` wrapper, `$ELEMENT.locale` assignment does not trigger re-render
