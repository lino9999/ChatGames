package io.github.lino9999.chatgames.session;

import io.github.lino9999.chatgames.api.ChallengeCategory;
import io.github.lino9999.chatgames.api.PodiumRecord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class ChatPuzzleSession {
    private final ChallengeCategory category;
    private final Set<String> acceptedAnswers;
    private volatile String canonicalAnswer;
    private final String promptText;
    private final int maxPodiumSlots;
    private final boolean strictCase;
    private final int durationSeconds;
    private final String interactionToken;
    private final SymbolicEquation algebraData;
    private final long startedAtMillis;
    private final List<PodiumRecord> podium = new ArrayList<>();
    private final Set<UUID> winnersSet = new HashSet<>();

    public ChatPuzzleSession(
            ChallengeCategory category,
            Set<String> acceptedAnswers,
            String canonicalAnswer,
            String promptText,
            int maxPodiumSlots,
            boolean strictCase,
            int durationSeconds,
            String interactionToken,
            SymbolicEquation algebraData
    ) {
        this.category = category;
        this.acceptedAnswers = new HashSet<>(acceptedAnswers);
        this.canonicalAnswer = canonicalAnswer;
        this.promptText = promptText;
        this.maxPodiumSlots = Math.max(1, maxPodiumSlots);
        this.strictCase = strictCase;
        this.durationSeconds = Math.max(1, durationSeconds);
        this.interactionToken = interactionToken;
        this.algebraData = algebraData;
        this.startedAtMillis = System.currentTimeMillis();
    }

    public ChallengeCategory getCategory() {
        return category;
    }

    public Set<String> getAcceptedAnswers() {
        return Collections.unmodifiableSet(acceptedAnswers);
    }

    public String getCanonicalAnswer() {
        return canonicalAnswer;
    }

    public void setCanonicalAnswer(String canonicalAnswer) {
        if (canonicalAnswer != null && !canonicalAnswer.isBlank()) {
            this.canonicalAnswer = canonicalAnswer;
        }
    }

    public String getPromptText() {
        return promptText;
    }

    public int getMaxPodiumSlots() {
        return maxPodiumSlots;
    }

    public boolean isStrictCase() {
        return strictCase;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public String getInteractionToken() {
        return interactionToken;
    }

    public SymbolicEquation getAlgebraData() {
        return algebraData;
    }

    public synchronized boolean hasClaimedSpot(UUID playerUuid) {
        return winnersSet.contains(playerUuid);
    }

    public synchronized PodiumRecord claimPodiumSpot(UUID playerUuid, String playerName) {
        if (winnersSet.contains(playerUuid) || podium.size() >= maxPodiumSlots) {
            return null;
        }
        winnersSet.add(playerUuid);
        double elapsed = Math.round(((System.currentTimeMillis() - startedAtMillis) / 1000.0) * 100.0) / 100.0;
        PodiumRecord record = new PodiumRecord(podium.size() + 1, playerUuid, playerName, Math.max(0.01, elapsed));
        podium.add(record);
        return record;
    }

    public synchronized boolean isPodiumFull() {
        return podium.size() >= maxPodiumSlots;
    }

    public synchronized List<PodiumRecord> getPodium() {
        return List.copyOf(podium);
    }
}
