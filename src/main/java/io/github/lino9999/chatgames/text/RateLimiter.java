package io.github.lino9999.chatgames.text;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RateLimiter {
    private final Map<UUID, Long> expirations = new ConcurrentHashMap<>();
    private final long cooldownMillis;

    public RateLimiter(long cooldownMillis) {
        this.cooldownMillis = cooldownMillis;
    }

    public boolean tryAcquire(UUID playerId) {
        long now = System.currentTimeMillis();
        Long expiresAt = expirations.get(playerId);
        if (expiresAt != null && now < expiresAt) {
            return false;
        }
        expirations.put(playerId, now + cooldownMillis);
        return true;
    }

    public double remainingSeconds(UUID playerId) {
        Long expiresAt = expirations.get(playerId);
        if (expiresAt == null) {
            return 0.0;
        }
        long diff = expiresAt - System.currentTimeMillis();
        if (diff <= 0L) {
            expirations.remove(playerId);
            return 0.0;
        }
        return Math.round((diff / 1000.0) * 10.0) / 10.0;
    }
}
