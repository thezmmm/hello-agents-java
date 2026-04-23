# RAG 系统设计文档

## 概述

RAG（Retrieval-Augmented Generation，检索增强生成）系统通过在 LLM 生成前注入相关上下文，有效解决知识截止、私有知识库等问题。本模块以五层架构实现，可作为独立能力集成进任意 Agent。

系统使用 **Apache Tika** 作为统一文档解析引擎，支持 PDF、Word/Excel/PowerPoint、HTML、纯文本、图片等数百种格式，输出统一的纯文本内容进入后续流程。

```
任意格式文档（PDF / DOCX / XLSX / HTML / 图片 / ...）
        ↓
Apache Tika 自动识别格式 + 提取正文与元数据
        ↓
智能分块 → 向量化（Embedding）→ KnowledgeBase 存储
        ↓
向量检索 → 上下文注入 → LLM 生成答案
```

---

## 五层架构

```
用户层：RagToolkit（rag_add / rag_search / rag_ask）
  ↓
应用层：RagSystem（统一门面：管理 + 搜索 + 问答）
  ↓
处理层：IndexPipeline（索引）/ QueryPipeline（查询）
  ↓
存储层：KnowledgeBase（统一封装 DocumentStore + VectorStore）
  ↓
基础层：EmbeddingModel、LlmClient、InMemory / 持久化实现
```

---

## 包结构

```
com.helloagents.rag/
├── core/                          # 核心数据类 + 接口
│   ├── Document.java              # 原始文档 record（DB 友好字段类型）
│   ├── DocumentStatus.java        # 文档状态枚举：PENDING / INDEXED / FAILED / DELETED
│   ├── Chunk.java                 # 分块 record（含 embedding float[]）
│   ├── SearchResult.java          # 检索结果 record（chunk + score）
│   ├── Embedding.java             # 嵌入结果 record（vector + modelId + tokenCount）
│   ├── ModelInfo.java             # 模型静态属性 record（modelId + dimension + maxInputTokens）
│   ├── EmbeddingModel.java        # 嵌入模型接口
│   ├── VectorStore.java           # 向量库接口
│   ├── DocumentStore.java         # 文档库接口（含状态管理）
│   └── KnowledgeBase.java         # 统一封装两个 store，是唯一存储入口
│
├── document/                      # 处理层
│   ├── DocumentParser.java        # 文档解析接口
│   ├── TextSplitter.java          # 分块策略接口
│   ├── parser/
│   │   └── TikaDocumentParser.java  # Apache Tika 通用解析器
│   └── splitter/
│       ├── FixedSizeSplitter.java   # 固定字符数分块（含重叠窗口）
│       └── RecursiveSplitter.java   # 递归分隔符分块（段落→句子→字符）
│
├── embedding/                     # 嵌入模型实现
│   ├── OpenAiEmbeddingModel.java  # OpenAI text-embedding-3-small/large
│   └── EmbeddingException.java    # 嵌入异常（含 ErrorKind 枚举）
│
├── store/                         # 存储实现
│   ├── InMemoryVectorStore.java   # 内存向量库（余弦相似度线性扫描）
│   └── InMemoryDocumentStore.java # 内存文档库（状态过滤）
│
├── retrieval/                     # 检索层
│   ├── Retriever.java             # 检索接口（支持 topK / filter）
│   └── VectorRetriever.java       # 向量检索实现（含 minScore 阈值、元数据过滤）
│
├── pipeline/                      # 处理管道
│   ├── IndexPipeline.java         # 索引：解析 → 分块 → 向量化 → KnowledgeBase
│   └── QueryPipeline.java         # 查询：校验 → 检索 → 相似度过滤
│
├── app/                           # 应用层
│   └── RagSystem.java             # 统一门面：管理 + 搜索 + 问答
│
└── tool/                          # 用户层
    ├── RagAddTool.java            # Tool: rag_add（文本 / 文件）
    ├── RagSearchTool.java         # Tool: rag_search
    ├── RagAskTool.java            # Tool: rag_ask
    └── RagToolkit.java            # 工厂：创建 RagSystem + 注册工具
```

---

## 核心数据模型

### Document

