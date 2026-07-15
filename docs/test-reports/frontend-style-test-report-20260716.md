# 前端界面风格改造 — test-automation 验收测试报告

**测试日期**: 2026-07-16
**测试技能**: test-automation (Playwright 浏览器测试)
**需求**: 全局字体调整 + 多语言基础设施

---

## 一、测试结果

| TC | 验收条件 | 结果 | 证据 |
|----|---------|------|------|
| TC1 | font-family 包含 SimHei/Microsoft YaHei | ✅ PASS | `font-family=Inter, "Segoe UI", SimHei, "Microsoft YaHei", sans-serif` |
| TC2 | 登录页右上角存在语言切换按钮 | ✅ PASS | 页面显示 "简中/EN" |
| TC3 | 点击 EN → login 页变英文 | ✅ PASS | title="System Login", placeholder="Username", btn="Login" |
| TC4 | 点击 简中 → 恢复中文 | ✅ PASS | title="系统登陆", placeholder="用户名", btn="登 录" |
| TC5 | 刷新后语言状态保持 | ✅ PASS | localStorage 持久化，页面重启后保持 zh |
| TC6 | 顶栏 font-weight=600 | ✅ PASS | `font-weight=600` |

**通过率: 6/6 (100%)**

## 二、涉及文件清单

| 文件 | 修改类型 | 说明 |
|------|----------|------|
| `vue/src/assets/css/global.css` | 修改 | 添加 `html{font-size:16px}` + `body{font-family:...}` + `.header-bar{font-weight:600}` |
| `vue/src/main.js` | 修改 | 引入 `createI18n`，注册中英文 locale，localStorage 持久化 |
| `vue/src/locales/zh.json` | **新建** | 中文 locale (Login 页面 6 个词条) |
| `vue/src/locales/en.json` | **新建** | 英文 locale (Login 页面 6 个词条) |
| `vue/src/views/Login.vue` | 修改 | 模板用 `$t()` 替换硬编码 + 右上角语言切换按钮 + `computed rules` |

## 三、技术决策记录

| 决策 | 原因 |
|------|------|
| `legacy: true` | 兼容 Options API 的 `this.$i18n.locale`，实时切换无需刷新 |
| `computed rules` | `data()` 中 `this.$t()` 不可用，改用 computed |
| 基准字号 16px (rem) | 符合需求，全局通过 `html{font-size:16px}` 设置 |
