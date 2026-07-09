# 图书馆管理系统项目介绍

## 1. 项目背景

本次课程使用的项目是一个基于前后端分离架构的图书馆管理系统，项目名称为 `Vue-Springboot-Library`。系统面向图书馆日常管理场景，覆盖管理员和读者两类用户的常见操作，包括图书管理、读者管理、图书查询、借书、还书、续借、借阅记录查看和基础数据统计。

这个项目不是一个从零开始的新项目，而是一个已经具备基本功能的已有系统。大家需要在理解现有代码和业务流程的基础上，分析系统中存在的问题，并结合智能体工具完成需求理解、代码定位、方案设计、重构实现和结果验证。

## 2. 项目技术栈

### 前端

- Vue 3
- Vue Router
- Vuex
- Element Plus
- Axios
- Moment
- ECharts

前端代码位于 `vue` 目录，主要负责页面展示、表单交互、菜单控制、接口调用和用户操作反馈。

### 后端

- Spring Boot 2.6.1
- MyBatis-Plus
- MySQL
- Lombok
- JWT 工具类

后端代码位于 `SpringBoot` 目录，主要提供 REST API，负责用户、图书、借阅记录和借阅状态等数据操作。

### 数据库

数据库使用 MySQL，初始化脚本位于：

```text
sql/springboot-vue.sql
```

主要数据表包括：

- `user`：用户表。
- `book`：图书表。
- `lend_record`：借阅历史记录表。
- `bookwithuser`：当前借阅状态表。

## 3. 系统角色

系统中有两类用户：

### 管理员

管理员负责维护系统数据和查看整体借阅情况，主要功能包括：

- 登录和注册。
- 查看展示板统计。
- 管理读者信息。
- 管理图书信息。
- 查看和维护借阅记录。
- 查看和维护当前借阅状态。
- 修改个人信息。
- 修改密码。

### 普通读者

读者主要围绕借阅图书使用系统，主要功能包括：

- 登录和注册。
- 查询图书。
- 借阅图书。
- 归还图书。
- 查看自己的借阅记录。
- 查看当前借阅状态。
- 对已借图书进行续借。
- 修改个人信息。
- 修改密码。

## 4. 主要功能模块

### 4.1 登录注册模块

用户可以注册账号并登录系统。登录成功后，系统会根据用户角色展示不同菜单。

当前系统会生成 token，但前端并没有真正把 token 加到后续请求头中，后端也没有完整的统一鉴权流程。这是后续可以重构的重点之一。

### 4.2 展示板模块

展示板用于展示系统基础统计数据，例如：

- 访问次数。
- 用户数量。
- 图书数量。
- 借阅记录数量。

对应后端接口为：

```text
GET /dashboard
```

### 4.3 读者管理模块

该模块主要供管理员使用。管理员可以查看读者列表，按条件搜索读者，新增、修改、删除读者。

对应前端页面：

```text
vue/src/views/User.vue
```

对应后端接口：

```text
SpringBoot/src/main/java/com/example/demo/controller/UserController.java
```

后端对应模块：

- `UserController`：提供注册、登录、读者新增、读者修改、读者删除、读者查询、密码修改等接口。
- `User`：用户实体类，对应数据库中的 `user` 表。
- `UserMapper`：用户表的数据访问接口，负责执行用户相关的数据库 CRUD。
- `TokenUtils`：登录成功后生成 token 的工具类。

### 4.4 图书管理与图书查询模块

管理员可以维护图书信息，包括上架、修改、删除和批量删除。

读者可以查询图书，并对可借图书执行借阅操作。

对应前端页面：

```text
vue/src/views/Book.vue
```

对应后端接口：

```text
SpringBoot/src/main/java/com/example/demo/controller/BookController.java
```

后端对应模块：

- `BookController`：提供图书新增、修改、删除、批量删除和分页查询接口。
- `Book`：图书实体类，对应数据库中的 `book` 表。
- `BookMapper`：图书表的数据访问接口，负责对 `book` 表进行增删改查。

需要注意：读者点击“借阅”和“还书”时，前端会调用 `BookController` 更新图书状态，但完整的借还书流程还会同时调用借阅记录和当前借阅状态相关接口。

### 4.5 借阅记录模块

借阅记录模块保存用户借过哪些书、什么时候借、什么时候还、当前是否已归还。

对应数据表：

```text
lend_record
```

对应前端页面：

```text
vue/src/views/LendRecord.vue
```

对应后端接口：

```text
SpringBoot/src/main/java/com/example/demo/controller/LendRecordController.java
```

