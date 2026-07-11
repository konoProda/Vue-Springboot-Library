# 数据一致性问题分析报告

> 日期: 2026-07-12
> 分支: refactor/test-drill
> 范围: lend_record 表、bookwithuser 表及关联的 Controller / Service

---

## 1. 数据表关系分析

### 1.1 两张表的结构

**lend_record（借阅历史）**:

| 字段 | 类型 | 说明 |
|------|------|------|
| reader_id | BIGINT | 读者 ID |
| isbn | VARCHAR(255) | 图书编号 |
| bookname | VARCHAR(255) | 书名 |
| lend_time | DATETIME | 借书时间 |
| return_time | DATETIME | 还书时间 |
| status | VARCHAR(1) | 0=未归还, 1=已归还 |
| borrownum | INT | 当前图书累计借阅次数 |

> **关键缺陷**: `lend_record` 表 **没有主键**。无自增 ID，无复合主键，无唯一约束。

**bookwithuser（活跃借阅）**:

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT AUTO_INCREMENT | 自增主键 |
| user_id | BIGINT | 读者 ID |
| isbn | VARCHAR(255) | 图书编号 |
| book_name | VARCHAR(255) | 书名 |
| nick_name | VARCHAR(255) | 读者昵称 |
| lendtime | DATETIME | 借阅时间 |
| deadtime | DATETIME | 应归还时间 |
| prolong | INT | 剩余续借次数 |

### 1.2 关联方式

两张表通过 **(reader_id / user_id, isbn)** 二元组形成松散关联：

```
lend_record.reader_id  ←→  bookwithuser.user_id
lend_record.isbn       ←→  bookwithuser.isbn
```

**不存在**外键约束、**不存在**共享的唯一借阅事件 ID（如 `lend_id`）。两表各自独立维护，仅在 BorrowService 的事务方法中同时写入。

### 1.3 多副本场景下的问题

| 场景 | 问题描述 |
|------|---------|
| 同一用户借同一 ISBN 两次 | `(reader_id, isbn)` 无法区分两次借阅。`bookwithuser` 防重复逻辑 (`duplicateCount > 0`) 禁止了此行为，但 `lend_record` 表无此限制 |
| 不同用户借同一 ISBN | `lend_record` 表中两条记录只有 `reader_id` 不同，无独立主键区分 |
| 管理员手动修改借阅记录 | 无法精确定位"哪一条"记录，只能通过 `isbn`（甚至只通过 isbn）批量匹配 |

---

## 2. 业务逻辑分析

### 2.1 正常借还流程（BorrowService）

```
借书: borrowService.borrowBook()
  ├─ UPDATE book (availableCopies-1, borrownum+1)
  ├─ INSERT lend_record (reader_id, isbn, bookname, lend_time, status="0", borrownum)
  └─ INSERT bookwithuser (user_id, isbn, book_name, lendtime, deadtime, prolong)

还书: borrowService.returnBook()
  ├─ UPDATE book (availableCopies+1)
  ├─ UPDATE lend_record SET return_time=now, status="1"
  │     WHERE isbn=? AND reader_id=? AND status="0"
  └─ DELETE bookwithuser WHERE isbn=? AND user_id=?
```

正常流程中，两张表的操作在同一个 `@Transactional` 内完成，数据一致。

### 2.2 管理员手动编辑借阅记录（问题路径）

管理员点击"编辑"→ 调用 `LendRecordController.update()`:

```java
// LendRecordController.java 第 82-103 行
@PutMapping("/{isbn}")
public Result<?> update(@PathVariable String isbn, @RequestBody LendRecord lendRecord, ...) {
    UpdateWrapper<LendRecord> updateWrapper = new UpdateWrapper<>();
    updateWrapper.eq("isbn", isbn);  // ← 仅用 isbn 做条件！

    LendRecord lendrecord = new LendRecord();
    lendrecord.setLendTime(lendRecord.getLendTime());
    lendrecord.setReturnTime(lendRecord.getReturnTime());
    lendrecord.setStatus(lendRecord.getStatus());
    LendRecordMapper.update(lendrecord, updateWrapper);
    // 注意：此处没有任何对 bookwithuser 表的操作！
}
```

