# 代码质量综合分析报告

**分支**: `refactor/test-drill`  
**分析日期**: 2026-07-14  
**分析范围**: 后端 `SpringBoot/src/main/java/com/example/demo/` + 前端 `vue/src/`（关键页面）

---

## 1. 分析概览

| 指标 | 数量 |
|------|------|
| 扫描文件总数 | 28（后端 18 + 前端 10） |
| 发现问题总数 | **27** |
| 🔴 高优先级（必须修复） | 7 |
| 🟡 中优先级（建议修复） | 14 |
| 🟢 低优先级（可选修复） | 6 |

---

## 2. 🔴 高优先级问题（必须修复）

### 2.1 [并发安全] `availableCopies` 无乐观锁保护，存在竞态条件

**文件**:
- `SpringBoot/src/main/java/com/example/demo/service/BorrowService.java` — 第 111-112 行（borrowBook: 检查后扣减）、第 161-163 行（returnBook: 递增）
- `SpringBoot/src/main/java/com/example/demo/controller/LendRecordController.java` — 第 106 行（syncDeleteById: 递增）、第 146-173 行（update: 状态变更时对 availableCopies 的增减）

**问题描述**: 经典的 read-check-write 竞态条件。`book` 表没有 `version` 乐观锁字段，两个并发借阅请求可同时通过 `availableCopies <= 0` 检查，然后各自执行扣减，导致 `availableCopies` 变为负数。

**失败场景**: 图书馆最后一本可借书被两个读者同时提交借阅请求，两次请求都检查到 availableCopies > 0，然后都执行 `setAvailableCopies(availableCopies - 1)` 和 `updateById`，最终 availableCopies = -1。

**修复方向**: 在 `book` 表添加 `version` 字段，使用 MyBatis-Plus 的 `@Version` 乐观锁注解；或在 SQL 层面使用 `UPDATE book SET available_copies = available_copies - 1 WHERE id = ? AND available_copies > 0`，并检查影响行数。

---

### 2.2 [并发安全] `LoginUser.visitCount` 非线程安全

**文件**: `SpringBoot/src/main/java/com/example/demo/LoginUser.java` — 第 4 行

**问题描述**: `static int visitCount` 使用 `++` 自增操作，该操作在 JVM 层面不是原子性的（read-increment-write 三步）。高并发登录场景下会导致计数丢失，且无跨线程可见性保证。

**失败场景**: 100 个并发登录请求可能只记录到 60-80 次访问。

**修复方向**: 改用 `AtomicInteger` 或 `LongAdder`。

---

### 2.3 [功能 Bug] `Password.vue` 调用了管理员专用接口，普通用户修改密码必然失败

**文件**:
- 前端: `vue/src/views/Password.vue` — 第 106 行
- 后端: `SpringBoot/src/main/java/com/example/demo/controller/UserController.java` — 第 102-118 行

**问题描述**: 前端 `Password.vue` 调用 `request.put("/user", this.form2)`（发送 `{ password, id }`），但后端 `PUT /user` 接口在第 105 行检查 `role != 1` 即返回 403 "无权限操作"。普通用户（role=2）修改密码永远失败。

正确的密码修改接口是 `UserController.java` 第 66-75 行的 `PUT /user/password`，该接口无角色检查，接受 `@RequestParam Integer id, @RequestParam String password2` 参数。

**失败场景**: 任一普通读者登录 → 点击修改密码 → 填写新旧密码 → 点击确认 → 收到 403 错误，密码未被修改。

**修复方向**: `Password.vue` 第 106 行改为调用 `PUT /user/password`，并按后端期望的 `@RequestParam` 格式传递 `id` 和 `password2` 参数。

---

### 2.4 [潜在 NPE] `TokenUtils.getUser()` 在非请求上下文中可能抛出 NullPointerException

**文件**: `SpringBoot/src/main/java/com/example/demo/utils/TokenUtils.java` — 第 47 行

**问题描述**: `((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest()` — 如果在非 HTTP 请求线程（如 `@Async` 方法、定时任务）中调用，`RequestContextHolder.getRequestAttributes()` 返回 `null`，链式调用直接抛出 NPE。虽然外层有 try-catch，但 NPE 发生在 `.getRequest()` 的调用链上，被 catch 后返回 null，且日志写的是 "解析token失败"，实际原因被掩盖。

**修复方向**: 在调用前显式判空：`RequestAttributes attrs = RequestContextHolder.getRequestAttributes(); if (attrs == null) return null;`，并区分日志信息。

