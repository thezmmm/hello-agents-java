# 工具调用系统设计文档

## 概述

工具调用系统让 Agent 能够在对话过程中执行外部操作——计算、读写记忆、检索知识库等。LLM 在回复中嵌入结构化标记，框架识别并执行对应工具，将结果作为 Observation 反馈给 LLM 继续推理。

```
LLM 回复文本
    ↓
ToolRegistry.parseToolCalls()   ← 正则扫描，提取 [TOOL_CALL:...] 标记
    ↓
List<ToolCall>                  ← 每个标记解析为 toolName + 参数 Map
    ↓
ToolRegistry.execute(call)      ← 按名称查找工具并调用
    ↓
tool.execute(Map<String,String>) ← 工具实现直接使用解析好的参数
    ↓
Observation 字符串              ← 结果追加到对话历史，LLM 继续推理
```

---

## 核心组件

```
com.helloagents.tools/
├── Tool.java               # 工具接口
├── ToolCall.java           # 单次工具调用（含参数解析）
├── ToolRegistry.java       # 工具注册表 + 调用分发
├── ToolParameter.java      # 参数元信息（供 prompt 注入）
├── Toolkit.java            # 工具集接口（一组相关工具的工厂）
└── ToolkitLoaderTool.java  # 元工具：让 Agent 按需加载 Toolkit
```

---

## 工具调用格式

LLM 在回复中通过以下格式调用工具：

```
[TOOL_CALL:tool_name:{"param1":"value1","param2":123}]
```

- `tool_name` — 工具名称，与 `Tool.name()` 一致
- 参数块 — 始终为 JSON 对象，由 `ToolCall.parse()` 解析为 `Map<String, String>`
- 数字值会被自动转成字符串，工具内部按需转型

**示例**

```
[TOOL_CALL:calculate:{"expression":"(3 + 5) * 2"}]
[TOOL_CALL:memory_add:{"type":"semantic","content":"Java 运行在 JVM 上","importance":0.9}]
[TOOL_CALL:load_toolkit:{"toolkit":"memory"}]
```

---

## Tool 接口

```java
public interface Tool {
    String name();                           // 工具唯一名称
    String description();                    // 一行说明，注入 system prompt
    default ToolParameter parameters() { … } // 参数元信息（可选，增强 prompt）
    String execute(Map<String, String> params); // 执行逻辑，直接读 params.get("key")
}
```

### 实现示例

```java
public class UpperCaseTool implements Tool {

    @Override public String name()        { return "uppercase"; }
    @Override public String description() { return "Convert text to uppercase."; }

    @Override
    public ToolParameter parameters() {
        return ToolParameter.of(
            ToolParameter.Param.required("text", "Text to convert", "string")
        );
    }

    @Override
    public String execute(Map<String, String> params) {
        String text = params.getOrDefault("text", "");
        if (text.isBlank()) return "Error: 'text' is required.";
        return text.toUpperCase();
    }
}
```

参数已由框架解析完毕，实现中**不需要**处理 JSON。

---

## ToolCall

`ToolCall` 是 `parseToolCalls()` 的输出单元，包含三个字段：

```java
public record ToolCall(
    String toolName,    // 工具名
    String parameters,  // 原始 JSON 字符串
    String original     // 原始标记文本（用于 SimpleAgent 清理回复）
) {
    // 将 parameters 解析为 key→value Map
    public Map<String, String> parsedParams() { … }

    // 静态：解析任意 JSON 字符串
    public static Map<String, String> parse(String input) { … }
}
```

`ToolCall.parse()` 是参数解析的唯一入口，由 Jackson 完成 JSON → `Map<String, String>` 转换，所有值统一为字符串。

---

## ToolRegistry

注册表负责管理工具、生成 prompt 描述、解析调用、分发执行。

### 注册

```java
ToolRegistry registry = new ToolRegistry();

// 实现了 Tool 接口的类
registry.register(new CalculatorTool());

// Lambda 快捷注册（无需实现 Tool）
registry.register("greet", "Greets the user",
    ToolParameter.of(Param.required("name", "Name to greet", "string")),
    params -> "Hello, " + params.getOrDefault("name", "there") + "!");
```

### 解析调用

```java
// 从 LLM 回复文本中提取所有工具调用
List<ToolCall> calls = registry.parseToolCalls(response);
```

正则模式：`\[TOOL_CALL:([^:]+):(\{[^}]*\}|[^]]+)]`
- `\{[^}]*\}` 匹配 JSON 参数块
- `[^]]+` 兜底匹配普通字符串

### 执行

```java
// 推荐：直接传入 ToolCall，内部调用 call.parsedParams()
String result = registry.execute(call);

// 便捷重载：传字符串（测试 / Demo 用）
String result = registry.execute("calculate", "{\"expression\":\"2+3\"}");
```

### Prompt 描述

```java
// 生成注入 system prompt 的工具列表
String desc = registry.describe();
```

输出格式：

```
- calculate: Evaluate a mathematical expression and return the numeric result.
  - expression (string, required): Mathematical expression to evaluate, e.g. (3 + 5) * 2
- memory_add: Store a new memory entry and return its ID.
  - type (string, required): Memory type: perceptual | working | episodic | semantic
  - content (string, required): The content to remember
  - importance (number, optional): Relevance weight 0.0-1.0, default 0.5
```

---

## Toolkit 接口

