package io.github.lino9999.chatgames;

import io.github.lino9999.chatgames.command.CommandRouter;
import io.github.lino9999.chatgames.config.ConfigurationRegistry;
import io.github.lino9999.chatgames.handler.ChatAttemptHandler;
import io.github.lino9999.chatgames.handler.SessionTelemetryHandler;
import io.github.lino9999.chatgames.handler.WorldActionHandler;
import io.github.lino9999.chatgames.hook.DiscordNotifier;
import io.github.lino9999.chatgames.hook.PlaceholderHook;
import io.github.lino9999.chatgames.persistence.CompetitorRepository;
import io.github.lino9999.chatgames.prize.PrizeEngine;
import io.github.lino9999.chatgames.session.RoundOrchestrator;
import io.github.lino9999.chatgames.text.AuditLogger;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class ChatGamesPlugin extends JavaPlugin {
    private ConfigurationRegistry configurationRegistry;
    private CompetitorRepository competitorRepository;
    private AuditLogger auditLogger;
    private DiscordNotifier discordNotifier;
    private PrizeEngine prizeEngine;
    private RoundOrchestrator roundOrchestrator;
    private PlaceholderHook placeholderHook;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.configurationRegistry = new ConfigurationRegistry(this);
        this.competitorRepository = new CompetitorRepository(this);
        this.auditLogger = new AuditLogger(this);
        this.discordNotifier = new DiscordNotifier(this);
        this.prizeEngine = new PrizeEngine(this, configurationRegistry);
        this.roundOrchestrator = new RoundOrchestrator(this, configurationRegistry, competitorRepository, prizeEngine);

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new ChatAttemptHandler(this, configurationRegistry, roundOrchestrator), this);
        pm.registerEvents(new WorldActionHandler(this, configurationRegistry, roundOrchestrator), this);
        pm.registerEvents(new SessionTelemetryHandler(competitorRepository, auditLogger, discordNotifier), this);

        CommandRouter router = new CommandRouter(this, configurationRegistry, roundOrchestrator, competitorRepository, auditLogger);
        PluginCommand mainCmd = getCommand("chatgames");
        if (mainCmd != null) {
            mainCmd.setExecutor(router);
            mainCmd.setTabCompleter(router);
        }

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            this.placeholderHook = new PlaceholderHook(competitorRepository, roundOrchestrator);
            this.placeholderHook.register();
            getLogger().info("PlaceholderAPI expansion registered.");
        }

        roundOrchestrator.startScheduler();
        getLogger().info("ChatGames v" + getDescription().getVersion() + " by Lino is now active.");
    }

    @Override
    public void onDisable() {
        if (roundOrchestrator != null) {
            roundOrchestrator.stopScheduler();
            roundOrchestrator.abortCurrentRound();
        }
        if (placeholderHook != null) {
            try {
                placeholderHook.unregister();
            } catch (Exception ignored) {
            }
        }
        if (competitorRepository != null) {
            competitorRepository.flushAllSync();
        }
        getLogger().info("ChatGames has been shut down cleanly.");
    }

    public ConfigurationRegistry getConfigurationRegistry() {
        return configurationRegistry;
    }

    public CompetitorRepository getCompetitorRepository() {
        return competitorRepository;
    }

    public RoundOrchestrator getRoundOrchestrator() {
        return roundOrchestrator;
    }

    public PrizeEngine getPrizeEngine() {
        return prizeEngine;
    }

    public AuditLogger getAuditLogger() {
        return auditLogger;
    }
}