---

### 2.5 [配置错误] `@MapperScan("com.example.demo.service")` 扫描了错误的包

**文件**: `SpringBoot/src/main/java/com/example/demo/commom/MybatisPlusConfig.java` — 第 13 行

**问题描述**: `@MapperScan` 用于扫描 MyBatis Mapper 接口。`service` 包下是 `@Service` 注解的类，不是 Mapper 接口。虽然当前不会报错（没有接口可代理），但语义错误，如果后续在 service 包中添加接口，MyBatis 会尝试为其生成代理，可能导致启动失败。

**修复方向**: 将 `@MapperScan` 改为 `"com.example.demo.mapper"`，或直接删除该注解（MyBatis-Plus 会自动扫描 `@Mapper` 注解的接口）。

---

### 2.6 [架构违规] `LendRecordController` 在 Controller 层使用 `@Transactional` 管理多表事务

**文件**: `SpringBoot/src/main/java/com/example/demo/controller/LendRecordController.java` — 第 82、98、115、132 行

**问题描述**: `delete`、`deleteRecord`、`deleteRecords`、`update` 四个方法直接标注 `@Transactional(rollbackFor = Exception.class)`，在 Controller 中操作 4 个不同的 Mapper 执行多表写操作。事务边界应该在 Service 层，这是 Spring 最佳实践的严重偏离。

**修复方向**: 将这四个方法中的多表事务逻辑提取到新的 `LendRecordService` 中，Controller 仅负责参数校验和调用 Service。

---

### 2.7 [潜在 Bug] `like()` 用于整数列查询

**文件**: `SpringBoot/src/main/java/com/example/demo/controller/UserController.java` — 第 170 行（`like(User::getId, search1)`）、第 175、189 行（`like(User::getRole, 2)`）

**问题描述**: 对 `Integer` 类型的 `id` 和 `role` 字段使用 SQL `LIKE` 操作。虽然 MySQL 会做隐式类型转换使查询"能跑"，但语义不正确、不可移植（换 PostgreSQL 直接报错）、且可能产生意外的模糊匹配结果。

**修复方向**: 将 `like` 改为 `eq`。`role` 字段改为 `wrappers.eq(User::getRole, 2)`，`id` 字段若确是搜索用途应确认需求后改用精确匹配。

---

## 3. 🟡 中优先级问题（建议修复）

### 3.1 [架构一致] 大量 Controller 仍然直接注入 Mapper，绕过 Service 层

**文件**:
- `controller/BookController.java` — 第 23-24 行（注入 `BookMapper`，全部 CRUD 直接用）
- `controller/BookWithUserController.java` — 第 27-31 行（注入 `BookWithUserMapper`、`BookMapper`）
- `controller/LendRecordController.java` — 第 32-42 行（注入 4 个 Mapper）
- `controller/DashboardController.java` — 第 21-26 行（注入 3 个 Mapper）
- `controller/UserController.java` — 第 31-32 行（注入 `UserMapper`，全部 CRUD 直接用）
- `controller/OperationLogController.java` — 第 23-24 行（注入 `OperationLogMapper`）
- `controller/LendRecordController1.java` — 第 22-23 行（注入 `BookMapper`）

**问题描述**: 代码库处于"半迁移"状态：只有借阅/归还/续借逻辑被提取到了 `BorrowService`，其余所有业务逻辑（图书 CRUD、用户 CRUD、借阅记录管理、仪表盘统计）仍直接写在 Controller 中，直接调用 Mapper。这导致：
- 无法在统一的地方实施横切关注点（缓存、权限、校验）
- 业务逻辑散布在多个 Controller 中
- `BookMapper` 被注入到 5 个不同的类中，数据访问无统一入口

**修复方向**: 逐步为每个领域抽取 Service 层（`BookService`、`UserService`、`LendRecordService`、`DashboardService`），Controller 只做参数校验和路由。

---

### 3.2 [死代码] `BookController` 注入了未使用的 `BorrowService`

**文件**: `SpringBoot/src/main/java/com/example/demo/controller/BookController.java` — 第 27-28 行

**问题描述**: `@Resource BorrowService borrowService` 被声明但从未在类中任何方法内使用。这是重构遗留的死注入。

**修复方向**: 删除该字段。

---

### 3.3 [命名异味] `LendRecordController1` 和路由 `/LendRecord1` 命名不合理

**文件**: `SpringBoot/src/main/java/com/example/demo/controller/LendRecordController1.java` — 全文

