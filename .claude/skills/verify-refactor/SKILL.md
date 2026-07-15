---
name: verify-refactor
description: 执行完整的重构验证流程：编译 → 启动 → API 测试 → 生成报告
---

# 验证重构技能

## 触发条件
当用户说「验证重构」或「检查代码」时激活。

## 执行流程
1. **编译检查**：`cd SpringBoot && mvn compile`
2. **启动后端**：`cd SpringBoot && mvn spring-boot:run &`
3. **API 冒烟测试**：`curl -s http://localhost:9090/dashboard | jq .`
4. **数据库检查**：`mysql -u root -p -e "SELECT COUNT(*) FROM springboot_vue_test.book;"`
5. **报告生成**：将结果汇总为 `docs/verify-report.md`

## 错误处理
- 如果编译失败，停止流程并输出错误日志
- 如果后端未启动，尝试自动启动
- 如果 API 返回 500，输出完整堆栈
