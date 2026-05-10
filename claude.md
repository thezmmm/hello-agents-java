# hello-agents-java

A Java implementation of Hello-Agents — rebuild AI-native agent demos from scratch using pure Java, inspired by Datawhale's hello-agents tutorial.

## 项目概述

本项目是 [Datawhale hello-agents](https://datawhalechina.github.io/hello-agents) 教程的 Java 复刻版，用纯 Java 从零实现 AI 原生智能体，帮助 Java 开发者理解并动手构建属于自己的 Agent 系统。

## 技术栈

- **Java 17**
- **Maven**
- **HTTP Client**：使用 Java 17 内置 `java.net.http.HttpClient` 直接调用 LLM API
- **LLM 支持**：OpenAI API / Anthropic API（兼容 OpenAI 格式）

## 项目结构

```
hello-agents-java/
├── src/
│   ├── main/java/com/helloagents/
│   │   ├── agents/        # Agent 实现（SimpleAgent、ReActAgent、ReflectionAgent、PlanAndSolveAgent）
│   │   │   └── core/      # BaseAgent 接口、AbstractAgent、ToolSupport
│   │   ├── llm/           # LLM 调用封装（OpenAI / Anthropic）
│   │   ├── tools/         # Tool 接口、ToolRegistry、6 个内置工具
│   │   ├── memory/        # 记忆模块（4 种类型、InMemory/Markdown 存储）
│   │   ├── rag/           # RAG 系统（解析、切分、向量检索）
│   │   ├── mcp/           # Model Context Protocol 客户端/服务端
│   │   ├── context/       # 对话压缩、动态 system prompt
│   │   └── demo/          # 各 Agent 模式 Demo 入口
│   │       ├── travel/    # 多 Agent 旅行规划（Spring Boot + React 前端）
│   │       ├── maintenance/ # 代码库维护 Agent
│   │       └── mcp/       # MCP 示例
│   └── test/
├── pom.xml
└── CLAUDE.md
```

## 开发规范

### 代码风格
- 使用 Java 17 特性：record、sealed class、pattern matching、text blocks
- 方法命名使用驼峰，类名使用大驼峰
- 每个 Agent 实现都要有对应的 Demo 类，放在 `demo/` 包下
- 所有 LLM 调用统一通过 `llm/` 包下的客户端封装，禁止在业务代码中直接发 HTTP 请求

### API Key 管理
- API Key 通过环境变量注入，**不得硬编码**
- `OPENAI_API_KEY` 或 `ANTHROPIC_API_KEY`
- 本地开发使用 `.env` 文件（已加入 `.gitignore`）

## 常用命令

```bash
# 编译
mvn compile

# 运行测试
mvn test

# 运行指定 Demo（示例）
mvn exec:java -Dexec.mainClass="com.helloagents.demo.ReActAgentDemo"

# 打包
mvn package
```

## 环境变量

| 变量名 | 说明 |
|--------|------|
| `OPENAI_API_KEY` | OpenAI API Key |
| `OPENAI_BASE_URL` | 自定义 API Base URL（可选，用于兼容其他模型） |
| `ANTHROPIC_API_KEY` | Anthropic API Key |
| `DEFAULT_MODEL` | 默认模型名，如 `gpt-4o` 或 `claude-sonnet-4-20250514` |

## 参考资料

- [hello-agents 原版教程](https://datawhalechina.github.io/hello-agents)
- [OpenAI API Docs](https://platform.openai.com/docs)
- [Anthropic API Docs](https://docs.anthropic.com)