**问题描述**: 类名 `LendRecordController1`（后缀 `1`）和路由 `/LendRecord1` 是明显的重构遗留产物。该 Controller 只包含一个还书端点 `PUT /`，注释说明这是将还书逻辑迁移到 `BorrowService.returnBook()` 时保留的兼容层。该端点应合并到 `BookWithUserController`（已有借阅相关的 `insertNew`）或 `LendRecordController`。

**修复方向**: 将 `LendRecordController1` 的唯一端点合并到 `BookWithUserController`，删除该文件，并更新前端调用路径。

---

### 3.4 [代码重复] 管理员权限检查模式在 15+ 个方法中重复

**涉及文件及方法**（共 18 处）:
- `BookController.java`: save（第 36 行）、update（第 62 行）、deleteBatch（第 90 行）、delete（第 112 行）
- `BookWithUserController.java`: update（第 104 行）、deleteRecords（第 128 行）
- `LendRecordController.java`: delete（第 82 行）、deleteRecord（第 98 行）、deleteRecords（第 115 行）、update（第 132 行）、update2（第 224 行）
- `OperationLogController.java`: findPage（第 31 行）
- `UserController.java`: save（第 78 行）、password（第 104 行）、deleteBatch（第 120 行）、delete（第 137 行）、findPage（第 155 行）、findPage2（第 173 行）

**重复模式**:
```java
Integer role = (Integer) request.getAttribute("role");
if (role == null || role != 1) {
    return Result.error("403", "无权限操作");
}
```

**修复方向**: 提取为 `JwtInterceptor` 中的注解驱动权限检查（如自定义 `@AdminOnly` 注解 + 拦截器判断），或至少提取为 `TokenUtils` 的静态方法 `TokenUtils.requireAdmin(request)`。

---

### 3.5 [代码重复] `QueryWrapper` 条件构建模式在 5 个 Controller 的 `findPage` 方法中重复

**涉及文件**:
- `BookController.java` — 第 125-137 行
- `BookWithUserController.java` — 第 143-161 行
- `LendRecordController.java` — 第 255-277 行
- `OperationLogController.java` — 第 40-56 行
- `UserController.java` — 第 157-171 行、第 179-195 行

**重复模式**:
```java
LambdaQueryWrapper<Entity> wrappers = Wrappers.<Entity>lambdaQuery();
if (StringUtils.isNotBlank(search1)) { wrappers.like(Entity::getField1, search1); }
if (StringUtils.isNotBlank(search2)) { wrappers.like(Entity::getField2, search2); }
if (StringUtils.isNotBlank(search3)) { wrappers.like(Entity::getField3, search3); }
wrappers.orderByDesc(Entity::getId);
```

**修复方向**: 抽取 `Page<T> findPage(int pageNum, int pageSize, String[] searchValues, SFunction<T, ?>[] searchFields, Class<T> entityClass)` 通用方法到公共工具类或 BaseService。

---

### 3.6 [死 API] 后端 `GET /user` 端点无前端调用

**文件**: `SpringBoot/src/main/java/com/example/demo/controller/UserController.java` — 第 155-171 行

**问题描述**: `GET /user`（单字段搜索版）从未被任何前端页面调用。前端 `User.vue` 第 144 行实际调用的是 `GET /user/usersearch`（四字段搜索版）。该接口为死代码。

**修复方向**: 删除该端点，或在前端搜索场景评估后合并两个搜索接口。

---

### 3.7 [死 API] 后端 `PUT /LendRecord/byTime/{lendTime}` 无前端调用

**文件**: `SpringBoot/src/main/java/com/example/demo/controller/LendRecordController.java` — 第 224-238 行

**问题描述**: `PUT /LendRecord/byTime/{lendTime}` 从未被任何前端页面调用。前端仅使用 `PUT /LendRecord/{id}`（按 ID 更新）和 DELETE 变体。

**修复方向**: 确认该端点无业务需求后删除。

---

### 3.8 [死调用] 前端 `POST /LendRecord` 调用后端空操作桩

**文件**: `vue/src/views/Book.vue` — 第 414 行

**问题描述**: 前端借阅流程中调用 `request.post("/LendRecord", this.form2)`，但后端 `LendRecordController` 第 277-281 行仅执行 `return Result.success()`（空操作）。注释说明 "lend_record 插入已迁移到 BorrowService.borrowBook()"。实际的借阅记录插入发生在下一行调用的 `POST /bookwithuser/insertNew` → `BorrowService.borrowBook()` 中。