后端对应模块：

- `LendRecordController`：提供借阅记录新增、查询、删除、批量删除和状态更新接口。
- `LendRecordController1`：项目中另一个与归还更新相关的 Controller，主要用于前端还书时更新借阅记录。
- `LendRecord`：借阅记录实体类，对应数据库中的 `lend_record` 表。
- `LendRecordMapper`：借阅记录表的数据访问接口。

该模块保存的是历史流水，不只表示当前正在借的书。即使图书已经归还，借阅记录也应该保留下来，用于后续查询和统计。

### 4.6 当前借阅状态模块

当前借阅状态模块记录现在还有哪些书没有归还。读者可以在这里查看自己的在借图书，也可以进行续借。

对应数据表：

```text
bookwithuser
```

对应前端页面：

```text
vue/src/views/BookWithUser.vue
```

对应后端接口：

```text
SpringBoot/src/main/java/com/example/demo/controller/BookWithUserController.java
```

后端对应模块：

- `BookWithUserController`：提供当前借阅状态新增、更新、删除、批量删除和分页查询接口。
- `BookWithUser`：当前借阅状态实体类，对应数据库中的 `bookwithuser` 表。
- `BookWithUserMapper`：当前借阅状态表的数据访问接口。

该模块保存的是“现在还没还的书”。还书完成后，对应记录会从 `bookwithuser` 表中删除。

### 4.7 个人信息与密码模块

用户可以修改自己的个人资料和密码。相关页面包括：

```text
vue/src/views/Person.vue
vue/src/views/Password.vue
```

后端对应模块：

- `UserController`：负责修改个人信息和修改密码。
- `User`：承载用户资料字段。
- `UserMapper`：执行用户信息更新。

### 4.8 展示板后端模块

展示板前端页面会调用后端统计接口获取系统概览数据。

后端对应模块：

- `DashboardController`：统计访问次数、用户数量、图书数量、借阅记录数量。
- `LoginUser`：项目中用于记录访问次数的简单类。
- `UserMapper`：查询用户数量。
- `BookMapper`：查询图书数量。
- `LendRecordMapper`：查询借阅记录数量。

## 5. 系统整体架构

系统采用前后端分离架构：

```text
浏览器
  |
  v
Vue 前端页面
  |
  | Axios 请求
  v
Spring Boot 后端接口
  |
  | MyBatis-Plus
  v
MySQL 数据库
```

前端主要负责：

- 页面展示。
- 表单输入。
- 菜单和按钮显示。
- 调用后端接口。
- 展示操作结果。

后端主要负责：

- 提供 REST API。
- 查询和更新数据库。
- 返回统一格式的数据。

数据库主要负责：

- 保存用户信息。
- 保存图书信息。
- 保存借阅记录。
- 保存当前借阅状态。

### 5.1 后端代码模块结构

后端代码位于：

```text
SpringBoot/src/main/java/com/example/demo
```

主要后端包和类如下：

```text
com.example.demo
├─ DemoApplication.java       Spring Boot 启动类
├─ LoginUser.java             记录访问次数的辅助类
├─ commom                     通用类和配置
├─ controller                 后端接口层
├─ entity                     实体类
├─ mapper                     数据访问层
└─ utils                      工具类
```

#### 5.1.1 启动模块

`DemoApplication.java` 是后端项目入口。运行这个类后，Spring Boot 会启动 Web 服务，默认监听 `9090` 端口。

#### 5.1.2 Controller 接口层

Controller 是前端请求进入后端的入口。项目中的主要 Controller 包括：

| 后端类 | 主要职责 |
| --- | --- |
| `UserController` | 登录、注册、读者管理、个人信息修改、密码修改 |
| `BookController` | 图书新增、修改、删除、批量删除、分页查询 |
| `LendRecordController` | 借阅记录新增、查询、删除、状态更新 |
| `LendRecordController1` | 还书时更新借阅记录相关逻辑 |
| `BookWithUserController` | 当前借阅状态新增、查询、修改、删除、续借更新 |
| `DashboardController` | 展示板统计数据 |

当前系统中 Controller 直接调用 Mapper 操作数据库，中间没有独立 Service 层。因此 Controller 既承担接口接收职责，也承担了一部分业务处理职责。

#### 5.1.3 Entity 实体层

实体类用于描述 Java 对象和数据库表字段之间的对应关系。主要实体类包括：

