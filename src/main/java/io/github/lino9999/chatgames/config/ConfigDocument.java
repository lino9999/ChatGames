package io.github.lino9999.chatgames.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;

public class ConfigDocument {
    private final JavaPlugin plugin;
    private final String resourceName;
    private final File file;
    private FileConfiguration yaml;

    public ConfigDocument(JavaPlugin plugin, String resourceName) {
        this.plugin = plugin;
        this.resourceName = resourceName;
        this.file = new File(plugin.getDataFolder(), resourceName);
        ensureExistsAndLoad();
    }

    private void ensureExistsAndLoad() {
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            plugin.saveResource(resourceName, false);
        }
        this.yaml = YamlConfiguration.loadConfiguration(file);
    }

    public boolean reload() {
        try {
            if (!file.exists()) {
                plugin.saveResource(resourceName, false);
            }
            this.yaml = YamlConfiguration.loadConfiguration(file);
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Unable to reload " + resourceName, e);
            return false;
        }
    }

    public void save() {
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Unable to save " + resourceName, e);
        }
    }

    public String text(String path, String fallback) {
        return yaml.getString(path, fallback);
    }

    public int number(String path, int fallback) {
        return yaml.getInt(path, fallback);
    }

    public double decimal(String path, double fallback) {
        return yaml.getDouble(path, fallback);
    }

    public boolean flag(String path, boolean fallback) {
        return yaml.getBoolean(path, fallback);
    }

    public List<String> lines(String path) {
        return yaml.getStringList(path);
    }

    public ConfigurationSection section(String path) {
        return yaml.getConfigurationSection(path);
    }

    public FileConfiguration raw() {
        return yaml;
    }
}
