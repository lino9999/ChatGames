package io.github.lino9999.chatgames.session;

import io.github.lino9999.chatgames.api.ChallengeCategory;
import io.github.lino9999.chatgames.api.PodiumRecord;
import io.github.lino9999.chatgames.api.event.RoundBeginEvent;
import io.github.lino9999.chatgames.api.event.RoundCompleteEvent;
import io.github.lino9999.chatgames.config.ConfigurationRegistry;
import io.github.lino9999.chatgames.persistence.CompetitorAccount;
import io.github.lino9999.chatgames.persistence.CompetitorRepository;
import io.github.lino9999.chatgames.prize.PrizeEngine;
import io.github.lino9999.chatgames.text.ChatRenderer;
import io.github.lino9999.chatgames.text.NameBeautifier;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class RoundOrchestrator {
    private final JavaPlugin plugin;
    private final ConfigurationRegistry registry;
    private final CompetitorRepository repository;
    private final PrizeEngine prizeEngine;

    private volatile ChatPuzzleSession activePuzzle;
    private volatile WorldChallengeSession activeChallenge;
    private BukkitTask roundTimerTask;
    private BukkitTask schedulerTask;
    private boolean schedulerActive;

    public RoundOrchestrator(JavaPlugin plugin, ConfigurationRegistry registry, CompetitorRepository repository, PrizeEngine prizeEngine) {
        this.plugin = plugin;
        this.registry = registry;
        this.repository = repository;
        this.prizeEngine = prizeEngine;
    }

    public synchronized boolean hasActiveRound() {
        return activePuzzle != null || activeChallenge != null;
    }

    public ChatPuzzleSession getActivePuzzle() {
        return activePuzzle;
    }

    public WorldChallengeSession getActiveChallenge() {
        return activeChallenge;
    }

    public synchronized boolean abortCurrentRound() {
        boolean hadRound = hasActiveRound();
        if (roundTimerTask != null) {
            roundTimerTask.cancel();
            roundTimerTask = null;
        }
        activePuzzle = null;
        activeChallenge = null;
        return hadRound;
    }

    public synchronized boolean triggerRound(ChallengeCategory category, String... arguments) {
        if (category == null) {
            return false;
        }
        abortCurrentRound();
        if (category.isWorldChallenge()) {
            return launchWorldChallenge(category, arguments);
        }
        return launchChatPuzzle(category, arguments);
    }

    public synchronized boolean triggerRandomRound() {
        List<ChallengeCategory> enabled = collectEnabledCategories();
        if (enabled.isEmpty()) {
            return false;
        }
        ChallengeCategory selected = enabled.get(ThreadLocalRandom.current().nextInt(enabled.size()));
        return triggerRound(selected);
    }

    public List<ChallengeCategory> collectEnabledCategories() {
        List<ChallengeCategory> available = new ArrayList<>();
        boolean challengesMaster = registry.challenges().flag("global.enabled", true);

        for (ChallengeCategory cat : ChallengeCategory.values()) {
            if (cat.isWorldChallenge()) {
                if (challengesMaster && registry.challenges().flag("categories." + cat.getId() + ".enabled", true)) {
                    available.add(cat);
                }
            } else {
                if (plugin.getConfig().getBoolean("modes." + cat.getId() + ".enabled", true)) {
                    available.add(cat);
                }
            }
        }
        return available;
    }

    private boolean launchChatPuzzle(ChallengeCategory category, String[] args) {
        String id = category.getId();
        String basePath = "modes." + id;
        int duration = plugin.getConfig().getInt(basePath + ".duration-seconds", 20);
        int podiumSlots = plugin.getConfig().getInt(basePath + ".podium-slots", 1);
        boolean strictCase = plugin.getConfig().getBoolean(basePath + ".strict-case", true);

        Set<String> validSolutions = new HashSet<>();
        String canonicalAnswer = "";
        String displayPrompt = "";
        String clickToken = null;
        SymbolicEquation algebraData = null;

        switch (category) {
            case SCRAMBLE, REVERSE, TYPE_RACES, MISSING_CHARS -> {
                String chosenPhrase = (args.length > 0 && !args[0].isBlank())
                        ? String.join(" ", args)
                        : pickRandomBankPhrase(id);
                if (chosenPhrase.isEmpty()) {
                    return false;
                }
                canonicalAnswer = chosenPhrase;
                validSolutions.add(chosenPhrase);
                displayPrompt = switch (category) {
                    case SCRAMBLE -> PuzzleGenerator.permutePhrase(chosenPhrase);
                    case REVERSE -> PuzzleGenerator.invertPhrase(chosenPhrase);
                    case MISSING_CHARS -> {
                        int pct = plugin.getConfig().getInt(basePath + ".mask-percentage", 35);
                        yield PuzzleGenerator.obscureCharacters(chosenPhrase, pct);
                    }
                    default -> chosenPhrase;
                };
            }
            case CIPHER_STRING -> {
                int minL = plugin.getConfig().getInt(basePath + ".min-length", 6);
                int maxL = plugin.getConfig().getInt(basePath + ".max-length", 8);
                if (args.length > 0) {
                    try {
                        int customLen = Integer.parseInt(args[0]);
                        minL = customLen;
                        maxL = customLen;
                    } catch (NumberFormatException ignored) {
                    }
                }
                String preset = plugin.getConfig().getString(basePath + ".charset-preset", "ALPHANUMERIC");
                String customAlpha = plugin.getConfig().getString(basePath + ".custom-charset", "");
                String code = PuzzleGenerator.synthesizeCipher(minL, maxL, preset, customAlpha);
                canonicalAnswer = code;
                validSolutions.add(code);
                displayPrompt = code;
            }
            case ARITHMETIC -> {
                if (args.length > 0) {
                    String formula = String.join(" ", args);
                    try {
                        int evaluated = PuzzleGenerator.parseAndComputeArithmetic(formula);
                        canonicalAnswer = String.valueOf(evaluated);
                        validSolutions.add(canonicalAnswer);
                        displayPrompt = formula;
                    } catch (Exception e) {
                        return false;
                    }
                } else {
                    int tier = plugin.getConfig().getInt(basePath + ".complexity-tier", 2);
                    List<String> ops = plugin.getConfig().getStringList(basePath + ".allowed-operators");
                    var problem = PuzzleGenerator.buildArithmetic(tier, ops);
                    canonicalAnswer = problem.solution();
                    validSolutions.add(canonicalAnswer);
                    displayPrompt = problem.expression();
                }
            }
            case NUMBER_GUESS -> {
                int minNum = plugin.getConfig().getInt(basePath + ".min-number", 1);
                int maxNum = plugin.getConfig().getInt(basePath + ".max-number", 25);
                if (args.length >= 2) {
                    try {
                        int secret = Integer.parseInt(args[0]);
                        int spread = Math.max(1, Integer.parseInt(args[1]));
                        canonicalAnswer = String.valueOf(secret);
                        validSolutions.add(canonicalAnswer);
                        displayPrompt = (secret - spread) + " - " + (secret + spread);
                    } catch (NumberFormatException e) {
                        return false;
                    }
                } else {
                    int lo = Math.min(minNum, maxNum);
                    int hi = Math.max(minNum, maxNum);
                    int secret = ThreadLocalRandom.current().nextInt(lo, hi + 1);
                    canonicalAnswer = String.valueOf(secret);
                    validSolutions.add(canonicalAnswer);
                    displayPrompt = lo + " - " + hi;
                }
            }
            case MEMORY_LIST -> {
                int count = plugin.getConfig().getInt(basePath + ".sequence-length", 3);
                if (args.length > 0) {
                    try {
                        count = Integer.parseInt(args[0]);
                    } catch (NumberFormatException ignored) {
                    }
                }
                List<String> dict = registry.content().lines("word-banks.memory_list");
                List<String> sequence = PuzzleGenerator.sampleMemorySequence(dict, count);
                if (sequence.isEmpty()) {
                    return false;
                }
                canonicalAnswer = String.join(", ", sequence);
                validSolutions.add(canonicalAnswer);
                displayPrompt = canonicalAnswer;
            }
            case CLICK_RUSH -> {
                clickToken = PuzzleGenerator.synthesizeCipher(10, 10, "ALPHANUMERIC", null);
                canonicalAnswer = clickToken;
                validSolutions.add(clickToken);
            }
            case HOVER_REVEAL -> {
                String secretWord = (args.length > 0 && !args[0].isBlank())
                        ? String.join(" ", args)
                        : pickRandomBankPhrase("hover_reveal");
                if (secretWord.isEmpty()) {
                    return false;
                }
                canonicalAnswer = secretWord;
                validSolutions.add(secretWord);
                displayPrompt = secretWord;
            }
            case ALGEBRA -> {
                ConfigurationSection symSec = registry.content().section("algebra-symbols");
                int tMin = plugin.getConfig().getInt(basePath + ".target-min", 25);
                int tMax = plugin.getConfig().getInt(basePath + ".target-max", 100);
                String glyph = plugin.getConfig().getString(basePath + ".unknown-glyph", "?");
                String pairTpl = registry.lang().text("announcements.algebra.row-pair-template", "&d{symbol} &7+ &d{symbol} &7+ &d{symbol} &7= &e{sum}");
                String finalTpl = registry.lang().text("announcements.algebra.final-row-template", "&d{symbol_a} &7+ &d{symbol_b} &7+ &b{glyph} &7= &e{total}");
                algebraData = PuzzleGenerator.buildAlgebraSystem(symSec, tMin, tMax, glyph, pairTpl, finalTpl);
                canonicalAnswer = String.valueOf(algebraData.expectedValue());
                validSolutions.add(canonicalAnswer);
                displayPrompt = glyph;
            }
            case QUIZ -> {
                ConfigurationSection quizSec = registry.content().section("quiz-bank");
                if (quizSec == null) {
                    return false;
                }
                String qKey = (args.length > 0 && quizSec.isConfigurationSection(args[0]))
                        ? args[0]
                        : pickRandomSectionKey(quizSec);
                if (qKey == null) {
                    return false;
                }
                displayPrompt = quizSec.getString(qKey + ".prompt", "");
                List<String> accepted = quizSec.getStringList(qKey + ".accepted-answers");
                if (accepted.isEmpty()) {
                    return false;
                }
                canonicalAnswer = accepted.get(0);
                validSolutions.addAll(accepted);
            }
            default -> {
                return false;
            }
        }

        ChatPuzzleSession session = new ChatPuzzleSession(
                category,
                validSolutions,
                canonicalAnswer,
                displayPrompt,
                podiumSlots,
                strictCase,
                duration,
                clickToken,
                algebraData
        );

        RoundBeginEvent beginEvent = new RoundBeginEvent(category, canonicalAnswer);
        Bukkit.getPluginManager().callEvent(beginEvent);
        if (beginEvent.isCancelled()) {
            return false;
        }

        this.activePuzzle = session;
        if (category == ChallengeCategory.MEMORY_LIST) {
            runMemoryListStages(session);
        } else {
            announcePuzzleBegin(session);
            armTimeoutTimer(duration, false);
        }
        return true;
    }

    private void runMemoryListStages(ChatPuzzleSession session) {
        int memorizeSecs = plugin.getConfig().getInt("modes.memory_list.memorize-seconds", 7);
        int recallSecs = session.getDurationSeconds();
        String prefix = registry.prefix();

        List<String> beginLines = registry.lang().lines("announcements.memory_list.begin");
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!canParticipate(player)) {
                continue;
            }
            emitAudioCue(player, "round-begin");
            for (String line : beginLines) {
                String hydrated = line.replace("{prompt}", session.getPromptText())
                                      .replace("{memorize}", String.valueOf(memorizeSecs))
                                      .replace("{duration}", String.valueOf(recallSecs));
                ChatRenderer.deliver(player, hydrated, prefix);
            }
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (activePuzzle != session) {
                return;
            }
            List<String> recallLines = registry.lang().lines("announcements.memory_list.recall-phase");
            String title = plugin.getConfig().getString("modes.memory_list.banner.title", "").replace("{duration}", String.valueOf(recallSecs));
            String subtitle = plugin.getConfig().getString("modes.memory_list.banner.subtitle", "").replace("{duration}", String.valueOf(recallSecs));

            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!canParticipate(player)) {
                    continue;
                }
                for (int i = 0; i < 85; i++) {
                    player.sendMessage("");
                }
                if (!title.isEmpty() || !subtitle.isEmpty()) {
                    player.sendTitle(ChatRenderer.format(title, prefix), ChatRenderer.format(subtitle, prefix), 10, 40, 10);
                }
                for (String line : recallLines) {
                    String hydrated = line.replace("{prompt}", session.getPromptText())
                                          .replace("{duration}", String.valueOf(recallSecs));
                    ChatRenderer.deliver(player, hydrated, prefix);
                }
            }
            armTimeoutTimer(recallSecs, false);
        }, Math.max(1, memorizeSecs) * 20L);
    }

    private boolean launchWorldChallenge(ChallengeCategory category, String[] args) {
        String id = category.getId();
        String catPath = "categories." + id;
        int duration = registry.challenges().number(catPath + ".duration-seconds", 90);

        Material targetMat = null;
        EntityType targetEntity = null;
        String prettyName = "";
        int quota = 1;
        List<String> poolIds = Collections.emptyList();

        ConfigurationSection targetsSec = registry.challenges().section(catPath + ".targets");
        if (targetsSec == null) {
            return false;
        }

        if (args.length >= 2) {
            String rawTarget = args[0].toUpperCase(Locale.ROOT);
            try {
                quota = Math.max(1, Integer.parseInt(args[1]));
            } catch (NumberFormatException e) {
                return false;
            }
            if (category == ChallengeCategory.SLAYER) {
                try {
                    targetEntity = EntityType.valueOf(rawTarget);
                    prettyName = NameBeautifier.prettifyEntity(targetEntity);
                } catch (IllegalArgumentException e) {
                    return false;
                }
            } else {
                targetMat = Material.matchMaterial(rawTarget);
                if (targetMat == null) {
                    return false;
                }
                prettyName = NameBeautifier.prettifyMaterial(targetMat);
            }
            poolIds = registry.challenges().lines(catPath + ".targets." + rawTarget + ".quotas." + quota + ".prize-pools");
        } else {
            String targetKey = pickRandomSectionKey(targetsSec);
            if (targetKey == null) {
                return false;
            }
            ConfigurationSection quotasSec = targetsSec.getConfigurationSection(targetKey + ".quotas");
            if (quotasSec == null) {
                return false;
            }
            String quotaKey = pickRandomSectionKey(quotasSec);
            if (quotaKey == null) {
                return false;
            }
            try {
                quota = Math.max(1, Integer.parseInt(quotaKey));
            } catch (NumberFormatException e) {
                quota = 1;
            }

            if (category == ChallengeCategory.SLAYER) {
                try {
                    targetEntity = EntityType.valueOf(targetKey);
                    prettyName = NameBeautifier.prettifyEntity(targetEntity);
                } catch (IllegalArgumentException e) {
                    return false;
                }
            } else {
                targetMat = Material.matchMaterial(targetKey);
                if (targetMat == null) {
                    return false;
                }
                prettyName = NameBeautifier.prettifyMaterial(targetMat);
            }
            poolIds = quotasSec.getStringList(quotaKey + ".prize-pools");
        }

        WorldChallengeSession session = new WorldChallengeSession(
                category,
                targetMat,
                targetEntity,
                prettyName,
                quota,
                duration,
                poolIds
        );

        RoundBeginEvent beginEvent = new RoundBeginEvent(category, prettyName);
        Bukkit.getPluginManager().callEvent(beginEvent);
        if (beginEvent.isCancelled()) {
            return false;
        }

        this.activeChallenge = session;
        announceChallengeBegin(session);
        armTimeoutTimer(duration, true);
        return true;
    }

    private void armTimeoutTimer(int durationSeconds, boolean challengeMode) {
        if (roundTimerTask != null) {
            roundTimerTask.cancel();
        }
        roundTimerTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (challengeMode && activeChallenge != null) {
                concludeChallengeTimeout(activeChallenge);
            } else if (!challengeMode && activePuzzle != null) {
                concludePuzzleTimeout(activePuzzle);
            }
        }, Math.max(1, durationSeconds) * 20L);
    }

    public synchronized void recordRoundVictory(Player victor) {
        if (activeChallenge != null) {
            WorldChallengeSession session = activeChallenge;
            activeChallenge = null;
            if (roundTimerTask != null) {
                roundTimerTask.cancel();
                roundTimerTask = null;
            }

            double elapsed = session.computeElapsedSeconds();
            PodiumRecord winnerRecord = new PodiumRecord(1, victor.getUniqueId(), victor.getName(), elapsed);

            CompetitorAccount account = repository.resolveAccount(victor.getUniqueId(), victor.getName());
            account.modifyScore(1);
            repository.persistAsync(account, false);

            announceChallengeVictory(session, victor, elapsed);
            prizeEngine.grantChallengePrizes(session.getPrizePoolIds(), victor);

            RoundCompleteEvent completeEvent = new RoundCompleteEvent(session.getCategory(), session.getFormattedTargetName(), List.of(winnerRecord));
            Bukkit.getPluginManager().callEvent(completeEvent);
        } else if (activePuzzle != null) {
            ChatPuzzleSession session = activePuzzle;
            PodiumRecord record = session.claimPodiumSpot(victor.getUniqueId(), victor.getName());
            if (record == null) {
                return;
            }

            CompetitorAccount account = repository.resolveAccount(victor.getUniqueId(), victor.getName());
            account.modifyScore(1);
            if (plugin.getConfig().getBoolean("engine.speed-leaderboard-enabled", true)) {
                account.recordReactionTime(record.elapsedSeconds());
            }
            repository.persistAsync(account, false);

            prizeEngine.grantPuzzlePrizes(session.getCategory().getId(), record.position(), victor);

            if (session.isPodiumFull()) {
                activePuzzle = null;
                if (roundTimerTask != null) {
                    roundTimerTask.cancel();
                    roundTimerTask = null;
                }
                announcePuzzleVictory(session);
                RoundCompleteEvent completeEvent = new RoundCompleteEvent(session.getCategory(), session.getCanonicalAnswer(), session.getPodium());
                Bukkit.getPluginManager().callEvent(completeEvent);
            } else {
                String waitNotice = registry.lang().text("gameplay.awaiting-remaining-winners", "");
                if (!waitNotice.isBlank()) {
                    ChatRenderer.deliver(victor, waitNotice, registry.prefix());
                }
            }
        }
    }

    private void concludePuzzleTimeout(ChatPuzzleSession session) {
        activePuzzle = null;
        if (roundTimerTask != null) {
            roundTimerTask.cancel();
            roundTimerTask = null;
        }
        if (!session.getPodium().isEmpty()) {
            announcePuzzleVictory(session);
            Bukkit.getPluginManager().callEvent(new RoundCompleteEvent(session.getCategory(), session.getCanonicalAnswer(), session.getPodium()));
        } else {
            announcePuzzleTimeout(session);
            Bukkit.getPluginManager().callEvent(new RoundCompleteEvent(session.getCategory(), session.getCanonicalAnswer(), Collections.emptyList()));
        }
    }

    private void concludeChallengeTimeout(WorldChallengeSession session) {
        activeChallenge = null;
        if (roundTimerTask != null) {
            roundTimerTask.cancel();
            roundTimerTask = null;
        }
        announceChallengeTimeout(session);
        Bukkit.getPluginManager().callEvent(new RoundCompleteEvent(session.getCategory(), session.getFormattedTargetName(), Collections.emptyList()));
    }

    private void announcePuzzleBegin(ChatPuzzleSession session) {
        String id = session.getCategory().getId();
        String prefix = registry.prefix();
        List<String> lines = registry.lang().lines("announcements." + id + ".begin");
        String title = plugin.getConfig().getString("modes." + id + ".banner.title", "");
        String subtitle = plugin.getConfig().getString("modes." + id + ".banner.subtitle", "");

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!canParticipate(player)) {
                continue;
            }
            emitAudioCue(player, "round-begin");

            if (session.getCategory() == ChallengeCategory.ALGEBRA && session.getAlgebraData() != null) {
                SymbolicEquation eq = session.getAlgebraData();
                for (String raw : lines) {
                    String hydrated = raw.replace("{eq_line_1}", eq.equationRows().get(0))
                                         .replace("{eq_line_2}", eq.equationRows().get(1))
                                         .replace("{eq_line_3}", eq.equationRows().get(2))
                                         .replace("{glyph}", eq.unknownGlyph())
                                         .replace("{duration}", String.valueOf(session.getDurationSeconds()));
                    ChatRenderer.deliver(player, hydrated, prefix);
                }
            } else if (session.getCategory() == ChallengeCategory.CLICK_RUSH) {
                String btnLabel = registry.lang().text("announcements.click_rush.button-label", "&a&l[CLICK TO WIN]");
                String btnTooltip = registry.lang().text("announcements.click_rush.button-tooltip", "&eClick now!");
                TextComponent button = new TextComponent(TextComponent.fromLegacyText(ChatRenderer.format(btnLabel, prefix)));
                button.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cg claim " + session.getInteractionToken()));
                button.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(ChatRenderer.format(btnTooltip, prefix)).create()));

                for (String raw : lines) {
                    if (raw.contains("{interactive_button}")) {
                        ChatRenderer.deliverInteractive(player, raw.replace("{interactive_button}", ""), button, prefix);
                    } else {
                        ChatRenderer.deliver(player, raw.replace("{duration}", String.valueOf(session.getDurationSeconds())), prefix);
                    }
                }
            } else if (session.getCategory() == ChallengeCategory.HOVER_REVEAL) {
                String hoverLabel = registry.lang().text("announcements.hover_reveal.hover-label", "&e&l[HOVER HERE]");
                String hoverTooltip = registry.lang().text("announcements.hover_reveal.hover-tooltip", "&b{prompt}").replace("{prompt}", session.getPromptText());
                TextComponent badge = new TextComponent(TextComponent.fromLegacyText(ChatRenderer.format(hoverLabel, prefix)));
                badge.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(ChatRenderer.format(hoverTooltip, prefix)).create()));

                for (String raw : lines) {
                    if (raw.contains("{interactive_hover}")) {
                        ChatRenderer.deliverInteractive(player, raw.replace("{interactive_hover}", ""), badge, prefix);
                    } else {
                        ChatRenderer.deliver(player, raw.replace("{duration}", String.valueOf(session.getDurationSeconds())), prefix);
                    }
                }
            } else {
                for (String raw : lines) {
                    String hydrated = raw.replace("{prompt}", session.getPromptText())
                                         .replace("{duration}", String.valueOf(session.getDurationSeconds()));
                    ChatRenderer.deliver(player, hydrated, prefix);
                }
            }

            if (!title.isEmpty() || !subtitle.isEmpty()) {
                String t = title.replace("{prompt}", session.getPromptText())
                                .replace("{glyph}", session.getPromptText())
                                .replace("{duration}", String.valueOf(session.getDurationSeconds()));
                String s = subtitle.replace("{prompt}", session.getPromptText())
                                   .replace("{glyph}", session.getPromptText())
                                   .replace("{duration}", String.valueOf(session.getDurationSeconds()));
                player.sendTitle(ChatRenderer.format(t, prefix), ChatRenderer.format(s, prefix), 10, 40, 10);
            }
        }
    }

    private void announcePuzzleVictory(ChatPuzzleSession session) {
        String id = session.getCategory().getId();
        String prefix = registry.prefix();
        List<String> lines = registry.lang().lines("announcements." + id + ".victory");
        List<PodiumRecord> podium = session.getPodium();
        String emptySlot = registry.lang().text("system.empty-podium-slot", "---");

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!canParticipate(player)) {
                continue;
            }
            emitAudioCue(player, "round-win");
            for (String raw : lines) {
                String line = raw.replace("{answer}", session.getCanonicalAnswer())
                                 .replace("{glyph}", session.getPromptText());
                for (int slot = 1; slot <= session.getMaxPodiumSlots(); slot++) {
                    if (slot <= podium.size()) {
                        PodiumRecord rec = podium.get(slot - 1);
                        int score = repository.resolveAccount(rec.competitorId(), rec.competitorName()).getScore();
                        line = line.replace("{winner_" + slot + "}", rec.competitorName())
                                   .replace("{time_" + slot + "}", String.valueOf(rec.elapsedSeconds()))
                                   .replace("{score_" + slot + "}", String.valueOf(score));
                    } else {
                        line = line.replace("{winner_" + slot + "}", emptySlot)
                                   .replace("{time_" + slot + "}", emptySlot)
                                   .replace("{score_" + slot + "}", emptySlot);
                    }
                }
                if (!podium.isEmpty()) {
                    PodiumRecord top = podium.get(0);
                    line = line.replace("{winner}", top.competitorName())
                               .replace("{elapsed}", String.valueOf(top.elapsedSeconds()));
                }
                ChatRenderer.deliver(player, line, prefix);
            }
        }
    }

    private void announcePuzzleTimeout(ChatPuzzleSession session) {
        String id = session.getCategory().getId();
        String prefix = registry.prefix();
        List<String> lines = registry.lang().lines("announcements." + id + ".timeout");

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!canParticipate(player)) {
                continue;
            }
            emitAudioCue(player, "round-timeout");
            for (String raw : lines) {
                String line = raw.replace("{answer}", session.getCanonicalAnswer())
                                 .replace("{glyph}", session.getPromptText())
                                 .replace("{duration}", String.valueOf(session.getDurationSeconds()));
                ChatRenderer.deliver(player, line, prefix);
            }
        }
    }

    private void announceChallengeBegin(WorldChallengeSession session) {
        String id = session.getCategory().getId();
        String prefix = registry.prefix();
        List<String> lines = registry.lang().lines("announcements." + id + ".begin");
        String title = registry.challenges().text("categories." + id + ".banner.title", "");
        String subtitle = registry.challenges().text("categories." + id + ".banner.subtitle", "");

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!canParticipate(player)) {
                continue;
            }
            emitAudioCue(player, "round-begin");
            for (String raw : lines) {
                String line = raw.replace("{quota}", String.valueOf(session.getRequiredQuota()))
                                 .replace("{target}", session.getFormattedTargetName())
                                 .replace("{duration}", String.valueOf(session.getDurationSeconds()));
                ChatRenderer.deliver(player, line, prefix);
            }
            if (!title.isEmpty() || !subtitle.isEmpty()) {
                String t = title.replace("{quota}", String.valueOf(session.getRequiredQuota()))
                                .replace("{target}", session.getFormattedTargetName());
                String s = subtitle.replace("{quota}", String.valueOf(session.getRequiredQuota()))
                                   .replace("{target}", session.getFormattedTargetName());
                player.sendTitle(ChatRenderer.format(t, prefix), ChatRenderer.format(s, prefix), 10, 40, 10);
            }
        }
    }

    private void announceChallengeVictory(WorldChallengeSession session, Player winner, double elapsed) {
        String id = session.getCategory().getId();
        String prefix = registry.prefix();
        List<String> lines = registry.lang().lines("announcements." + id + ".victory");
        int newScore = repository.resolveAccount(winner.getUniqueId(), winner.getName()).getScore();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!canParticipate(player)) {
                continue;
            }
            emitAudioCue(player, "round-win");
            for (String raw : lines) {
                String line = raw.replace("{winner}", winner.getName())
                                 .replace("{quota}", String.valueOf(session.getRequiredQuota()))
                                 .replace("{target}", session.getFormattedTargetName())
                                 .replace("{elapsed}", String.valueOf(elapsed))
                                 .replace("{score}", String.valueOf(newScore));
                ChatRenderer.deliver(player, line, prefix);
            }
        }
    }

    private void announceChallengeTimeout(WorldChallengeSession session) {
        String id = session.getCategory().getId();
        String prefix = registry.prefix();
        List<String> lines = registry.lang().lines("announcements." + id + ".timeout");

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!canParticipate(player)) {
                continue;
            }
            emitAudioCue(player, "round-timeout");
            for (String raw : lines) {
                String line = raw.replace("{quota}", String.valueOf(session.getRequiredQuota()))
                                 .replace("{target}", session.getFormattedTargetName())
                                 .replace("{duration}", String.valueOf(session.getDurationSeconds()));
                ChatRenderer.deliver(player, line, prefix);
            }
        }
    }

    public boolean canParticipate(Player player) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        List<String> restricted = plugin.getConfig().getStringList("engine.restricted-worlds");
        if (restricted.contains(player.getWorld().getName())) {
            return false;
        }
        if (plugin.getConfig().getBoolean("engine.require-participation-permission", false)
                && !player.hasPermission("chatgames.participate")) {
            return false;
        }
        CompetitorAccount account = repository.resolveAccount(player.getUniqueId(), player.getName());
        return account.isChatVisible();
    }

    public void emitAudioCue(Player player, String cueKey) {
        CompetitorAccount account = repository.resolveAccount(player.getUniqueId(), player.getName());
        if (!account.isAudioEnabled()) {
            return;
        }
        String soundId = plugin.getConfig().getString("audio.cues." + cueKey);
        if (soundId == null || soundId.isBlank()) {
            return;
        }
        float volume = (float) plugin.getConfig().getDouble("audio.master-volume", 0.6);
        float pitch = (float) plugin.getConfig().getDouble("audio.master-pitch", 1.0);
        try {
            player.playSound(player.getLocation(), soundId, volume, pitch);
        } catch (Exception ignored) {
        }
    }

    public void startScheduler() {
        stopScheduler();
        if (!plugin.getConfig().getBoolean("scheduler.enabled", true)) {
            return;
        }
        int baseSeconds = plugin.getConfig().getInt("scheduler.interval-seconds", 300);
        if (baseSeconds <= 0) {
            return;
        }
        schedulerActive = true;
        queueNextScheduledRound();
    }

    public void stopScheduler() {
        schedulerActive = false;
        if (schedulerTask != null) {
            schedulerTask.cancel();
            schedulerTask = null;
        }
    }

    public boolean isSchedulerActive() {
        return schedulerActive;
    }

    public void toggleScheduler() {
        if (schedulerActive) {
            stopScheduler();
        } else {
            schedulerActive = true;
            queueNextScheduledRound();
        }
    }

    private void queueNextScheduledRound() {
        if (!schedulerActive) {
            return;
        }
        long waitSeconds;
        if (plugin.getConfig().getBoolean("scheduler.randomized-interval.enabled", false)) {
            int minS = Math.max(5, plugin.getConfig().getInt("scheduler.randomized-interval.min-seconds", 120));
            int maxS = Math.max(minS, plugin.getConfig().getInt("scheduler.randomized-interval.max-seconds", 600));
            waitSeconds = ThreadLocalRandom.current().nextInt(minS, maxS + 1);
        } else {
            waitSeconds = Math.max(5, plugin.getConfig().getInt("scheduler.interval-seconds", 300));
        }

        schedulerTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!schedulerActive) {
                return;
            }
            int minOnline = plugin.getConfig().getInt("scheduler.minimum-online-players", 1);
            if (!hasActiveRound() && countEligibleOnlinePlayers() >= minOnline) {
                triggerRandomRound();
            }
            queueNextScheduledRound();
        }, waitSeconds * 20L);
    }

    private int countEligibleOnlinePlayers() {
        boolean includeVanished = plugin.getConfig().getBoolean("scheduler.include-vanished-players", false);
        int count = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!includeVanished && isPlayerVanished(player)) {
                continue;
            }
            if (canParticipate(player)) {
                count++;
            }
        }
        return count;
    }

    private boolean isPlayerVanished(Player player) {
        for (MetadataValue meta : player.getMetadata("vanished")) {
            if (meta.asBoolean()) {
                return true;
            }
        }
        return false;
    }

    private String pickRandomBankPhrase(String bankKey) {
        List<String> phrases = registry.content().lines("word-banks." + bankKey);
        if (phrases == null || phrases.isEmpty()) {
            phrases = registry.content().lines("word-banks.scramble");
        }
        if (phrases == null || phrases.isEmpty()) {
            return "";
        }
        return phrases.get(ThreadLocalRandom.current().nextInt(phrases.size()));
    }

    private String pickRandomSectionKey(ConfigurationSection section) {
        Set<String> keys = section.getKeys(false);
        if (keys.isEmpty()) {
            return null;
        }
        List<String> list = new ArrayList<>(keys);
        return list.get(ThreadLocalRandom.current().nextInt(list.size()));
    }
}