| 实体类 | 对应数据表 | 含义 |
| --- | --- | --- |
| `User` | `user` | 用户信息，包括管理员和读者 |
| `Book` | `book` | 图书基础信息和借阅状态 |
| `LendRecord` | `lend_record` | 借阅历史记录 |
| `BookWithUser` | `bookwithuser` | 当前未归还图书状态 |

理解实体类时，可以对照 `sql/springboot-vue.sql` 中的表结构一起看。

#### 5.1.4 Mapper 数据访问层

Mapper 是 MyBatis-Plus 的数据访问接口。主要 Mapper 包括：

| Mapper | 操作的数据表 |
| --- | --- |
| `UserMapper` | `user` |
| `BookMapper` | `book` |
| `LendRecordMapper` | `lend_record` |
| `BookWithUserMapper` | `bookwithuser` |

这些 Mapper 继承 MyBatis-Plus 的基础能力后，可以直接使用 `insert`、`updateById`、`deleteById`、`selectPage` 等方法。

#### 5.1.5 通用模块

`commom` 包中主要包括：

- `Result`：统一接口返回结构。
- `MybatisPlusConfig`：MyBatis-Plus 分页等配置。

前端接口通常会收到类似下面结构的数据：

```text
{
  code: "0",
  msg: "...",
  data: ...
}
```

统一返回结构的好处是前端可以用一致的方式判断接口是否成功。

#### 5.1.6 工具模块

`utils` 包中主要包括：

- `TokenUtils`：根据用户信息生成 token。

当前系统虽然生成了 token，但 token 没有形成完整的前后端鉴权闭环。后续如果做权限重构，可以从这个工具类开始扩展。

### 5.2 后端调用关系

以查询图书列表为例，后端调用链大致如下：

```text
前端 Book.vue
  |
  | GET /book
  v
BookController.findPage()
  |
  | 构造 MyBatis-Plus 查询条件和分页对象
  v
BookMapper.selectPage()
  |
  v
MySQL book 表
  |
  v
Result.success(BookPage)
```

以登录为例，后端调用链大致如下：

```text
前端 Login.vue
  |
  | POST /user/login
  v
UserController.login()
  |
  | 根据用户名和密码查询
  v
UserMapper.selectOne()
  |
  v
MySQL user 表
  |
  | 查询成功
  v
TokenUtils.genToken()
  |
  v
Result.success(user)
```

以借书为例，当前系统不是一个单独的后端借书接口，而是前端连续调用多个后端接口：

```text
前端 Book.vue
  |
  | PUT /book
  v
BookController 更新图书状态
  |
  | POST /LendRecord
  v
LendRecordController 新增借阅历史
  |
  | POST /bookwithuser/insertNew
  v
BookWithUserController 新增当前借阅状态
```

这个调用关系是后续重构的重要观察点：理想情况下，借书应该由一个后端业务接口统一完成，并使用事务保证数据一致。

## 6. 重点业务流程

### 6.1 借书流程

当前系统中的借书流程主要由前端页面组织：

```text
读者点击借阅
  |
  v
前端判断是否已借满 5 本
  |
  v
前端判断是否存在逾期图书
  |
  v
更新图书状态为已借出
  |
  v
新增借阅记录
  |
  v
新增当前借阅状态
```

涉及的数据变化：

- `book.status` 从 `1` 改为 `0`。
- `book.borrownum` 加 1。
- `lend_record` 新增一条记录。
- `bookwithuser` 新增一条当前借阅记录。

### 6.2 还书流程

当前系统中的还书流程大致为：

```text
读者点击还书
  |
  v
更新图书状态为可借
  |
  v
更新借阅记录的归还时间和状态
  |
  v
删除当前借阅状态
```

涉及的数据变化：

- `book.status` 从 `0` 改为 `1`。
- `lend_record.return_time` 写入归还时间。
- `lend_record.status` 改为已归还。
- `bookwithuser` 删除对应记录。

### 6.3 续借流程

当前系统中的续借流程为：

```text
读者进入当前借阅状态页面
  |
  v
点击续借
  |
  v
判断是否还有续借次数
  |
  v
归还截止日期增加 30 天
  |
  v
续借次数减 1
```

涉及的数据变化：

- `bookwithuser.deadtime` 延后 30 天。
- `bookwithuser.prolong` 减 1。

## 7. 代码阅读顺序

建议按照下面顺序阅读项目代码：

