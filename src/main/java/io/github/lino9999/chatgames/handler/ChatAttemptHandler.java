package io.github.lino9999.chatgames.handler;

import io.github.lino9999.chatgames.api.ChallengeCategory;
import io.github.lino9999.chatgames.config.ConfigurationRegistry;
import io.github.lino9999.chatgames.session.ChatPuzzleSession;
import io.github.lino9999.chatgames.session.RoundOrchestrator;
import io.github.lino9999.chatgames.text.ChatRenderer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class ChatAttemptHandler implements Listener {
    private final JavaPlugin plugin;
    private final ConfigurationRegistry registry;
    private final RoundOrchestrator orchestrator;

    public ChatAttemptHandler(JavaPlugin plugin, ConfigurationRegistry registry, RoundOrchestrator orchestrator) {
        this.plugin = plugin;
        this.registry = registry;
        this.orchestrator = orchestrator;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChatAttempt(AsyncPlayerChatEvent event) {
        ChatPuzzleSession puzzle = orchestrator.getActivePuzzle();
        if (puzzle == null || puzzle.getCategory() == ChallengeCategory.CLICK_RUSH) {
            return;
        }

        Player sender = event.getPlayer();
        if (!orchestrator.canParticipate(sender) || puzzle.hasClaimedSpot(sender.getUniqueId())) {
            return;
        }

        String rawInput = ChatColor.stripColor(event.getMessage()).trim();
        if (rawInput.endsWith(".")) {
            rawInput = rawInput.substring(0, rawInput.length() - 1).trim();
        }
        if (rawInput.isEmpty()) {
            return;
        }

        boolean exactMatch = false;
        boolean caseOnlyMismatch = false;

        if (puzzle.getCategory() == ChallengeCategory.MEMORY_LIST) {
            String compactAttempt = rawInput.replaceAll("\\s+", "");
            for (String target : puzzle.getAcceptedAnswers()) {
                String compactTarget = target.replaceAll("\\s+", "");
                if (puzzle.isStrictCase()) {
                    if (compactTarget.equals(compactAttempt)) {
                        exactMatch = true;
                        puzzle.setCanonicalAnswer(target);
                        break;
                    } else if (compactTarget.equalsIgnoreCase(compactAttempt)) {
                        caseOnlyMismatch = true;
                    }
                } else if (compactTarget.equalsIgnoreCase(compactAttempt)) {
                    exactMatch = true;
                    puzzle.setCanonicalAnswer(target);
                    break;
                }
            }
        } else {
            for (String target : puzzle.getAcceptedAnswers()) {
                if (puzzle.isStrictCase()) {
                    if (target.equals(rawInput)) {
                        exactMatch = true;
                        puzzle.setCanonicalAnswer(target);
                        break;
                    } else if (target.equalsIgnoreCase(rawInput)) {
                        caseOnlyMismatch = true;
                    }
                } else if (target.equalsIgnoreCase(rawInput)) {
                    exactMatch = true;
                    puzzle.setCanonicalAnswer(target);
                    break;
                }
            }
        }

        if (caseOnlyMismatch && !exactMatch) {
            String hint = registry.lang().text("gameplay.case-mismatch-hint", "");
            if (!hint.isBlank()) {
                ChatRenderer.deliver(sender, hint, registry.prefix());
            }
            orchestrator.emitAudioCue(sender, "case-mismatch");
            return;
        }

        if (exactMatch) {
            if (plugin.getConfig().getBoolean("engine.suppress-winning-chat", true)) {
                event.setCancelled(true);
            }
            Bukkit.getScheduler().runTask(plugin, () -> orchestrator.recordRoundVictory(sender));
        }
    }
}
