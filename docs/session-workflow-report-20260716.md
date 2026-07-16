# 会话完整工作流总结报告

> 生成时间：2026-07-16
> 覆盖范围：`main` 分支初始版本 → `refactor/final` 分支全部改动（共 **39 个提交**，2026-07-10 ~ 2026-07-16）
> 相关分支：`refactor/test-drill`（refactor-test / refactor-test-2 阶段）→ `refactor/final`
> 环境：deepseek-v4-pro[1m] 模型 + Claude Code CLI
> 关联历史文档：`docs/refactor-analysis-report.md`、`docs/refactor-plan.md`、`docs/refactor-final-report.md`、`docs/plugin-usage-report.md`、`docs/verify-report.md`

---

## 1. 总体概览

### 1.1 变更规模（剔除前端构建产物与浏览器快照后）

| 指标 | 数值 |
|------|------|
| 提交数（main..refactor/final） | 39 |
| 有效源码/文档变更文件数 | 91 |
| 有效新增行 | ~8,781 |
| 有效删除行 | ~1,204 |
| 新增后端 Java 类 | 12（Controller 2、Service 6、Interceptor 1、Utils 1、Entity 1、Mapper 1） |
| 删除后端 Java 类 | 1（`LendRecordController1.java`） |
| 新增 SQL 迁移脚本 | 6 |
| 新增前端文件 | 4（`Log.vue`、`locales/zh.json`、`locales/en.json`、`login-bg.png`） |
| 新增文档/测试产物 | 35+（`docs/` 下报告、脚本、API 文档） |

### 1.2 工作流三大阶段

整个演进过程按"**探索重构 → 质量加固 → 需求交付**"三大阶段推进，每个阶段遵循同一条方法论主线：**分析 → 计划 → 分阶段实施 → 自动化验证 → 报告归档**。

| 阶段 | 提交范围 | 时间 | 主题 |
|------|---------|------|------|
| A. refactor-test | `8bc7d6f`..`244cbe4`（14 个提交） | 07-10 ~ 07-12 | 核心服务重构、JWT 鉴权、多副本库存、可视化仪表盘、操作日志、深色模式 |
| B. refactor-test-2 | `0ba89e7`..`38afabd`（8 个提交） | 07-14 | 基于静态分析报告的质量加固：紧急 Bug、并发安全、死代码、架构分层 |
| C. refactor-final | `f1acf8e`..`0785929`（17 个提交） | 07-15 ~ 07-16 | 需求变化 1~5 交付 + 孤儿数据防护 + UI/易用性 Bug 修复 + 测试脚本泛化 |

---

## 2. 阶段 A：refactor-test —— 架构探索与功能扩展（07-10 ~ 07-12）

### 2.1 核心服务重构（8bc7d6f, e001922）

- 创建 `BorrowService`，将借书业务从 Controller 层多接口拼接迁移为单一 `@Transactional` 服务方法。
- 重构逻辑：**"先建立事务边界，再迁移调用方"** —— 保留旧接口兼容运行，新服务先并行存在，逐步切换前端调用。

### 2.2 JWT 鉴权体系（6709c5e → f0c4be8 → c899228）

三步渐进式引入，每步独立可回滚：
1. `JwtInterceptor` 基础设施落地但**不启用校验**（仅解析 token、设置 request attributes）；
2. 启用拦截 + `requireAdmin()` 静态辅助方法，为管理员接口加角色控制（`role=1` 管理员 / `role=2` 读者）；
3. 前端 `utils/request.js` 适配：Axios 拦截器统一附加 `Authorization: Bearer <token>`，401 响应重定向 `/login`。

配套：`WebMvcConfig` 注册拦截器并白名单化 `/user/login`、`/user/register`、`/dashboard`、`/error`，`GET /book` 免鉴权（公开查询）。

### 2.3 多副本库存模型（344020d, 9bd955b, 6546190）

- `book` 表升级为 `total_copies` / `available_copies` 双字段（`sql/migration_multicopy.sql`）。
- 前端 Book/BookWithUser 页面同步展示副本余量；随后修复了一批副本数量显示 Bug。

