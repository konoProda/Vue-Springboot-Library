# 三处修复 — test-automation 验收测试报告

**测试日期**: 2026-07-16

---

## 一、UI 测试 4/4 PASS

| Fix | 验证内容 | 结果 | 证据 |
|-----|---------|------|------|
| Fix1 | Log 时间范围拆为"开始时间""结束时间" | ✅ | start=true end=true |
| Fix2 | 导航栏"借阅管理"→"借阅记录" | ✅ | nav 包含"借阅记录"不含"借阅管理" |
| Fix3a | BW 筛选有"逾期未还+未逾期" | ✅ | 选项: 逾期未还,未逾期 |
| Fix3b | LR 筛选有"逾期未还+已归还+未归还" | ✅ | 选项: 逾期未还,已归还,未归还 |

## 二、API 后端筛选 3/3 PASS

| 接口 | 参数 | 结果 |
|------|------|------|
| `/bookwithuser?overdueFilter=2` | 未逾期 | ✅ 返回正确 |
| `/LendRecord?overdueFilter=2` | 已归还 | ✅ all status=1 |
| `/LendRecord?overdueFilter=3` | 未归还 | ✅ all status=0 |

## 三、修改文件

| 文件 | 修改 |
|------|------|
| `Header.vue` | "借阅管理" → "借阅记录" |
| `Log.vue` | 时间范围拆为两个独立 datetime 选择器 |
| `BookWithUser.vue` | overdueFilter 加"未逾期"选项 |
| `LendRecord.vue` | overdueFilter 加"已归还""未归还"选项 |
| `BookWithUserController.java` | overdueFilter="2" → ge(deadtime, now) |
| `LendRecordController.java` | overdueFilter="2"→eq(status,'1'), "3"→eq(status,'0') |
