package io.github.lino9999.chatgames.text;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.Locale;

public final class NameBeautifier {

    private NameBeautifier() {
    }

    public static String prettifyMaterial(Material material) {
        return material != null ? prettifyConstant(material.name()) : "Unknown";
    }

    public static String prettifyEntity(EntityType entityType) {
        return entityType != null ? prettifyConstant(entityType.name()) : "Unknown";
    }

    public static String prettifyConstant(String rawEnumName) {
        if (rawEnumName == null || rawEnumName.isBlank()) {
            return "";
        }
        String[] segments = rawEnumName.toLowerCase(Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder();
        for (String segment : segments) {
            if (segment.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(segment.charAt(0)));
            if (segment.length() > 1) {
                builder.append(segment.substring(1));
            }
        }
        return builder.toString();
    }
}
