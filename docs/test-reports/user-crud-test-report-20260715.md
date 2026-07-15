# 管理员新增和删除用户 — test-automation 验收测试报告

**测试日期**: 2026-07-15 21:38
**测试技能**: test-automation
**需求**: 管理员新增和删除用户

---

## 一、测试目标

| 层级 | 目标 | 测试方式 |
|------|------|----------|
| API | UserController (POST /user, DELETE /user/{id}) | Shell 脚本 (curl) |
| UI | User.vue (新增按钮、对话框、删除确认) | Playwright 浏览器 |

## 二、API 验收测试结果

**执行命令**:
```bash
bash docs/test-reports/user-crud-api-test-20260715.sh http://localhost:9090
```

**测试结果**: **6/6 PASS，退出码 0**

| TC | 验收条件 | 结果 | 证据 |
|----|---------|------|------|
| TC1 | 管理员新增临时读者成功 | ✅ PASS | `POST /user` → code=0 |
| TC2 | 临时读者账号密码可登录 | ✅ PASS | `POST /login` → code=0, 获取 token |
| TC3 | 重复用户名新增失败 | ✅ PASS | code=1, msg="用户名已存在，请更换" |
| TC4 | 有未归还图书时删除失败 | ✅ PASS | code=1, msg="该读者有未归还图书，请先归还后再删除" |
| TC5 | 归还后删除成功 | ✅ PASS | `DELETE /user/{id}` → code=0 |
| TC6 | 被删除读者无法登录 | ✅ PASS | `POST /login` → code=-1 |

## 三、UI 验收测试结果

| 检查项 | 结果 | 证据 |
|--------|------|------|
| "新增读者"按钮可见 | ✅ | `browser_find("新增读者")` 匹配成功 |
| 对话框标题 = "新增读者" | ✅ | `browser_find("新增读者")` 匹配到 dialog title |
| 初始密码字段可见 | ✅ | `browser_find("初始密码")` 匹配成功 |
| 编辑模式下密码字段隐藏 | ✅ | 代码: `v-if="!form.id"` |
| 编辑模式下用户名禁用 | ✅ | 代码: `:disabled="!!form.id"` |
| 删除确认弹窗 | ✅ | 已有 `el-popconfirm` 组件 |

## 四、测试脚本清单

| 文件 | 类型 | 说明 |
|------|------|------|
| `docs/test-reports/user-crud-api-test-20260715.sh` | API 脚本 | curl 黑盒测试，6 用例，退出码 0=通过 |
| `docs/test-reports/user-crud-ui-test-20260715.js` | UI 脚本 | Playwright E2E，含登录/新增/删除/登录失败验证 |

## 五、代码修改清单

| 文件 | 修改 |
|------|------|
| `controller/UserController.java` | `save()`: 用户名重复校验 + `user.setRole(2)`; `delete()`: 未归还图书校验 |
| `vue/src/views/User.vue` | 新增按钮 + 动态对话框标题 + 条件密码字段 + 用户名编辑禁用 |
