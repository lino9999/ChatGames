package io.github.lino9999.chatgames.api.event;

import io.github.lino9999.chatgames.api.ChallengeCategory;
import org.bukkit.Bukkit;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class RoundBeginEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final ChallengeCategory category;
    private final String objectiveDescription;
    private boolean cancelled;

    public RoundBeginEvent(ChallengeCategory category, String objectiveDescription) {
        super(!Bukkit.isPrimaryThread());
        this.category = category;
        this.objectiveDescription = objectiveDescription != null ? objectiveDescription : "";
    }

    public ChallengeCategory getCategory() {
        return category;
    }

    public String getObjectiveDescription() {
        return objectiveDescription;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
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
