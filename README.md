# 🏆 LoL Match Analyzer

![alt text](https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)


![alt text](https://img.shields.io/badge/Spring_Boot-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white)


![alt text](https://img.shields.io/badge/PostgreSQL-316192?style=for-the-badge&logo=postgresql&logoColor=white)


![alt text](https://img.shields.io/badge/Docker-2496ED?style=for-the-badge&logo=docker&logoColor=white)

LoL Match Analyzer is an analytical web service designed to predict the outcome of League of Legends matches. The system fetches deep statistics for all 10 players via the Riot Games API, processes them using custom algorithms, and provides a visual win-probability forecast.

## ✨ Key Features

📊 Deep 5v5 / 1v1 Analytics: Aggregates data for entire teams in a single request flow.

⚖️ Role-Based Normalization: Intelligent efficiency (GPM) calculation that adjusts for specific roles.

🔥 "Main Champion" Bonus: Automatically compares the selected champion with the player's top mastery list. If a player is on their signature pick, their WinScore receives a boost.

🗄️ Caching: Player data is cached in PostgreSQL for 24 hours to reduce API latency and preserve Riot API rate limits.

⏳ Throttling System: Implemented request-delay logic (Thread.sleep) to ensure stability.

🎨 Modern UI: A sleek, gamer-oriented dark theme built with Thymeleaf, featuring dynamic chance progress bars and lane matchup cards.


## 🛠 Tech Stack

Layer	Technology
Java 17/21, Spring Boot
PostgreSQL, Spring Data JPA, Hibernate
Thymeleaf, HTML5, CSS3
Docker
Lombok, Jackson, RestTemplate

### 📐 WinScore Algorithm (v1.2)

Each player's individual score is calculated using a weighted formula:

Rank Weight (40%): Scores range from 10 (Iron) to 130 (Challenger) with a progressive scale.

KDA Weight (25%): Analyzes performance efficiency over the last 5 ranked matches.

Account Level (15%): Reflects overall game experience (benchmarked at level 500).

GPM Weight (20%): Gold Per Minute, normalized against role-specific benchmarks (TOP, JNG, MID, ADC, SUP).

## 📁 Project Structure
code
Text
download
content_copy
expand_less
src/main/java/com/lol/analyzer/
├── 🎮 controller/   # Web and REST controllers (Route handling)
├── 🧠 service/      # Business logic, WinScore calculations, and normalization
├── 🔌 client/       # Riot API Client with Throttling logic
├── 📦 model/        # JPA Entities (Database) and DTOs (API exchange)
├── 📂 repository/   # Data access layer (Spring Data JPA)
└── ⚙️ config/       # Beans configuration and RestTemplate setup

## 🚀 Quick Start
### 1. Obtain an API Key

Get your temporary key from the Riot Developer Portal.

### 2. Configure secrets (do not commit real keys)

Create a file named `.env` in the project root (same folder as `pom.xml`). Copy from `.env.example` and set:

```
RIOT_API_KEY=RGAPI-your-riot-key
GEMINI_API_KEY=your-gemini-key
```

- **Local run:** `dotenv-java` loads `.env` at startup (see `AnalyzerApplication`). You can also set the same variables in your OS or IntelliJ Run Configuration.
- **Docker:** `docker compose` reads `.env` via `env_file` (see `docker-compose.yml`).

`application.properties` maps them to `riot.api.key` / `gemini.api.key`:

```
riot.api.key=${RIOT_API_KEY:}
gemini.api.key=${GEMINI_API_KEY:}
```

Database URL for local Postgres (optional; defaults match Docker port mapping):

```
spring.datasource.url=jdbc:postgresql://localhost:5434/lol_db
spring.datasource.username=user
spring.datasource.password=pass
```
### 3. Run via Docker Compose

The project is fully containerized. Run the following command in the project root:

```
docker compose up --build
```

Copy `.env.example` to `.env` and add keys before starting, or Compose will fail when the app needs Riot/Gemini.
Once started, the service will be available at: http://localhost:8080

Project Status: MVP Completed.
Future Plans: Integration of Gemini AI for automated tactical reporting.
