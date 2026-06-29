# День 16: Подключение MCP

Минимальный MCP-клиент на официальном [kotlin-sdk-client](https://github.com/modelcontextprotocol/kotlin-sdk): устанавливает соединение с внешним MCP-сервером и выводит список доступных инструментов.

## Что реализовано

- `McpConnectionConfig` — парсинг `--stdio`, `--url`, env `MCP_STDIO_COMMAND` / `MCP_URL`
- `McpToolExplorer` — connect + `listTools()` через stdio или Streamable HTTP
- `McpDemoMain` — runnable demo для desktop JVM

## UI в приложении

**Настройки → MCP**

1. Выберите тип подключения: **HTTP URL** или **Stdio**
2. Укажите URL или команду запуска MCP-сервера
3. Нажмите **«Проверить подключение»**
4. Ниже появится список tools с названием и описанием

Настройки сохраняются в `mcp_settings.txt` между запусками.

## Как проверить (консоль)

### A. Stdio (дефолт) — spawn MCP через npx

**Требования:** Node.js + npx в PATH.

```bash
cd AiAgentApp
./gradlew :desktopApp:runMcpDemo
```

Клиент запускает `@modelcontextprotocol/server-everything` и выводит tools (echo, add и др.).

Если npm registry недоступен (корпоративный proxy), укажите публичный registry:

```bash
NPM_CONFIG_REGISTRY=https://registry.npmjs.org ./gradlew :desktopApp:runMcpDemo
```

Кастомная команда:

```bash
./gradlew :desktopApp:runMcpDemo --args="--stdio npx -y @modelcontextprotocol/server-everything"
```

Или через env:

```bash
export MCP_STDIO_COMMAND='npx -y @modelcontextprotocol/server-everything'
./gradlew :desktopApp:runMcpDemo
```

### B. HTTP — внешний сервер уже запущен

```bash
# Terminal 1: любой HTTP MCP server на :3000
# Terminal 2:
./gradlew :desktopApp:runMcpDemo --args="--url http://127.0.0.1:3000/mcp"
```

Или:

```bash
export MCP_URL='http://127.0.0.1:3000/mcp'
./gradlew :desktopApp:runMcpDemo
```

## Ожидаемый вывод

```
Connecting via stdio: npx -y @modelcontextprotocol/server-everything
MCP connected
Tools (13):
  - echo: Echoes back the input string
  - get-sum: Returns the sum of two numbers
  ...
```

## Критерии успеха

- Соединение устанавливается без exception
- Список инструментов не пустой, у каждого есть name

## Файлы

- `shared/.../mcp/McpConnectionConfig.kt` — конфиг подключения
- `shared/.../mcp/McpToolExplorer.kt` — MCP client wrapper
- `shared/.../mcp/McpToolInfo.kt` — модель инструмента
- `desktopApp/.../McpDemoMain.kt` — demo entry point

## Зависимости

- `io.modelcontextprotocol:kotlin-sdk-client:0.10.0`
- `io.ktor:ktor-client-cio`, `io.ktor:ktor-sse` — HTTP transport
- Kotlin 2.1.0 (требование MCP SDK 0.10)
