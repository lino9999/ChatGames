package io.github.lino9999.chatgames.session;

import io.github.lino9999.chatgames.api.ChallengeCategory;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WorldChallengeSession {
    private final ChallengeCategory category;
    private final Material targetMaterial;
    private final EntityType targetEntity;
    private final String formattedTargetName;
    private final int requiredQuota;
    private final int durationSeconds;
    private final List<String> prizePoolIds;
    private final long startedAtMillis;
    private final Map<UUID, Integer> progressTracker = new ConcurrentHashMap<>();

    public WorldChallengeSession(
            ChallengeCategory category,
            Material targetMaterial,
            EntityType targetEntity,
            String formattedTargetName,
            int requiredQuota,
            int durationSeconds,
            List<String> prizePoolIds
    ) {
        this.category = category;
        this.targetMaterial = targetMaterial;
        this.targetEntity = targetEntity;
        this.formattedTargetName = formattedTargetName;
        this.requiredQuota = Math.max(1, requiredQuota);
        this.durationSeconds = Math.max(1, durationSeconds);
        this.prizePoolIds = prizePoolIds != null ? List.copyOf(prizePoolIds) : List.of();
        this.startedAtMillis = System.currentTimeMillis();
    }

    public ChallengeCategory getCategory() {
        return category;
    }

    public Material getTargetMaterial() {
        return targetMaterial;
    }

    public EntityType getTargetEntity() {
        return targetEntity;
    }

    public String getFormattedTargetName() {
        return formattedTargetName;
    }

    public int getRequiredQuota() {
        return requiredQuota;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public List<String> getPrizePoolIds() {
        return prizePoolIds;
    }

    public int incrementProgress(UUID playerUuid, int delta) {
        if (delta <= 0) {
            return progressTracker.getOrDefault(playerUuid, 0);
        }
        return progressTracker.merge(playerUuid, delta, Integer::sum);
    }

    public boolean hasCompletedQuota(UUID playerUuid) {
        return progressTracker.getOrDefault(playerUuid, 0) >= requiredQuota;
    }

    public double computeElapsedSeconds() {
        double secs = (System.currentTimeMillis() - startedAtMillis) / 1000.0;
        return Math.max(0.01, Math.round(secs * 100.0) / 100.0);
    }
}