```java
public record Document(
    String id,              // VARCHAR(8)，UUID 短形式
    String source,          // TEXT，文件路径或名称
    String content,         // TEXT，提取后的纯文本
    String metadata,        // TEXT / JSON，通过 metadataMap() 反序列化
    long createdAt,         // BIGINT，epoch millis
    DocumentStatus status   // VARCHAR，存 name()
)
```

**状态流转**：

```
of() 创建 → PENDING → 索引成功 → INDEXED
                    → 索引失败 → FAILED
withStatus(DELETED) → DELETED（软删除，不参与检索）
```

### Embedding

```java
public record Embedding(
    float[] vector,     // 嵌入向量
    String modelId,     // 生成模型 ID
    int tokenCount      // 消耗 token 数（-1 表示不可用）
)
```

### SearchResult

```java
public record SearchResult(Chunk chunk, double score)
```

---

## 核心数据流

### 索引流程

```
addDocument(source, content)          addFile(Path filePath)
        ↓                                      ↓
TikaDocumentParser.parse()      TikaDocumentParser.parseFile()
        ↓                                      ↓
              Document(PENDING, content, metadata)
                              ↓
                    KnowledgeBase.saveDocument()
                              ↓
                    TextSplitter.split()  →  List<String>
                              ↓
               EmbeddingModel.embedBatch()  →  List<Embedding>
                              ↓
              KnowledgeBase.saveChunk() × N
                              ↓
              KnowledgeBase.updateDocumentStatus(INDEXED)
```

### 查询流程

```
search(query, topK) / ask(question, topK)
        ↓
EmbeddingModel.embed(query).vector()
        ↓
KnowledgeBase.searchChunks(vector, topK)
        ↓
filter(score >= minScore) + filter(metadata)
        ↓
List<SearchResult>
        ↓（ask 场景）
buildContext() → augmentedPrompt → LlmClient.chat() → 答案
```

---

## 关键接口

### EmbeddingModel

```java
public interface EmbeddingModel {
    ModelInfo modelInfo();                          // 静态属性（维度、最大 token）
    Embedding embed(String text);                   // 抛 EmbeddingException
    List<Embedding> embedBatch(List<String> texts); // 返回顺序与输入严格一一对应
    CompletableFuture<List<Embedding>> embedBatchAsync(List<String> texts); // 默认包装同步
    default int dimension()      { return modelInfo().dimension(); }
    default int maxInputTokens() { return modelInfo().maxInputTokens(); }
}
```

### EmbeddingException.ErrorKind

| Kind | 触发场景 | 建议处理 |
|------|----------|----------|
| `NETWORK_ERROR` | 连接超时、DNS 失败 | 重试 |
| `RATE_LIMITED` | HTTP 429 | 指数退避 |
| `TOKEN_LIMIT_EXCEEDED` | 输入超过模型限制 | 减小分块大小 |
| `AUTHENTICATION_ERROR` | API Key 无效 | 停止，检查配置 |
| `UNKNOWN` | 其他 | 记录日志 |

### Retriever

```java
public interface Retriever {
    int DEFAULT_TOP_K = 3;
    List<SearchResult> retrieve(String query, int topK, Map<String, String> filter);
    default List<SearchResult> retrieve(String query, int topK) { ... }
    default List<SearchResult> retrieve(String query)           { ... }
}
```

`filter` 支持的 key：
- `"documentId"` — 只检索指定文档的 chunk
- 其他 key — 匹配 chunk 元数据字段，多条件 AND

### DocumentStore

```java
public interface DocumentStore {
    void save(Document document);
    Optional<Document> get(String id);
    boolean updateStatus(String id, DocumentStatus status);  // 状态转换专用
    boolean delete(String id);                               // 硬删除
    List<Document> listAll();                                // 排除 DELETED
    List<Document> listByStatus(DocumentStatus status);
    int size();                                              // 排除 DELETED
}
```

### KnowledgeBase

```java
// 文档
void saveDocument(Document)
Optional<Document> getDocument(String id)
boolean updateDocumentStatus(String id, DocumentStatus)
boolean deleteDocument(String id)
List<Document> listDocuments()          // 排除 DELETED
List<Document> listByStatus(DocumentStatus)
int documentCount()

// Chunk
void saveChunk(Chunk)
List<SearchResult> searchChunks(float[] vector, int topK)
void deleteChunksByDocument(String documentId)
int chunkCount()

// 组合
boolean remove(String documentId)       // 删文档 + 删 chunk
void clear()                            // 清空所有状态的文档和 chunk
```

