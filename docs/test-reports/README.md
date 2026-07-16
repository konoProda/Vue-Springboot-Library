# 测试用例与报告索引

## 分类说明

| 目录 | 对应需求 | 内容 |
|------|---------|------|
| `inventory/` | 需求1: 图书库存数量 | API+UI 验收测试 |
| `overdue/` | 需求2: 逾期管理 | 逾期规则验收测试 |
| `backend-flow/` | 需求3: 借书/还书/续借后端化 | 后端流程验收报告 |
| `user-crud/` | 需求4: 管理员新增和删除用户 | API+UI 验收测试 |
| `frontend-style/` | 需求5: 前端界面风格 | 字体+i18n 基础验收 |
| `i18n/` | 需求5扩展: 多语言补齐 | 全页面 i18n 验收 |
| `layout/` | 需求5扩展: 布局改造 | 侧边栏/搜索栏/overflow/注册页 |
| `orphan-data/` | 孤儿数据风险修复 | BookWithUser/Book 删除校验 |
| `final/` | 全局综合验收 | 全功能回归测试 |

## 需求3 独立文档

> 按需求3要求，验证脚本和说明文档单独存放于 `docs/backend-flow-refactor/`

| 文件 | 说明 |
|------|------|
| `../backend-flow-refactor/verify-borrow-flow.sh` | 黑盒验证脚本（bash, 零依赖） |
| `../backend-flow-refactor/api-reference.md` | 借书/还书/续借 API 参考与运行说明 |
