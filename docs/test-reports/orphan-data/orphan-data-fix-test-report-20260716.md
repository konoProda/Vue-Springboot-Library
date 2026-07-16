# 删除图书孤儿数据风险修复 — test-automation 验收测试报告

**测试日期**: 2026-07-16
**测试技能**: test-automation (Shell API 测试)
**需求**: 防止删除被借图书 / 直接删活跃借阅导致孤儿数据

---

## 一、测试结果 6/6 PASS

| TC | 验证内容 | 结果 | 证据 |
|----|---------|------|------|
| TC1 | 删除被借图书（操作系统 id=12）→ 拒绝 | ✅ | `"该图书《操作系统》正在被借阅中，无法删除，请等待归还后再操作"` |
| TC2 | 删除未借图书 → 成功 | ✅ | code=0，图书已删除 |
| TC3 | 直接删活跃借阅 deleteRecords → 拒绝 | ✅ | `"活跃借阅记录不能直接删除。请通过还书流程...处理"` |
| TC4 | 拒绝后 bookwithuser + available 不变 | ✅ | bookwithuser=1 available=0（数据未被损坏） |
| TC5 | deleteRecord 死代码端点 → 404 | ✅ | HTTP 404 |
| TC6 | 批量删除含被借图书 → 拒绝 | ✅ | `"该图书《操作系统》正在被借阅中..."` |

## 二、代码修改

| 文件 | 修改 |
|------|------|
| `controller/BookController.java` | +`BookWithUserMapper` 注入；`delete()` +3行检查；`deleteBatch()` +5行检查 |
| `controller/BookWithUserController.java` | `deleteRecords()` 改为拒绝删除；删除 `deleteRecord()` 死代码 |

## 三、测试脚本

```
bash docs/test-reports/orphan-data-fix-test-20260716.sh http://localhost:9090
```