---

## 相似度计算

余弦相似度：`similarity(A, B) = (A · B) / (|A| × |B|)`，取值 [-1, 1]。

`InMemoryVectorStore` 暴力线性扫描，适合万级 chunk 以内场景。规模更大时替换 `VectorStore` 实现（Chroma / Qdrant / pgvector），上层代码零改动。

---

## 工具调用格式

遵循项目统一的 `[TOOL_CALL:tool_name:parameters]` 格式。

| 工具名 | 参数格式 | 说明 |
|--------|----------|------|
| `rag_add` | `source=<名称>\|content=<文本>` | 添加文本到知识库 |
| `rag_add` | `file=<路径>` | 索引本地文件（PDF/DOCX/XLSX/HTML 等） |
| `rag_search` | `query=<问题>\|topk=5` | 语义搜索，返回相关段落 |
| `rag_ask` | `question=<问题>\|topk=3` | 检索增强问答，返回 LLM 生成答案 |

---

## 配置与环境变量

| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| `LLM_API_KEY` | OpenAI API Key（必需） | 无 |
| `LLM_BASE_URL` | 自定义 API 端点 | `https://api.openai.com/v1` |
| `LLM_MODEL` | 对话模型 | `gpt-4o` |
| `EMBEDDING_MODEL` | 嵌入模型 | `text-embedding-3-small` |

### Apache Tika 支持的格式

| 类型 | 格式 |
|------|------|
| 文档 | PDF、DOC/DOCX、ODT、RTF |
| 表格 | XLS/XLSX、ODS、CSV |
| 演示 | PPT/PPTX、ODP |
| 网页 | HTML、HTM、XHTML、XML |
| 文本 | TXT、MD、RST、JSON |
| 图片 | JPG、PNG、GIF、BMP、TIFF、WEBP（OCR 需安装 Tesseract） |
| 压缩 | ZIP、EPUB |

---

## 使用示例

### 独立使用

```java
var embeddingModel = OpenAiEmbeddingModel.fromEnv();
var llm = LlmClient.fromEnv();
var kb  = new KnowledgeBase(new InMemoryDocumentStore(), new InMemoryVectorStore());
var rag = new RagSystem(embeddingModel, kb, llm);

// 索引
rag.addDocument("readme.txt", textContent);
rag.addFile(Path.of("report.pdf"));          // Tika 自动解析

// 搜索
List<SearchResult> hits = rag.search("JVM 内存模型", 3);

// 问答
String answer = rag.ask("Java 的垃圾回收机制是什么？");

// 管理
System.out.println(rag.documentCount() + " docs, " + rag.chunkCount() + " chunks");
rag.removeDocument(docId);
rag.clear();
```

### 集成进 Agent

```java
var kb      = new KnowledgeBase(new InMemoryDocumentStore(), new InMemoryVectorStore());
var toolkit = new RagToolkit(embeddingModel, kb, llm);
var agent   = new SimpleAgent(llm);
toolkit.getTools().forEach(agent::addTool);

agent.run("先将 report.pdf 加入知识库，再回答 JVM 内存模型的问题");
```

### 高级检索（minScore + filter）

```java
// 直接使用 QueryPipeline
var kb       = new KnowledgeBase(docStore, vectorStore);
var pipeline = new QueryPipeline(embeddingModel, kb, /*defaultTopK=*/5, /*minScore=*/0.75);

// 只检索某个文档的相关 chunk
List<SearchResult> results = pipeline.query("GC 算法", 3,
        Map.of("documentId", "abc12345"));
```

---

## 扩展方向

| 功能 | 说明 |
|------|------|
| 持久化向量库 | 实现 `VectorStore` 接入 Chroma / Qdrant / pgvector |
| 持久化文档库 | 实现 `DocumentStore` 接入 PostgreSQL / SQLite |
| OCR 支持 | 安装 Tesseract，Tika 自动调用进行图片文字识别 |
| 混合检索 | `VectorRetriever` + BM25，实现新的 `Retriever` 组合 |
| 重排序 | 在 `QueryPipeline` 后接 Cross-Encoder 精排 |
| 增量索引 | 文档更新时对比 hash，只重建变更 chunk |