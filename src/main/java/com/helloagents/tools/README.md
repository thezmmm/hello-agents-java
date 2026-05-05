# tools 包

工具系统，让 Agent 能够调用外部能力。

## 目录结构

```
tools/
├── Tool.java            # 工具接口
├── ToolParameter.java   # 参数 schema
├── Toolkit.java         # 工具集接口
├── ToolRegistry.java    # 注册与派发
└── builtin/             # 内置工具实现
    ├── CalculatorTool.java
    ├── FileReadTool.java
    ├── FileWriteTool.java
    ├── HttpTool.java
    ├── WebSearchTool.java
    ├── TerminalTool.java

```

## 核心抽象

### Tool — 工具接口

```java
public interface Tool {
    String name();                           // Agent 调用时使用的名称
    String description();                    // 展示给 LLM 的一行说明
    default ToolParameter parameters() {...} // 参数 schema，默认空
    String execute(Map<String, String> params);
}
```

`execute` 接收 `Map<String, String>`，参数由 LLM 的 function call JSON 解析而来，直接 `params.get("key")` 即可。返回值作为 Observation 回传给 LLM。

### ToolParameter — 参数 schema

```java
ToolParameter.of(
    Param.required("path",    "文件路径",         "string"),
    Param.optional("mode",    "overwrite/append", "string")
)
```

`Param.required` / `Param.optional` — 两个工厂方法，声明参数名、描述、类型。

### Toolkit — 工具集接口

将共享同一底层服务的多个工具打包，通过 `registerAll(registry)` 批量注册。
项目内已有实现：`MemoryToolkit`（memory 包）、`RagToolkit`（rag 包）。

### ToolRegistry — 注册与派发

```java
ToolRegistry registry = new ToolRegistry();

// 注册 Tool 实例
registry.register(new CalculatorTool());

// 注册 lambda（无需实现接口）
registry.register("greet", "Say hello", params ->
    "Hello, " + params.getOrDefault("name", "world") + "!");

// 注册 lambda + 参数 schema
registry.register("greet", "Say hello",
    ToolParameter.of(Param.optional("name", "名字", "string")),
    params -> "Hello, " + params.getOrDefault("name", "world") + "!");

// 执行
String result = registry.execute("calculate", Map.of("expression", "2+3"));
String result = registry.execute("calculate", "{\"expression\":\"2+3\"}"); // JSON 字符串重载
```

## 内置工具

| 工具 | tool name | 说明 | 环境变量 |
|------|-----------|------|---------|
| `CalculatorTool` | `calculate` | 数学表达式求值（递归下降解析器，无 eval） | — |
| `FileReadTool` | `file_read` | 读取本地文件，截断至 8000 字符 | — |
| `FileWriteTool` | `file_write` | 写入本地文件，支持 overwrite / append | — |
| `HttpTool` | `http_fetch` | HTTP GET / POST，响应体截断至 8000 字符（非 2xx 截断至 300 字符） | — |
| `WebSearchTool` | `web_search` | Tavily 搜索，含 LLM 摘要 + 来源列表 | `TAVILY_API_KEY` |
| `TerminalTool` | `terminal` | 执行只读 shell 命令（白名单 + 沙箱 + 超时） | — |

`FileReadTool`、`FileWriteTool`、`TerminalTool` 构造时可传入 `Path workspace` 限制访问范围。

## 如何注册工具给 Agent

```java
ReActAgent agent = new ReActAgent(llm);
agent.addTool(new CalculatorTool());
agent.addTool(new WebSearchTool(apiKey));
agent.addTool(new FileReadTool(workspace));
```

`addTool` 来自 `ToolSupport`（`AbstractAgent` 已实现），可随时在运行前动态添加。

## 自定义工具

实现 `Tool` 接口，放在业务包下（不需要放 `builtin/`）：

```java
public class TranslateTool implements Tool {

    @Override public String name() { return "translate"; }

    @Override public String description() {
        return "将文本翻译为目标语言。";
    }

    @Override public ToolParameter parameters() {
        return ToolParameter.of(
            Param.required("text",   "待翻译的文本",          "string"),
            Param.required("target", "目标语言，如 en / zh",   "string")
        );
    }

    @Override public String execute(Map<String, String> params) {
        String text   = params.getOrDefault("text", "");
        String target = params.getOrDefault("target", "en");
        // ... 调用翻译 API
        return translated;
    }
}
```

## 自定义 Toolkit

将多个共享同一服务的工具打包：

```java
public class SearchToolkit implements Toolkit {
    private final SearchService svc;

    public SearchToolkit(SearchService svc) { this.svc = svc; }

    @Override public String name()        { return "search"; }
    @Override public String description() { return "网页搜索工具集"; }

    @Override public List<Tool> getTools() {
        return List.of(
            new WebSearchTool(svc.apiKey()),
            new HttpTool()
        );
    }
}

// 注册
new SearchToolkit(service).registerAll(registry);
```