`Toolkit` 将一组共享底层资源的工具打包为可复用单元：

```java
public interface Toolkit {
    String name();          // 标识符，如 "memory"、"rag"
    String description();   // 一行描述
    List<Tool> getTools();  // 所有工具

    default void registerAll(ToolRegistry registry) {
        getTools().forEach(registry::register);
    }
}
```

**已有实现**

| Toolkit | 工具数 | 描述 |
|---------|--------|------|
| `MemoryToolkit` | 9 | 记忆存取、搜索、遗忘、整合 |
| `RagToolkit` | 3 | 文档索引、语义搜索、问答 |

---

## ToolkitLoaderTool（按需加载）

元工具，让 Agent 在运行时自主加载所需的工具集，而无需在启动时注册全部工具。

```java
ToolRegistry registry = new ToolRegistry();
registry.register(new CalculatorTool());  // 永久可用
registry.register(new ToolkitLoaderTool(registry)
    .addAvailable("memory", MemoryToolkit::new)
    .addAvailable("rag",    () -> new RagToolkit(embeddingModel, kb, llm)));
```

Agent 工作流：

```
1. Agent 发现需要 memory_add，但该工具尚未加载
2. Agent 调用 load_toolkit[{"toolkit":"list"}] 查询可用 Toolkit
3. Agent 调用 load_toolkit[{"toolkit":"memory"}] 加载
4. ToolkitLoaderTool 调用 MemoryToolkit::new，将 9 个工具注册进 registry
5. Agent 在同一次对话中直接使用 memory_add
```

**Observation 示例**

```
Loaded toolkit 'memory': Tools for storing, searching, updating, and managing agent memory
New tools now available: memory_add, memory_update, memory_remove, memory_clear,
                         memory_search, memory_summary, memory_stats, memory_forget, memory_consolidate
```

重复加载幂等，返回 `Toolkit 'memory' is already loaded.`

---

## 集成进 Agent

### SimpleAgent

`SimpleAgent` 通过外部传入的 `ToolRegistry` 共享同一实例，支持 `ToolkitLoaderTool` 动态扩展：

```java
ToolRegistry registry = new ToolRegistry();
registry.register(new CalculatorTool());
registry.register(new ToolkitLoaderTool(registry)
    .addAvailable("memory", MemoryToolkit::new));

SimpleAgent agent = new SimpleAgent("DynamicAgent", llm,
    "You are a helpful assistant.", registry);

agent.run("Calculate 17 * 24 and store the result in memory.");
```

工具描述注入 system prompt 的位置：

```
## Available Tools
- calculate: Evaluate a mathematical expression and return the numeric result.
  ...
- load_toolkit: Add more tools at runtime by loading a toolkit by name. ...

## Tool Call Format
When you need to use a tool, embed this exact tag in your response:
`[TOOL_CALL:tool_name:{"param":"value"}]`

Always pass parameters as a JSON object. Examples:
- `[TOOL_CALL:calculate:{"expression":"2 + 3 * 4"}]`
- `[TOOL_CALL:memory_add:{"type":"semantic","content":"hello world","importance":0.9}]`
```

### ReActAgent

`ReActAgent` 在 Thought/Action/Observation 循环中使用工具，工具调用出现在 Action 行：

```
Thought: I need to calculate 7 * 5 first.
Action: [TOOL_CALL:calculate:{"expression":"7 * 5"}]
Observation: 35
Thought: The area is 35. Now multiply by 3.
Action: [TOOL_CALL:calculate:{"expression":"35 * 3"}]
Observation: 105
Thought: The final result is 105.
Action: Finish
```

---

## System Prompt 格式说明

两个 Agent 在 system prompt 里均明确约定：

```
Always pass parameters as a JSON object. Examples:
- `[TOOL_CALL:calculate:{"expression":"2 + 3 * 4"}]`
- `[TOOL_CALL:memory_add:{"type":"semantic","content":"hello world","importance":0.9}]`
- `[TOOL_CALL:load_toolkit:{"toolkit":"memory"}]`
```

LLM 应始终输出 JSON 参数；框架在 `parseToolCalls()` 中正则提取后，由 `ToolCall.parse()` 统一解析，工具实现无需感知 JSON。

---

## 扩展：自定义 Tool

**最简实现**（单参数工具）：

```java
public class EchoTool implements Tool {
    @Override public String name()        { return "echo"; }
    @Override public String description() { return "Echo the input text back."; }

    @Override
    public ToolParameter parameters() {
        return ToolParameter.of(
            ToolParameter.Param.required("text", "Text to echo", "string")
        );
    }

    @Override
    public String execute(Map<String, String> params) {
        return params.getOrDefault("text", "");
    }
}
```

**自定义 Toolkit**（共享底层资源）：

```java
public class MyToolkit implements Toolkit {
    private final MyService service;
    private final List<Tool> tools;

    public MyToolkit(MyService service) {
        this.service = service;
        this.tools = List.of(
            new MyReadTool(service),
            new MyWriteTool(service)
        );
    }

    @Override public String name()        { return "my_toolkit"; }
    @Override public String description() { return "Custom tools backed by MyService."; }
    @Override public List<Tool> getTools() { return tools; }
}
```

注册：

```java
registry.register(new ToolkitLoaderTool(registry)
    .addAvailable("my_toolkit", () -> new MyToolkit(new MyService())));
```
