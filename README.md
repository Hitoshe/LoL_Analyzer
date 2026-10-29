# LoL Match Analyzer

Web-сервис прогноза шанса победы в League of Legends: профили из Riot API, своя Elo-модель, история и калибровка.

**Стек:** Java 21, Spring Boot, PostgreSQL, Thymeleaf, Docker.

## Что умеет сейчас (ветка `feature/pro-analytics`)

- Все серверы (EUW, EUNE, KR, NA, …) — выбор на форме
- Вставка `Ник#TAG`, пачка ников, список чемпионов с иконками
- Шанс **до пиков** и **после** (если чемпионы указаны)
- Роль / офф-роль, сессия, опыт на чемпионе, ранг с дивизионом и LP
- Загрузка текущей игры (Spectator — с экрана загрузки)
- История анализов + сверка с реальным исходом (фоновый job)
- Мета/контры/связки из своей БД (пока слабый вес при малой выборке)
- Meta collector **выключен** (`analyzer.collector.enabled=false`) — для production-ключа

Тесты: `.\mvnw.cmd test` (H2, без Riot) — 22 теста.

## Запуск

1. `.env` в корне (см. `.env.example`): `RIOT_API_KEY`, опционально `GEMINI_API_KEY`
2. Postgres: `docker compose up -d db` (или весь стек `docker compose up --build`)
3. Приложение: IntelliJ / `.\mvnw.cmd spring-boot:run` → http://localhost:8080

Локально нужен JDK 21 (`JAVA_HOME`).

Ключевые настройки — `src/main/resources/application.properties`  
(TTL профиля, лимиты Riot, collector, outcome resolver).

## Модель (кратко)

Pre-draft: ранг (тир+дивизион+LP) + форма + роль + сессия + сторона карты.  
Post-draft: + комфорт на герое + мета + контрпики + связки (при малой выборке почти не влияют).  
Шанс = логистика по разнице Elo.

## Дальше (когда вернёшься)

1. Прогнать руками с живым ключом: форма, регион, live game, анализ с/без пиков
2. При production-ключе: `analyzer.collector.enabled=true`
3. Калибровка весов по `/history` после накопления исходов
4. Полировка UI / деплой

Старый WinScore v1.2 заменён Elo-моделью в `analysis/WinProbabilityModel`.
