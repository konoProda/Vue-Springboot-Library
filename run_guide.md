# 图书馆管理系统运行指南

## 1. 项目目录

本项目名称为 `Vue-Springboot-Library`，主要目录如下：

```text
Vue-Springboot-Library
├─ SpringBoot   后端项目
├─ vue          前端项目
├─ sql          数据库初始化脚本
├─ images       项目截图
├─ run          原项目提供的运行文件
└─ README.md    项目说明
```

运行系统时，需要同时准备数据库、后端服务和前端服务。

## 2. 环境准备

请先确认本机已经安装以下环境：

- JDK
- Maven
- Node.js
- npm
- MySQL

建议使用命令检查环境：

```powershell
java -version
mvn -version
node -v
npm -v
mysql --version
```

如果某个命令无法识别，说明对应工具没有安装，或者没有配置到系统环境变量。

## 3. 初始化数据库

项目数据库脚本位于：

```text
Vue-Springboot-Library/sql/springboot-vue.sql
```

需要将该 SQL 文件导入 MySQL。导入后会创建数据库：

```text
springboot-vue
```

可以使用 Navicat、DataGrip、MySQL Workbench 等数据库工具导入，也可以使用命令行导入：

```powershell
mysql -uroot -p < sql/springboot-vue.sql
```

执行命令后会提示输入 MySQL 密码。

## 4. 修改后端数据库配置

后端配置文件位于：

```text
Vue-Springboot-Library/SpringBoot/src/main/resources/application.properties
```

需要重点检查数据库连接配置：

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/springboot-vue?useUnicode=true&characterEncoding=utf-8&allowMultiQueries=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=GMT%2b8
spring.datasource.username=root
spring.datasource.password=你的MySQL密码
```

请将 `spring.datasource.password` 改成本机 MySQL 的真实密码。

如果你的 MySQL 用户不是 `root`，也需要同步修改：

```properties
spring.datasource.username=你的MySQL用户名
```

## 5. 启动后端

进入后端目录：

```powershell
cd "Vue-Springboot-Library/SpringBoot"
```

使用 Maven 打包：

```powershell
mvn -DskipTests package
```

打包成功后，运行后端 jar：

```powershell
java -jar target/demo-0.0.1-SNAPSHOT.jar
```

后端默认端口为：

```text
http://127.0.0.1:9090/
```

可以在浏览器访问下面地址测试后端是否正常：

```text
http://127.0.0.1:9090/dashboard
```

如果能返回 JSON 数据，说明后端已经连上数据库。

## 6. 启动前端

重新打开一个终端，进入前端目录：

```powershell
cd "Vue-Springboot-Library/vue"
```

安装前端依赖：

```powershell
npm install --legacy-peer-deps
```

启动前端开发服务：

```powershell
npm run serve
```

前端默认访问地址为：

```text
http://127.0.0.1:9876/
```

打开浏览器访问该地址，即可进入系统登录页面。

## 7. 前后端关系

前端运行在：

```text
http://127.0.0.1:9876/
```

后端运行在：

```text
http://127.0.0.1:9090/
```

前端代码中通过 `/api` 访问后端接口。开发环境下，`vue/vue.config.js` 会把 `/api` 请求代理到后端 `9090` 端口。

也就是说，前端页面请求：

```text
/api/book
```

实际会转发到：

```text
http://127.0.0.1:9090/book
```

## 8. 准备测试账号

原项目 README 中说明：测试账号需要自己插入数据库。

如果导入 SQL 后没有可用账号，可以在 MySQL 中执行以下语句创建测试账号：

```sql
INSERT INTO user(username, password, nick_name, phone, sex, address, role)
VALUES ('admin', '123456', '管理员', '13800000000', '男', '学校', 1);

INSERT INTO user(username, password, nick_name, phone, sex, address, role)
VALUES ('reader', '123456', '读者', '13900000000', '女', '学校', 2);
```

创建后可以使用以下账号登录：

| 角色 | 用户名 | 密码 |
| --- | --- | --- |
| 管理员 | `admin` | `123456` |
| 读者 | `reader` | `123456` |

## 9. 推荐验证流程

系统启动后，建议按下面顺序测试：

1. 使用管理员账号登录。
2. 进入书籍管理页面，查看图书列表。
3. 新增一本图书。
4. 使用读者账号登录。
5. 查询刚新增的图书。
6. 点击借阅。
7. 查看借阅状态。
8. 执行续借。
9. 执行还书。
10. 再次查看图书状态是否恢复为可借。

## 10. 常见问题

### 10.1 Maven 命令无法识别

说明 Maven 没有安装，或者没有配置环境变量。

可以检查：

```powershell
mvn -version
```

如果命令失败，需要重新安装 Maven，或将 Maven 的 `bin` 目录加入系统 `Path`。

### 10.2 后端启动失败，提示数据库连接错误

重点检查：

- MySQL 是否已经启动。
- `springboot-vue` 数据库是否已经创建。
- `application.properties` 中的用户名和密码是否正确。
- MySQL 端口是否为 `3306`。

如果看到类似错误：

```text
Access denied for user 'root'@'localhost'
```

通常说明数据库用户名或密码不正确。

### 10.3 后端启动失败，提示 Public Key Retrieval is not allowed

可以检查 JDBC URL 中是否包含：

```properties
allowPublicKeyRetrieval=true
```

完整示例：

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/springboot-vue?useUnicode=true&characterEncoding=utf-8&allowMultiQueries=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=GMT%2b8
```

### 10.4 前端 npm install 失败

可以尝试：

```powershell
npm install --legacy-peer-deps
```

如果依赖版本冲突，优先使用上面的命令。

### 10.5 前端页面能打开，但接口请求失败

检查后端是否已经启动。

后端地址应为：

```text
http://127.0.0.1:9090/
```

同时检查前端代理配置 `vue/vue.config.js` 中是否代理到正确端口。

### 10.6 登录失败

可能原因：

- 数据库中没有测试账号。
- 用户名或密码输入错误。
- 后端没有连接上数据库。

可以先在数据库中确认 `user` 表是否有账号数据。

## 11. 运行成功标志

当以下条件都满足时，说明项目运行成功：

- MySQL 中存在 `springboot-vue` 数据库。
- 后端服务启动在 `9090` 端口。
- 前端服务启动在 `9876` 端口。
- 浏览器能打开登录页面。
- 使用管理员或读者账号可以成功登录。
- 图书列表、借阅状态、借阅记录等页面能正常加载数据。

