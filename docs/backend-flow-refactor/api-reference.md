# 借书 / 还书 / 续借 — API 参考与运行说明

**更新日期**: 2026-07-15

---

## 一、后端流程验证 — 可直接执行的命令

### 所需运行环境

| 依赖 | 版本要求 | 说明 |
|------|---------|------|
| `bash` | ≥ 3.0 | Shell 解释器 |
| `curl` | ≥ 7.0 | HTTP 客户端 |
| `python3` | ≥ 3.6 | JSON 解析（仅标准库 `sys, json`） |

> 以上均为 Linux/macOS 系统自带工具，**验收时不需要临时联网安装任何依赖**。

### 执行命令

```bash
# 默认后端地址 (http://localhost:9090)
bash docs/backend-flow-refactor/verify-borrow-flow.sh

# 自定义后端地址
bash docs/backend-flow-refactor/verify-borrow-flow.sh http://192.168.1.100:9090
```

### 脚本行为说明

| 项目 | 说明 |
|------|------|
| 测试数据 | 自动创建带时间戳唯一 ISBN 的测试图书，**不与原有数据冲突** |
| 数据清理 | 脚本结束前自动删除测试图书，**不残留测试数据** |
| 输出格式 | 每个用例输出 `PASS` 或 `FAIL` + 实际结果 + 失败原因 |
| 退出码 | 全部通过返回 `0`；任一失败返回非零退出码 |
| 数据库 | **不直接连接数据库**，所有操作和验证均通过 HTTP 接口完成 |

### 验证用例清单

| 用例 | 验证内容 |
|------|---------|
| TC1 | 借阅可借图书成功，库存减少，生成当前借阅和历史记录 |
| TC2 | 库存为 0 时借阅失败，失败前后库存和记录数量不变 |
| TC3 | 首次续借成功，应还日期延长 30 天，剩余续借次数变为 0 |
| TC4 | 再次续借失败，应还日期不变化 |
| TC5 | 正常还书成功，库存恢复，当前借阅消失，历史记录显示已归还 |
| TC6 | 重复还书失败，库存不得再次增加 |

---

## 二、借书 / 还书 / 续借 接口说明

### 通用约定

- **Base URL**: `http://localhost:9090`
- **认证方式**: 请求头 `Authorization: Bearer <token>`（通过 `POST /user/login` 获取）
- **userId 来源**: 后端从 JWT 中提取，**前端不传、请求体不包含**
- **响应格式**: `{"code":"0","msg":"成功","data":null}` — code 为 `"0"` 表示成功，`"1"` 表示业务失败

### 1. 借书 — `POST /borrow`

**请求体**:
```json
{"isbn": "978-7-100-12345-6"}
```

**curl 示例**:
```bash
curl -X POST http://localhost:9090/borrow \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer <reader_token>' \
  -d '{"isbn":"978-7-100-12345-6"}'
```

**后端自动完成**: 校验库存/逾期限制/借阅上限/重复借阅 → 库存 -1 → 生成 bookwithuser（自动计算 deadtime = 当前+30天）→ 生成 lend_record

**可能失败原因**: `库存不足` | `存在逾期未还图书` | `借阅数量已达上限` | `不可重复借阅同一本书`

---

### 2. 还书 — `POST /return`

**请求体**:
```json
{"isbn": "978-7-100-12345-6"}
```

**curl 示例**:
```bash
curl -X POST http://localhost:9090/return \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer <reader_token>' \
  -d '{"isbn":"978-7-100-12345-6"}'
```

**后端自动完成**: 校验用户是否持有该书 → 库存 +1 → 更新 lend_record 归还时间 → 删除 bookwithuser

**可能失败原因**: `未找到该书的借阅记录，无法归还`

---

### 3. 续借 — `POST /renew`

**请求体**:
```json
{"isbn": "978-7-100-12345-6"}
```

**curl 示例**:
```bash
curl -X POST http://localhost:9090/renew \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer <reader_token>' \
  -d '{"isbn":"978-7-100-12345-6"}'
```

**续借规则**: 最多 1 次，每次从原应还日期延长 30 天

**可能失败原因**: `该图书已逾期，无法续借` | `续借次数已用完`

---

## 三、前端调用方式

```javascript
// 借书 — 仅传 isbn
request.post("/borrow", { isbn: row.isbn })

// 还书 — 仅传 isbn
request.post("/return", { isbn: row.isbn })

// 续借 — 仅传 isbn
request.post("/renew", { isbn: row.isbn })
```

> `request` 为 Axios 实例（`vue/src/utils/request.js`），baseURL=`/api`，自动注入 `Authorization` 头。Vue devServer 代理 `/api/*` → `http://localhost:9090/*`（剥离 `/api` 前缀）。