**修复方向**: 从 `Book.vue` 的 `handlelend()` 方法中删除该冗余调用。

---

### 3.9 [死调用] 前端两处调用 `POST /bookwithuser/deleteRecord` 均为空操作桩

**文件**:
- `vue/src/views/Book.vue` — 第 347 行
- `vue/src/views/BookWithUser.vue` — 第 185 行

**问题描述**: 两处均调用 `request.post("/bookwithuser/deleteRecord", form3)`，但后端 `BookWithUserController` 第 121-125 行仅执行 `return Result.success()`。注释说明 "bookwithuser 删除已迁移到 BorrowService.returnBook()"。

**修复方向**: 从两个前端文件中删除该冗余调用。

---

### 3.10 [无 DTO 层] Entity 类直接暴露给前端

**涉及文件**: `entity/Book.java`、`entity/User.java`、`entity/LendRecord.java`、`entity/BookWithUser.java`、`entity/OperationLog.java`

**问题描述**: 项目完全没有 DTO 层，所有 Entity 同时承担 ORM 持久化和 API 序列化的双重职责。`BookWithUser.java` 中已经体现了这种做法的痛苦：使用 `@JsonIgnore` 隐藏主键 `id`，又用 `@JsonProperty("id")` 将 `userId` 映射为 `"id"` 以兼容前端。

**影响**: 数据库 schema 变更直接暴露给 API 消费者；敏感字段（如 `User.password`）可能被意外序列化；前端契约与数据库结构耦合。

**修复方向**: 引入 DTO/VO 层，至少对需要隐藏字段或变更格式的 Entity（BookWithUser、User）创建专门的响应对象。

---

### 3.11 [方法过长] `LendRecordController.update()` 约 85 行，嵌套深度 3-4 层

**文件**: `SpringBoot/src/main/java/com/example/demo/controller/LendRecordController.java` — 第 122-207 行

**问题描述**: `update` 方法包含三个独立分支（wasReturned→nowReturned、wasReturned→!nowReturned、状态不变），每个分支都涉及多表操作，嵌套深度达 3-4 层，逻辑难以理解和测试。

**修复方向**: 拆分为三个私有方法 `handleReturnToNotReturn()`、`handleNotReturnToReturn()`、`handleStatusUnchanged()`。

---

### 3.12 [方法过长] `BorrowService.borrowBook()` 约 75 行

**文件**: `SpringBoot/src/main/java/com/example/demo/service/BorrowService.java` — 第 101-176 行

**问题描述**: borrowBook 方法虽有注释分段，但仍过长。可将其中的"借阅数量校验"和"BookWithUser 记录创建"逻辑提取为独立的私有方法。

**修复方向**: 提取 `validateBorrowLimit()` 和 `createBookWithUserRecord()` 私有方法。

---

### 3.13 [数据溢出风险] `Long` 到 `int` 的 `.intValue()` 转换可能截断

**文件**:
- `SpringBoot/src/main/java/com/example/demo/service/BorrowService.java` — 第 109、127、131、163、173、180、200、207、214 行
- `SpringBoot/src/main/java/com/example/demo/controller/BookController.java` — 第 72 行

**问题描述**: `BorrowService` 方法参数使用 `Long userId, Long bookId`，但 Entity 的 ID 字段为 `Integer`。多处调用 `.intValue()` 转换，如果 ID 超过 `Integer.MAX_VALUE`（约 21 亿），值会被静默截断。

**修复方向**: 统一 ID 类型。推荐将 Entity ID 字段改为 `Long`（MyBatis-Plus 推荐做法），或者至少在 `.intValue()` 前添加溢出检查。

---

### 3.14 [CORS 不完整] `@CrossOrigin` 仅标注在登录接口上

**文件**: `SpringBoot/src/main/java/com/example/demo/controller/UserController.java` — 第 48 行

**问题描述**: `@CrossOrigin` 仅标注在 `login()` 方法上。`WebMvcConfig.java` 也没有覆盖 `addCorsMappings()`。虽然 `JwtInterceptor` 对 OPTIONS 请求返回 `true`，但不会添加 CORS 响应头。如果前端部署在不同源（非 dev-server 代理模式），非登录接口的跨域请求将失败。

**修复方向**: 在 `WebMvcConfig` 中全局配置 CORS，或至少在 `JwtInterceptor.preHandle()` 中为 OPTIONS 请求添加必要的 CORS 响应头。

---

