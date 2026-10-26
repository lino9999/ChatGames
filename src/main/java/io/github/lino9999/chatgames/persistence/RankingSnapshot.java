package io.github.lino9999.chatgames.persistence;

import java.util.UUID;

public record RankingSnapshot(int position, UUID playerUuid, String playerName, int score, double bestReactionSeconds) {
}
