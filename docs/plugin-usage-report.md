# Claude Code 插件使用情况分析报告

> 生成时间：2026-07-14  
> 分析范围：`refactor/test-drill` 分支完整重构周期  
> 环境：deepseek-v4-pro[1m] 模型，Claude Code CLI

---

## 1. 已安装插件清单

### 1.1 提供可用 Agent / Skill 的活跃插件（4 个）

| 插件名称 | 版本 | 来源 | 类型 | 核心功能 |
|----------|------|------|------|---------|
| **code-simplifier** | 1.0.0 | Official Marketplace | Agent | 代码简化/精炼，保持功能不变的前提下改善清晰度、一致性和可维护性 |
| **gkd** | — | Marketplace (3rd-party) | Skill 集 | 任务委派：将任务派给其他模型（GLM/Kimi/Deepseek/Codex）的子进程，节省主模型 token |
| **frontend-design** | — | Official Marketplace | Skill | 前端 UI/UX 设计指导：视觉方向、排版、避免模板化默认样式 |
| **playwright** | — | Official Marketplace | MCP Server | 浏览器自动化 & E2E 测试：页面导航、点击、截图、快照等 |

### 1.2 GKD 子技能清单

| 技能名 | 功能 |
|--------|------|
| `gkd:ask` | 委派只读分析/咨询（Read/Grep/Glob + Bash git） |
| `gkd:brainstorm` | 多模型并行发散式讨论，综合分歧与共识 |
| `gkd:do` | 委派写文件/落盘/执行（Edit/Write/Bash 全权） |
| `gkd:review` | 对 git diff 做代码审查，支持 `--adversarial` 对抗模式 |
| `gkd:resume` | 续接任意历史委派任务线程 |
| `gkd:workflow` | 批量委派编排为 dynamic workflow |

### 1.3 MCP Server（2 个可用）

| MCP Server | 工具数 | 核心功能 |
|------------|--------|---------|
| **filesystem** | 14 | 文件 CRUD、目录遍历、搜索、读写文本/媒体 |
| **playwright** | 26 | 浏览器全生命周期控制（导航、交互、截图、网络监控） |

### 1.4 已安装但未激活的插件（32 个）

以下插件已在 marketplace 中安装但未提供本会话可用的 Agent/Skill/MCP 工具：

`imessage`, `terraform`, `serena`, `laravel-boost`, `gemini`, `codex`, `codex-plan-gate`, `api-design`, `gh-cli`, `swift`, `agy`, `go-style-guide`, `go-formatter`, `python-formatter-black`, `python-formatter-ruff`, `multimedia`, `claude-md`, `linear`, `greptile`, `fakechat`, `firebase`, `context7`, `github`, `asana`, `discord`, `telegram`, `gitlab`, `explanatory-output-style`, `agent-sdk-dev`, `ralph-loop`, `playground`, `feature-dev`, `math-olympiad`, `mcp-server-dev`, `example-plugin`, `claude-md-management`, `commit-commands`, `hookify`, `skill-creator`, `code-modernization`, `security-guidance`, `claude-code-setup`, `plugin-dev`, `code-review`, `project-artifact`, `pr-review-toolkit`, `learning-output-style`, `cwc-makers`

> **说明**：这些插件大多因项目技术栈不匹配（如 Go/Swift/Python/Laravel 专用）或功能与本任务无关（如 Discord/Telegram 消息通道）而未使用。

---

## 2. CLAUDE.md 中的工具触发规则

项目 `CLAUDE.md` 定义了以下自动工具匹配规则：

| 意图 | 预期工具 | 是否在本会话可用 |
|------|---------|:---:|
| 重构/清理/简化代码 | `code-simplifier` 插件 | ✅ 可用 |
| 派发子任务/后台执行/批量修改 | `GKD` 插件 | ✅ 可用 |
| 分析代码库/查重复/死代码/架构违规 | `codebase-analysis` 技能 | ❌ 不可用 |