### 2.3 问题根因分析

#### 根因 1：UpdateWrapper 条件过于宽泛（仅匹配 isbn）

```sql
-- 实际执行的 SQL：
UPDATE lend_record
SET lend_time=?, return_time=?, status=?
WHERE isbn = ?
```

`WHERE isbn = ?` 会匹配到 **所有** 该 ISBN 的借阅记录，不论读者是谁、借阅时间是什么。这就是 "修改一本记录影响所有同名书" 的直接原因。

**正确做法**：应至少使用 `(isbn, reader_id, borrownum)` 三元组或自增 ID 来精准定位单条记录。

#### 根因 2：lend_record 表无主键/无唯一标识

`lend_record` 表没有任何主键字段：
- 无自增 ID
- 无复合主键如 `(reader_id, isbn, lend_time)`
- 无 `UNIQUE` 约束

这导致：
- MyBatis-Plus 启动时警告 `Can not find table primary key in Class: LendRecord`
- `updateById()` / `deleteById()` 等按主键操作的方法不可用
- 所有更新和删除必须手动构造 `UpdateWrapper` / `deleteByMap`，条件容易遗漏

#### 根因 3：管理员编辑路径未同步 bookwithuser

`LendRecordController.update()` 只更新了 `lend_record` 表，完全没有触及 `bookwithuser` 表。当管理员将 status 从 "0" 改为 "1"（模拟还书）时：

- `lend_record.status` 变为 "1" ✅
- `bookwithuser` 中对应记录 **未被删除** ❌
- `book.availableCopies` **未 +1** ❌

这导致：
- 当前借阅状态列表仍显示该借阅（数据不一致）
- 图书可借数量未恢复（库存不准确）

#### 根因 4：前端编辑对话框的 form 数据丢失关键字段

前端 `LendRecord.vue` 的编辑对话框仅包含三个字段：
```html
<el-form-item label="借阅时间">...</el-form-item>
<el-form-item label="归还时间">...</el-form-item>
<el-form-item label="是否归还">...</el-form-item>
```

`readerId` 和 `borrownum` 虽然在 `handleEdit(row)` 时存在于 `this.form` 中，但：
- 用户在对话框中看不到这两个字段
- PUT 请求体中虽然包含这两个字段（来自 JSON 深拷贝），但后端 `update()` 方法**忽略**了它们，仅用 `isbn` 做 WHERE 条件

### 2.4 完整的问题链路

```
1. 管理员打开借阅管理 → 看到 lend_record 列表
2. 点击某条记录的"编辑" → handleEdit(row) → form = 深拷贝 row 数据
3. 修改"是否归还"为"已归还" → 点击"确定"
4. 前端: PUT /LendRecord/{isbn} → body 含完整 form 数据
5. 后端: LendRecordController.update()
   ├─ UpdateWrapper.eq("isbn", isbn)          ← 只按 isbn 匹配
   ├─ LendRecordMapper.update(lendrecord, wrapper) ← 更新所有同名书记录
   └─ ❌ 未更新 bookwithuser
6. 结果:
   ├─ lend_record: 所有同名书的状态都被改了 ❌
   ├─ bookwithuser: 未被删除，活跃借阅仍然存在 ❌
   └─ book.availableCopies: 未恢复，库存不准 ❌
```

---

## 3. 数据一致性风险全面评估

### 3.1 可能导致不一致的场景

