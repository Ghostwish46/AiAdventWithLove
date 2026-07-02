# День 17: Первый MCP-инструмент (AniList)

MCP-сервер в отдельном модуле `mcpServer` + вызов tool из приложения и агента.

## Архитектура (вариант A)

```
AiAgentApp/
├── mcpServer/       ← AniList MCP server (JVM, отдельный процесс)
├── shared/          ← MCP client + agent + UI
├── androidApp/
└── desktopApp/
```

- **Сервер не в APK** — запускается локально на Mac
- **Приложение** — MCP-клиент, подключается по HTTP URL из настроек
- **Агент** — LLM tool calling → `callTool` → MCP server → AniList GraphQL

## MCP-сервер: инструмент `search_anime`

| Поле | Значение |
|------|----------|
| name | `search_anime` |
| search | string, required — название аниме |
| perPage | integer, optional (1–10, default 5) |
| API | `POST https://graphql.anilist.co` |

## Запуск (локально, без облака)

### 1. Запустить MCP-сервер

```bash
cd AiAgentApp
./gradlew :mcpServer:run
```

Сервер слушает `http://127.0.0.1:3000/mcp`.

### 2. Проверить клиентом (консоль)

```bash
# Terminal 2
./gradlew :desktopApp:runMcpDemo --args="--url http://127.0.0.1:3000/mcp"
```

Ожидание:

```
Tools (1):
  - search_anime: Search anime by title using AniList GraphQL API...
```

### 3. Проверить в приложении

**Настройки → MCP Tools**

1. При первом запуске создаётся профиль «Default» (миграция из старых настроек)
2. Нажмите **+** чтобы добавить MCP-сервер или отредактируйте существующий
3. **Refresh** (в toolbar или на карточке сервера) — загрузить tools через `listTools`
4. У каждого tool: **Switch** «Для агента», **Параметры** (inputSchema), **Тест**
5. Summary вверху: сколько серверов, tools и сколько активно для агента

### 4. Проверить в чате

Запустите MCP-сервер и приложение. В чате:

> Найди популярные аниме про ниндзя

Агент вызовет `search_anime`, получит данные AniList и сформирует ответ. В боковой панели чата отображается статус `MCP: search_anime`.

## Android emulator

MCP-сервер на хост-машине, URL в настройках:

```
http://10.0.2.2:3000/mcp
```

## MCP Tools Manager (multi-server)

Экран **Настройки → MCP Tools** (`McpToolsScreen`):

| Функция | Описание |
|---------|----------|
| Несколько серверов | Add / Edit / Delete профилей подключения |
| Refresh | Toolbar + per-server; статус Online/Offline |
| Switch сервера | Включить/выключить все tools сервера |
| Switch tool | Включить/выключить tool для агента (persisted) |
| Параметры | Expandable inputSchema (имя, type, required) |
| Тест | Generic JSON args → `callTool` |
| Агент | Только `server.enabled && tool.enabled && ONLINE` |

Данные: `mcp_catalog.json`. Tool key для агента: `{serverId}::{toolName}`.

## Файлы

| Модуль | Файлы |
|--------|-------|
| mcpServer | `AniListClient.kt`, `AniListMcpServer.kt`, `Main.kt` |
| shared | `McpRegistry.kt`, `McpCatalog.kt`, `McpToolsScreen.kt`, `McpToolsViewModel.kt` |
| shared | `SimpleAgent.kt` (tool loop), `ChatViewModel.kt`, `McpToolExecutorImpl.kt` |

## Критерии успеха

- [x] Свой MCP-сервер вокруг API (AniList)
- [x] Регистрация tool + inputSchema + CallToolResult
- [x] Подключение к агенту (LLM tool calling loop)
- [x] Вызов из приложения (Settings test + Chat)
- [x] Использование результата в ответе LLM

## Зависимости

- `io.modelcontextprotocol:kotlin-sdk-server:0.10.0` (mcpServer)
- `io.modelcontextprotocol:kotlin-sdk-client:0.10.0` (shared, уже был с Day 16)
