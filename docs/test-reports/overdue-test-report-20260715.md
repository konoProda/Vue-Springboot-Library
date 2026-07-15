# 逾期管理和逾期限制规则 — 验收测试报告

**测试日期**: 2026-07-15 19:32
**测试人员**: Claude Code (自动化)
**需求**: 增加逾期管理和逾期限制规则

---

## 一、代码修改清单

| # | 文件 | 修改类型 | 说明 |
|---|------|----------|------|
| 1 | `entity/BookWithUser.java` | 新增字段 | `status`(String) + `overdueDays`(Integer) @TableField(exist=false) |
| 2 | `entity/LendRecord.java` | 新增字段 | `overdueStatus`(String) + `overdueDays`(Integer) @TableField(exist=false) |
| 3 | `controller/BookWithUserController.java` | 修改 | `findPage()`: overdueFilter 参数 + 状态计算; `update()`: 支持 deadtime 编辑 |
| 4 | `controller/LendRecordController.java` | 修改 | `findPage()`: overdueFilter 参数 + 逾期状态计算 |
| 5 | `service/BorrowService.java` | 修改 | `validateBorrowPreconditions()`: 逾期检查; `renewBook()`: 逾期续借检查 |
| 6 | `vue/src/views/BookWithUser.vue` | 修改 | 状态列(el-tag) + 逾期筛选 + 编辑对话框 deadtime |
| 7 | `vue/src/views/LendRecord.vue` | 修改 | 逾期筛选下拉框 |

## 二、测试结果汇总

| TC | 验收条件 | 结果 | 证据 |
|----|---------|------|------|
| TC1 | 借书后应还日期=借书日+30天 | ✅ PASS | DB 验证: DATEDIFF(deadtime, lendtime) = 30 |
| TC2 | 管理员改 deadtime 为昨天 → 已逾期 ≥1天 | ✅ PASS | status=已逾期, overdueDays=1, deadtime=2026-07-14 |
| TC3 | 逾期读者借新书失败 | ✅ PASS | `{"code":"1","msg":"存在逾期未还图书，请先归还后再借阅"}` |
| TC4 | 逾期读者续借逾期书失败 | ✅ PASS | `{"code":"1","msg":"该图书已逾期，无法续借，请先归还"}`, deadtime 不变 |
| TC5 | 管理员按"逾期未还"筛选 | ✅ PASS | 返回 3 条逾期记录，含 TEST-INV-001 (已逾期 1天) |
| TC6 | 归还逾期书后可再借阅 | ✅ PASS | 归还成功 → overdue_count=0 → 借阅 999-9-99-999999-9 成功 |

**通过率: 6/6 (100%)**

## 三、逾期状态计算规则

| 条件 | 状态 | 逾期天数 |
|------|------|----------|
| `now > deadtime` | 已逾期 | `(now - deadtime) / 86400000` |
| `deadtime - 3天 <= now <= deadtime` | 即将到期 | 0 |
| `now < deadtime - 3天` | 正常 | 0 |

## 四、逾期借阅限制规则

- **借新书检查**: 查询 `bookwithuser WHERE user_id=? AND deadtime < NOW()` — count > 0 则拒绝
- **续借检查**: 该书 `deadtime < NOW()` 时拒绝
- **限制解除**: 归还逾期书后自动解除（不再有 `deadtime < NOW()` 的记录）
