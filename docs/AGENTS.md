# ChatGames Agent Guide

This document assists AI coding assistants and developers in navigating, modifying, and verifying **ChatGames** while conserving context tokens.

## Architecture & Project Boundaries

- **Author**: Lino
- **Package Base**: `io.github.lino9999.chatgames`
- **Platforms**: Spigot, Paper, Purpur, Folia 1.21.11 through 26.3
- **Bytecode**: Java 21 (`<maven.compiler.release>21</maven.compiler.release>`)
- **Coding Standards**:
  - Strictly zero comments in Java source code.
  - All public documentation, commands, log outputs, and player messages are in English.
  - Zero third-party shaded dependencies: uses standard Java JDBC (`java.sql.DriverManager`) and Java 21 `java.net.http.HttpClient`.
  - Asynchronous database operations with an in-memory thread-safe competitor cache (`CompetitorAccount` via `ConcurrentHashMap`) so chat events and game broadcasts never block server threads.

## Token-Saving Navigation Map

When working on a specific feature, read **only** the relevant files:

| Task / Feature | Files to Inspect |
|---|---|
| Lifecycle & Wiring | `src/main/java/io/github/lino9999/chatgames/ChatGamesPlugin.java`, `src/main/resources/plugin.yml` |
| Categories & Podium Records | `src/main/java/io/github/lino9999/chatgames/api/ChallengeCategory.java`, `src/main/java/io/github/lino9999/chatgames/api/PodiumRecord.java` |
| Developer Events | `src/main/java/io/github/lino9999/chatgames/api/event/RoundBeginEvent.java`, `RoundCompleteEvent.java` |
| Puzzle Generation & Math | `src/main/java/io/github/lino9999/chatgames/session/PuzzleGenerator.java`, `SymbolicEquation.java` |
| Active Session & Scheduler | `src/main/java/io/github/lino9999/chatgames/session/RoundOrchestrator.java`, `ChatPuzzleSession.java`, `WorldChallengeSession.java` |
| Structured Prize Engine | `src/main/java/io/github/lino9999/chatgames/prize/PrizeEngine.java`, `PrizePool.java`, `PrizeAction.java`, `src/main/resources/prizes.yml` |
| Persistence & Leaderboards | `src/main/java/io/github/lino9999/chatgames/persistence/CompetitorRepository.java`, `CompetitorAccount.java`, `RankingSnapshot.java` |
| Chat Answer Validation | `src/main/java/io/github/lino9999/chatgames/handler/ChatAttemptHandler.java` |
| World Action Challenges | `src/main/java/io/github/lino9999/chatgames/handler/WorldActionHandler.java`, `src/main/resources/challenges.yml` |
| Telemetry, Audit & Discord | `src/main/java/io/github/lino9999/chatgames/handler/SessionTelemetryHandler.java`, `src/main/java/io/github/lino9999/chatgames/hook/DiscordNotifier.java`, `src/main/java/io/github/lino9999/chatgames/text/AuditLogger.java` |
| Commands & Tab Completion | `src/main/java/io/github/lino9999/chatgames/command/CommandRouter.java` |
| PlaceholderAPI Expansion | `src/main/java/io/github/lino9999/chatgames/hook/PlaceholderHook.java` |
| Chat Rendering & Centering | `src/main/java/io/github/lino9999/chatgames/text/ChatRenderer.java`, `NameBeautifier.java` |
| YAML Configuration Registry | `src/main/java/io/github/lino9999/chatgames/config/ConfigurationRegistry.java`, `ConfigDocument.java` |

## Resource Files Overview

- `config.yml`: Engine settings, automated scheduler, SQLite/MySQL credentials, sound cues, Discord webhook, and puzzle mode parameters.
- `lang.yml`: All player-facing messages and round broadcasts using `{placeholder}` tokens and `[center]` alignment tags.
- `content.yml`: Word banks, algebra symbol values, and trivia quiz questions.
- `challenges.yml`: World challenge categories (`slayer`, `mining`, `building`, `angling`, `culinary`, `artisan`, `smelting`), target materials/entities, and quotas.
- `prizes.yml`: Weighted prize pools (`GIVE_ITEM`, `CONSOLE_COMMAND`, `PLAYER_COMMAND`, `OP_COMMAND`, `PLAYER_MESSAGE`, `BROADCAST`) and podium assignments.

## Building and Verification

- Build plugin: `./mvnw -B clean package`
- Target artifact: `target/ChatGames-1.0.0.jar`
