package io.github.lino9999.chatgames.hook;

import io.github.lino9999.chatgames.api.ChallengeCategory;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.logging.Level;

public class DiscordNotifier {
    private final JavaPlugin plugin;
    private final HttpClient httpClient;

    public DiscordNotifier(JavaPlugin plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public void dispatchVictoryEmbed(String winnerName, double elapsedSeconds, ChallengeCategory category) {
        if (!plugin.getConfig().getBoolean("webhook.enabled", false)) {
            return;
        }
        String endpoint = plugin.getConfig().getString("webhook.endpoint-url", "");
        if (endpoint == null || endpoint.isBlank()) {
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String botName = sanitizeJson(plugin.getConfig().getString("webhook.bot-username", "ChatGames"));
                String avatar = plugin.getConfig().getString("webhook.avatar-url", "");
                String title = sanitizeJson(plugin.getConfig().getString("webhook.embed.title", "ChatGames Round Complete"));
                String desc = sanitizeJson(plugin.getConfig().getString("webhook.embed.description", "A round has concluded!"));
                int colorInt = parseHexColor(plugin.getConfig().getString("webhook.embed.color-hex", "#FFAA00"));

                String winnerVal = sanitizeJson(plugin.getConfig().getString("webhook.embed.winner-label", "{winner} ({elapsed}s)")
                        .replace("{winner}", winnerName)
                        .replace("{elapsed}", String.valueOf(elapsedSeconds)));
                String catVal = sanitizeJson(plugin.getConfig().getString("webhook.embed.category-label", "{category}")
                        .replace("{category}", category.getId()));

                StringBuilder payload = new StringBuilder();
                payload.append("{\"username\":\"").append(botName).append("\",");
                if (avatar != null && !avatar.isBlank()) {
                    payload.append("\"avatar_url\":\"").append(sanitizeJson(avatar)).append("\",");
                }
                payload.append("\"embeds\":[{");
                payload.append("\"title\":\"").append(title).append("\",");
                payload.append("\"description\":\"").append(desc).append("\",");
                payload.append("\"color\":").append(colorInt).append(",");
                payload.append("\"fields\":[");
                payload.append("{\"name\":\"Winner\",\"value\":\"").append(winnerVal).append("\",\"inline\":true},");
                payload.append("{\"name\":\"Category\",\"value\":\"").append(catVal).append("\",\"inline\":true}");
                payload.append("]}]}");

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint.trim()))
                        .timeout(Duration.ofSeconds(5))
                        .header("Content-Type", "application/json; charset=UTF-8")
                        .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                        .build();

                HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
                int status = response.statusCode();
                if (status < 200 || status >= 300) {
                    plugin.getLogger().log(Level.WARNING, "Discord webhook returned HTTP status " + status);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Unable to dispatch Discord webhook: " + e.getMessage());
            }
        });
    }

    private static int parseHexColor(String hex) {
        if (hex == null || hex.isBlank()) {
            return 16755200;
        }
        String cleaned = hex.trim().replace("#", "");
        try {
            return Integer.parseInt(cleaned, 16);
        } catch (NumberFormatException e) {
            return 16755200;
        }
    }

    private static String sanitizeJson(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }
}
