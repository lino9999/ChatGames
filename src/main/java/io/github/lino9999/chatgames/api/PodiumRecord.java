package io.github.lino9999.chatgames.api;

import java.util.UUID;

public record PodiumRecord(int position, UUID competitorId, String competitorName, double elapsedSeconds) {
}
