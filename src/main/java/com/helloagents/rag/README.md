# rag 包

RAG（Retrieval-Augmented Generation，检索增强生成）系统。在 LLM 生成前注入相关文档片段，解决知识截止和私有知识库问题。可作为独立能力集成进任意 Agent。

## 架构

```
tool/          用户层   RagToolkit（rag_add / rag_search）
  ↓
app/           应用层   RagSystem（统一门面）
  ↓
pipeline/      处理层   IndexPipeline（索引）/ QueryPipeline（查询）
  ↓
core/          存储层   KnowledgeBase = DocumentStore + VectorStore
  ↓
embedding/     基础层   EmbeddingModel（OpenAI text-embedding-*）
```

## 包结构

```
rag/
├── core/                       # 数据类型 + 接口
│   ├── Document.java           # record：原始文档（id, source, content, status）
│   ├── DocumentStatus.java     # enum：PENDING / INDEXED / FAILED / DELETED
│   ├── Chunk.java              # record：文本片段（含 float[] embedding）
│   ├── SearchResult.java       # record：检索结果（chunk + score）
│   ├── Embedding.java          # record：向量化结果（vector, modelId, tokenCount）
│   ├── ModelInfo.java          # record：模型属性（dimension, maxInputTokens）
│   ├── EmbeddingModel.java     # 接口：embed / embedBatch
│   ├── DocumentStore.java      # 接口：文档 CRUD + 状态管理
│   ├── VectorStore.java        # 接口：chunk 存储 + 向量检索
│   └── KnowledgeBase.java      # 统一封装 DocumentStore + VectorStore
│
├── document/                   # 解析 + 分块
│   ├── DocumentParser.java     # 接口：parse(Path/bytes)
│   ├── TextSplitter.java       # 接口：split(text)
│   ├── parser/
│   │   └── TikaDocumentParser.java   # Apache Tika 通用解析器
│   └── splitter/
│       ├── FixedSizeSplitter.java    # 固定字符数 + 重叠窗口
│       └── RecursiveSplitter.java    # 递归分隔符（段落→句子→字符）
│
├── embedding/
│   ├── OpenAiEmbeddingModel.java     # OpenAI embedding API 实现
│   └── EmbeddingException.java       # 含 ErrorKind 枚举
│
├── store/
│   ├── InMemoryDocumentStore.java    # 内存文档库
│   └── InMemoryVectorStore.java      # 内存向量库（余弦相似度线性扫描）
│
├── retrieval/
│   ├── Retriever.java                # 接口：retrieve(query, topK, filter)
│   └── VectorRetriever.java          # 向量检索 + minScore 阈值 + 元数据过滤
│
├── pipeline/
│   ├── IndexPipeline.java            # 解析 → 分块 → 向量化 → KnowledgeBase
│   └── QueryPipeline.java            # embed(query) → searchChunks → score 过滤
│
├── app/
│   └── RagSystem.java                # 门面：addDocument / addFile / search / listDocuments
│
└── tool/
    ├── RagAddTool.java               # Tool: rag_add
    ├── RagSearchTool.java            # Tool: rag_search
    └── RagToolkit.java               # Toolkit 工厂
```

## 数据流

**索引**
```
addDocument(source, text) / addFile(path)
  → TikaDocumentParser（解析 PDF/DOCX/... → 纯文本）
  → TextSplitter（分块）
  → EmbeddingModel.embedBatch（批量向量化）
  → KnowledgeBase.saveChunk × N
  → Document 状态置为 INDEXED
```

**查询**
```
search(query, topK)
  → EmbeddingModel.embed(query)
  → KnowledgeBase.searchChunks（余弦相似度）
  → score 过滤（minScore）
  → List<SearchResult>
```

## 快速上手

**最简用法**
```java
var embeddingModel = OpenAiEmbeddingModel.fromEnv();
var kb  = new KnowledgeBase(new InMemoryDocumentStore(), new InMemoryVectorStore());
var rag = new RagSystem(embeddingModel, kb);

// 索引
rag.addDocument("java-intro.txt", "Java 是面向对象语言，运行在 JVM 上...");
rag.addFile(Path.of("report.pdf"));   // Tika 自动识别格式

// 搜索
List<SearchResult> hits = rag.search("JVM 内存管理", 3);
hits.forEach(r -> System.out.printf("[%.3f] %s%n", r.score(), r.chunk().content()));

// 管理
System.out.println(rag.documentCount() + " docs, " + rag.chunkCount() + " chunks");
rag.removeDocument(docId);
```

**集成进 Agent（推荐）**
```java
var kb      = new KnowledgeBase(new InMemoryDocumentStore(), new InMemoryVectorStore());
var toolkit = new RagToolkit(embeddingModel, kb);

var agent = new SimpleAgent(llm);
toolkit.getTools().forEach(agent::addTool);   // 注册 rag_add + rag_search

agent.run("将 report.pdf 加入知识库，然后回答 JVM 垃圾回收的问题");
```

**高级检索（minScore + 文档过滤）**
```java
var pipeline = new QueryPipeline(embeddingModel, kb, /*topK=*/5, /*minScore=*/0.75);

// 只检索指定文档的 chunk
List<SearchResult> hits = pipeline.query("GC 算法", 3,
        Map.of("documentId", "abc12345"));
```

## 内置工具

| Tool | 参数 | 说明 |
|------|------|------|
| `rag_add` | `content` + `source`（文本）或 `file`（路径） | 添加文档到知识库 |
| `rag_search` | `query`，可选 `topk` | 语义搜索，返回相关片段 |

## 文档解析支持（Apache Tika）

| 类型 | 格式 |
|------|------|
| 文档 | PDF、DOC/DOCX、ODT、RTF |
| 表格 | XLS/XLSX、ODS、CSV |
| 演示 | PPT/PPTX |
| 网页 | HTML、XHTML、XML |
| 文本 | TXT、MD、JSON |
| 图片 | JPG、PNG、TIFF（OCR 需安装 Tesseract） |
| 压缩 | ZIP、EPUB |

## 环境变量

| 变量名 | 说明 |
|--------|------|
| `OPENAI_API_KEY` 或 `LLM_API_KEY` | OpenAI API Key（必需） |
| `OPENAI_BASE_URL` 或 `LLM_BASE_URL` | 自定义 API 端点（可选） |
| `EMBEDDING_MODEL` | 嵌入模型，默认 `text-embedding-3-small` |

## 扩展

替换 `InMemoryVectorStore` / `InMemoryDocumentStore` 可无缝接入持久化存储：

```java
// 换成 Chroma / Qdrant / pgvector 只需实现对应接口
VectorStore  vs = new ChromaVectorStore(...);
DocumentStore ds = new PostgresDocumentStore(...);
var kb = new KnowledgeBase(ds, vs);
// 上层 RagSystem / RagToolkit 代码零改动
```

其他扩展方向：
- 混合检索 — 实现 `Retriever` 结合 BM25 + 向量检索
- 重排序 — 在 `QueryPipeline` 后接 Cross-Encoder 精排
- 自定义分块 — 实现 `TextSplitter`（如按 Markdown 标题分块）