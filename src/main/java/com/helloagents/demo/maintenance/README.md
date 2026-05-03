# 长程代码库维护助手

## 概述

本 Demo 实现了一个面向 Java 代码库的**长程智能体**（Long-running Agent），通过持续的交互式会话帮助开发者维护和改善代码质量。

"长程"指的是 Agent 在一次进程中持续运行，任务间共享上下文和历史记忆，而非每次都从零开始。

## 为什么需要长程智能体？

| 场景 | 普通 Agent | 长程 Agent |
|------|-----------|-----------|
| 多步骤代码审查 | 每轮丢失上下文 | 记住已分析的文件和发现的问题 |
| 跨文件重构 | 无法关联多次结果 | 积累修改记录，保持全局视图 |
| 长对话历史 | 容易超出 Token 限制 | `CompressedHistory` 自动压缩 |

## 架构

```
CodebaseMaintainer（主控类）
├── main()                — 创建 TerminalTool / buildAgent / 启动会话
├── buildAgent()          — 装配 ReActAgent + CompressedHistory + 3 个工具
├── runInteractiveLoop()  — REPL 主循环，每次任务前重置 TerminalTool 工作目录
├── executeTask()         — 流式执行单个任务，成功后记录统计
├── handleCommand()       — 处理 /status /trace /history /tools /clear /exit
└── SessionStats（record）— 会话统计
    ├── completedTasks    — 成功完成的任务列表（失败任务不计入）
    ├── modifiedFiles     — 被写入/修改的文件路径集合
    └── elapsed()         — 会话已运行时长
```

## 工具配置

| 工具 | 沙箱 | 典型用途 |
|------|------|---------|
| `terminal` | 只读白名单（ls / grep / find / cat 等），工作目录锁定在 workspace | 目录浏览、文件搜索、内容 grep |
| `file_read` | 读取路径必须在 workspace 内 | 详细阅读源码 |
| `file_write` | 写入路径必须在 workspace 内 | 修复 Bug、添加 Javadoc、生成文档 |

三个工具均以 workspace 为边界，无法操作 workspace 根目录之外的路径。

## 运行方式

```bash
# 使用当前目录作为 workspace（默认）
mvn exec:java -Dexec.mainClass="com.helloagents.demo.maintenance.CodebaseMaintainer"

# 指定目标代码库路径
mvn exec:java \
  -Dexec.mainClass="com.helloagents.demo.maintenance.CodebaseMaintainer" \
  -Dexec.args="/path/to/your/project"
```

## 交互命令

启动后进入 REPL，支持以下内置命令：

| 命令 | 功能 |
|------|------|
| `/status` | 查看会话统计（耗时、已完成任务数、修改文件列表） |
| `/trace` | 打印上一次任务的完整执行 Trace（工具调用链） |
| `/history` | 查看对话历史消息列表 |
| `/tools` | 列出当前注册的所有工具 |
| `/clear` | 清空对话历史（会话统计保留） |
| `/help` | 显示帮助信息 |
| `/exit` | 退出会话并打印统计总结 |

## 示例任务

```
> Find all TODO and FIXME comments in the project and summarize them
> Review the error handling in src/main/java/com/example/OrderService.java
> Add Javadoc to all public methods in UserRepository.java
> Check for potential null pointer exceptions in the service layer
> List all public classes that have no corresponding test class
> Identify duplicated utility methods across the codebase
> Generate a summary of what each package is responsible for
```

## 关键设计点

### CompressedHistory — 防止上下文溢出

长会话中对话历史会不断增长，超过 LLM 的 Token 限制。
`CompressedHistory` 在历史超过阈值时自动调用 LLM 将旧对话压缩为摘要，
再拼接最近 N 条原始消息发送给模型，使会话可以无限延续。

历史中只保留 user/assistant（final answer）消息对，工具调用链不进 history。
为确保跨任务上下文连贯，System Prompt 要求 Agent 在 `finish` 的 answer 里
写明本次探索、发现和修改，压缩后这条摘要仍完整保留。

### maxSteps = 20 — 支持复杂任务

每个任务最多允许 20 轮工具调用，足以完成跨多个文件的分析和修改任务。

### TerminalTool 工作目录隔离

每次新任务开始前自动重置 `TerminalTool` 的工作目录到 workspace 根，
防止前一个任务的 `cd` 操作影响后续任务的 `ls`/`find` 结果。

### SessionStats — 会话感知

只有成功执行完成的任务才计入 `completedTasks`，执行中抛异常的任务不计入。
`/status` 和退出时的总结让用户清楚本次会话做了什么、改了哪些文件，
便于 Code Review 和回滚。修改文件列表从工具调用的结构化参数中提取，
不依赖工具返回值的字符串格式。