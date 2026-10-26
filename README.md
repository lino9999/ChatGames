<div align="center">

<img src="assets/chatgames-icon-128.png" alt="ChatGames Logo" width="128" height="128" />

# 🎮 ChatGames

**High-Performance, Zero-Lag Chat Puzzles & Live World Challenges for Modern Minecraft Servers**

[![GitHub Release](https://img.shields.io/github/v/release/lino9999/ChatGames?color=FFAA00&style=for-the-badge&logo=github)](https://github.com/lino9999/ChatGames/releases)
[![Minecraft Versions](https://img.shields.io/badge/Minecraft-1.21.11%20--%2026.3-55FF55?style=for-the-badge&logo=minecraft)](https://papermc.io)
[![Java Version](https://img.shields.io/badge/Java-21+-007396?style=for-the-badge&logo=openjdk)](https://adoptium.net)
[![License](https://img.shields.io/badge/License-MIT-blue?style=for-the-badge)](LICENSE)

[Features](#-features) • [Chat Puzzles](#-chat-puzzles) • [World Challenges](#-world-challenges) • [Commands & Permissions](#-commands--permissions) • [Placeholders](#-placeholderapi) • [Developer API](#-developer-api)

</div>

---

## 🌟 Overview

**ChatGames** is a high-performance chat puzzle and live server competition engine built from the ground up by **Lino** for Spigot, Paper, Purpur, and Folia servers running Minecraft **1.21.11 through 26.3**.

Designed with a modern Java 21 architecture, **ChatGames** combines 12 interactive chat puzzles and 7 in-world action challenges with an asynchronous SQLite/MySQL persistence layer, structured prize pools, interactive JSON chat components, and native Discord webhook embeds.

---

## ⚡ Features

- **🚀 Zero-Lag In-Memory Caching:** Competitor profiles, scores, and preferences are cached in thread-safe memory (`ConcurrentHashMap`) backed by non-blocking asynchronous SQLite or MySQL storage.
- **🎯 12 Chat Puzzle Modes:** Scramble, Reverse, Type Race, Number Guess, Memory List, Click Rush, Missing Chars, Hover Reveal, Cipher String, Arithmetic, Algebra, and Quiz.
- **🏁 7 World Action Challenges:** Slayer (mob hunting), Mining, Building, Angling (fishing), Culinary (food consumption), Artisan (crafting with shift-craft support), and Smelting.
- **🏆 Multi-Winner Podiums:** Reward 1st, 2nd, and 3rd place finishers with distinct prize pools per category.
- **🎁 Structured Prize Engine:** Weighted prize pools (`GIVE_ITEM`, `CONSOLE_COMMAND`, `PLAYER_COMMAND`, `OP_COMMAND`, `PLAYER_MESSAGE`, `BROADCAST`) with per-action probabilities and **automatic full-inventory ground drop protection**.
- **💬 Modern Chat Renderer:** Full RGB Hex (`#RRGGBB`), legacy formatting (`&a`), pixel-accurate `[center]` alignment, hover tooltips, and clickable chat buttons.
- **📊 Dual Leaderboards:** Track all-time top scores (`/cg top`) and fastest reaction times (`/cg fastest`).
- **🔔 Discord Webhook Integration:** Automatically post round winners and reaction speeds to Discord using Java 21's non-blocking `HttpClient`.
- **🧩 PlaceholderAPI Expansion:** Display competitor scores, reaction records, rankings, and active round status across scoreboards and tablists.

---

## 🎲 Chat Puzzles

| Puzzle Category | Identifier | Description |
| :--- | :--- | :--- |
| **Scramble** | `scramble` | Unscramble permuted letters back into the original word or phrase. |
| **Reverse** | `reverse` | Decode reversed words and phrases in chat. |
| **Type Race** | `type_race` | High-speed typing test where the quickest competitor wins. |
| **Number Guess** | `number_guess` | Guess the secret number within a dynamic range. |
| **Memory List** | `memory_list` | Memorize a sequence of items before chat clears, then recall them in order! |
| **Click Rush** | `click_rush` | Interactive JSON chat button that rewards the fastest clicker. |
| **Missing Chars** | `missing_chars` | Complete words with obscured characters (e.g., `O_s_di_n`). |
| **Hover Reveal** | `hover_reveal` | Hover over the interactive badge in chat to reveal the secret phrase. |
| **Cipher String** | `cipher_string` | Fast typing challenge with generated alphanumeric, hex, or symbol codes. |
| **Arithmetic** | `arithmetic` | Solve generated math expressions (`+`, `-`, `*`, `/`) with scaling complexity. |
| **Algebra** | `algebra` | Deduce the unknown glyph value from a 3-row system of symbol equations. |
| **Quiz** | `quiz` | Server trivia questions supporting multiple accepted answers. |

---

## 🏃 World Challenges

- **🗡️ Slayer (`slayer`):** First player to slay the target quota of a specific mob type.
- **⛏️ Mining (`mining`):** First player to mine the required amount of target blocks.
- **🧱 Building (`building`):** First player to place the required amount of target blocks.
- **🎣 Angling (`angling`):** First player to catch the required fish or treasure items.
- **🍎 Culinary (`culinary`):** First player to consume the required food items.
- **🔨 Artisan (`artisan`):** First player to craft the target items (supports shift-click bulk crafting).
- **🔥 Smelting (`smelting`):** First player to extract cooked items from a furnace.

---

## ⌨️ Commands & Permissions

**Base Command:** `/chatgames`  
**Aliases:** `/cg`, `/cgames`, `/chatgame`

| Command | Permission | Description |
| :--- | :--- | :--- |
| `/cg help` | `chatgames.command.help` | Display the command reference menu. |
| `/cg start <category\|random> [args]` | `chatgames.command.start.*` | Manually launch a specific category or random round. |
| `/cg stop` | `chatgames.command.stop` | Cancel the currently active round. |
| `/cg top` | `chatgames.command.leaderboard` | View the top score leaderboard. |
| `/cg fastest` | `chatgames.command.fastest` | View the fastest reaction speed leaderboard. |
| `/cg score [player]` | `chatgames.command.score` | Check personal or another competitor's score. |
| `/cg score <add\|take\|set\|reset> <player> [amt]` | `chatgames.command.score.manage` | Modify or reset competitor scores. |
| `/cg visibility [player]` | `chatgames.command.visibility` | Toggle chat round broadcast visibility. |
| `/cg audio [player]` | `chatgames.command.audio` | Toggle round sound alerts. |
| `/cg scheduler` | `chatgames.command.scheduler` | Pause or resume the automated round scheduler. |
| `/cg history [count]` | `chatgames.command.history` | Inspect recent round audit logs. |
| `/cg reload` | `chatgames.command.reload` | Reload all YAML configuration files. |

---

## 🧩 PlaceholderAPI

| Placeholder | Output |
| :--- | :--- |
| `%chatgames_score%` | Competitor's total score / wins. |
| `%chatgames_fastest_time%` | Competitor's fastest reaction record in seconds. |
| `%chatgames_visibility%` | Whether round broadcasts are visible (`true`/`false`). |
| `%chatgames_sound%` | Whether sound cues are enabled (`true`/`false`). |
| `%chatgames_rank_score%` | Competitor's rank on the score leaderboard. |
| `%chatgames_rank_speed%` | Competitor's rank on the reaction speed leaderboard. |
| `%chatgames_active%` | `true` if a round is currently active, `false` otherwise. |
| `%chatgames_current_game%` | Identifier of the active category, or `none`. |
| `%chatgames_leaderboard_player_<1-10>%` | Name of the competitor at score rank #N. |
| `%chatgames_leaderboard_score_<1-10>%` | Score of the competitor at score rank #N. |
| `%chatgames_speed_player_<1-10>%` | Name of the competitor at speed rank #N. |
| `%chatgames_speed_time_<1-10>%` | Reaction time of the competitor at speed rank #N. |

---

## ⚙️ Modular Configuration Files

- **`config.yml`:** Core engine settings, automated scheduler, SQLite/MySQL database setup, audio cues, Discord webhook, and puzzle mode parameters.
- **`lang.yml`:** All player messages, leaderboards, and round broadcasts using `{placeholder}` syntax and `[center]` tags.
- **`content.yml`:** Curated word banks, algebra symbol values, and trivia quiz questions.
- **`challenges.yml`:** World challenge definitions, quotas, and prize pool links.
- **`prizes.yml`:** Structured weighted prize pools and podium slot assignments.

---

## 💻 Developer API

```java
import io.github.lino9999.chatgames.api.event.RoundBeginEvent;
import io.github.lino9999.chatgames.api.event.RoundCompleteEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class CustomRoundListener implements Listener {

    @EventHandler
    public void onRoundBegin(RoundBeginEvent event) {
        String categoryId = event.getCategory().getId();
    }

    @EventHandler
    public void onRoundComplete(RoundCompleteEvent event) {
        if (event.hasWinners()) {
            var winner = event.getPodium().get(0);
        }
    }
}
```

---

## 📄 License

Released under the **MIT License**. Engineered by **Lino**.
