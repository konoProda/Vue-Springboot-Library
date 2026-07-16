# 主界面框架 + 仪表盘搜索 — test-automation 验收测试报告

**测试日期**: 2026-07-16
**测试技能**: test-automation (Playwright 浏览器测试)

---

## 一、测试结果 7/7 PASS

| TC | 验收条件 | 结果 | 证据 |
|----|---------|------|------|
| TC1 | 第一行: Logo/系统名 + 深色切换 + 语言切换 + 用户信息 | ✅ | `图书馆管理系统☀️ 浅色|简中/EN|管理员` |
| TC2 | 第二行: 所有导航水平排列 (展示板/读者管理/书籍管理/借阅管理等) | ✅ | 7 个菜单项全部可见 |
| TC3 | 左侧垂直导航已消失 | ✅ | `.el-menu-vertical-demo` gone |
| TC4 | 搜索框宽 60-80% | ✅ | `w=964 ratio=62.7%` |
| TC5 | 搜索"三体"→跳转 /book 且列表含三体 | ✅ | `url=/book=true 三体=true` |
| TC6 | 搜索不存在的书→不跳转+红字提示 | ✅ | `stayed=true err=true` |
| TC7 | 统计卡片(4个)+图表(2个)正常 | ✅ | `cards=4 trend=true top=true` |

## 二、Bug 修复记录

| # | Bug | 根因 | 修复 |
|---|-----|------|------|
| 1 | Dashboard 全空白 | `props: ['user']` 与 `data().user` 同名冲突，`created()` 中 `this.user = ...` 触发 Vue Proxy 拒绝写入只读 prop | 移除 `props`，`user` 仅保留在 `data()` 中 |
| 2 | 搜索"三体"无结果 | `search2=三体 AND search3=三体`，作者"刘慈欣"≠"三体"，AND 导致 0 匹配 | 先按书名搜，无结果再按作者搜 |

## 三、修改文件

| 文件 | 修改 |
|------|------|
| `vue/src/components/Header.vue` | 重写为两行顶栏(Row1:logo+深色+语言+用户, Row2:水平导航); 移除 props 冲突 |
| `vue/src/layout/Layout.vue` | 移除 `<Aside/>` 和 flex 容器，`<router-view>` 全宽渲染 |
| `vue/src/views/Dashboard.vue` | 添加搜索栏 + `doSearch()` 方法（书名优先→作者回退） |
| `vue/src/views/Book.vue` | `created()` 中读取 `$route.query.q` 自动填入搜索 |
| `docs/test-reports/topnav-dashboard-test-20260716.js` | Playwright E2E 测试脚本 |
