package io.github.lino9999.chatgames.text;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

public class AuditLogger {
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JavaPlugin plugin;
    private final File logFile;
    private final boolean consoleEcho;
    private final boolean diskPersistence;

    public AuditLogger(JavaPlugin plugin) {
        this.plugin = plugin;
        String fileName = plugin.getConfig().getString("audit.file-name", "round-history.log");
        this.logFile = new File(plugin.getDataFolder(), fileName);
        this.consoleEcho = plugin.getConfig().getBoolean("audit.console-logging", true);
        this.diskPersistence = plugin.getConfig().getBoolean("audit.file-logging", true);
        initializeFile();
    }

    private void initializeFile() {
        if (!diskPersistence) {
            return;
        }
        try {
            if (!logFile.exists()) {
                logFile.getParentFile().mkdirs();
                logFile.createNewFile();
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Unable to create round audit file", e);
        }
    }

    public boolean isDiskPersistenceEnabled() {
        return diskPersistence;
    }

    public void record(String summary) {
        if (consoleEcho) {
            plugin.getLogger().info(summary);
        }
        if (diskPersistence) {
            String stamped = "[" + LocalDateTime.now().format(TIMESTAMP_FORMAT) + "] " + summary;
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> appendLineSync(stamped));
        }
    }

    private synchronized void appendLineSync(String line) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, StandardCharsets.UTF_8, true))) {
            writer.write(line);
            writer.newLine();
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Unable to append audit entry", e);
        }
    }

    public List<String> readRecentEntries(int maxEntries) {
        if (!diskPersistence || !logFile.exists()) {
            return Collections.emptyList();
        }
        try {
            List<String> all = Files.readAllLines(logFile.toPath(), StandardCharsets.UTF_8);
            if (all.isEmpty()) {
                return Collections.emptyList();
            }
            int count = Math.min(Math.max(1, maxEntries), all.size());
            List<String> slice = new ArrayList<>(all.subList(all.size() - count, all.size()));
            Collections.reverse(slice);
            return slice;
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }
}
