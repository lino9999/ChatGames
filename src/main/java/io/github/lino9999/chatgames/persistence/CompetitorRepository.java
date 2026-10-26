package io.github.lino9999.chatgames.persistence;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class CompetitorRepository {
    private final JavaPlugin plugin;
    private final boolean mysqlMode;
    private final String tableName;
    private final String jdbcUri;
    private final String dbUser;
    private final String dbPass;
    private final Map<UUID, CompetitorAccount> accounts = new ConcurrentHashMap<>();

    public CompetitorRepository(JavaPlugin plugin) {
        this.plugin = plugin;
        String driverType = plugin.getConfig().getString("database.driver", "SQLITE");
        this.mysqlMode = driverType.equalsIgnoreCase("MYSQL");
        String prefix = plugin.getConfig().getString("database.table-prefix", "cg_");
        this.tableName = prefix + "competitors";

        if (mysqlMode) {
            ConfigurationSection sec = plugin.getConfig().getConfigurationSection("database.mysql");
            String host = sec != null ? sec.getString("host", "127.0.0.1") : "127.0.0.1";
            int port = sec != null ? sec.getInt("port", 3306) : 3306;
            String db = sec != null ? sec.getString("database", "chatgames") : "chatgames";
            String params = sec != null ? sec.getString("parameters", "?autoReconnect=true&useSSL=false") : "";
            this.dbUser = sec != null ? sec.getString("username", "root") : "root";
            this.dbPass = sec != null ? sec.getString("password", "") : "";
            this.jdbcUri = "jdbc:mysql://" + host + ":" + port + "/" + db + params;
        } else {
            String sqliteFile = plugin.getConfig().getString("database.sqlite.file-name", "competitors.sqlite");
            if (!sqliteFile.endsWith(".sqlite") && !sqliteFile.endsWith(".db")) {
                sqliteFile = sqliteFile + ".sqlite";
            }
            File file = new File(plugin.getDataFolder(), sqliteFile);
            if (!file.exists()) {
                file.getParentFile().mkdirs();
            }
            this.dbUser = null;
            this.dbPass = null;
            this.jdbcUri = "jdbc:sqlite:" + file.getAbsolutePath();
        }

        bootstrapSchema();
    }

    private Connection openConnection() throws SQLException {
        if (mysqlMode) {
            return DriverManager.getConnection(jdbcUri, dbUser, dbPass);
        }
        return DriverManager.getConnection(jdbcUri);
    }

    private void bootstrapSchema() {
        try (Connection conn = openConnection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                    "player_uuid VARCHAR(36) NOT NULL, " +
                    "player_name VARCHAR(32) NOT NULL, " +
                    "total_score INT NOT NULL DEFAULT 0, " +
                    "chat_opt_in BOOLEAN NOT NULL DEFAULT 1, " +
                    "audio_opt_in BOOLEAN NOT NULL DEFAULT 1, " +
                    "best_reaction DOUBLE NOT NULL DEFAULT 0.0, " +
                    "PRIMARY KEY (player_uuid)" +
                    ");");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Database schema initialization failed", e);
        }
    }

    public CompetitorAccount resolveAccount(UUID uuid, String name) {
        CompetitorAccount existing = accounts.get(uuid);
        if (existing != null) {
            if (name != null && !name.isBlank() && !name.equals(existing.getPlayerName())) {
                existing.setPlayerName(name);
            }
            return existing;
        }
        CompetitorAccount loaded = queryAccount(uuid, name);
        accounts.put(uuid, loaded);
        return loaded;
    }

    public CompletableFuture<CompetitorAccount> preloadAsync(UUID uuid, String name) {
        return CompletableFuture.supplyAsync(() -> resolveAccount(uuid, name));
    }

    private CompetitorAccount queryAccount(UUID uuid, String fallbackName) {
        String sql = "SELECT player_name, total_score, chat_opt_in, audio_opt_in, best_reaction FROM " + tableName + " WHERE player_uuid = ?";
        try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String storedName = rs.getString("player_name");
                    String effectiveName = (fallbackName != null && !fallbackName.isBlank()) ? fallbackName : storedName;
                    return new CompetitorAccount(
                            uuid,
                            effectiveName,
                            rs.getInt("total_score"),
                            rs.getBoolean("chat_opt_in"),
                            rs.getBoolean("audio_opt_in"),
                            rs.getDouble("best_reaction")
                    );
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Unable to query competitor record: " + e.getMessage());
        }

        String initialName = (fallbackName != null && !fallbackName.isBlank()) ? fallbackName : "Player";
        CompetitorAccount created = new CompetitorAccount(uuid, initialName, 0, true, true, 0.0);
        persistAsync(created, true);
        return created;
    }

    public void persistAsync(CompetitorAccount account, boolean insertNew) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> writeAccountSync(account, insertNew));
    }

    private void writeAccountSync(CompetitorAccount account, boolean insertNew) {
        String sql;
        if (insertNew) {
            sql = mysqlMode
                    ? "INSERT IGNORE INTO " + tableName + " (player_uuid, player_name, total_score, chat_opt_in, audio_opt_in, best_reaction) VALUES (?, ?, ?, ?, ?, ?)"
                    : "INSERT OR IGNORE INTO " + tableName + " (player_uuid, player_name, total_score, chat_opt_in, audio_opt_in, best_reaction) VALUES (?, ?, ?, ?, ?, ?)";
        } else {
            sql = "UPDATE " + tableName + " SET player_name = ?, total_score = ?, chat_opt_in = ?, audio_opt_in = ?, best_reaction = ? WHERE player_uuid = ?";
        }
        try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            if (insertNew) {
                ps.setString(1, account.getPlayerUuid().toString());
                ps.setString(2, account.getPlayerName());
                ps.setInt(3, account.getScore());
                ps.setBoolean(4, account.isChatVisible());
                ps.setBoolean(5, account.isAudioEnabled());
                ps.setDouble(6, account.getBestReactionSeconds());
            } else {
                ps.setString(1, account.getPlayerName());
                ps.setInt(2, account.getScore());
                ps.setBoolean(3, account.isChatVisible());
                ps.setBoolean(4, account.isAudioEnabled());
                ps.setDouble(5, account.getBestReactionSeconds());
                ps.setString(6, account.getPlayerUuid().toString());
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Unable to persist competitor record: " + e.getMessage());
        }
    }

    public void deleteAccount(UUID uuid) {
        accounts.remove(uuid);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = openConnection();
                 PreparedStatement ps = conn.prepareStatement("DELETE FROM " + tableName + " WHERE player_uuid = ?")) {
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Unable to delete competitor record: " + e.getMessage());
            }
        });
    }

    public CompletableFuture<List<RankingSnapshot>> queryTopScores(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<RankingSnapshot> results = new ArrayList<>();
            String sql = "SELECT player_uuid, player_name, total_score, best_reaction FROM " + tableName + " WHERE total_score > 0 ORDER BY total_score DESC LIMIT ?";
            try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, Math.max(1, limit));
                try (ResultSet rs = ps.executeQuery()) {
                    int pos = 1;
                    while (rs.next()) {
                        UUID id = UUID.fromString(rs.getString("player_uuid"));
                        String name = resolveDisplayName(id, rs.getString("player_name"));
                        results.add(new RankingSnapshot(pos++, id, name, rs.getInt("total_score"), rs.getDouble("best_reaction")));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Unable to query top scores: " + e.getMessage());
            }
            return results;
        });
    }

    public CompletableFuture<List<RankingSnapshot>> queryTopSpeeds(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<RankingSnapshot> results = new ArrayList<>();
            String sql = "SELECT player_uuid, player_name, total_score, best_reaction FROM " + tableName + " WHERE best_reaction > 0.0 ORDER BY best_reaction ASC LIMIT ?";
            try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, Math.max(1, limit));
                try (ResultSet rs = ps.executeQuery()) {
                    int pos = 1;
                    while (rs.next()) {
                        UUID id = UUID.fromString(rs.getString("player_uuid"));
                        String name = resolveDisplayName(id, rs.getString("player_name"));
                        results.add(new RankingSnapshot(pos++, id, name, rs.getInt("total_score"), rs.getDouble("best_reaction")));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Unable to query top speeds: " + e.getMessage());
            }
            return results;
        });
    }

    public CompletableFuture<Integer> queryGlobalScoreSum() {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = openConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT COALESCE(SUM(total_score), 0) FROM " + tableName);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Unable to query global score sum: " + e.getMessage());
            }
            return 0;
        });
    }

    public CompletableFuture<Integer> queryScorePlacement(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) + 1 FROM " + tableName + " WHERE total_score > (SELECT COALESCE(total_score, 0) FROM " + tableName + " WHERE player_uuid = ?)";
            try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            } catch (SQLException ignored) {
            }
            return 0;
        });
    }

    public CompletableFuture<Integer> querySpeedPlacement(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) + 1 FROM " + tableName + " WHERE best_reaction > 0.0 AND best_reaction < (SELECT best_reaction FROM " + tableName + " WHERE player_uuid = ?)";
            try (Connection conn = openConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            } catch (SQLException ignored) {
            }
            return 0;
        });
    }

    private String resolveDisplayName(UUID uuid, String storedName) {
        if (storedName != null && !storedName.isBlank() && !storedName.equals("Unknown")) {
            return storedName;
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        return offline.getName() != null ? offline.getName() : uuid.toString().substring(0, 8);
    }

    public void flushAllSync() {
        for (CompetitorAccount account : accounts.values()) {
            writeAccountSync(account, false);
        }
    }
}
