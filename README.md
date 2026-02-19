# Ai Agent App

Android-приложение для отправки запросов в DeepSeek API и отображения ответов нейросети.

## Требования

- Android Studio Ladybug (2024.2.1) или новее (или AGP 8.2 + Kotlin 1.9)
- JDK 17
- minSdk 24

## API-ключ (вариант B)

1. Получите API-ключ на [platform.deepseek.com](https://platform.deepseek.com/api_keys).
2. В корне проекта создайте или отредактируйте файл `local.properties` (он в `.gitignore` и не коммитится).
3. Добавьте строку:
   ```
   DEEPSEEK_API_KEY=sk-ваш-ключ
   ```
4. Пересоберите проект.

Пока ключа нет — в коде используется заглушка `test-key-replace-me` (запросы к API будут возвращать ошибку авторизации).

## Сборка и запуск

- Откройте папку проекта в Android Studio и выполните **Sync Project with Gradle Files**. Android Studio скачает Gradle и при необходимости создаст `gradle/wrapper/gradle-wrapper.jar`.
- Соберите и запустите на эмуляторе или устройстве: **Run** (Shift+F10) или через меню **Build → Run**.
- Из командной строки: `./gradlew installDebug` (нужен полный Gradle Wrapper — при его отсутствии сначала откройте проект в Android Studio для синхронизации).

## Стек

- Kotlin, Jetpack Compose, Material3
- Retrofit + OkHttp, Gson
- Coroutines, ViewModel

## API

- [DeepSeek API](https://api-docs.deepseek.com/) — модель `deepseek-chat`, endpoint `POST https://api.deepseek.com/chat/completions`.