> **注意**：`codebase-analysis` 技能在 CLAUDE.md 中被引用，但**未在本环境的可用技能列表中出现**。根据规则第 4 条，回退到"常规对话式代码分析"。

---

## 3. 各插件/Skill 在重构中的实际使用情况

### 3.1 使用总览

| 插件/Skill | 安装状态 | 调用次数 | 使用阶段 | 备注 |
|------------|:---:|:---:|------|------|
| **code-simplifier** (Agent) | 已安装 | **0** | — | 未调用 |
| **gkd:do** (Skill) | 已安装 | **0** | — | 未调用 |
| **gkd:ask** (Skill) | 已安装 | **0** | — | 未调用 |
| **gkd:review** (Skill) | 已安装 | **0** | — | 未调用 |
| **gkd:brainstorm** (Skill) | 已安装 | **0** | — | 未调用 |
| **gkd:workflow** (Skill) | 已安装 | **0** | — | 未调用 |
| **frontend-design** (Skill) | 已安装 | **0** | — | 未调用 |
| **playwright** (MCP) | 已安装 | **3 次尝试** | 最终检验 | 全部失败（见 4.2） |
| **filesystem** (MCP) | 已安装 | **0** | — | 使用原生 Read/Write/Edit/Bash 替代 |
| **codebase-analysis** (Skill) | **未安装** | **0** | — | 回退到 grep 手动扫描 |

### 3.2 各阶段工具使用详情

#### 阶段一：并发安全加固（乐观锁 + AtomicInteger）
- **使用工具**：原生 Bash（`curl`、`mysql`）、Read/Edit
- **未使用插件原因**：改动集中且明确（3 个文件），无需委派；`code-simplifier` 更适合"简化已有代码"而非"新增功能"

#### 阶段二：Bug 修复（密码接口、整数列 LIKE→EQ、MapperScan 修复）
- **使用工具**：原生 Bash、Read/Edit
- **未使用插件原因**：每个修复都是精准的单点修改，委派子进程的开销大于收益

#### 阶段三：死代码清理 + BookService 创建
- **使用工具**：原生 Bash、Read/Edit/Write
- **未使用插件原因**：代码清理涉及多文件联动（前端 Vue + 后端 Controller + Service），需要在同一上下文中协调

#### 阶段四：架构加固（LendRecordService、权限检查、QueryUtils、方法拆分）
- **使用工具**：原生 Bash、Read/Edit/Write
- **未使用插件原因**：重构涉及跨层依赖（Controller→Service→Mapper），需要全局视野

#### 阶段五：最终检验（Playwright 测试 + 静态扫描 + 报告生成）
- **Playwright MCP**：尝试 3 次调用 `browser_navigate`，均因 Chrome 依赖缺失失败
- **静态扫描**：使用 `grep` + Bash 脚本实现，替代不可用的 `codebase-analysis`
- **E2E 测试**：使用 `curl` API 级测试替代 Playwright 浏览器测试

#### 阶段六：短期修复 + 日志增强
- **使用工具**：原生 Bash、Read/Edit/Write
- **未使用插件原因**：改动范围小（1 个 Controller + 1 条 SQL）

---

## 4. 插件效能评估与限制

### 4.1 贡献度排序

本次重构中，所有代码编写和测试均由主模型（deepseek-v4-pro）使用原生工具完成。各插件的实际贡献度：

| 排名 | 插件 | 贡献度 | 原因 |
|:---:|------|:---:|------|
| 1 | **原生工具 (Bash/Read/Edit/Write)** | ⭐⭐⭐⭐⭐ | 完成 100% 的代码修改和 95% 的测试 |
| 2 | **curl + mysql CLI** | ⭐⭐⭐⭐ | 完成全部 API 测试和数据库验证 |
| 3 | **grep + find** | ⭐⭐⭐ | 替代 codebase-analysis 完成静态扫描 |
| 4 | **playwright MCP** | ⭐ | 尝试 3 次均失败，未产生有效输出 |
| 5 | **code-simplifier** | — | 未使用 |
| 6 | **gkd 技能集** | — | 未使用 |

