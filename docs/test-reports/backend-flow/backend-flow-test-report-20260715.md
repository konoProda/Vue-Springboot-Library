# 借书/还书/续借流程后端化 — 验收测试报告

**测试日期**: 2026-07-15 21:15
**需求**: 借书、还书、续借流程后端化

---

## 一、代码修改清单

| # | 文件 | 修改类型 | 说明 |
|---|------|----------|------|
| 1 | `controller/BorrowController.java` | **新建** | 三个纯业务接口 `/borrow`, `/return`, `/renew`，userId 从 JWT 提取 |
| 2 | `controller/BookWithUserController.java` | **修改** | 删除 `insertNew()`，移除续借检测 hack，精简 import |
| 3 | `controller/LendRecordController1.java` | **删除** | 被 POST /return 完全替代 |
| 4 | `service/BorrowService.java` | **修改** | `returnBook()` 增加"用户是否借了该书"校验 |
| 5 | `vue/src/views/Book.vue` | **简化** | `handlelend()` 和 `handlereturn()` 改为单 POST 请求 |
| 6 | `vue/src/views/BookWithUser.vue` | **简化** | `handlereProlong()` 和 `handleReturn()` 改为单 POST 请求 |

## 二、新接口清单

| 操作 | 方法 | 路径 | 请求体 | userId 来源 |
|------|------|------|--------|-------------|
| 借书 | POST | `/borrow` | `{"isbn":"..."}` | JWT |
| 还书 | POST | `/return` | `{"isbn":"..."}` | JWT |
| 续借 | POST | `/renew` | `{"isbn":"..."}` | JWT |

## 三、黑盒验证脚本结果

```
bash docs/verify-borrow-flow.sh http://localhost:9090

PASS TC1 库存减少=0, 当前借阅=1, 历史记录=1
PASS TC2 借阅失败(code=1), 库存/记录未变
PASS TC3 续借成功, 延长30天, 剩余续借次数=0
PASS TC4 续借失败(code=1), 应还日期不变
PASS TC5 库存恢复=1, 借阅清理, 历史status=1
PASS TC6 还书失败(code=1), 库存不变=1

全部通过: 6/6
```

## 四、浏览器网络面板验证

| 操作 | 网络请求 | 数量 |
|------|---------|------|
| 借书 | `[POST] /api/borrow` | **1 个写请求** |
| 还书 | `[POST] /api/return` | **1 个写请求** |
| 续借 | `[POST] /api/renew` | **1 个写请求** |

请求体为 `{"isbn":"..."}`，不包含任何前端计算的日期、状态、库存数据。

## 五、文档产出

| 文件 | 说明 |
|------|------|
| `docs/api-reference.md` | API 参考文档（接口说明 + curl 示例） |
| `docs/verify-borrow-flow.sh` | 黑盒验证脚本（6 用例，零依赖） |
