package io.github.lino9999.chatgames.text;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChatRenderer {
    private static final Pattern HEX_COLOR_REGEX = Pattern.compile("#([a-fA-F0-9]{6})");
    private static final int CHAT_WIDTH_PIXELS = 320;
    private static final byte[] ASCII_CHAR_WIDTHS = new byte[128];

    static {
        for (int i = 0; i < ASCII_CHAR_WIDTHS.length; i++) {
            ASCII_CHAR_WIDTHS[i] = 5;
        }
        ASCII_CHAR_WIDTHS[' '] = 3;
        ASCII_CHAR_WIDTHS['!'] = 1;
        ASCII_CHAR_WIDTHS['"'] = 3;
        ASCII_CHAR_WIDTHS['\''] = 1;
        ASCII_CHAR_WIDTHS['('] = 4;
        ASCII_CHAR_WIDTHS[')'] = 4;
        ASCII_CHAR_WIDTHS[','] = 1;
        ASCII_CHAR_WIDTHS['.'] = 1;
        ASCII_CHAR_WIDTHS[':'] = 1;
        ASCII_CHAR_WIDTHS[';'] = 1;
        ASCII_CHAR_WIDTHS['<'] = 4;
        ASCII_CHAR_WIDTHS['>'] = 4;
        ASCII_CHAR_WIDTHS['@'] = 6;
        ASCII_CHAR_WIDTHS['I'] = 3;
        ASCII_CHAR_WIDTHS['['] = 3;
        ASCII_CHAR_WIDTHS[']'] = 3;
        ASCII_CHAR_WIDTHS['`'] = 2;
        ASCII_CHAR_WIDTHS['f'] = 4;
        ASCII_CHAR_WIDTHS['i'] = 1;
        ASCII_CHAR_WIDTHS['k'] = 4;
        ASCII_CHAR_WIDTHS['l'] = 1;
        ASCII_CHAR_WIDTHS['t'] = 4;
        ASCII_CHAR_WIDTHS['{'] = 4;
        ASCII_CHAR_WIDTHS['|'] = 1;
        ASCII_CHAR_WIDTHS['}'] = 4;
    }

    private ChatRenderer() {
    }

    public static String format(String input, String prefix) {
        if (input == null) {
            return "";
        }
        String merged = input.replace("{prefix}", prefix != null ? prefix : "");
        Matcher matcher = HEX_COLOR_REGEX.matcher(merged);
        StringBuilder buffer = new StringBuilder();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder code = new StringBuilder("§x");
            for (int i = 0; i < hex.length(); i++) {
                code.append('§').append(hex.charAt(i));
            }
            matcher.appendReplacement(buffer, code.toString());
        }
        matcher.appendTail(buffer);
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }

    public static void deliver(CommandSender target, String template, String prefix) {
        if (target == null || template == null) {
            return;
        }
        if (template.contains("[center]")) {
            String stripped = template.replace("[center]", "");
            for (String line : wrapAndCenter(format(stripped, prefix))) {
                target.sendMessage(line);
            }
        } else {
            target.sendMessage(format(template, prefix));
        }
    }

    public static void deliverInteractive(Player player, String prefixLead, BaseComponent interactive, String prefix) {
        String coloredLead = format(prefixLead.replace("[center]", ""), prefix);
        int interactivePx = measurePixels(interactive.toPlainText());
        int spaces = computeCenterPadding(coloredLead, interactivePx);
        TextComponent root = new TextComponent(" ".repeat(spaces) + coloredLead);
        root.addExtra(interactive);
        player.spigot().sendMessage(root);
    }

    private static List<String> wrapAndCenter(String coloredText) {
        if (coloredText == null || coloredText.isEmpty()) {
            return List.of("");
        }
        String[] tokens = coloredText.split(" ");
        List<String> lines = new ArrayList<>();
        StringBuilder activeLine = new StringBuilder();

        for (String token : tokens) {
            int tokenPx = measurePixels(token + " ");
            if (measurePixels(activeLine.toString()) + tokenPx > CHAT_WIDTH_PIXELS && activeLine.length() > 0) {
                String trimmed = activeLine.toString().trim();
                lines.add(" ".repeat(computeCenterPadding(trimmed, 0)) + trimmed);
                activeLine = new StringBuilder(ChatColor.getLastColors(trimmed));
            }
            activeLine.append(token).append(" ");
        }

        if (activeLine.length() > 0) {
            String trimmed = activeLine.toString().trim();
            lines.add(" ".repeat(computeCenterPadding(trimmed, 0)) + trimmed);
        }
        return lines;
    }

    private static int computeCenterPadding(String text, int extraPixels) {
        int totalPx = measurePixels(text) + extraPixels;
        int freePx = CHAT_WIDTH_PIXELS - totalPx;
        if (freePx <= 0) {
            return 0;
        }
        return (freePx / 2) / 4;
    }

    public static int measurePixels(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int total = 0;
        boolean formattingCode = false;
        boolean boldActive = false;

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '§') {
                formattingCode = true;
                continue;
            }
            if (formattingCode) {
                formattingCode = false;
                if (ch == 'l' || ch == 'L') {
                    boldActive = true;
                } else if (ch == 'r' || ch == 'R' || (ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f') || (ch >= 'A' && ch <= 'F')) {
                    boldActive = false;
                }
                continue;
            }
            int charWidth = (ch < 128) ? ASCII_CHAR_WIDTHS[ch] : 5;
            if (boldActive && ch != ' ') {
                charWidth += 1;
            }
            total += charWidth + 1;
        }
        return total;
    }
}
