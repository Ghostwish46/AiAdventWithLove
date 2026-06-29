# День 14+15: Инварианты и контролируемые переходы

## Инварианты (День 14)

- Глобальные блоки в **Настройки → Инварианты** (`invariants.json`)
- Блок = название + список правил (предпочтения и запреты в одном списке)
- Пример: «Стек Android-разработки» — Kotlin, Compose, Не использовать Java
- **Контекстный промптинг**: только релевантные блоки попадают в `[INVARIANTS]`
- **Pre-turn**: конфликт запроса с инвариантом → отказ
- **Post-turn**: validate → rework (до 2 переработок)

## FSM (День 15)

Таблица переходов в `TaskPhaseTransitions.kt`:

| From | To |
|------|-----|
| PLANNING | EXECUTION |
| EXECUTION | VALIDATION, PLANNING |
| VALIDATION | DONE, EXECUTION |
| DONE | — |

Skip (planning→done и т.д.) блокируется в коде.

## Как проверить

1. `./gradlew :desktopApp:run`
2. Настройки → Инварианты — пресет «Стек Android-разработки»
3. Чат: «Напиши ViewModel на Java» → отказ
4. «Сделай экран на Compose» → pass
5. «Расскажи про impressionism» → без Android-блока в панели
6. Задача с planning: «сразу пиши код» → blocked transition
7. Уход/возврат — фаза в `taskStateJson` сохранена

## Файлы

- `agent/invariant/` — блоки, промпт, forbidden terms
- `agent/task/TaskPhaseTransitions.kt` — граф переходов
- `agent/validation/ResponseValidator.kt` — локальная + LLM validate
- `InvariantsScreen.kt`, `InvariantBlockEditorScreen.kt` — CRUD UI
