# hello-agents-java

> Datawhale [hello-agents](https://datawhalechina.github.io/hello-agents) 教程的 Java 复刻版 —— 用纯 Java 从零实现 AI 原生智能体。

本项目帮助 Java 开发者理解并动手构建完整的 Agent 系统，涵盖推理框架、工具调用、记忆管理、RAG 以及上下文工程等核心能力。

---

## 目录

- [技术栈](#技术栈)
- [项目结构](#项目结构)
- [快速开始](#快速开始)
- [Agent 实现](#agent-实现)
- [上下文工程](#上下文工程)
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
| Apache Tika | 2.9.2 | 文档解析（PDF、Office、HTML 等） |
| dotenv-java | 3.0.0 | 本地 `.env` 配置加载 |
| JUnit 5 | 5.10.2 | 单元测试 |

---

## 项目结构

```
hello-agents-java/
├── src/main/java/com/helloagents/
│   ├── core/              # Agent 基础抽象
│   │   ├── BaseAgent            # 接口（run / stream / history / executionTrace）
│   │   ├── AbstractAgent        # 公共实现（history、executionTrace、可选上下文增强）
│   │   └── ToolSupport          # 工具管理 Mixin
│   ├── context/           # 上下文工程
│   │   ├── SystemPromptBuilder  # 构建 system message（RAG + 记忆 + 指令）
│   │   ├── CompressedHistory    # 对话历史滚动摘要压缩
│   │   ├── ContextConfig        # SystemPromptBuilder 参数配置
│   │   └── ContextPacket        # 上下文信息单元
│   ├── agents/            # Agent 实现
│   │   ├── SimpleAgent          # 通用对话 Agent，支持工具调用
│   │   ├── ReActAgent           # 推理-行动（ReAct）框架
│   │   ├── ReflectionAgent      # 生成-反思-精炼
│   │   └── PlanAndSolveAgent    # 规划-求解两阶段
│   ├── llm/               # LLM 调用封装
│   │   ├── LlmClient            # 接口（chat / stream）
│   │   ├── OpenAiClient         # 基于官方 SDK 的实现
│   │   ├── Message              # 消息记录（role + content + toolCalls）
│   │   ├── LlmResponse          # 结构化响应（content / toolCalls）
│   │   └── FunctionCall         # 工具调用描述
│   ├── tools/             # 工具系统
│   │   ├── Tool                 # 工具接口
│   │   ├── ToolRegistry         # 注册与分发（支持 Lambda 注册）
│   │   └── CalculatorTool       # 内置计算器工具
│   ├── memory/            # 认知记忆系统
│   │   ├── core/                # MemoryType、MemoryEntry、MemoryStore 接口
│   │   ├── store/               # InMemoryStore、MarkdownMemoryStore
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
│   │   ├── app/                 # RagSystem 门面
│   │   └── tool/                # 3 个 RAG 工具
│   └── demo/              # Demo 入口
├── docs/                  # 学习笔记与改造规划
├── .env.example           # 环境变量模板
└── pom.xml
```

---

## 快速开始

### 1. 配置环境变量

```bash
cp .env.example .env
```

编辑 `.env`：

```dotenv
LLM_API_KEY=sk-...
# LLM_BASE_URL=https://api.openai.com/v1   # 可选，默认 OpenAI
# LLM_MODEL=gpt-4o                          # 可选
```

支持 OpenAI、Anthropic（OpenAI 兼容格式）及本地模型（Ollama、DeepSeek 等）。

### 2. 编译

```bash
mvn compile
```

### 3. 运行 Demo

```bash
mvn exec:java -Dexec.mainClass="com.helloagents.demo.SimpleAgentDemo"
mvn exec:java -Dexec.mainClass="com.helloagents.demo.ReActAgentDemo"
mvn exec:java -Dexec.mainClass="com.helloagents.demo.ReflectionAgentDemo"
mvn exec:java -Dexec.mainClass="com.helloagents.demo.PlanAndSolveAgentDemo"
mvn exec:java -Dexec.mainClass="com.helloagents.demo.RagDemo"
```

### 4. 运行测试

```bash
mvn test
```

---

## Agent 实现

所有 Agent 继承 `AbstractAgent`，持有两条并行记录：

| 字段 | 内容 | 消费者 |
|------|------|--------|
| `history` | 每轮 `run()` 的 user + assistant 对 | `CompressedHistory` → LLM 上下文 |
| `executionTrace` | 每轮 `run()` 的完整消息序列（含工具调用） | 用户查看 / 持久化 |

### SimpleAgent

通用对话 Agent，支持多轮历史、工具调用循环和流式输出。

```java
LlmClient llm = LlmClient.fromEnv();
SimpleAgent agent = new SimpleAgent(llm);

// 基础对话
String reply = agent.run("Java 17 有哪些新特性？");

// 流式输出
agent.stream("解释一下 ReAct 框架", token -> System.out.print(token));

// 工具调用
agent.addTool(new CalculatorTool());
agent.run("计算 (123 + 456) * 7 的结果");

// 查看执行过程（含工具调用中间步骤）
agent.getLastExecution().forEach(msg -> System.out.println(msg.role() + ": " + msg.content()));
```

### ReActAgent

实现 ReAct 推理-行动框架，内置 `finish` 工具终止循环。

```java
ReActAgent agent = new ReActAgent(llm);
agent.addTool(new CalculatorTool());
String result = agent.run("如果一个正方形面积是 144，它的周长是多少？");
```

### ReflectionAgent

三阶段自我改进：Generate → Reflect → Refine。

```java
ReflectionAgent agent = new ReflectionAgent(llm);
String result = agent.run("写一篇关于量子计算的简介");
```

### PlanAndSolveAgent

两阶段执行：Planner 分解任务，Solver 逐步执行并调用工具。

```java
PlanAndSolveAgent agent = new PlanAndSolveAgent(llm);
agent.addTool(new CalculatorTool());
String result = agent.run("调研并总结 2024 年大模型发展趋势");
```

---

## 上下文工程

`context/` 包实现了系统化的上下文管理，解决长对话中 token 溢出和信息召回两个核心问题。

### SystemPromptBuilder

针对每次 user query 动态构建 system message，整合记忆索引和 RAG 召回结果。输出固定为单条 system message，不干扰多轮对话的消息结构。

```
[Role & Policies]   ← systemInstructions
[Memory]            ← 记忆索引 + search_memory 提示
[Evidence]          ← RAG 召回的相关文档片段
[Output]            ← 输出要求
```

```java
SystemPromptBuilder spb = new SystemPromptBuilder(
        ContextConfig.builder()
                .embeddingModel(embeddingModel)  // 启用向量相关性计算
                .minRelevance(0.3)
                .build())
        .withMemory(memoryService)
        .withRag(ragSystem);

agent.withSystemPromptBuilder(spb);
```

内部使用 GSSC 四阶段流水线（Gather → Select → Structure → Compress）从多个数据源收集上下文，按 token 预算和相关性打分筛选，最终组装为结构化文本。

### CompressedHistory

解决长对话中对话历史无限增长导致 token 溢出的问题，采用**滚动摘要压缩**策略：

```
初始:   [turn1, turn2, turn3, turn4, turn5]  ← 超过阈值
压缩后: [summary("turn1-3的内容"), turn4, turn5]
继续后: [summary("turn1-3"), turn4, turn5, turn6, turn7]  ← 再次超过
再压缩: [summary("turn1-5，整合旧摘要"), turn6, turn7]
```

摘要通过 LLM 调用增量生成，每次压缩只处理新增的被淘汰消息，不重新摘要全部历史。摘要内容合并进 system message，与主 system prompt 合并为**单条** system message，保证协议合规。

```java
CompressedHistory ch = new CompressedHistory(
        llm,
        2000,   // maxRecentTokens：超过此阈值触发压缩
        800     // keepRecentTokens：压缩后保留的 recent token 数
);

agent.withCompressedHistory(ch);
```

`CompressedHistory` 通过水位线（`consumed`）追踪已处理的消息数，调用方每次传入全量 `history`，内部自动只处理新增部分，不会重复写入。

### 与 Agent 的集成

两者均为可选组件，不影响未使用它们的 Agent：

```java
LlmClient llm = LlmClient.fromEnv();

SystemPromptBuilder spb = new SystemPromptBuilder(ContextConfig.defaults())
        .withMemory(memoryService)
        .withRag(ragSystem);

CompressedHistory ch = new CompressedHistory(llm);

SimpleAgent agent = new SimpleAgent(llm, "You are a helpful assistant.");
agent.withSystemPromptBuilder(spb);
agent.withCompressedHistory(ch);

// 每次 run() 的消息结构：
// [system: spb输出 + 摘要(若有)]
// [recent user/assistant 对]
// [user: 当前 query]
agent.run("Java 的起源是什么？");
```

### ExecutionTrace

每次 `run()` 完整记录所有消息，包括工具调用中间步骤，方便用户查看推理过程，也为后续持久化预留接口。

```java
// 查看最近一次执行的完整过程
List<Message> trace = agent.getLastExecution();
// 例：[user, assistantWithToolCalls, tool(result), assistant(最终答案)]

// 查看所有历史执行
List<List<Message>> allTraces = agent.getExecutionTrace();

// 清空
agent.clearExecutionTrace();
```

---

## 工具系统

基于 OpenAI 原生 function calling 协议，无需文本解析。

```java
// 实现 Tool 接口
public class SearchTool implements Tool {
    public String name()        { return "search"; }
    public String description() { return "搜索互联网信息"; }
    public ToolParameter parameters() {
        return ToolParameter.of(ToolParameter.Param.required("query", "搜索关键词", "string"));
    }
    public String execute(Map<String, String> params) { ... }
}

// 注册 Tool 实现
agent.addTool(new SearchTool());

// Lambda 快速注册（无需定义 Tool 类）
ToolRegistry registry = new ToolRegistry()
    .register("reverse", "反转字符串",
        ToolParameter.of(ToolParameter.Param.required("text", "输入文本", "string")),
        params -> new StringBuilder(params.getOrDefault("text", "")).reverse().toString());

SimpleAgent agent = new SimpleAgent("MyAgent", llm, null, registry);
```

---

## 记忆系统

跨会话持久记忆，支持内存和 Markdown 文件两种存储后端。

**四种记忆类型：**

| 类型 | 适用场景 |
|------|----------|
| `user` | 用户偏好、编码风格 |
| `feedback` | 已验证有效的做法、明确纠正过的错误 |
| `project` | 无法从代码推导的设计决策、截止日期 |
| `reference` | 问题单看板、监控面板 URL |

```java
// 内存存储（默认）
MemoryToolkit toolkit = new MemoryToolkit();

// 持久化到 Markdown 文件
MemoryManager manager = new MemoryManager(new MarkdownMemoryStore(Path.of(".agent-memory")));
MemoryToolkit toolkit = new MemoryToolkit(new MemoryService(manager));

SimpleAgent agent = new SimpleAgent(llm);
toolkit.getTools().forEach(agent::addTool);
```

---

## RAG 系统

模块化 RAG 架构，Apache Tika 驱动多格式文档解析。

```java
KnowledgeBase kb  = new KnowledgeBase(new InMemoryDocumentStore(), new InMemoryVectorStore());
RagSystem     rag = new RagSystem(embeddingModel, kb);

// 索引文档
rag.addDocument("Java 17", "Java 17 是 LTS 版本，引入了 records、sealed classes...");
rag.addFile(Path.of("report.pdf"));

// 语义检索
List<SearchResult> results = rag.search("Java 17 新特性", 5);

// 与 Agent 集成
RagToolkit ragToolkit = new RagToolkit(rag);
ragToolkit.getTools().forEach(agent::addTool);
```

---

## Demo 列表

| Demo | 入口类 | 演示内容 |
|------|--------|---------|
| SimpleAgent | `SimpleAgentDemo` | 基础问答、多轮对话、工具调用、ExecutionTrace 查看、CompressedHistory |
| ReAct | `ReActAgentDemo` | Thought-Action-Observation 推理循环 |
| Reflection | `ReflectionAgentDemo` | 三阶段自我改进流程 |
| PlanAndSolve | `PlanAndSolveAgentDemo` | 任务规划与分步执行 |
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
- [OpenAI API Docs](https://platform.openai.com/docs)
- [Anthropic API Docs](https://docs.anthropic.com)
- [Apache Tika](https://tika.apache.org)