package io.github.lino9999.chatgames.config;

import org.bukkit.plugin.java.JavaPlugin;

public class ConfigurationRegistry {
    private final JavaPlugin plugin;
    private final ConfigDocument langDoc;
    private final ConfigDocument contentDoc;
    private final ConfigDocument challengesDoc;
    private final ConfigDocument prizesDoc;

    public ConfigurationRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
        this.langDoc = new ConfigDocument(plugin, "lang.yml");
        this.contentDoc = new ConfigDocument(plugin, "content.yml");
        this.challengesDoc = new ConfigDocument(plugin, "challenges.yml");
        this.prizesDoc = new ConfigDocument(plugin, "prizes.yml");
    }

    public boolean reloadAll() {
        plugin.reloadConfig();
        boolean r1 = langDoc.reload();
        boolean r2 = contentDoc.reload();
        boolean r3 = challengesDoc.reload();
        boolean r4 = prizesDoc.reload();
        return r1 && r2 && r3 && r4;
    }

    public String prefix() {
        return plugin.getConfig().getString("engine.prefix", "&8[&6&lChatGames&8]");
    }

    public ConfigDocument lang() {
        return langDoc;
    }

    public ConfigDocument content() {
        return contentDoc;
    }

    public ConfigDocument challenges() {
        return challengesDoc;
    }

    public ConfigDocument prizes() {
        return prizesDoc;
    }
}