1. 阅读 `README.md`，先了解系统功能和页面截图。
2. 阅读 `sql/springboot-vue.sql`，理解数据库表。
3. 阅读 `vue/src/router/index.js`，了解系统有哪些页面。
4. 阅读 `vue/src/components/Aside.vue`，了解管理员和读者菜单差异。
5. 阅读 `vue/src/views/Book.vue`，重点理解借书和还书流程。
6. 阅读 `vue/src/views/BookWithUser.vue`，理解续借和当前借阅状态。
7. 阅读 `SpringBoot/src/main/java/com/example/demo/controller`，了解后端接口。
8. 阅读 `entity` 和 `mapper`，理解 Java 实体类和数据库表的对应关系。

## 8. 当前系统中值得关注的问题

这个项目已经有完整功能，但也存在不少适合重构和改进的地方。

### 8.1 业务逻辑分散

很多业务逻辑写在前端页面中，例如借书时判断是否超过 5 本、是否有逾期书籍、如何更新借阅状态等。后端更多只是提供基础 CRUD 接口。

更合理的做法是把核心业务规则放到后端 Service 层。

### 8.2 缺少 Service 层

当前后端 Controller 直接调用 Mapper 操作数据库。这样代码简单，但业务复杂后不容易维护。

可以考虑增加：

- `UserService`
- `BookService`
- `BorrowService`
- `LendRecordService`

### 8.3 借还书流程没有事务

借书和还书都涉及多张表。如果其中一个步骤成功、另一个步骤失败，就可能造成数据不一致。

例如借书时：

- 图书状态已经改成已借出。
- 但借阅记录没有插入成功。

这时系统数据就会出现问题。

### 8.4 权限控制不完整

当前系统主要靠前端隐藏菜单来区分管理员和读者。但真正安全的权限控制应该在后端完成。

例如：

- 普通读者不能调用管理员删除图书接口。
- 普通读者只能查看自己的借阅记录。
- 管理员接口需要校验管理员身份。

### 8.5 图书库存模型较简单

当前 `book.status` 只能表示一本书是否被借出，不能很好支持“一种图书有多本副本”的真实场景。

后续可以考虑把图书拆成：

- 书目信息。
- 馆藏副本信息。

### 8.6 数据表主键设计有改进空间

例如 `bookwithuser` 表当前使用 `book_name` 作为主键，这在同名书、多副本书、多读者并发借阅场景下都不够合理。

更稳妥的方式是使用独立自增 ID，或者使用明确的借阅记录 ID 关联当前借阅状态。

## 9. 本次学习重点

本项目的重点不是简单运行一个系统，而是学习如何面对一个已有项目：

- 快速理解业务功能。
- 找到前后端关键代码。
- 理解数据表之间的关系。
- 分析现有实现的不足。
- 将模糊需求拆成可实现任务。
- 使用智能体辅助定位、修改和验证代码。
- 在不破坏已有功能的前提下完成重构。

## 10. 可选重构方向

后续分组任务可能围绕以下方向展开：

- 将借书、还书、续借流程迁移到后端 Service 层。
- 为借还书流程增加事务控制。
- 完善 token 登录鉴权和角色权限校验。
- 重构图书库存模型，支持多副本馆藏。
- 优化借阅记录表和当前借阅状态表设计。
- 增加逾期规则、罚款规则或借阅上限规则。
- 优化查询、筛选、分页和用户体验。
- 增强展示板统计功能。
- 增加操作日志，记录关键业务操作。

## 11. 项目运行说明

本地运行时需要准备：

- JDK。
- Maven。
- Node.js 和 npm。
- MySQL。

基本运行流程：

1. 导入 `sql/springboot-vue.sql`，创建 `springboot-vue` 数据库。
2. 修改后端 `application.properties` 中的数据库账号和密码。
3. 启动 Spring Boot 后端服务，默认端口为 `9090`。
4. 进入 `vue` 目录安装依赖并启动前端服务。
5. 浏览器访问前端页面。

前端开发服务端口：

```text
http://127.0.0.1:9876/
```

后端服务端口：

```text
http://127.0.0.1:9090/
```

## 12. 提交建议

完成任务时，建议提交以下内容：

- 修改后的源码。
- 简要说明修改了哪些功能。
- 说明涉及哪些前端页面、后端接口和数据库表。
- 给出至少 2 到 3 个验证用例。
- 如果修改了数据库结构，需要提供 SQL 变更脚本。
- 如果有未完成或有风险的地方，需要在说明中明确写出。

验证用例可以包括：

- 管理员新增一本图书后，读者能查询到。
- 读者借书后，图书状态变为已借出。
- 读者还书后，图书状态恢复为可借。
- 读者续借后，应归还时间延后。
- 普通读者不能访问管理员专属操作。
