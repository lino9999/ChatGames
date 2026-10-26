package io.github.lino9999.chatgames.hook;

import io.github.lino9999.chatgames.persistence.CompetitorAccount;
import io.github.lino9999.chatgames.persistence.CompetitorRepository;
import io.github.lino9999.chatgames.persistence.RankingSnapshot;
import io.github.lino9999.chatgames.session.RoundOrchestrator;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

public class PlaceholderHook extends PlaceholderExpansion {
    private final CompetitorRepository repository;
    private final RoundOrchestrator orchestrator;

    private volatile List<RankingSnapshot> cachedScores = List.of();
    private volatile List<RankingSnapshot> cachedSpeeds = List.of();
    private long lastRefreshMillis = 0L;

    public PlaceholderHook(CompetitorRepository repository, RoundOrchestrator orchestrator) {
        this.repository = repository;
        this.orchestrator = orchestrator;
    }

    @Override
    @NotNull
    public String getIdentifier() {
        return "chatgames";
    }

    @Override
    @NotNull
    public String getAuthor() {
        return "Lino";
    }

    @Override
    @NotNull
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    private void refreshSnapshotsIfStale() {
        long now = System.currentTimeMillis();
        if (now - lastRefreshMillis > 5000L) {
            lastRefreshMillis = now;
            repository.queryTopScores(10).thenAccept(list -> cachedScores = list);
            repository.queryTopSpeeds(10).thenAccept(list -> cachedSpeeds = list);
        }
    }

    @Override
    @Nullable
    public String onPlaceholderRequest(Player player, @NotNull String identifier) {
        refreshSnapshotsIfStale();
        String key = identifier.toLowerCase(Locale.ROOT);

        switch (key) {
            case "active", "round_active" -> {
                return String.valueOf(orchestrator.hasActiveRound());
            }
            case "current_game", "round_category" -> {
                if (orchestrator.getActivePuzzle() != null) {
                    return orchestrator.getActivePuzzle().getCategory().getId();
                }
                if (orchestrator.getActiveChallenge() != null) {
                    return orchestrator.getActiveChallenge().getCategory().getId();
                }
                return "none";
            }
            default -> {
            }
        }

        if (player != null) {
            CompetitorAccount account = repository.resolveAccount(player.getUniqueId(), player.getName());
            switch (key) {
                case "score", "points" -> {
                    return String.valueOf(account.getScore());
                }
                case "fastest_time", "best_reaction" -> {
                    return String.valueOf(account.getBestReactionSeconds());
                }
                case "visibility", "chat_enabled" -> {
                    return String.valueOf(account.isChatVisible());
                }
                case "sound", "audio_enabled" -> {
                    return String.valueOf(account.isAudioEnabled());
                }
                case "rank_score" -> {
                    Integer rank = repository.queryScorePlacement(player.getUniqueId()).join();
                    return rank != null ? String.valueOf(rank) : "0";
                }
                case "rank_speed" -> {
                    Integer rank = repository.querySpeedPlacement(player.getUniqueId()).join();
                    return rank != null ? String.valueOf(rank) : "0";
                }
                default -> {
                }
            }
        }

        if (key.startsWith("leaderboard_player_")) {
            int idx = parseRankIndex(key.substring("leaderboard_player_".length()));
            return (idx >= 1 && idx <= cachedScores.size()) ? cachedScores.get(idx - 1).playerName() : "---";
        }
        if (key.startsWith("leaderboard_score_") || key.startsWith("leaderboard_points_")) {
            String sub = key.startsWith("leaderboard_score_")
                    ? key.substring("leaderboard_score_".length())
                    : key.substring("leaderboard_points_".length());
            int idx = parseRankIndex(sub);
            return (idx >= 1 && idx <= cachedScores.size()) ? String.valueOf(cachedScores.get(idx - 1).score()) : "0";
        }
        if (key.startsWith("speed_player_")) {
            int idx = parseRankIndex(key.substring("speed_player_".length()));
            return (idx >= 1 && idx <= cachedSpeeds.size()) ? cachedSpeeds.get(idx - 1).playerName() : "---";
        }
        if (key.startsWith("speed_time_")) {
            int idx = parseRankIndex(key.substring("speed_time_".length()));
            return (idx >= 1 && idx <= cachedSpeeds.size()) ? String.valueOf(cachedSpeeds.get(idx - 1).bestReactionSeconds()) : "0.0";
        }

        return null;
    }

    private int parseRankIndex(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
