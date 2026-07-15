---
name: test-automation
description: 自动生成测试用例、执行测试、生成测试报告
---

# 测试自动化 Skill

## 触发条件
用户说「帮我写测试」、「运行测试」、「生成测试报告」

## 执行流程
1. 识别测试目标（Controller / Service / Mapper）
2. 生成对应的 JUnit 或 Playwright 测试用例，存放在约定目录
3. 执行测试（使用 Maven 或 Playwright CLI）
4. 生成测试报告到 `docs/test-reports/`,注意为每次测试用例作明显的时间和用途等标识
5. 将报告摘要返回给用户