### 2.4 功能扩展（4f3b54e, a5c8e4f, e3d14a7）

- **仪表盘**：`DashboardController` 提供聚合计数、`/trend`（7 日借阅趋势）、`/top-books`（Top 5），前端 ECharts 渲染。
- **操作日志**：`OperationLog` 实体 + Mapper + `@Async` 异步写入的 `OperationLogService`，管理员端 `Log.vue` 集成（12 类操作类型）。
- **深色模式**：`global.css` CSS 变量体系 + Header 切换开关 + 各组件 `html.dark` 选择器。

### 2.5 测试期修复（f518f91, 46d45e4, 244cbe4）

- 新增 `lend_record` 主键与 `bookwithuser` 唯一索引（`migration_lend_record_pk.sql`、`migration_bookwithuser_unique.sql`），修复多副本数据一致性问题。
- 读者借阅状态栏添加还书按钮、仪表盘卡片样式调整、深浅色模式下账号记忆 Bug 修复。

---

## 3. 阶段 B：refactor-test-2 —— 分析驱动的质量加固（07-14）

本阶段的独特之处是**先产出静态分析报告，再按优先级逐项修复**，全程"一提交一问题"，可精确追溯。

### 3.1 分析先行

- `docs/refactor-analysis-report.md`（429 行）：识别出 7 个高优先级问题（并发安全、功能 Bug、配置错误）+ 14 个中优先级问题（架构一致性、死代码、方法过长）。
- `docs/refactor-plan.md`（658 行）：对借/还/续借三条流程做完整调用时序分析，给出目标分层架构与迁移步骤清单（此文档同时是阶段 C 需求 3 的施工蓝图）。

### 3.2 按优先级逐项修复

| 提交 | 问题类别 | 修复内容 |
|------|---------|---------|
| `0ba89e7` | 紧急 Bug 01 | `Password.vue` 误调管理员专用接口，普通用户改密必失败 → 切换正确端点 |
| `16789a3` | 紧急 Bug 02 | 查询接口对整数列误用 `like()` → 改为 `eq()` |
| `5023c08` | 并发安全 | `availableCopies` 无锁竞态 → `Book` 加 `@Version` 乐观锁 + `OptimisticLockerInnerInterceptor` + `migration_add_book_version.sql` |
| `ab2fe6c` | 并发安全 | `LoginUser.visitCount` 非线程安全 → `AtomicInteger` |
| `84aecd0` | 死代码 | 清理未使用注入、死 API 端点（`GET /user`、`PUT /LendRecord/byTime`）、前端空操作桩调用 |
| `09892ee` | 架构加固 | 提取 `LendRecordService`/`BookService`（事务从 Controller 下移）、`QueryUtils.likeIfNotBlank()/eqIfNotBlank()` 消除 5 处重复条件构建、`requireAdmin()` 消除 15+ 处重复权限检查、`BorrowService.borrowBook()` 拆为 4 个子方法、`LendRecordController.update()` 拆为 3 个状态处理 |
| `f0d9635` | 归档 | `docs/refactor-final-report.md` 成果总结 |
| `38afabd` | 短期优化 | 操作日志补充借阅人信息说明文本 |

### 3.3 验证与归档

- `docs/verify-report.md`：编译检查 → 后端启动 → curl API 冒烟 → 数据库校验四步验证。
- `docs/plugin-usage-report.md`：本阶段工具/插件使用复盘（详见第 6 节）。
- 结果：高/中优先级问题 **100% 修复**。

---

## 4. 阶段 C：refactor-final —— 五项需求变化交付（07-15 ~ 07-16）

### 4.1 需求变化 1：图书库存数量支持（`7641088`）

- 大部分能力已在阶段 A 的多副本重构中就绪；本次补齐**已借出数量校验**（`BookController` 编辑时校验 `total_copies ≥ 已借出数`）与非法借阅/编辑的明确失败提示。
- 产出测试：`inventory-test-script-20260715.js`、`inventory-test-20260715.spec.js`、测试报告。

### 4.2 需求变化 2：逾期管理与逾期限制（`d038840`）