### 3.15 [命名规范] Mapper 字段名首字母大写，违反 Java 命名约定

**文件**:
- `BookController.java` — 第 19 行: `BookMapper BookMapper`（应为 `bookMapper`）
- `BookWithUserController.java` — 第 21 行: `BookWithUserMapper BookWithUserMapper`（应为 `bookWithUserMapper`）
- `LendRecordController.java` — 第 24 行: `LendRecordMapper LendRecordMapper`（应为 `lendRecordMapper`）

**问题描述**: 字段名与类名完全相同（包括首字母大写），违反 JavaBeans 规范的小写首字母约定。虽然合法但在代码审查和静态分析工具中会触发警告。

**修复方向**: 改为标准小写首字母命名。

---

### 3.16 [硬编码] `LendRecordController` 中 `prolong` 被硬编码为 `1`，绕过系统配置

**文件**: `SpringBoot/src/main/java/com/example/demo/controller/LendRecordController.java` — 第 184 行

**问题描述**: 管理员将"已归还"记录改为"未归还"状态时，`BookWithUser` 记录的 `prolong` 被硬编码为 `1`。这绕过了 `BorrowService` 中的 `borrow.max-renew-count` 配置，用户在后续续借时会少一次机会（系统配置可能允许更多续借次数）。

**修复方向**: 从 `BorrowService` 或配置中读取 `maxRenewCount` 值。

---

### 3.17 [命名冲突] Lombok `@Slf4j` 生成的 `log` 字段与方法 `log()` 同名

**文件**: `SpringBoot/src/main/java/com/example/demo/service/OperationLogService.java` — 第 18-19 行 + 第 34 行

**问题描述**: Lombok `@Slf4j` 生成 `private static final Logger log`，同时类中有 `public void log(...)` 方法。虽不导致编译错误（字段和方法在不同的 Java 命名空间），但第 46 行的 `log.error(...)` 引用的是 SLF4J Logger 而非自身方法，阅读代码时容易混淆。

**修复方向**: 将业务方法重命名为 `writeLog()` 或 `recordLog()`。

---

## 4. 🟢 低优先级问题（可选修复）

### 4.1 `Book.vue` 借阅/归还流程中存在冗余 API 调用链

**文件**: `vue/src/views/Book.vue`

- 第 318-352 行（归还流程）: 连续调用 `PUT /book`（空操作）+ `PUT /LendRecord1/`（实际操作）+ `POST /bookwithuser/deleteRecord`（空操作），共 3 次 HTTP 请求，仅中间一次有用。
- 第 391-432 行（借阅流程）: 连续调用 `PUT /book`（空操作）+ `POST /LendRecord`（空操作）+ `POST /bookwithuser/insertNew`（实际操作），共 3 次 HTTP 请求，仅最后一次有用。

对比 `BookWithUser.vue` 第 215-231 行的归还流程仅发出 1 次请求（`PUT /LendRecord1/`），干净利落。

**修复方向**: 参考 `BookWithUser.vue` 的实现，`Book.vue` 中移除冗余调用。

---

### 4.2 `Result` 类的 `code`/`msg` 字段未在构造时初始化

**文件**: `SpringBoot/src/main/java/com/example/demo/commom/Result.java` — 第 27 行（无参构造器）

**问题描述**: 无参构造器不初始化 `code` 和 `msg` 字段。当前通过静态工厂方法（`success()`/`error()`）构造对象，不会出问题，但无参构造器是 `public` 的，存在被错误使用的风险。

**修复方向**: 将无参构造器改为 `private`，或在此构造器中设置默认值。

---

### 4.3 `res.code` 比较风格不一致（`== 0` vs `=== '0'`）

**涉及文件**: 所有前端视图文件

- 使用 `=== '0'`（严格相等，推荐）: `Book.vue` 第 226 行、`BookWithUser.vue` 第 136 行、`LendRecord.vue` 第 154 行、`User.vue` 第 134/204 行、`Log.vue` 第 137 行、`Person.vue` 第 55 行
- 使用 `== 0`（松散相等）: 其余所有位置

当前两种写法都能正常工作（JS 的 `"0" == 0` 为 true），但不一致。一致性修复建议统一为 `=== '0'`，与后端 String 类型匹配。

**修复方向**: 全局搜索替换为统一的严格相等比较。

---

### 4.4 `Book.vue` 中 `dialogVisible3` 初始值应为 `false`

**文件**: `vue/src/views/Book.vue` — 第 199 行

