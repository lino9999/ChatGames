package io.github.lino9999.chatgames.api;

import java.util.Locale;

public enum ChallengeCategory {
    SCRAMBLE("scramble", false, false, "unscramble", "anagram"),
    REVERSE("reverse", false, false, "unreverse", "backwards"),
    TYPE_RACES("type_race", false, false, "reaction", "typerace", "fasttype"),
    NUMBER_GUESS("number_guess", false, false, "guess_the_number", "guessthenumber", "number"),
    MEMORY_LIST("memory_list", false, false, "shoppinglist", "shopping_list", "memorize"),
    CLICK_RUSH("click_rush", false, false, "clickable", "click"),
    MISSING_CHARS("missing_chars", false, false, "fillout", "blanks"),
    HOVER_REVEAL("hover_reveal", false, false, "hoverable", "hover"),
    CIPHER_STRING("cipher_string", false, false, "random", "code"),
    ARITHMETIC("arithmetic", false, false, "math", "calculation"),
    ALGEBRA("algebra", false, false, "variable", "equations"),
    QUIZ("quiz", false, false, "trivia", "question"),
    SLAYER("slayer", true, false, "hunt", "mobhunt"),
    MINING("mining", true, true, "mine", "excavate"),
    BUILDING("building", true, true, "place", "construct"),
    ANGLING("angling", true, true, "fish", "fishing"),
    CULINARY("culinary", true, true, "eat", "consume"),
    ARTISAN("artisan", true, true, "craft", "crafting"),
    SMELTING("smelting", true, true, "furnace", "cook");

    private final String id;
    private final boolean worldChallenge;
    private final boolean materialBased;
    private final String[] aliases;

    ChallengeCategory(String id, boolean worldChallenge, boolean materialBased, String... aliases) {
        this.id = id;
        this.worldChallenge = worldChallenge;
        this.materialBased = materialBased;
        this.aliases = aliases;
    }

    public String getId() {
        return id;
    }

    public boolean isWorldChallenge() {
        return worldChallenge;
    }

    public boolean isMaterialBased() {
        return materialBased;
    }

    public static ChallengeCategory resolve(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        for (ChallengeCategory cat : values()) {
            if (cat.id.equals(normalized) || cat.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return cat;
            }
            for (String alias : cat.aliases) {
                if (alias.equals(normalized)) {
                    return cat;
                }
            }
        }
        return null;
    }
}