- `BorrowService` 增加逾期检查：**有逾期未还图书的用户禁止新借阅**。
- `BookWithUserController.findPage` 计算 `status` + `overdueDays`，支持 `overdueFilter`（1=逾期, 2=未逾期）；`LendRecordController.findPage` 支持三态过滤（逾期未还/已归还/未归还）。
- 前端 BookWithUser/LendRecord 增加状态标签（逾期/临期/正常）与过滤下拉。
- 产出测试：`overdue-test-script-20260715.js` + 报告。

### 4.3 需求变化 3：借/还/续借流程后端化（`68863aa`）—— 本轮最核心的重构

**重构前**（见 `refactor-plan.md` 分析）：前端串行调用 3 个接口（`PUT /book` 改库存 → `POST /LendRecord` 写记录 → `POST /bookwithuser/insertNew` 写借阅），日期/状态由**前端计算**，无事务保证，任一步失败即数据不一致。

**重构后**：
- 新建 `BorrowController`，提供 `POST /borrow`、`/return`、`/renew` 三个干净端点，请求体只需 `{isbn}`，`userId` 从 JWT 提取——**前端不再传任何可伪造的计算字段**。
- 业务规则集中到 `BorrowService`（`@Transactional`），通过 `@Value` 可配置：`max-borrow-count=5`、`borrow-duration-days=30`、`renew-duration-days=30`、`max-renew-count=1`。校验链：图书存在/有余量 → 用户无逾期 → 未达借阅上限 → 未重复借阅（还书时校验用户确实持有该书）。
- 删除命名异味的 `LendRecordController1.java`（`/LendRecord1` 路由）；`Book.vue` 借书逻辑减少约 90 行前端计算代码。
- 产出：`docs/backend-flow-refactor/api-reference.md`（API 文档）+ `verify-borrow-flow.sh`（6 用例零依赖黑盒验证脚本）+ 测试报告。

### 4.4 需求变化 4：管理员用户增删（`988cb60`, `26811f8`）

- `UserController` 增加管理员新增用户（用户名重复检查）与 `DELETE /user/{id}`；删除前检查 `bookwithuser` 活跃借阅，**拒绝产生孤儿数据**。
- `PUT /user` 权限模型：管理员可编辑任何人，读者只能编辑自己。
- 产出测试：`user-crud-api-test-20260715.sh`（6 用例）、`user-crud-ui-test-20260715.js` + 报告。

### 4.5 需求变化 5：前端界面风格整体改版（分 4 个子阶段，`6544945`..`c0f95ec`）

采用**分阶段渐进改版**策略，每个子阶段独立提交 + 独立 Playwright 测试脚本验证：

| 子阶段 | 提交 | 内容 |
|--------|------|------|
| 阶段 1 | `6544945` | 全局字体体系（`global.css`）、vue-i18n 双语言基础（`zh.json`/`en.json`、`main.js` legacy 模式接入）、登录页初版改造（`login-bg.png` 背景） |
| 阶段 2 | `d11231e` | 登录页重做：白色顶栏 50px（logo + 语言切换）、背景图 cover 填充、表单右对齐布局 |
| 阶段 3 | `ebf0b87` | 主框架改造：移除左侧 Aside，`Header.vue` 改为两行顶部导航（Row1: logo+深色+语言+用户；Row2: 水平菜单）；`Dashboard.vue` 增加搜索栏（书名→作者回退搜索）、ECharts 深色自适应（MutationObserver + `$watch` locale） |
| 阶段 4.1 | `b4ea1e3` | 五个数据页（Book/BookWithUser/LendRecord/Log/User）统一 `.page-layout` 弹性布局：左侧搜索面板 22% + 内容 flex:1，独立滚动，<1024px 可折叠，按钮 `flex-wrap:nowrap` 响应式适配 |
| 阶段 4.2 | `c0f95ec` | 样式收尾 + i18n 全量补齐：`zh.json`/`en.json` 扩至 ~130 键 10 个分区；`<el-config-provider :locale>` 解决 Element Plus 1.2.x 分页组件语言不刷新问题；Password/Person/Register 全量翻译 |

