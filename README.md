# hello-agents-java

> Datawhale [hello-agents](https://datawhalechina.github.io/hello-agents) 教程的 Java 复刻版 —— 用纯 Java 从零实现 AI 原生智能体。

本项目帮助 Java 开发者理解并动手构建完整的 Agent 系统，涵盖推理框架、工具调用、记忆管理、RAG（检索增强生成）等核心能力。

---

## 目录

- [技术栈](#技术栈)
- [项目结构](#项目结构)
- [快速开始](#快速开始)
- [Agent 实现](#agent-实现)
- [工具系统](#工具系统)
- [记忆系统](#记忆系统)
- [RAG 系统](#rag-系统)
- [Demo 列表](#demo-列表)
- [开发规范](#开发规范)

---

## 技术栈

| 组件 | 版本 | 说明 |
|------|------|------|
| Java | 17 | records、sealed class、text blocks |
| Maven | 3.x | 构建工具 |
| openai-java | 2.1.0 | 官方 OpenAI Java SDK |
| jackson-databind | 2.17.1 | JSON 序列化 |
| Apache Tika | 2.9.2 | 文档解析（PDF、Office、HTML 等多种格式） |
| dotenv-java | 3.0.0 | 本地 `.env` 配置加载 |
| JUnit 5 | 5.10.2 | 单元测试 |

---

## 项目结构

```
hello-agents-java/
├── src/main/java/com/helloagents/
│   ├── core/              # Agent 基础抽象（BaseAgent、AbstractAgent、ToolSupport）
│   ├── agents/            # Agent 实现
│   │   ├── SimpleAgent          # 通用对话 Agent，支持工具调用
│   │   ├── ReActAgent           # 推理-行动（ReAct）框架
│   │   ├── ReflectionAgent      # 生成-反思-精炼
│   │   ├── PlanAndSolveAgent    # 规划-求解两阶段
│   │   ├── Planner              # 任务分解
│   │   └── Solver               # 步骤执行
│   ├── llm/               # LLM 调用封装
│   │   ├── LlmClient            # 接口（chat / stream）
│   │   ├── LlmConfig            # 配置（key、url、model、temperature 等）
│   │   ├── OpenAiClient         # 基于官方 SDK 的实现
│   │   └── Message              # 消息记录（role + content）
│   ├── tools/             # 工具系统
│   │   ├── Tool                 # 工具接口
│   │   ├── ToolRegistry         # 工具注册与分发
│   │   └── CalculatorTool       # 内置计算器工具
│   ├── memory/            # 认知记忆系统
│   │   ├── core/                # 类型定义与接口（MemoryType、MemoryEntry、MemoryStore 等）
│   │   ├── store/               # 存储实现（InMemoryStore、WorkingMemory 等四个记忆类）
│   │   ├── MemoryManager        # 统一协调器
│   │   ├── MemoryService        # 业务逻辑（搜索、遗忘、整合）
│   │   └── tool/                # 9 个记忆工具
│   ├── rag/               # RAG 系统
│   │   ├── core/                # 数据模型与接口
│   │   ├── document/            # 文档解析与切分
│   │   ├── embedding/           # 向量化（OpenAI Embedding）
│   │   ├── store/               # 内存向量库 & 文档库
│   │   ├── retrieval/           # 检索策略
│   │   ├── pipeline/            # 索引管道 & 查询管道
│   │   ├── app/                 # RagSystem 应用层门面
│   │   └── tool/                # 3 个 RAG 工具
│   └── demo/              # Demo 入口
│       ├── SimpleAgentDemo
│       ├── ReActAgentDemo
│       ├── ReflectionAgentDemo
│       ├── PlanAndSolveAgentDemo
│       ├── MemoryToolDemo
│       └── RagDemo
├── docs/                  # 学习笔记
├── .env.example           # 环境变量模板
└── pom.xml
```

---

## 快速开始

### 1. 配置环境变量

复制 `.env.example` 并填入你的 API Key：

```bash
cp .env.example .env
```

编辑 `.env`：

```dotenv
LLM_API_KEY=sk-...
# LLM_BASE_URL=https://api.openai.com/v1   # 可选，默认 OpenAI
# LLM_MODEL=gpt-4o                          # 可选，默认 gpt-4o
```

支持 OpenAI、Anthropic（OpenAI 兼容格式）及本地模型（Ollama、DeepSeek 等）。

### 2. 编译

```bash
mvn compile
```

### 3. 运行 Demo

```bash
# SimpleAgent — 基础对话与工具调用
mvn exec:java -Dexec.mainClass="com.helloagents.demo.SimpleAgentDemo"

# ReActAgent — 推理-行动循环
mvn exec:java -Dexec.mainClass="com.helloagents.demo.ReActAgentDemo"

# ReflectionAgent — 生成-反思-精炼
mvn exec:java -Dexec.mainClass="com.helloagents.demo.ReflectionAgentDemo"

# PlanAndSolveAgent — 规划-求解
mvn exec:java -Dexec.mainClass="com.helloagents.demo.PlanAndSolveAgentDemo"

# 记忆系统 Demo
mvn exec:java -Dexec.mainClass="com.helloagents.demo.MemoryToolDemo"

# RAG Demo
mvn exec:java -Dexec.mainClass="com.helloagents.demo.RagDemo"
```

### 4. 运行测试

```bash
mvn test
```

---

## Agent 实现

### SimpleAgent

通用对话 Agent，支持多轮对话历史、工具调用循环和流式输出。

```java
LlmClient llm = LlmClient.fromEnv();
SimpleAgent agent = new SimpleAgent(llm);

// 注册工具（Lambda 方式）
agent.register("word_count", "统计输入字符串的词数",
    params -> String.valueOf(params.getOrDefault("text", "").split("\\s+").length));

// 基础对话
String reply = agent.chat("Java 17 有哪些新特性？");

// 流式输出
agent.stream("解释一下 ReAct 框架", token -> System.out.print(token));
```

### ReActAgent

实现 ReAct 推理-行动框架。模型通过 native function calling 调用工具，调用内置 `finish` 工具时终止循环。

```java
LlmClient llm = LlmClient.fromEnv();
ReActAgent agent = new ReActAgent(llm);
agent.register("calculator", "计算数学表达式",
    params -> /* eval params.get("expression") */);
String result = agent.run("如果一个正方形面积是 144，它的周长是多少？");
```

### ReflectionAgent

三阶段自我改进框架：
1. **Generate** — 生成初稿（可使用工具）
2. **Reflect** — 对初稿进行批判性反思
3. **Refine** — 根据反思优化输出

```java
LlmClient llm = LlmClient.fromEnv();
ReflectionAgent agent = new ReflectionAgent(llm);
String result = agent.run("写一篇关于量子计算的简介");
```

### PlanAndSolveAgent

两阶段任务分解执行：
1. **Plan** — 将复杂任务分解为有序步骤（Planner，无工具）
2. **Solve** — 逐步执行，调用工具（Solver）

```java
LlmClient llm = LlmClient.fromEnv();
PlanAndSolveAgent agent = new PlanAndSolveAgent(llm);
agent.register("search", "搜索信息", params -> /* search api */);
String result = agent.run("调研并总结 2024 年大模型发展趋势");
```

---

## 工具系统

基于 OpenAI 原生 function calling 协议，无需文本解析。工具的参数 schema 自动序列化为 JSON 提交给模型，结果以 `tool` 角色消息回传。

```java
// 实现 Tool 接口
public class SearchTool implements Tool {
    public String name() { return "search"; }
    public String description() { return "搜索互联网信息"; }
    public ToolParameter parameters() {
        return ToolParameter.of(ToolParameter.Param.required("query", "搜索关键词", "string"));
    }
    public String execute(Map<String, String> params) {
        String query = params.get("query");
        // ...
    }
}

// 注册到 Agent
agent.register(new SearchTool());

// 也支持 Lambda 快速注册
agent.register("reverse", "反转字符串",
    params -> new StringBuilder(params.getOrDefault("text", "")).reverse().toString());
```

工具 schema 自动序列化为 JSON Schema 并传入 API，模型结构化返回工具调用，`ToolRegistry` 按名称派发执行。

---

## 记忆系统

跨会话 Agent 记忆系统，让 Agent 在对话结束后仍能保留关键信息。默认使用内存存储（`InMemoryStore`），也可切换到 `MarkdownMemoryStore` 将每条记忆持久化为带 YAML frontmatter 的 `.md` 文件。

**四种记忆类型：**

| 类型 | 说明 | 适用场景 |
|------|------|----------|
| `user` | 用户偏好 | 代码风格、响应详略、工具链习惯 |
| `feedback` | 用户纠正 | 明确纠正过的错误，或已验证有效的做法 |
| `project` | 项目约定 | 无法从代码推导的设计决策、团队规则 |
| `reference` | 外部资源 | 问题单看板、监控面板、文档 URL |

**5 个 Agent 工具：**

| 工具 | 说明 |
|------|------|
| `save_memory` | 保存一条跨会话记忆（需提供 name、description、type、content） |
| `search_memory` | 按关键词检索，可按 type 过滤 |
| `list_memories` | 列出全部记忆，按类型分组 |
| `update_memory` | 更新已有记忆的内容（需先 search_memory 取 id） |
| `delete_memory` | 删除失效或用户明确撤销的记忆 |

```java
// 内存存储（默认）
MemoryToolkit toolkit = new MemoryToolkit();

// 持久化到 Markdown 文件
MemoryManager manager = new MemoryManager(new MarkdownMemoryStore(Path.of(".agent-memory")));
MemoryToolkit toolkit = new MemoryToolkit(new MemoryService(manager));

LlmClient llm = LlmClient.fromEnv();
SimpleAgent agent = new SimpleAgent(llm);
toolkit.getTools().forEach(agent::register);
```

---

## RAG 系统

模块化 RAG 架构，支持多种文档格式（PDF、Office、HTML、纯文本等，由 Apache Tika 驱动），包含 8 个功能包：

| 包 | 职责 |
|----|------|
| `core/` | 数据模型与接口（Document、Chunk、Embedding、VectorStore 等） |
| `document/` | 文档解析（Apache Tika）与文本切分（固定长度 / 递归切分） |
| `embedding/` | 向量化（OpenAI text-embedding-3-small/large） |
| `store/` | 内存向量库与文档库实现 |
| `retrieval/` | 检索策略（余弦相似度 + 阈值过滤） |
| `pipeline/` | 索引管道（解析→切分→向量化→存储）和查询管道 |
| `app/` | `RagSystem` 统一门面 |
| `tool/` | Agent 可调用的 RAG 工具（rag_add / rag_search / rag_ask） |

**快速使用：**

```java
LlmConfig config = LlmConfig.fromEnv();
RagSystem rag = new RagSystem(config);

// 添加文本
rag.addDocument("Java 17", "Java 17 是 LTS 版本，引入了 records、sealed classes...");

// 添加文件（自动解析）
rag.addFile(Path.of("report.pdf"));

// 语义检索
List<SearchResult> results = rag.search("Java 17 新特性", 5);

// RAG 问答
String answer = rag.ask("Java 17 中 record 类有什么用？");
```

**与 Agent 集成：**

```java
RagToolkit ragToolkit = new RagToolkit(rag, config);
SimpleAgent agent = new SimpleAgent(config);
ragToolkit.getTools().forEach(agent::register);

// Agent 现在可以自主调用 rag_add / rag_search / rag_ask
agent.chat("帮我查一下文档里关于 record 的内容");
```

---

## Demo 列表

| Demo | 入口类 | 演示内容 |
|------|--------|---------|
| SimpleAgent | `SimpleAgentDemo` | 基础问答、多轮对话、工具调用、Lambda 注册 |
| ReAct | `ReActAgentDemo` | Thought-Action-Observation 推理循环 |
| Reflection | `ReflectionAgentDemo` | 三阶段自我改进流程 |
| PlanAndSolve | `PlanAndSolveAgentDemo` | 任务规划与分步执行 |
| Memory | `MemoryToolDemo` | 四类记忆操作、整合与遗忘 |
| RAG | `RagDemo` | 文档索引、语义检索、Agent 集成 |

---

## 开发规范

- 使用 Java 17 特性：`record`、`sealed class`、pattern matching、text blocks
- 方法名驼峰，类名大驼峰
- 每个 Agent 实现都有对应的 `Demo` 类，位于 `demo/` 包
- 所有 LLM 调用通过 `llm/` 包下的客户端封装，业务代码不直接发 HTTP 请求
- API Key 通过环境变量注入，**不得硬编码**

---

## 参考资料

- [hello-agents 原版教程](https://datawhalechina.github.io/hello-agents)
- [ReAct: Synergizing Reasoning and Acting in Language Models](https://arxiv.org/abs/2210.11610)
- [OpenAI API Docs](https://platform.openai.com/docs)
- [Anthropic API Docs](https://docs.anthropic.com)
- [Apache Tika](https://tika.apache.org)