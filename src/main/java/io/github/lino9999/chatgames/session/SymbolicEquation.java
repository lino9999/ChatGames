package io.github.lino9999.chatgames.session;

import java.util.List;

public record SymbolicEquation(List<String> equationRows, String unknownGlyph, int expectedValue) {
}
