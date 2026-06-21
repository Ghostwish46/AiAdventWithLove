# Ai Agent App

Kotlin Multiplatform чат с AI: **Android** и **Desktop (Mac)** из одной кодовой базы.

## Требования

- Android Studio Ladybug (2024.2.1) или новее
- JDK 17
- minSdk 24 (Android)

## Структура модулей

| Модуль | Назначение |
|--------|------------|
| `shared` | Общая логика, UI (Compose), API, сохранение диалогов |
| `androidApp` | Тонкая Android-оболочка (`MainActivity`) |
| `desktopApp` | Desktop-приложение для Mac |

Старый модуль `app/` больше не используется — можно удалить после проверки.

## API-ключи

В корне проекта в `local.properties` (не коммитится):

```
ROUTERAI_API_KEY=ваш-ключ-routerai
DEEPSEEK_API_KEY=sk-ваш-ключ-deepseek
```

**Android:** ключи подставляются через `BuildConfig` при сборке.

**Desktop:** приоритет — переменные окружения `ROUTERAI_API_KEY` / `DEEPSEEK_API_KEY`, затем `local.properties` в корне проекта. Данные чатов сохраняются в `~/.aiagentapp/conversations.json` (отдельно от Android).

## Сборка и запуск

### Первый запуск

Откройте папку `AiAgentApp` в Android Studio и выполните **Sync Project with Gradle Files**. Studio скачает Gradle и создаст `gradle/wrapper/gradle-wrapper.jar`, если его нет.

### Android

```bash
./gradlew :androidApp:installDebug
```

Или **Run** на конфигурации `androidApp`.

### Desktop (Mac)

```bash
./gradlew :desktopApp:run
```

Окно с тем же UI, нативная клавиатура — удобно для быстрого ввода текста без эмулятора.

Сборка DMG (опционально):

```bash
./gradlew :desktopApp:packageDmg
```

## Стек

- Kotlin Multiplatform, Compose Multiplatform, Material3
- Retrofit + OkHttp + Gson (JVM-слой)
- Navigation Compose (KMP), Lifecycle ViewModel (KMP)
- Стратегии контекста: FULL, SLIDING_WINDOW, FACTS_KV

## API

- [RouterAI](https://routerai.ru/) — основной endpoint в приложении
- [DeepSeek API](https://api-docs.deepseek.com/) — альтернативный endpoint
