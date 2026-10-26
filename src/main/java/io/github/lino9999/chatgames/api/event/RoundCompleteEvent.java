package io.github.lino9999.chatgames.api.event;

import io.github.lino9999.chatgames.api.ChallengeCategory;
import io.github.lino9999.chatgames.api.PodiumRecord;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class RoundCompleteEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final ChallengeCategory category;
    private final String solutionText;
    private final List<PodiumRecord> podium;

    public RoundCompleteEvent(ChallengeCategory category, String solutionText, List<PodiumRecord> podium) {
        super(!Bukkit.isPrimaryThread());
        this.category = category;
        this.solutionText = solutionText != null ? solutionText : "";
        this.podium = podium != null ? List.copyOf(podium) : Collections.emptyList();
    }

    public ChallengeCategory getCategory() {
        return category;
    }

    public String getSolutionText() {
        return solutionText;
    }

    public List<PodiumRecord> getPodium() {
        return podium;
    }

    public boolean hasWinners() {
        return !podium.isEmpty();
    }

    @Override
    @NotNull
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
