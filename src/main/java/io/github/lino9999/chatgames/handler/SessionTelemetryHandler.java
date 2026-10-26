package io.github.lino9999.chatgames.handler;

import io.github.lino9999.chatgames.api.PodiumRecord;
import io.github.lino9999.chatgames.api.event.RoundBeginEvent;
import io.github.lino9999.chatgames.api.event.RoundCompleteEvent;
import io.github.lino9999.chatgames.hook.DiscordNotifier;
import io.github.lino9999.chatgames.persistence.CompetitorRepository;
import io.github.lino9999.chatgames.text.AuditLogger;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.List;
import java.util.stream.Collectors;

public class SessionTelemetryHandler implements Listener {
    private final CompetitorRepository repository;
    private final AuditLogger auditLogger;
    private final DiscordNotifier discordNotifier;

    public SessionTelemetryHandler(CompetitorRepository repository, AuditLogger auditLogger, DiscordNotifier discordNotifier) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.discordNotifier = discordNotifier;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        repository.preloadAsync(player.getUniqueId(), player.getName());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRoundBegin(RoundBeginEvent event) {
        auditLogger.record("ROUND_BEGIN [category=" + event.getCategory().getId()
                + ", objective=" + event.getObjectiveDescription() + "]");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRoundComplete(RoundCompleteEvent event) {
        List<PodiumRecord> podium = event.getPodium();
        if (podium.isEmpty()) {
            auditLogger.record("ROUND_EXPIRED [category=" + event.getCategory().getId()
                    + ", solution=" + event.getSolutionText() + "]");
            return;
        }

        String podiumSummary = podium.stream()
                .map(r -> "#" + r.position() + " " + r.competitorName() + " (" + r.elapsedSeconds() + "s)")
                .collect(Collectors.joining(", "));

        auditLogger.record("ROUND_VICTORY [category=" + event.getCategory().getId()
                + ", solution=" + event.getSolutionText()
                + ", podium=" + podiumSummary + "]");

        PodiumRecord firstPlace = podium.get(0);
        discordNotifier.dispatchVictoryEmbed(firstPlace.competitorName(), firstPlace.elapsedSeconds(), event.getCategory());
    }
}