| # | 场景 | lend_record | bookwithuser | book | 一致性 |
|---|------|:----------:|:------------:|:----:|:------:|
| 1 | 正常借书 (BorrowService) | INSERT ✅ | INSERT ✅ | availableCopies-1 ✅ | ✅ |
| 2 | 正常还书 (BorrowService) | UPDATE ✅ | DELETE ✅ | availableCopies+1 ✅ | ✅ |
| 3 | 管理员手动编辑借阅记录 | UPDATE ⚠️ | 未操作 ❌ | 未操作 ❌ | ❌ |
| 4 | 管理员手动删除借阅记录 | DELETE ⚠️ | 未操作 ❌ | 未操作 ❌ | ❌ |
| 5 | 管理员修改 bookwithuser | UPDATE ⚠️ | 未操作 ❌ | 未操作 ❌ | ❌ |
| 6 | 管理员直接删 bookwithuser | DELETE ⚠️ | 未操作 ❌ | 未操作 ❌ | ❌ |

### 3.2 不一致导致的业务影响

| 影响类型 | 具体表现 |
|---------|---------|
| **库存不准** | 管理员手动改状态为"已归还"后，`book.availableCopies` 未恢复。其他读者看到可借数为 0，无法借阅 |
| **活跃借阅残留** | 读者已"被还书"，但"借阅状态"列表中仍显示该借阅。读者可能收到逾期通知 |
| **借阅历史错误** | 如果图书馆有 3 本《三体》，管理员修改其中一条记录的状态，可能 3 条记录全部被改 |
| **统计失真** | Dashboard 的借阅趋势统计基于 `lend_record` 表，如果大量记录被误改，统计曲线会异常 |
| **审计追溯困难** | 因为没有唯一借阅 ID，无法准确追溯"哪一次借阅"被修改了 |

---

## 4. 改进建议

### 4.1 为 lend_record 添加主键

```sql
ALTER TABLE lend_record ADD COLUMN id BIGINT AUTO_INCREMENT PRIMARY KEY FIRST;
```

- 配合 `LendRecord` 实体增加 `@TableId(type = IdType.AUTO) private Long id;`
- 所有更新/删除操作改为 `updateById()` / `deleteById()`，精准定位单条记录
- 前端编辑时传递 `id` 而非仅 `isbn`

### 4.2 建立借阅事件唯一标识

在正常借书流程（`borrowService.borrowBook()`）中，`lend_record` 和 `bookwithuser` 同在一个事务中创建。可以让它们共享一个 `lend_event_id`（如 UUID 或 lend_record 的自增 ID），实现精确的一对一关联。

### 4.3 管理员手动操作时同步关联表

如果管理员在 `LendRecordController` 中修改了 `lend_record.status` 为 "1"（已归还），应同时：

1. 删除 `bookwithuser` 中对应的活跃记录
2. 恢复 `book.availableCopies`
3. 整个过程包裹在 `@Transactional` 中

即：管理员的"编辑还书"应复用 `borrowService.returnBook()` 的逻辑，而非绕过 Service 直接操作 Mapper。

### 4.4 编辑对话框应展示只读的关键标识字段

前端编辑对话框中应显示（不可编辑）：
- 读者 ID
- 图书 ISBN
- 借阅事件 ID（如有）
- 借阅时间（原始值）

这样既能帮助管理员确认操作目标，也能确保后端收到完整的定位信息。

### 4.5 增加唯一约束防止重复

```sql
ALTER TABLE bookwithuser ADD UNIQUE INDEX uq_user_isbn (user_id, isbn);
```

这可以防止同一用户重复借阅同一 ISBN，将"防重复"从应用层下沉到数据库层。

---

## 5. 总结

| 维度 | 现状 | 风险等级 |
|------|------|:--------:|
| 主键设计 | lend_record 无主键，bookwithuser 有自增 ID | 🔴 高 |
| 表关联 | 松散 (reader_id, isbn) 二元组, 无外键 | 🔴 高 |
| 更新精度 | 仅按 isbn 匹配，多副本下误伤 | 🔴 高 |
| 跨表同步 | 仅 BorrowService 保证，Controller 绕过 | 🔴 高 |
| 唯一约束 | 无，依赖应用层防重复 | 🟡 中 |
| 前端表单 | 缺少关键标识字段，后端忽略现有字段 | 🟡 中 |
