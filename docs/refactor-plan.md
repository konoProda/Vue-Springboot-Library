# 借书/还书/续借 流程分析与重构迁移计划

> **阶段：仅分析，不修改代码**
> 生成日期: 2026-07-09
> 分支: refactor/test-drill

---

## 目录

1. [当前架构分析](#1-当前架构分析)
2. [借书流程详解](#2-借书流程详解)
3. [还书流程详解](#3-还书流程详解)
4. [续借流程详解](#4-续借流程详解)
5. [涉及的所有类和文件清单](#5-涉及的所有类和文件清单)
6. [当前架构问题清单](#6-当前架构问题清单)
7. [目标架构设计](#7-目标架构设计)
8. [迁移步骤清单](#8-迁移步骤清单)

---

## 1. 当前架构分析

### 1.1 整体架构

```
┌─────────────────────────────────────────────────┐
│                   Vue 3 Frontend                 │
│  Book.vue (借书/还书) + BookWithUser.vue (续借)   │
│          Axios → /api/* → localhost:9090/*       │
└──────────────────────┬──────────────────────────┘
                       │ HTTP
┌──────────────────────┴──────────────────────────┐
│              Spring Boot Backend                 │
│                                                  │
│  Controller ──(直接注入)──→ Mapper (MyBatis-Plus) │
│       ↓                        ↓                 │
│   无 Service 层         无自定义 SQL/XML          │
│   无事务管理              纯 BaseMapper            │
└──────────────────────┬──────────────────────────┘
                       │ JDBC
┌──────────────────────┴──────────────────────────┐
│              MySQL: springboot-vue                │
│  Tables: book, user, lend_record, bookwithuser   │
└─────────────────────────────────────────────────┘
```

### 1.2 数据表关系

| 表名 | 用途 | 关键字段 |
|------|------|---------|
| `book` | 图书目录 | id, isbn, name, status(0=已借/1=未借), borrownum |
| `user` | 用户 | id, username, role(1=admin/2=reader) |
| `lend_record` | 借阅历史 | reader_id, isbn, bookname, lend_time, return_time, status, borrownum |
| `bookwithuser` | 活跃借阅 | id(userId), isbn, book_name, nick_name, lendtime, deadtime, prolong |

**核心关系**:
- `bookwithuser` 记录"谁正在借哪本书"（活跃借阅）
- `lend_record` 记录"谁借过哪本书"（历史记录，含归还时间）
- `book.borrownum` 是该书的累计借阅次数
- `book.status` 标识该书当前是否可借

---

## 2. 借书流程详解

### 2.1 调用时序

```
Frontend (Book.vue: handlelend)
  │
  ├─(1) PUT /book
  │     Controller: BookController.update()
  │     Mapper: BookMapper.updateById()
  │     操作: 更新 book.status = "0" (已借阅), book.borrownum++
  │
  ├─(2) POST /LendRecord
  │     Controller: LendRecordController.save()
  │     Mapper: LendRecordMapper.insert()
  │     操作: 插入 lend_record (readerId, isbn, bookname, lendTime, status="0", borrownum)
  │
  └─(3) POST /bookwithuser/insertNew
        Controller: BookWithUserController.insertNew()
        Mapper: BookWithUserMapper.insert()
        操作: 插入 bookwithuser (id=userId, isbn, bookName, nickName, lendtime, deadtime=+30天, prolong=1)
```

### 2.2 参与类和方法签名

#### BookController.java — `PUT /book`
```java
@RestController
@RequestMapping("/book")
public class BookController {
    @Resource BookMapper BookMapper;

    @PutMapping
    public Result<?> update(@RequestBody Book book) {
        BookMapper.updateById(book);
        return Result.success();
    }
}
```
- **输入**: `Book` JSON (含 id, status="0", borrownum++)
- **操作**: `BookMapper.updateById(book)` — MyBatis-Plus 内置方法
- **注意**: 同时被还书流程调用（status="1"）

#### LendRecordController.java — `POST /LendRecord`
```java
@RestController
@RequestMapping("/LendRecord")
public class LendRecordController {
    @Resource LendRecordMapper LendRecordMapper;

    @PostMapping
    public Result<?> save(@RequestBody LendRecord LendRecord) {
        LendRecordMapper.insert(LendRecord);
        return Result.success();
    }
}
```
- **输入**: `LendRecord` JSON (readerId, isbn, bookname, lendTime, status="0", borrownum)
- **操作**: `LendRecordMapper.insert(lendRecord)`

#### BookWithUserController.java — `POST /bookwithuser/insertNew`
```java
@RestController
@RequestMapping("/bookwithuser")
public class BookWithUserController {
    @Resource BookWithUserMapper BookWithUserMapper;

    @PostMapping("/insertNew")
    public Result<?> insertNew(@RequestBody BookWithUser BookWithUser) {
        BookWithUserMapper.insert(BookWithUser);
        return Result.success();
    }
}
```
- **输入**: `BookWithUser` JSON (id=userId, isbn, bookName, nickName, lendtime, deadtime=+30天, prolong=1)
- **操作**: `BookWithUserMapper.insert(bookWithUser)`

#### 涉及的 Mapper
| Mapper | 实体 | 使用的方法 |
|--------|------|-----------|
| `BookMapper` | `Book` | `updateById(Book)` |
| `LendRecordMapper` | `LendRecord` | `insert(LendRecord)` |
| `BookWithUserMapper` | `BookWithUser` | `insert(BookWithUser)` |

---

## 3. 还书流程详解

### 3.1 调用时序

```
Frontend (Book.vue: handlereturn)
  │
  ├─(1) PUT /book
  │     Controller: BookController.update()
  │     Mapper: BookMapper.updateById()
  │     操作: 更新 book.status = "1" (未借阅)
  │
  ├─(2) PUT /LendRecord1
  │     Controller: LendRecordController1.update2()
  │     Mapper: LendRecordMapper.update()
  │     操作: 更新 lend_record 的 returnTime 和 status="1"
  │           条件: isbn + reader_id + borrownum 精确匹配
  │
  └─(3) POST /bookwithuser/deleteRecord
        Controller: BookWithUserController.deleteRecord()
        Mapper: BookWithUserMapper.deleteByMap()
        操作: 删除 bookwithuser 中该用户+该书的记录
```

### 3.2 参与类和方法签名

#### BookController.java — `PUT /book` (同上)
```java
@PutMapping
public Result<?> update(@RequestBody Book book)
```
- 与借书共用同一接口，借书传 status="0"，还书传 status="1"

#### LendRecordController1.java — `PUT /LendRecord1`
```java
@RestController
@RequestMapping("/LendRecord1")
public class LendRecordController1 {
    @Resource LendRecordMapper LendRecordMapper;

    @PutMapping
    public Result<?> update2(@RequestBody LendRecord lendRecord) {
        UpdateWrapper<LendRecord> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("isbn", lendRecord.getIsbn())
                     .eq("reader_id", lendRecord.getReaderId())
                     .eq("borrownum", lendRecord.getBorrownum());
        LendRecord lendrecord = new LendRecord();
        lendrecord.setReturnTime(lendRecord.getReturnTime());
        lendrecord.setStatus(lendRecord.getStatus());
        LendRecordMapper.update(lendrecord, updateWrapper);
        return Result.success();
    }
}
```
- **输入**: `LendRecord` JSON (isbn, readerId, borrownum, returnTime, status="1")
- **查询条件**: `isbn` + `reader_id` + `borrownum` 三个字段联合定位唯一记录
- **注意**: 这是一个**独立的Controller**，仅用于还书操作。为何不合并到 LendRecordController 中待分析

#### BookWithUserController.java — `POST /bookwithuser/deleteRecord`
```java
@PostMapping("/deleteRecord")
public Result<?> deleteRecord(@RequestBody BookWithUser BookWithUser) {
    Map<String,Object> map = new HashMap<>();
    map.put("isbn", BookWithUser.getIsbn());
    map.put("id", BookWithUser.getId());
    BookWithUserMapper.deleteByMap(map);
    return Result.success();
}
```
- **输入**: `BookWithUser` JSON (isbn, id=userId)
- **操作**: 按 isbn + userId 删除活跃借阅记录
- **注意**: 前端实际传了更多字段(bookName, nickName, lendtime, deadtime, prolong)，但后端只用了 isbn 和 id

#### 涉及的 Mapper
| Mapper | 实体 | 使用的方法 |
|--------|------|-----------|
| `BookMapper` | `Book` | `updateById(Book)` |
| `LendRecordMapper` | `LendRecord` | `update(LendRecord, UpdateWrapper)` |
| `BookWithUserMapper` | `BookWithUser` | `deleteByMap(Map)` |

---

## 4. 续借流程详解

### 4.1 调用时序

```
Frontend (BookWithUser.vue: handlereProlong)
  │
  └─(1) POST /bookwithuser
        Controller: BookWithUserController.update()
        Mapper: BookWithUserMapper.update()
        操作: 更新 bookwithuser 的 deadtime (+30天), prolong--
        条件: isbn + id(userId) 匹配
```

### 4.2 参与类和方法签名

#### BookWithUserController.java — `POST /bookwithuser`
```java
@PostMapping
public Result<?> update(@RequestBody BookWithUser BookWithUser) {
    UpdateWrapper<BookWithUser> updateWrapper = new UpdateWrapper<>();
    updateWrapper.eq("isbn", BookWithUser.getIsbn())
                 .eq("id", BookWithUser.getId());
    BookWithUserMapper.update(BookWithUser, updateWrapper);
    return Result.success();
}
```
- **输入**: `BookWithUser` JSON (isbn, id=userId, deadtime=+30天, prolong--)
- **查询条件**: isbn + id (userId)
- **更新字段**: deadtime, prolong
- **注意**: 此接口同时也是管理员修改借阅信息的接口（BookWithUser.vue: save()）

#### 涉及的 Mapper
| Mapper | 实体 | 使用的方法 |
|--------|------|-----------|
| `BookWithUserMapper` | `BookWithUser` | `update(BookWithUser, UpdateWrapper)` |

**注意**: 续借操作**不涉及** `lend_record` 表的更新，也不涉及 `book` 表的变更。

---

## 5. 涉及的所有类和文件清单

### 5.1 后端 Java 类

| 文件路径 | 类型 | 角色 |
|---------|------|------|
| `SpringBoot/src/main/java/com/example/demo/controller/BookController.java` | Controller | 图书 CRUD + 借书/还书时更新 book.status |
| `SpringBoot/src/main/java/com/example/demo/controller/BookWithUserController.java` | Controller | 活跃借阅管理 (insertNew/update/deleteRecord) |
| `SpringBoot/src/main/java/com/example/demo/controller/LendRecordController.java` | Controller | 借阅历史 CRUD (save/findPage/delete) |
| `SpringBoot/src/main/java/com/example/demo/controller/LendRecordController1.java` | Controller | 还书操作 (更新 returnTime + status) |
| `SpringBoot/src/main/java/com/example/demo/controller/UserController.java` | Controller | 用户管理 (间接相关: 登录/注册) |
| `SpringBoot/src/main/java/com/example/demo/entity/Book.java` | Entity | book 表映射 |
| `SpringBoot/src/main/java/com/example/demo/entity/BookWithUser.java` | Entity | bookwithuser 表映射 |
| `SpringBoot/src/main/java/com/example/demo/entity/LendRecord.java` | Entity | lend_record 表映射 |
| `SpringBoot/src/main/java/com/example/demo/entity/User.java` | Entity | user 表映射 |
| `SpringBoot/src/main/java/com/example/demo/mapper/BookMapper.java` | Mapper | Book CRUD |
| `SpringBoot/src/main/java/com/example/demo/mapper/BookWithUserMapper.java` | Mapper | BookWithUser CRUD |
| `SpringBoot/src/main/java/com/example/demo/mapper/LendRecordMapper.java` | Mapper | LendRecord CRUD |
| `SpringBoot/src/main/java/com/example/demo/mapper/UserMapper.java` | Mapper | User CRUD |
| `SpringBoot/src/main/java/com/example/demo/utils/TokenUtils.java` | Util | JWT 生成/解析 |
| `SpringBoot/src/main/java/com/example/demo/commom/Result.java` | Common | 统一响应包装 |
| `SpringBoot/src/main/java/com/example/demo/commom/MybatisPlusConfig.java` | Config | 分页插件配置 |

### 5.2 前端 Vue 文件

| 文件路径 | 角色 |
|---------|------|
| `vue/src/views/Book.vue` | 图书列表页 — 借书按钮(`handlelend`) + 还书按钮(`handlereturn`) |
| `vue/src/views/BookWithUser.vue` | 借阅管理页 — 续借按钮(`handlereProlong`) + 管理员修改/删除 |
| `vue/src/utils/request.js` | Axios 实例 (baseURL: `/api`, 请求拦截器) |
| `vue/src/router/index.js` | 路由配置 |

### 5.3 数据库表

| 表名 | 借书 | 还书 | 续借 |
|------|:----:|:----:|:----:|
| `book` | ✅ (status→0, borrownum++) | ✅ (status→1) | ❌ |
| `lend_record` | ✅ (INSERT 新记录) | ✅ (UPDATE returnTime+status) | ❌ |
| `bookwithuser` | ✅ (INSERT 新记录) | ✅ (DELETE 记录) | ✅ (UPDATE deadtime+prolong) |

---

## 6. 当前架构问题清单

### 6.1 架构层面

| # | 问题 | 严重度 | 说明 |
|---|------|--------|------|
| A1 | **无 Service 层** | 🔴 高 | Controller 直接注入 Mapper，业务逻辑分散在各 Controller 中，无法复用。借书逻辑分散在 3 个 API 调用中 |
| A2 | **无事务管理** | 🔴 高 | 借书是 3 步操作（更新book + 插入lend_record + 插入bookwithuser），还书也是 3 步，任一步失败都会导致数据不一致 |
| A3 | **前端编排业务逻辑** | 🔴 高 | 借书/还书的多步操作在前端通过串行请求完成，网络中断会导致部分数据写入。业务规则（如最多借5本、逾期不能借）全在前端判断 |
| A4 | **无认证拦截** | 🔴 高 | TokenUtils 可生成和解析 token，但所有 Controller 端点都没有验证 token。任何知道 API 地址的人都可以操作 |
| A5 | **LendRecordController1 冗余** | 🟡 中 | 仅有一个还书方法，应合并到 LendRecordController 中 |
| A6 | **命名不一致** | 🟡 中 | `LendRecord` vs `BookWithUser` 大小写混乱，Controller 变量名 `BookWithUserMapper` 以大写开头（与类名冲突） |

### 6.2 业务逻辑层面

| # | 问题 | 严重度 | 说明 |
|---|------|--------|------|
| B1 | **硬编码业务规则在前端** | 🔴 高 | 借书期限 30 天、最大借阅 5 本、续借延长 30 天、续借次数 1 次全部硬编码在 Vue 中 |
| B2 | **book.status 状态管理不一致** | 🟡 中 | book.status 用字符串 "0"/"1" 表示已借/未借，lend_record.status 也用 "0"/"1"，但语义不同（"0"=借阅中/"1"=已归还），容易混淆 |
| B3 | **borrownum 字段冗余** | 🟡 中 | `book.borrownum` (累计借阅次数) 和 `lend_record.borrownum` (该次借阅的序号) 容易混淆 |
| B4 | **bookwithuser.prolong 语义反直觉** | 🟡 中 | 初始值为 1，续借后减到 0，0 表示不可再续。不符合直觉（应为剩余次数或已续次数） |
| B5 | **还书时前端构建无用字段** | 🟢 低 | `handlereturn` 中往 `bookwithuser/deleteRecord` 发送了 7 个字段，但后端只用了 isbn 和 id |

### 6.3 接口设计层面

| # | 问题 | 严重度 | 说明 |
|---|------|--------|------|
| C1 | **HTTP 方法语义错误** | 🟡 中 | `POST /bookwithuser` 用于更新（应为 PUT）；`POST /bookwithuser/deleteRecord` 用于删除（应为 DELETE） |
| C2 | **删除操作用 POST** | 🟡 中 | `POST /bookwithuser/deleteRecord` 和 `POST /bookwithuser/deleteRecords` 语义上应该是 DELETE |
| C3 | **LendRecordController 路径变量冲突** | 🟡 中 | `PUT /LendRecord/{isbn}` (String) 和 `PUT /LendRecord/{lendTime}` (Date) 存在路径歧义 |
| C4 | **缺少请求/响应 DTO** | 🟢 低 | 直接使用 Entity 类作为请求体，暴露了数据库内部结构 |
| C5 | **无参数校验** | 🟢 低 | 无 @Valid/@NotNull 等校验注解，可能导致 NPE |

### 6.4 代码质量层面

| # | 问题 | 严重度 | 说明 |
|---|------|--------|------|
| D1 | **重复 import** | 🟢 低 | `LendRecordController.java` 和 `BookWithUserController.java` 中有重复的 import 语句 |
| D2 | **注释掉的代码** | 🟢 低 | `BookWithUserController.java` 中有大段被注释的代码 |
| D3 | **BookWithUser 构造函数不匹配** | 🟢 低 | 前端还书时 `deleteRecord` 发送了满字段，但 `BookWithUser` 没有全参构造函数，依赖 Lombok 默认行为 |
| D4 | **TokenUtils 静态 hack** | 🟡 中 | 使用 `@PostConstruct` + 静态变量绕过 Spring DI，耦合度高 |

---

## 7. 目标架构设计

### 7.1 目标分层架构

```
┌─────────────────────────────────────────────┐
│              Vue 3 Frontend                  │
│   单一 API 调用: POST /api/borrow            │
│   单一 API 调用: POST /api/return            │
│   单一 API 调用: POST /api/renew             │
└──────────────────────┬──────────────────────┘
                       │ HTTP + JWT Auth
┌──────────────────────┴──────────────────────┐
│          Spring Boot Backend                 │
│                                              │
│  Controller (薄层，接收请求，调用 Service)     │
│      ↓                                       │
│  Service (厚层，业务逻辑 + @Transactional)    │
│      ↓                                       │
│  Mapper (数据访问，MyBatis-Plus BaseMapper)   │
│      ↓                                       │
│  Entity / DTO (数据对象)                      │
└──────────────────────┬──────────────────────┘
                       │
                   MySQL
```

### 7.2 目标类结构

```
controller/
├── BookController.java          — 图书 CRUD (不变)
├── BorrowController.java        — 【新】借书/还书/续借的统一入口
├── UserController.java          — 用户管理 (不变)
├── LendRecordController.java    — 借阅历史查询
└── DashboardController.java     — 仪表盘 (不变)

service/                          — 【新包】
├── BorrowService.java           — 借书/还书/续借核心业务逻辑
└── impl/
    └── BorrowServiceImpl.java   — 实现

mapper/                           — 不变
├── BookMapper.java
├── BookWithUserMapper.java
├── LendRecordMapper.java
└── UserMapper.java

entity/                           — 不变
├── Book.java
├── BookWithUser.java
├── LendRecord.java
└── User.java

dto/                              — 【新包】
├── BorrowRequest.java           — 借书请求 DTO
├── ReturnRequest.java           — 还书请求 DTO
└── RenewRequest.java            — 续借请求 DTO

config/                           — 【新包】
└── WebMvcConfig.java            — JWT 拦截器配置

interceptor/                      — 【新包】
└── JwtInterceptor.java          — JWT 认证拦截器
```

### 7.3 目标 API 设计

| 操作 | 方法 | 路径 | 请求体 | 响应 |
|------|------|------|--------|------|
| 借书 | `POST` | `/api/borrow` | `{isbn, userId}` | `Result<BorrowResult>` |
| 还书 | `POST` | `/api/return` | `{isbn, userId}` | `Result<ReturnResult>` |
| 续借 | `POST` | `/api/renew` | `{isbn, userId}` | `Result<RenewResult>` |
| 借阅列表 | `GET` | `/api/borrows` | query: pageNum, pageSize, search... | `Result<Page<BookWithUser>>` |
| 借阅历史 | `GET` | `/api/lend-records` | query: pageNum, pageSize, search... | `Result<Page<LendRecord>>` |

### 7.4 目标 Service 接口

```java
public interface BorrowService {

    /**
     * 借书: 校验借阅资格 → 更新 book.status → 插入 lend_record → 插入 bookwithuser
     * 整个过程在一个事务中完成
     */
    BorrowResult borrow(String isbn, Integer userId);

    /**
     * 还书: 更新 lend_record.returnTime → 删除 bookwithuser → 更新 book.status
     * 整个过程在一个事务中完成
     */
    ReturnResult returnBook(String isbn, Integer userId);

    /**
     * 续借: 校验续借资格 → 延长 bookwithuser.deadtime → 更新 bookwithuser.prolong
     * 整个过程在一个事务中完成
     */
    RenewResult renew(String isbn, Integer userId);
}
```

### 7.5 业务规则集中化

当前分散在前端的业务规则应集中到 Service 层：

| 规则 | 当前位置 | 目标位置 |
|------|---------|---------|
| 最多借阅 5 本 | `Book.vue: handlelend()` | `BorrowServiceImpl.borrow()` |
| 有逾期未还不可借 | `Book.vue: handlelend()` | `BorrowServiceImpl.borrow()` |
| 同一本书不可重复借 | 无校验 | `BorrowServiceImpl.borrow()` |
| 借阅期限 30 天 | `Book.vue: handlelend()` | `BorrowServiceImpl.borrow()` |
| 续借延长 30 天 | `BookWithUser.vue: handlereProlong()` | `BorrowServiceImpl.renew()` |
| 续借次数上限 1 次 | `BookWithUser.vue` (prolong > 0) | `BorrowServiceImpl.renew()` |
| 还书后才能再借同一本 | `Book.vue` (status check) | `BorrowServiceImpl.borrow()` |

---

## 8. 迁移步骤清单

### 阶段 0: 准备工作

- [ ] **0.1** 创建 `refactor/borrow-service` 分支
- [ ] **0.2** 运行现有测试确认基线: `cd SpringBoot && mvn test`
- [ ] **0.3** 确认前端 baseline 可运行: `cd vue && npm run build`
- [ ] **0.4** 备份数据库或确认测试数据库可用

### 阶段 1: 后端基础设施

- [ ] **1.1** 创建 `dto/` 包，添加请求 DTO
  - `BorrowRequest.java` — `{String isbn, Integer userId}`
  - `ReturnRequest.java` — `{String isbn, Integer userId}`
  - `RenewRequest.java` — `{String isbn, Integer userId}`
  - `BorrowResult.java` — 借书结果
  - `ReturnResult.java` — 还书结果
  - `RenewResult.java` — 续借结果

- [ ] **1.2** 创建 `service/` 包，添加接口
  - `BorrowService.java` — 定义 `borrow()`, `returnBook()`, `renew()` 方法签名

- [ ] **1.3** 创建 `service/impl/` 包，实现 Service
  - `BorrowServiceImpl.java` — 实现完整业务逻辑
  - 注入: `BookMapper`, `BookWithUserMapper`, `LendRecordMapper`
  - 所有 borrow/return/renew 方法加 `@Transactional`
  - 业务规则校验:
    - `borrow()`: 检查用户借阅数 < 5, 无逾期记录, 书状态为可借, 未重复借同一本
    - `returnBook()`: 检查确实有该借阅记录
    - `renew()`: 检查 prolong > 0, 无逾期
  - 借阅期限、续借次数上限等可配置（`@Value` 从配置文件读取）

- [ ] **1.4** 创建 JWT 认证拦截器
  - `interceptor/JwtInterceptor.java`
  - 从请求头 `token` 解析用户，注入到 request attribute
  - 配置白名单: `/user/login`, `/user/register`

- [ ] **1.5** 创建 `config/WebMvcConfig.java`
  - 注册 JwtInterceptor
  - 配置拦截路径

- [ ] **1.6** 添加配置项到 `application.properties`
  ```properties
  borrow.max-count=5
  borrow.duration-days=30
  borrow.renew-duration-days=30
  borrow.max-renew-count=1
  ```

### 阶段 2: 后端 API 整合

- [ ] **2.1** 创建 `BorrowController.java`
  - `POST /borrow` → `borrowService.borrow(request.isbn, currentUserId)`
  - `POST /return` → `borrowService.returnBook(request.isbn, currentUserId)`
  - `POST /renew` → `borrowService.renew(request.isbn, currentUserId)`
  - `GET /borrows` → 分页查询 bookwithuser (替代 BookWithUserController 的查询)
  - 从 JWT 拦截器注入的 request attribute 获取当前用户

- [ ] **2.2** 重构 `LendRecordController.java`
  - 合并 `LendRecordController1` 的还书更新逻辑（或直接废弃，由 BorrowService 处理）
  - 保留 `GET /LendRecord` 查询功能
  - 移除与借书/还书直接相关的写操作

- [ ] **2.3** 重构 `BookWithUserController.java`
  - 保留 `GET /bookwithuser` 查询（或迁移到 BorrowController）
  - 移除 `insertNew`, `deleteRecord`, `deleteRecords`（由 BorrowService 接管）
  - 移除 `POST /bookwithuser` 更新操作（由 BorrowController.renew 接管）

- [ ] **2.4** 重构 `BookController.java`
  - 移除借书/还书中直接修改 `book.status` 的逻辑（改为由 BorrowService 内部调用）
  - 保留图书 CRUD 功能

- [ ] **2.5** 废弃 `LendRecordController1.java`
  - 删除整个文件（功能已合并到 BorrowService）

### 阶段 3: 后端测试

- [ ] **3.1** 编写 `BorrowServiceImplTest`
  - 正常借书场景
  - 借书失败场景（超限、逾期、书已借出、重复借阅）
  - 正常还书场景
  - 还书失败场景（无此借阅记录）
  - 正常续借场景
  - 续借失败场景（无续借次数、已逾期）
  - 事务回滚测试

- [ ] **3.2** 编写 `BorrowControllerTest`
  - API 端点集成测试
  - JWT 认证测试

- [ ] **3.3** 运行全部测试: `mvn test`

### 阶段 4: 前端适配

- [ ] **4.1** 重构 `Book.vue`
  - `handlelend()`: 将 3 个请求合并为 1 个 `POST /borrow {isbn, userId}`
  - `handlereturn()`: 将 3 个请求合并为 1 个 `POST /return {isbn, userId}`
  - 移除前端业务规则判断（逾期检查、数量限制），改为信任后端返回的错误信息
  - 保留逾期通知 UI（从后端返回的数据判断）

- [ ] **4.2** 重构 `BookWithUser.vue`
  - `handlereProlong()`: 将 `POST /bookwithuser` 改为 `POST /renew {isbn, userId}`
  - 移除 `prolong` 和 `deadtime` 的前端计算逻辑
  - 管理员修改功能保持（或改为调用专门的管理接口）

- [ ] **4.3** 添加请求拦截器发送 JWT token
  - `vue/src/utils/request.js`: 在请求拦截器中从 sessionStorage 读取 token 并设置到请求头

### 阶段 5: 集成验证

- [ ] **5.1** 启动后端: `cd SpringBoot && mvn spring-boot:run`
- [ ] **5.2** 启动前端: `cd vue && npm run serve`
- [ ] **5.3** 端到端测试:
  - 读者登录 → 借书 → 验证数据库 3 张表状态
  - 读者登录 → 还书 → 验证数据库 3 张表状态
  - 读者登录 → 续借 → 验证 deadtime 延长 + prolong 减少
  - 管理员登录 → 借阅管理 → 查看/修改/删除
  - 边界测试: 借第 6 本书、续借已过期的书、还已还的书

- [ ] **5.4** 前端构建: `cd vue && npm run build`
- [ ] **5.5** 验证生产构建: 通过 Spring Boot 静态资源访问前端页面

### 阶段 6: 清理和文档

- [ ] **6.1** 删除已废弃的代码
  - `LendRecordController1.java`
  - `BookWithUserController.java` 中的废弃方法
  - `LendRecordController.java` 中的废弃方法
  - 前端中的注释掉的旧代码

- [ ] **6.2** 更新 `CLAUDE.md` 中的架构描述
- [ ] **6.3** 更新 `project_intro.md` 和 `run_guide.md`
- [ ] **6.4** 提交 PR 到 main 分支

---

## 附录 A: 前端请求汇总

### 借书 (Book.vue: handlelend)

| 步骤 | 当前请求 | 目标请求 |
|------|---------|---------|
| 1 | `PUT /api/book` (更新 status, borrownum) | |
| 2 | `POST /api/LendRecord` (插入历史记录) | → `POST /api/borrow {isbn}` |
| 3 | `POST /api/bookwithuser/insertNew` (插入活跃记录) | |

### 还书 (Book.vue: handlereturn)

| 步骤 | 当前请求 | 目标请求 |
|------|---------|---------|
| 1 | `PUT /api/book` (更新 status) | |
| 2 | `PUT /api/LendRecord1` (更新 returnTime) | → `POST /api/return {isbn}` |
| 3 | `POST /api/bookwithuser/deleteRecord` (删除活跃记录) | |

### 续借 (BookWithUser.vue: handlereProlong)

| 步骤 | 当前请求 | 目标请求 |
|------|---------|---------|
| 1 | `POST /api/bookwithuser` (更新 deadtime, prolong) | → `POST /api/renew {isbn}` |

---

## 附录 B: 文件修改影响范围

| 文件 | 操作 | 影响 |
|------|------|------|
| `BookController.java` | 修改 | 移除借书/还书时直接调用（改为内部调用） |
| `BookWithUserController.java` | 修改 | 移除 insertNew/deleteRecord/deleteRecords/update |
| `LendRecordController.java` | 修改 | 移除 save 和 update2 (借书/还书相关) |
| `LendRecordController1.java` | **删除** | 功能迁移到 BorrowService |
| `Book.vue` | 修改 | handlelend/handlereturn 简化为单请求 |
| `BookWithUser.vue` | 修改 | handlereProlong 简化为单请求 |
| `request.js` | 修改 | 添加 JWT token 发送 |
| **新增文件** | | |
| `BorrowController.java` | 新建 | 借书/还书/续借统一入口 |
| `BorrowService.java` | 新建 | Service 接口 |
| `BorrowServiceImpl.java` | 新建 | Service 实现 + @Transactional |
| `BorrowRequest.java` | 新建 | 借书请求 DTO |
| `ReturnRequest.java` | 新建 | 还书请求 DTO |
| `RenewRequest.java` | 新建 | 续借请求 DTO |
| `JwtInterceptor.java` | 新建 | JWT 认证拦截器 |
| `WebMvcConfig.java` | 新建 | 拦截器配置 |