**问题描述**: `dialogVisible3`（逾期通知对话框）在 `data()` 中初始化为 `true`，意味着页面加载时会先闪现一个空对话框，然后 `mounted` 中调用 `load()` 后才被业务逻辑控制。应初始化为 `false`（由 `toLook()` 方法在第 500 行设为 `true` 打开）。

**修复方向**: 将初始值从 `true` 改为 `false`。

---

### 4.5 未使用的 import 语句

**文件**: `SpringBoot/src/main/java/com/example/demo/controller/UserController.java`
- 第 3 行: `import com.baomidou.mybatisplus.core.toolkits.Constants;` — 未使用
- 第 7 行: `import com.example.demo.entity.BookWithUser;` — 未使用
- 第 14 行: `import org.apache.ibatis.annotations.Param;` — 未使用
- 第 15 行: `import org.apache.ibatis.jdbc.Null;` — 未使用

**修复方向**: 删除未使用的 import，或配置 IDE 自动清理。

---

### 4.6 包名拼写错误: `commom` 应为 `common`

**文件**: `SpringBoot/src/main/java/com/example/demo/commom/` 目录

**问题描述**: 包名 `commom` 是 `common` 的拼写错误。该目录下包含 `Result.java` 和 `MybatisPlusConfig.java`。

**修复方向**: 将 `commom` 重命名为 `common`，并更新所有引用该包中类的 import 语句。

---

## 5. 无显著问题的模块

以下模块在本次扫描中未发现明显问题：

| 模块 | 状态 |
|------|------|
| `mapper/` 全部 5 个接口 | ✅ 均为纯 MyBatis-Plus BaseMapper，无自定义 SQL，结构清晰 |
| `config/WebMvcConfig.java` | ✅ 拦截器注册正确，静态资源排除配置合理 |
| `interceptor/JwtInterceptor.java` | ✅ 逻辑清晰，OPTIONS 放行、token 校验、角色信息注入 |
| `entity/Book.java` | ✅ 字段定义规范，注释说明了多副本迁移状态 |
| `entity/User.java` | ✅ 字段定义规范，token 字段正确使用 `@TableField(exist = false)` |
| `entity/OperationLog.java` | ✅ 结构完整，createTime 填充逻辑正确 |
| `DemoApplication.java` | ✅ 启动类配置简洁，`@EnableAsync` 正确启用 |
| `service/OperationLogService.java` | ✅ 异步日志写入设计合理，异常静默吞噬符合日志旁路语义 |
| `vue/src/components/Aside.vue` | ✅ 角色菜单控制清晰，用户信息读取正确 |
| `vue/src/utils/request.js` | ✅ Axios 配置合理，拦截器逻辑清晰 |
| `vue/src/views/Dashboard.vue` | ✅ API 调用简洁，字段映射完全匹配后端 |
| `vue/src/views/Log.vue` | ✅ 筛选条件传递正确，分页逻辑完整 |

---

## 6. 修复优先级建议

| 序号 | 问题 | 优先级 | 建议修复顺序 |
|------|------|--------|------------|
| 1 | `Password.vue` 调错接口（2.3） | 🔴 高 | **立即修复** — 功能性 Bug |
| 2 | `availableCopies` 竞态条件（2.1） | 🔴 高 | **立即修复** — 数据完整性风险 |
| 3 | `visitCount` 非线程安全（2.2） | 🔴 高 | 短期修复 |
| 4 | `@MapperScan` 包路径错误（2.5） | 🔴 高 | 短期修复 |
| 5 | Controller 中 `@Transactional`（2.6） | 🔴 高 | 中期重构 |
| 6 | `like()` 用于整数列（2.7） | 🔴 高 | 短期修复 |
| 7 | `TokenUtils` NPE 风险（2.4） | 🔴 高 | 短期修复 |
| 8 | 死调用/死 API 清理（3.2/3.6/3.7/3.8/3.9） | 🟡 中 | 本迭代清理 |
| 9 | 权限检查重复（3.4） | 🟡 中 | 中期重构 |
| 10 | Controller 直调 Mapper（3.1） | 🟡 中 | 长期重构 |
| 11 | 其余中优先级问题 | 🟡 中 | 按需修复 |
| 12 | 低优先级问题 | 🟢 低 | 随手修复 |

---

> **注**: 本报告由自动化工具辅助生成，所有文件路径和行号基于 `refactor/test-drill` 分支的当前 HEAD（commit `244cbe4`）。报告仅做分析，未对代码库做任何修改。