每个子阶段配套 `docs/test-reports/` 下的 Playwright 测试脚本与报告（frontend-style / login-layout / topnav-dashboard / sidebar-layout / i18n 系列 / final-comprehensive）。

### 4.6 穿插的 Bug 修复与收尾提交

| 提交 | 内容 |
|------|------|
| `c4b0eab` | **孤儿数据防护**：图书删除前检查 `bookwithuser` 活跃借阅；`BookWithUserController.deleteRecords` 直接拒绝删除并引导走还书流程；借阅记录默认排序优化（未还按 lendTime ASC 优先，已还按 returnTime DESC）。配套 `orphan-data-fix-test-20260716.sh` |
| `a8e60ba` | UI/易用性修复：`Register.vue` 重做为与 Login 同款布局并全量 i18n；注册接口与前端同步（`UserController` 调整）；`App.vue` 引入 `<el-config-provider>` locale 同步；LendRecord/User 细节修复。配套 `register-sync-test-20260716.js` |
| `5929469` | CLAUDE.md 全面更新：沉淀最终架构描述、命令、Common Gotchas（`0 \|\| 1` 陷阱、`data()` 不能用 `$t()`、created 钩子顺序等 8 条） |
| `1ce50fb` | 五个数据页表格溢出/交互修复（各 +13 行统一处理）。配套 `overflow-fix-test-20260716.js` |
| `8728cff` | `verify-borrow-flow.sh` 泛用性提升（去除环境硬编码，支持 `[BASE_URL]` 参数） |
| `0785929` | `verify-borrow-flow.sh` 增加**自动创建测试用户**环节，实现新环境零准备直接运行 |

---

## 5. 重构逻辑总结（方法论层面)

贯穿三个阶段的重构决策逻辑可归纳为六条原则：

1. **分析先行，报告驱动**：每轮大改动前先产出分析文档（`refactor-analysis-report.md`、`refactor-plan.md`），按"高优先级 Bug → 并发安全 → 死代码 → 架构分层"的顺序排定修复优先级，改完再出成果报告闭环。
2. **小步提交，单一主题**：39 个提交几乎每个只做一件事（一个 Bug、一个需求子阶段），提交信息带 `refactor-test / refactor-test-2 / refactor-final / bug-fixed` 前缀标注阶段,可精确回滚与追溯。
3. **渐进式切换而非大爆炸**：JWT 分三步启用（基础设施→校验→前端适配）；借阅流程后端化时先并行提供新端点再删旧路由；界面改版拆成 4 个子阶段逐页推进。
4. **业务逻辑下沉 + 信任边界收紧**：事务从 Controller 下移到 Service；日期/状态计算从前端收回后端；`userId` 从请求体参数改为 JWT 提取；业务常量集中为 `@Value` 可配置项。
5. **数据一致性三重防线**：乐观锁（`@Version`）防并发超借；唯一索引/主键（`uq_user_isbn`、`lend_record` PK）防重复数据；删除前置检查（Book/User 删除查活跃借阅、BookWithUser 拒删引导还书）防孤儿数据。每项 schema 变更均落地为独立可重放的 `sql/migration_*.sql`。
6. **每改必测，测试脚本与代码同提交**：每个需求/修复配套自动化脚本（shell curl 黑盒 / Playwright 浏览器脚本）+ Markdown 测试报告，全部归档在 `docs/test-reports/`（28 个文件）；脚本本身也被迭代维护（如 `verify-borrow-flow.sh` 两次泛化改造）。

---

## 6. 工具调用逻辑总结

### 6.1 CLAUDE.md 工具触发规则与实际执行

项目 CLAUDE.md 定义了 5 条触发规则,实际执行情况：

