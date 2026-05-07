# 智能旅行助手 · 前端

第 13 章 Demo 的 React 前端，与后端 `TravelAssistantApp` 配合使用。

## 技术栈

- **React 18 + TypeScript**
- **Vite 5** — 开发服务器与构建
- **Tailwind CSS** — CDN 引入，无需构建配置
- **Leaflet + OpenStreetMap** — 免 Key 地图，可替换为高德地图

## 快速启动

```bash
# 1. 确保后端已启动（端口 8090）
mvn exec:java -Dexec.mainClass="com.helloagents.demo.travel.TravelAssistantApp" \
  -Dspring.profiles.active=local

# 2. 安装依赖并启动前端
npm install
npm run dev

# 3. 访问
open http://localhost:5173
```

## 目录结构

```
src/
├── App.tsx                  主布局（三栏：表单 | 地图 | 行程+预算）
├── types/travel.ts          与后端 Java record 对齐的 TS 类型
├── api/travelApi.ts         fetch + ReadableStream 解析 SSE
├── hooks/useTravelPlan.ts   规划状态管理（loading / logs / plan）
└── components/
    ├── TravelForm.tsx        目的地、日期、预算、偏好标签输入
    ├── StreamLog.tsx         实时 Agent 执行日志面板
    ├── ItineraryPanel.tsx    分天行程卡片（景点 / 餐饮 / 酒店）
    ├── MapView.tsx           Leaflet 地图（标记 + 路线）
    └── BudgetPanel.tsx       预算分项条形图
```

## 数据流

```
用户填写表单
  → POST /api/travel/plan
  → SSE 流式接收事件
      thinking   → StreamLog 蓝色条目
      tool_result → StreamLog 橙色条目
      token      → StreamLog 灰色文本（LLM 输出片段）
      complete   → 解析 TravelPlan JSON → 渲染地图 / 行程 / 预算
```

## 替换为高德地图

当前使用 Leaflet + OpenStreetMap，无需 API Key。
获得高德 Key 后，修改 `src/components/MapView.tsx`：

```ts
// 替换 tileLayer 部分
L.tileLayer(
  `https://webrd0{s}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}`,
  { subdomains: ['1', '2', '3', '4'] }
).addTo(map)
```

同时在 `index.html` 中加载高德 JS SDK：
```html
<script src="https://webapi.amap.com/maps?v=2.0&key=YOUR_KEY"></script>
```

## 环境变量

前端本身不直接使用任何 API Key，所有密钥均由后端管理。
参考后端目录下的 `src/main/resources/application-local.properties`。