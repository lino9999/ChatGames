package io.github.lino9999.chatgames.command;

import io.github.lino9999.chatgames.api.ChallengeCategory;
import io.github.lino9999.chatgames.config.ConfigurationRegistry;
import io.github.lino9999.chatgames.persistence.CompetitorAccount;
import io.github.lino9999.chatgames.persistence.CompetitorRepository;
import io.github.lino9999.chatgames.persistence.RankingSnapshot;
import io.github.lino9999.chatgames.session.ChatPuzzleSession;
import io.github.lino9999.chatgames.session.RoundOrchestrator;
import io.github.lino9999.chatgames.text.AuditLogger;
import io.github.lino9999.chatgames.text.ChatRenderer;
import io.github.lino9999.chatgames.text.RateLimiter;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class CommandRouter implements CommandExecutor, TabCompleter {
    private static final List<String> ROOT_SUBCOMMANDS = List.of(
            "help", "start", "stop", "top", "fastest", "score", "visibility", "audio", "scheduler", "history", "reload"
    );

    private final JavaPlugin plugin;
    private final ConfigurationRegistry registry;
    private final RoundOrchestrator orchestrator;
    private final CompetitorRepository repository;
    private final AuditLogger auditLogger;
    private final RateLimiter scoreQueryLimiter = new RateLimiter(4000L);

    public CommandRouter(
            JavaPlugin plugin,
            ConfigurationRegistry registry,
            RoundOrchestrator orchestrator,
            CompetitorRepository repository,
            AuditLogger auditLogger
    ) {
        this.plugin = plugin;
        this.registry = registry;
        this.orchestrator = orchestrator;
        this.repository = repository;
        this.auditLogger = auditLogger;
    }

    private void reply(CommandSender recipient, String message) {
        ChatRenderer.deliver(recipient, message, registry.prefix());
    }

    private boolean isAuthorized(CommandSender sender, String node) {
        return sender.hasPermission("chatgames.admin") || sender.hasPermission(node);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            return executeHelp(sender);
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        return switch (action) {
            case "help" -> executeHelp(sender);
            case "reload" -> executeReload(sender);
            case "stop", "cancel" -> executeStop(sender);
            case "top", "leaderboard" -> executeScoreLeaderboard(sender);
            case "fastest", "speed" -> executeSpeedLeaderboard(sender);
            case "score", "points" -> executeScoreCommand(sender, Arrays.copyOfRange(args, 1, args.length));
            case "visibility", "toggle" -> executeVisibilityToggle(sender, args.length >= 2 ? args[1] : null);
            case "audio", "sound" -> executeAudioToggle(sender, args.length >= 2 ? args[1] : null);
            case "scheduler", "auto" -> executeSchedulerToggle(sender);
            case "history", "logs" -> executeHistoryView(sender, args.length >= 2 ? args[1] : "5");
            case "claim" -> {
                if (args.length >= 2) {
                    executeTokenClaim(sender, args[1]);
                }
                yield true;
            }
            case "start" -> {
                if (args.length < 2) {
                    reply(sender, registry.lang().text("system.invalid-syntax", "&cSpecify a category."));
                    yield true;
                }
                String categoryInput = args[1];
                String[] extra = args.length > 2 ? Arrays.copyOfRange(args, 2, args.length) : new String[0];
                yield executeStartRound(sender, categoryInput, extra);
            }
            default -> {
                ChallengeCategory directCat = ChallengeCategory.resolve(action);
                if (directCat != null) {
                    String[] extra = args.length > 1 ? Arrays.copyOfRange(args, 1, args.length) : new String[0];
                    yield executeStartRound(sender, action, extra);
                }
                reply(sender, registry.lang().text("system.unknown-subcommand", "&cUnknown command. Run /cg help"));
                yield true;
            }
        };
    }

    private boolean executeHelp(CommandSender sender) {
        if (!isAuthorized(sender, "chatgames.command.help")) {
            reply(sender, registry.lang().text("system.permission-denied", "&cNo permission."));
            return true;
        }
        for (String line : registry.lang().lines("help-menu")) {
            reply(sender, line);
        }
        return true;
    }

    private boolean executeReload(CommandSender sender) {
        if (!isAuthorized(sender, "chatgames.command.reload")) {
            reply(sender, registry.lang().text("system.permission-denied", "&cNo permission."));
            return true;
        }
        boolean success = registry.reloadAll();
        orchestrator.startScheduler();
        reply(sender, registry.lang().text(success ? "system.configuration-reloaded" : "system.invalid-syntax", "&aReloaded."));
        return true;
    }

    private boolean executeStop(CommandSender sender) {
        if (!isAuthorized(sender, "chatgames.command.stop")) {
            reply(sender, registry.lang().text("system.permission-denied", "&cNo permission."));
            return true;
        }
        if (orchestrator.abortCurrentRound()) {
            reply(sender, registry.lang().text("system.round-cancelled", "&eActive round stopped."));
        } else {
            reply(sender, registry.lang().text("system.no-active-round", "&cNo active round."));
        }
        return true;
    }

    private boolean executeScoreLeaderboard(CommandSender sender) {
        if (!isAuthorized(sender, "chatgames.command.leaderboard")) {
            reply(sender, registry.lang().text("system.permission-denied", "&cNo permission."));
            return true;
        }
        repository.queryTopScores(10).thenAccept(rows -> {
            if (rows.isEmpty()) {
                reply(sender, registry.lang().text("leaderboards.score.empty", "&cNo scores recorded yet."));
                return;
            }
            reply(sender, registry.lang().text("leaderboards.score.header", "&6-- TOP SCORES --"));
            String rowTpl = registry.lang().text("leaderboards.score.row-format", "&e#{rank} &f{player} &8- &6{score}");
            for (RankingSnapshot snap : rows) {
                String line = rowTpl.replace("{rank}", String.valueOf(snap.position()))
                                    .replace("{player}", snap.playerName())
                                    .replace("{score}", String.valueOf(snap.score()));
                reply(sender, line);
            }
            if (sender instanceof Player player) {
                CompetitorAccount self = repository.resolveAccount(player.getUniqueId(), player.getName());
                String selfLine = registry.lang().text("leaderboards.score.personal-footer", "");
                if (!selfLine.isBlank()) {
                    reply(sender, selfLine.replace("{personal_score}", String.valueOf(self.getScore())));
                }
            }
            repository.queryGlobalScoreSum().thenAccept(totalWins -> {
                String globalLine = registry.lang().text("leaderboards.score.global-footer", "");
                if (!globalLine.isBlank()) {
                    reply(sender, globalLine.replace("{global_wins}", String.valueOf(totalWins)));
                }
                String footer = registry.lang().text("leaderboards.score.footer", "");
                if (!footer.isBlank()) {
                    reply(sender, footer);
                }
            });
        });
        return true;
    }

    private boolean executeSpeedLeaderboard(CommandSender sender) {
        if (!isAuthorized(sender, "chatgames.command.fastest")) {
            reply(sender, registry.lang().text("system.permission-denied", "&cNo permission."));
            return true;
        }
        if (!plugin.getConfig().getBoolean("engine.speed-leaderboard-enabled", true)) {
            reply(sender, registry.lang().text("leaderboards.speed.disabled", "&cSpeed leaderboard disabled."));
            return true;
        }
        repository.queryTopSpeeds(10).thenAccept(rows -> {
            if (rows.isEmpty()) {
                reply(sender, registry.lang().text("leaderboards.speed.empty", "&cNo reaction records yet."));
                return;
            }
            reply(sender, registry.lang().text("leaderboards.speed.header", "&b-- FASTEST REACTIONS --"));
            String rowTpl = registry.lang().text("leaderboards.speed.row-format", "&b#{rank} &f{player} &8- &e{elapsed}s");
            for (RankingSnapshot snap : rows) {
                String line = rowTpl.replace("{rank}", String.valueOf(snap.position()))
                                    .replace("{player}", snap.playerName())
                                    .replace("{elapsed}", String.valueOf(snap.bestReactionSeconds()));
                reply(sender, line);
            }
            if (sender instanceof Player player) {
                CompetitorAccount self = repository.resolveAccount(player.getUniqueId(), player.getName());
                String selfLine = registry.lang().text("leaderboards.speed.personal-footer", "");
                if (!selfLine.isBlank()) {
                    reply(sender, selfLine.replace("{personal_best}", String.valueOf(self.getBestReactionSeconds())));
                }
            }
            String footer = registry.lang().text("leaderboards.speed.footer", "");
            if (!footer.isBlank()) {
                reply(sender, footer);
            }
        });
        return true;
    }

    private boolean executeScoreCommand(CommandSender sender, String[] subArgs) {
        if (subArgs.length >= 2) {
            String op = subArgs[0].toLowerCase(Locale.ROOT);
            if (op.equals("add") || op.equals("take") || op.equals("set") || op.equals("reset")) {
                return executeScoreModification(sender, op, subArgs[1], subArgs.length >= 3 ? subArgs[2] : "0");
            }
        }

        if (subArgs.length == 1) {
            if (!isAuthorized(sender, "chatgames.command.score.manage")) {
                reply(sender, registry.lang().text("system.permission-denied", "&cNo permission."));
                return true;
            }
            OfflinePlayer target = locatePlayer(subArgs[0]);
            if (target == null) {
                reply(sender, registry.lang().text("system.competitor-not-found", "&cPlayer not found.").replace("{player}", subArgs[0]));
                return true;
            }
            CompetitorAccount account = repository.resolveAccount(target.getUniqueId(), target.getName());
            String msg = registry.lang().text("score.other-balance", "&f{player}: &e{score}")
                    .replace("{player}", account.getPlayerName())
                    .replace("{score}", String.valueOf(account.getScore()));
            reply(sender, msg);
            return true;
        }

        if (!(sender instanceof Player player)) {
            reply(sender, registry.lang().text("system.players-only", "&cPlayers only."));
            return true;
        }
        if (!isAuthorized(sender, "chatgames.command.score")) {
            reply(sender, registry.lang().text("system.permission-denied", "&cNo permission."));
            return true;
        }
        if (!scoreQueryLimiter.tryAcquire(player.getUniqueId())) {
            double rem = scoreQueryLimiter.remainingSeconds(player.getUniqueId());
            reply(sender, registry.lang().text("system.command-cooldown", "&cWait {remaining}s").replace("{remaining}", String.valueOf(rem)));
            return true;
        }
        CompetitorAccount account = repository.resolveAccount(player.getUniqueId(), player.getName());
        reply(sender, registry.lang().text("score.self-balance", "&7Score: &e{score}").replace("{score}", String.valueOf(account.getScore())));
        return true;
    }

    private boolean executeScoreModification(CommandSender sender, String operation, String targetName, String rawAmount) {
        if (!isAuthorized(sender, "chatgames.command.score.manage")) {
            reply(sender, registry.lang().text("system.permission-denied", "&cNo permission."));
            return true;
        }
        OfflinePlayer target = locatePlayer(targetName);
        if (target == null) {
            reply(sender, registry.lang().text("system.competitor-not-found", "&cPlayer not found.").replace("{player}", targetName));
            return true;
        }

        if (operation.equals("reset")) {
            repository.deleteAccount(target.getUniqueId());
            reply(sender, registry.lang().text("system.competitor-reset", "&eProfile reset.").replace("{player}", targetName));
            return true;
        }

        int value;
        try {
            value = Math.max(0, Integer.parseInt(rawAmount));
        } catch (NumberFormatException e) {
            reply(sender, registry.lang().text("system.invalid-number", "&cInvalid number."));
            return true;
        }

        CompetitorAccount account = repository.resolveAccount(target.getUniqueId(), target.getName());
        String langKey = switch (operation) {
            case "add" -> {
                account.modifyScore(value);
                yield "score.granted";
            }
            case "take" -> {
                account.modifyScore(-value);
                yield "score.deducted";
            }
            default -> {
                account.setScore(value);
                yield "score.updated";
            }
        };
        repository.persistAsync(account, false);

        String response = registry.lang().text(langKey, "&aScore updated.")
                .replace("{player}", account.getPlayerName())
                .replace("{amount}", String.valueOf(value));
        reply(sender, response);
        return true;
    }

    private boolean executeVisibilityToggle(CommandSender sender, @Nullable String otherName) {
        Player target;
        if (otherName != null && !otherName.isBlank()) {
            if (!isAuthorized(sender, "chatgames.command.visibility.others")) {
                reply(sender, registry.lang().text("system.permission-denied", "&cNo permission."));
                return true;
            }
            target = Bukkit.getPlayerExact(otherName);
            if (target == null) {
                reply(sender, registry.lang().text("system.competitor-not-found", "&cPlayer not found.").replace("{player}", otherName));
                return true;
            }
        } else {
            if (!(sender instanceof Player p)) {
                reply(sender, registry.lang().text("system.players-only", "&cPlayers only."));
                return true;
            }
            if (!isAuthorized(sender, "chatgames.command.visibility")) {
                reply(sender, registry.lang().text("system.permission-denied", "&cNo permission."));
                return true;
            }
            target = p;
        }

        CompetitorAccount account = repository.resolveAccount(target.getUniqueId(), target.getName());
        boolean nextState = !account.isChatVisible();
        account.setChatVisible(nextState);
        repository.persistAsync(account, false);

        String label = registry.lang().text(nextState ? "system.status-enabled" : "system.status-disabled", nextState ? "ENABLED" : "DISABLED");
        reply(sender, registry.lang().text("preferences.visibility-toggled", "&7Visibility: {state}").replace("{state}", label));
        return true;
    }

    private boolean executeAudioToggle(CommandSender sender, @Nullable String otherName) {
        Player target;
        if (otherName != null && !otherName.isBlank()) {
            if (!isAuthorized(sender, "chatgames.command.audio.others")) {
                reply(sender, registry.lang().text("system.permission-denied", "&cNo permission."));
                return true;
            }
            target = Bukkit.getPlayerExact(otherName);
            if (target == null) {
                reply(sender, registry.lang().text("system.competitor-not-found", "&cPlayer not found.").replace("{player}", otherName));
                return true;
            }
        } else {
            if (!(sender instanceof Player p)) {
                reply(sender, registry.lang().text("system.players-only", "&cPlayers only."));
                return true;
            }
            if (!isAuthorized(sender, "chatgames.command.audio")) {
                reply(sender, registry.lang().text("system.permission-denied", "&cNo permission."));
                return true;
            }
            target = p;
        }

        CompetitorAccount account = repository.resolveAccount(target.getUniqueId(), target.getName());
        boolean nextState = !account.isAudioEnabled();
        account.setAudioEnabled(nextState);
        repository.persistAsync(account, false);

        String label = registry.lang().text(nextState ? "system.status-enabled" : "system.status-disabled", nextState ? "ENABLED" : "DISABLED");
        reply(sender, registry.lang().text("preferences.audio-toggled", "&7Audio: {state}").replace("{state}", label));
        return true;
    }

    private boolean executeSchedulerToggle(CommandSender sender) {
        if (!isAuthorized(sender, "chatgames.command.scheduler")) {
            reply(sender, registry.lang().text("system.permission-denied", "&cNo permission."));
            return true;
        }
        orchestrator.toggleScheduler();
        boolean active = orchestrator.isSchedulerActive();
        String label = registry.lang().text(active ? "system.status-enabled" : "system.status-disabled", active ? "ENABLED" : "DISABLED");
        reply(sender, registry.lang().text("preferences.scheduler-toggled", "&7Scheduler: {state}").replace("{state}", label));
        return true;
    }

    private boolean executeHistoryView(CommandSender sender, String rawCount) {
        if (!isAuthorized(sender, "chatgames.command.history")) {
            reply(sender, registry.lang().text("system.permission-denied", "&cNo permission."));
            return true;
        }
        if (!auditLogger.isDiskPersistenceEnabled()) {
            reply(sender, registry.lang().text("history.disabled", "&cHistory logging disabled."));
            return true;
        }
        int limit = 5;
        try {
            limit = Math.max(1, Math.min(50, Integer.parseInt(rawCount)));
        } catch (NumberFormatException ignored) {
        }
        List<String> entries = auditLogger.readRecentEntries(limit);
        if (entries.isEmpty()) {
            reply(sender, registry.lang().text("history.empty", "&cNo history entries."));
            return true;
        }
        reply(sender, registry.lang().text("history.header", "&6Recent {count} entries:").replace("{count}", String.valueOf(entries.size())));
        for (String entry : entries) {
            reply(sender, "&7" + entry);
        }
        return true;
    }

    private void executeTokenClaim(CommandSender sender, String token) {
        if (!(sender instanceof Player player)) {
            return;
        }
        ChatPuzzleSession puzzle = orchestrator.getActivePuzzle();
        if (puzzle != null && puzzle.getCategory() == ChallengeCategory.CLICK_RUSH && token.equals(puzzle.getInteractionToken())) {
            if (orchestrator.canParticipate(player)) {
                orchestrator.recordRoundVictory(player);
            }
        }
    }

    private boolean executeStartRound(CommandSender sender, String rawCategory, String[] extraArgs) {
        if (sender instanceof Player player) {
            List<String> restricted = plugin.getConfig().getStringList("engine.restricted-worlds");
            if (restricted.contains(player.getWorld().getName())) {
                boolean allowAdmin = plugin.getConfig().getBoolean("engine.allow-admin-start-in-restricted-worlds", true);
                if (!allowAdmin && !isAuthorized(sender, "chatgames.command.start.*")) {
                    reply(sender, registry.lang().text("system.world-restricted", "&cRestricted world."));
                    return true;
                }
            }
        }

        if (rawCategory.equalsIgnoreCase("random") || rawCategory.equalsIgnoreCase("any")) {
            if (!isAuthorized(sender, "chatgames.command.start.*")) {
                reply(sender, registry.lang().text("system.permission-denied", "&cNo permission."));
                return true;
            }
            if (orchestrator.triggerRandomRound()) {
                reply(sender, registry.lang().text("system.round-launched", "&aRound launched!").replace("{category}", "random"));
            } else {
                reply(sender, registry.lang().text("system.invalid-syntax", "&cUnable to launch round."));
            }
            return true;
        }

        ChallengeCategory category = ChallengeCategory.resolve(rawCategory);
        if (category == null) {
            reply(sender, registry.lang().text("system.unknown-subcommand", "&cUnknown category."));
            return true;
        }

        String permNode = "chatgames.command.start." + category.getId();
        if (!isAuthorized(sender, permNode) && !isAuthorized(sender, "chatgames.command.start.*")) {
            reply(sender, registry.lang().text("system.permission-denied", "&cNo permission."));
            return true;
        }

        if (orchestrator.triggerRound(category, extraArgs)) {
            reply(sender, registry.lang().text("system.round-launched", "&aRound launched!").replace("{category}", category.getId()));
        } else {
            reply(sender, registry.lang().text("system.invalid-syntax", "&cCould not launch round with given parameters."));
        }
        return true;
    }

    private OfflinePlayer locatePlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        if (offline.getName() != null || offline.hasPlayedBefore()) {
            return offline;
        }
        return null;
    }

    @Override
    @Nullable
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> suggestions = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (String sub : ROOT_SUBCOMMANDS) {
                if (sub.startsWith(prefix)) {
                    suggestions.add(sub);
                }
            }
            return suggestions;
        }

        String root = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && root.equals("start")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            if ("random".startsWith(prefix)) {
                suggestions.add("random");
            }
            for (ChallengeCategory cat : ChallengeCategory.values()) {
                if (cat.getId().startsWith(prefix)) {
                    suggestions.add(cat.getId());
                }
            }
            return suggestions;
        }

        if (args.length == 2 && (root.equals("score") || root.equals("points"))) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            for (String op : List.of("add", "take", "set", "reset")) {
                if (op.startsWith(prefix)) {
                    suggestions.add(op);
                }
            }
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    suggestions.add(online.getName());
                }
            }
            return suggestions;
        }

        if (args.length == 2 && (root.equals("visibility") || root.equals("toggle") || root.equals("audio") || root.equals("sound"))) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    suggestions.add(online.getName());
                }
            }
            return suggestions;
        }

        if (args.length == 3 && (root.equals("score") || root.equals("points"))) {
            String prefix = args[2].toLowerCase(Locale.ROOT);
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    suggestions.add(online.getName());
                }
            }
            return suggestions;
        }

        return Collections.emptyList();
    }
}