| 规则 | 实际执行情况 |
|------|-------------|
| 需求分析 → `requirement-analyzer` 技能 | 阶段 C 需求解构时按规则加载 |
| 测试用例/脚本 → `test-automation` 技能 | 各需求配套测试脚本生成时按规则加载 |
| 重构/简化 → `code-simplifier` 插件 | 阶段 A/B 未调用（改动跨层联动，需主会话全局上下文，委派会丢失已建立的代码理解） |
| 批量修改 → `GKD` 插件 | 未调用（任务颗粒度多为 2-5 个文件，委派开销大于收益） |
| **所有工具执行前向用户确认范围与目标文件** | 全程遵守,每个阶段实施前均与用户确认操作范围 |

### 6.2 原生工具循环为主力

据 `plugin-usage-report.md` 统计（阶段 A/B），主模型原生工具循环承担了全部有效工作：

| 工具 | 调用量级 | 用途 |
|------|---------|------|
| Bash | ~80+ | `mvn` 编译、`curl` API 测试、`mysql` 数据校验、grep 静态扫描、git 操作 |
| Read / Edit / Write | ~85+ | 源码阅读、精准字符串替换、新建 Service/Utils/报告 |
| TaskCreate/TaskUpdate | 7+ | 多阶段任务跟踪 |

### 6.3 Playwright MCP：从失败降级到成功启用

- **阶段 A/B（07-14 前）**：3 次 `browser_navigate` 尝试均因 Chrome 依赖缺失失败，E2E 测试**降级为 curl API 级黑盒测试**——这是"环境受限时选择次优但可行的验证手段"的典型决策。
- **阶段 C（07-16）**：环境修复后 Playwright MCP 成功启用，`browser_snapshot` / `browser_take_screenshot` 用于界面改版各子阶段的验证（提交中包含 `.playwright-mcp/page-2026-07-16T*.yml` 页面快照与截图证据），测试目标 `http://localhost:9876`。

### 6.4 验证工具链的固化

会话中沉淀出两类可复用验证资产,并写入 CLAUDE.md 命令区：

- **API 级**：`docs/backend-flow-refactor/verify-borrow-flow.sh`（借/还/续借 6 用例，自动创建测试用户，零依赖）、`docs/test-reports/user-crud-api-test-20260715.sh`（用户 CRUD 6 用例）。
- **浏览器级**：`docs/test-reports/` 下 15+ 个 Playwright JS 脚本，覆盖库存、逾期、登录布局、顶部导航、侧栏布局、i18n、注册同步、溢出修复等场景。

---

## 7. 最终架构快照

```
前端 (vue/, 端口 9876)                    后端 (SpringBoot/, 端口 9090)
├── 顶部导航双行布局 (Aside 已移除)        ├── JwtInterceptor (鉴权 + requireAdmin)
├── vue-i18n 中英双语 (~130 键)           ├── Controller 层 (7 个, 薄壳化)
├── 深色模式 (CSS 变量)                   ├── Service 层 (Borrow/Book/LendRecord/OperationLog)
├── .page-layout 统一数据页布局           │   └── @Transactional 事务边界 + @Value 业务配置
├── Axios Bearer token + 401 重定向       ├── MyBatis-Plus (乐观锁 + 分页插件)
└── ECharts 仪表盘 (深色自适应)           └── Result<T> 统一响应 {code, msg, data}

数据库: springboot-vue (5 表: user/book/lend_record/bookwithuser/operation_log)
部署: vue build → SpringBoot/static, 单 jar 同时服务 API 与前端
```

## 8. 遗留事项与建议

1. **Playwright MCP 配置**：建议固化为使用 Chromium headless shell,避免环境迁移后再次回退到 curl 降级测试。
2. **DTO 层缺失**（analysis-report 3.10）：Entity 仍直接暴露给前端,属已知低优先级项。
3. **测试脚本 CI 化**：`verify-borrow-flow.sh` 已具备零依赖直跑能力,可直接接入 CI；Playwright 脚本建议在有 Chrome 的 CI 环境统一回归。
4. **委派类插件（GKD / code-simplifier）**：在未来 50+ 文件同构批量修改或代码完成后的二次精炼场景中启用,小颗粒任务继续由主模型直接完成。

---

*本报告基于 `git log main..refactor/final` 全量提交、各提交 diffstat、以及 `docs/` 下既有分析/验证/插件使用报告综合生成。*