### 4.2 已知限制与问题

#### playwright MCP：环境依赖阻断
- **问题**：需要 Chrome 浏览器，但安装 `npx playwright install chrome` 需要 root 权限
- **影响**：无法执行浏览器级 E2E 测试，降级为 curl API 测试
- **根因**：Chromium headless shell 已安装（`~/.cache/ms-playwright/chromium_headless_shell-1228`），但 MCP Server 配置要求 `/opt/google/chrome/chrome` 路径的完整 Chrome
- **建议**：配置 `@playwright/mcp` 使用 Chromium 而非 Chrome

#### codebase-analysis：技能不存在
- **问题**：CLAUDE.md 引用的 `codebase-analysis` 技能不在可用技能列表中
- **影响**：代码库分析回退到手动 grep，效率较低
- **建议**：安装 `codebase-analysis` 技能或从 CLAUDE.md 中移除该引用

#### code-simplifier / gkd：未触发使用
- **问题**：所有任务由主模型直接完成，未委派给专用 Agent/Skill
- **原因分析**：
  1. 本次重构的每个任务都涉及多文件联动，委派子进程会丢失全局上下文
  2. 子进程（Agent/委派模型）无法读取主会话中已建立的代码理解
  3. 任务颗粒度精细（通常 2-5 个文件），委派开销（prompt 构建 + 子进程启动）可能大于直接操作
  4. CLAUDE.md 规则要求"所有工具执行前需向用户确认操作范围和目标文件"，增加了交互成本
- **适用场景**：`gkd:workflow` 适合 50+ 文件的同构批量修改；`code-simplifier` 适合对已完成代码做二次精炼

---

## 5. 改进建议

### 5.1 短期（立即可执行）

| 建议 | 优先级 |
|------|:---:|
| 修复 Playwright MCP：重配置为使用 Chromium headless shell 或安装 Chrome | 高 |
| 清理 CLAUDE.md：移除不可用的 `codebase-analysis` 引用或安装对应技能 | 中 |
| 为高重复性操作（如"编译+重启+测试"）建立脚本化 workflow | 中 |

### 5.2 中期

| 建议 | 说明 |
|------|------|
| 在异构批量修改场景试用 `gkd:workflow` | 例如：统一修改所有 Controller 的返回格式 |
| 在代码编写完成后试用 `code-simplifier` 做二次精炼 | 检查变量命名、方法长度、注释完整性 |
| 建立 Playwright 测试脚本库 | 覆盖核心用户流程的浏览器级回归测试 |

### 5.3 长期

| 建议 | 说明 |
|------|------|
| 开发项目专属 Skill | 封装"编译→重启→API 测试→清理"为 `/verify-refactor` 命令 |
| CI 集成 Playwright | 将浏览器测试移到 CI 环境（有 Chrome），本地用 curl 快速验证 |

---

## 附录：会话工具使用统计

| 工具 | 调用次数（估计） | 用途 |
|------|:---:|------|
| Bash | ~80+ | 编译、测试、curl API 调用、mysql 查询、grep 扫描、git 操作 |
| Read | ~30+ | 读取源码文件 |
| Edit | ~40+ | 精准字符串替换 |
| Write | ~15 | 新建 Service/Utils/报告文件 |
| TaskCreate/TaskUpdate | 7 | 任务跟踪 |
| mcp__playwright__browser_navigate | 3 | 尝试启动浏览器（均失败） |
| Agent / Skill | 0 | — |

> **结论**：本次重构中，主模型的工具循环（Bash + Read + Edit + Write）承担了全部有效工作。插件/MCP 基础设施已就位但未被充分利用。在未来的大规模重构中，合理使用 `gkd:workflow` 和 `code-simplifier` 有望显著提升效率。
