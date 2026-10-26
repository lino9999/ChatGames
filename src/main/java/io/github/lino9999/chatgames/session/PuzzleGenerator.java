package io.github.lino9999.chatgames.session;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class PuzzleGenerator {

    private PuzzleGenerator() {
    }

    public static String permutePhrase(String phrase) {
        if (phrase == null || phrase.isBlank()) {
            return "";
        }
        String[] tokens = phrase.split(" ");
        List<String> output = new ArrayList<>(tokens.length);
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        for (String token : tokens) {
            if (token.length() <= 1) {
                output.add(token);
                continue;
            }
            char[] letters = token.toCharArray();
            String permuted = token;
            for (int cycle = 0; cycle < 12; cycle++) {
                for (int i = letters.length - 1; i > 0; i--) {
                    int swapIdx = rng.nextInt(i + 1);
                    char tmp = letters[i];
                    letters[i] = letters[swapIdx];
                    letters[swapIdx] = tmp;
                }
                String candidate = new String(letters);
                if (!candidate.equals(token)) {
                    permuted = candidate;
                    break;
                }
            }
            output.add(permuted);
        }
        return String.join(" ", output);
    }

    public static String invertPhrase(String phrase) {
        return phrase != null ? new StringBuilder(phrase).reverse().toString() : "";
    }

    public static String obscureCharacters(String phrase, int maskPercent) {
        if (phrase == null || phrase.isBlank()) {
            return "";
        }
        int ratio = Math.max(5, Math.min(95, maskPercent));
        String[] words = phrase.split(" ");
        List<String> obscured = new ArrayList<>(words.length);
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        for (String word : words) {
            if (word.isEmpty()) {
                obscured.add(word);
                continue;
            }
            int maskCount = Math.min(word.length(), Math.max(1, (int) Math.ceil(word.length() * (ratio / 100.0))));
            List<Integer> positions = new ArrayList<>(word.length());
            for (int i = 0; i < word.length(); i++) {
                positions.add(i);
            }
            Collections.shuffle(positions, rng);
            char[] buf = word.toCharArray();
            for (int i = 0; i < maskCount; i++) {
                buf[positions.get(i)] = '_';
            }
            obscured.add(new String(buf));
        }
        return String.join(" ", obscured);
    }

    public static List<String> sampleMemorySequence(List<String> dictionary, int count) {
        if (dictionary == null || dictionary.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> pool = new ArrayList<>(dictionary);
        Collections.shuffle(pool, ThreadLocalRandom.current());
        return pool.subList(0, Math.min(Math.max(1, count), pool.size()));
    }

    public static String synthesizeCipher(int minLen, int maxLen, String preset, String customAlphabet) {
        int lo = Math.max(1, Math.min(minLen, maxLen));
        int hi = Math.max(lo, Math.max(minLen, maxLen));
        int length = ThreadLocalRandom.current().nextInt(lo, hi + 1);

        String alphabet = switch (preset != null ? preset.toUpperCase(Locale.ROOT) : "ALPHANUMERIC") {
            case "LETTERS" -> "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
            case "DIGITS" -> "0123456789";
            case "HEX" -> "0123456789ABCDEF";
            case "SYMBOLS" -> "!@#$%^&*()-_=+[]{}<>?";
            case "LOWERCASE" -> "abcdefghijklmnopqrstuvwxyz";
            case "UPPERCASE" -> "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
            case "CUSTOM" -> (customAlphabet != null && !customAlphabet.isEmpty()) ? customAlphabet : "0123456789";
            default -> "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        };

        StringBuilder out = new StringBuilder(length);
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int i = 0; i < length; i++) {
            out.append(alphabet.charAt(rng.nextInt(alphabet.length())));
        }
        return out.toString();
    }

    public static ArithmeticProblem buildArithmetic(int complexityTier, List<String> allowedOps) {
        List<String> ops = (allowedOps != null && !allowedOps.isEmpty()) ? allowedOps : List.of("+", "-", "*", "/");
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        String chosenOp = ops.get(rng.nextInt(ops.size())).trim();

        int bound = switch (Math.max(1, Math.min(5, complexityTier))) {
            case 1 -> 12;
            case 2 -> 80;
            case 3 -> 500;
            case 4 -> 2500;
            default -> 10000;
        };

        int left;
        int right;
        int answer;
        String displayOp;

        switch (chosenOp) {
            case "-" -> {
                int v1 = rng.nextInt(bound) + 2;
                int v2 = rng.nextInt(bound) + 1;
                left = Math.max(v1, v2);
                right = Math.min(v1, v2);
                answer = left - right;
                displayOp = "-";
            }
            case "*", "x", "X" -> {
                int factorCap = Math.max(3, (int) Math.sqrt(bound) + 2);
                left = rng.nextInt(factorCap) + 2;
                right = rng.nextInt(factorCap) + 2;
                answer = left * right;
                displayOp = "*";
            }
            case "/" -> {
                int factorCap = Math.max(3, (int) Math.sqrt(bound) + 2);
                right = rng.nextInt(factorCap) + 2;
                answer = rng.nextInt(factorCap) + 2;
                left = right * answer;
                displayOp = "/";
            }
            default -> {
                left = rng.nextInt(bound) + 1;
                right = rng.nextInt(bound) + 1;
                answer = left + right;
                displayOp = "+";
            }
        }

        return new ArithmeticProblem(left + " " + displayOp + " " + right, String.valueOf(answer));
    }

    public static int parseAndComputeArithmetic(String rawFormula) {
        if (rawFormula == null || rawFormula.isBlank()) {
            throw new IllegalArgumentException("Empty arithmetic formula");
        }
        String sanitized = rawFormula.replace('x', '*').replace('X', '*').replaceAll("\\s+", "");
        List<Double> numbers = new ArrayList<>();
        List<Character> operators = new ArrayList<>();

        int idx = 0;
        while (idx < sanitized.length()) {
            int start = idx;
            if ((sanitized.charAt(idx) == '+' || sanitized.charAt(idx) == '-') && (idx == 0 || isOperator(sanitized.charAt(idx - 1)))) {
                idx++;
            }
            while (idx < sanitized.length() && (Character.isDigit(sanitized.charAt(idx)) || sanitized.charAt(idx) == '.')) {
                idx++;
            }
            if (start == idx) {
                throw new IllegalArgumentException("Malformed number token in formula");
            }
            numbers.add(Double.parseDouble(sanitized.substring(start, idx)));
            if (idx < sanitized.length()) {
                char op = sanitized.charAt(idx);
                if (!isOperator(op)) {
                    throw new IllegalArgumentException("Unsupported operator: " + op);
                }
                operators.add(op);
                idx++;
            }
        }

        for (int i = 0; i < operators.size(); ) {
            char op = operators.get(i);
            if (op == '*' || op == '/') {
                double a = numbers.get(i);
                double b = numbers.get(i + 1);
                if (op == '/' && b == 0.0) {
                    throw new ArithmeticException("Division by zero");
                }
                double computed = (op == '*') ? (a * b) : (a / b);
                numbers.set(i, computed);
                numbers.remove(i + 1);
                operators.remove(i);
            } else {
                i++;
            }
        }

        double result = numbers.get(0);
        for (int i = 0; i < operators.size(); i++) {
            char op = operators.get(i);
            double next = numbers.get(i + 1);
            result = (op == '+') ? (result + next) : (result - next);
        }
        return (int) Math.round(result);
    }

    private static boolean isOperator(char c) {
        return c == '+' || c == '-' || c == '*' || c == '/';
    }

    public static SymbolicEquation buildAlgebraSystem(
            ConfigurationSection symbolSection,
            int minTotal,
            int maxTotal,
            String unknownGlyph,
            String pairRowTemplate,
            String finalRowTemplate
    ) {
        List<Map.Entry<String, Integer>> symbols = new ArrayList<>();
        if (symbolSection != null) {
            for (String key : symbolSection.getKeys(false)) {
                symbols.add(Map.entry(key, symbolSection.getInt(key, 10)));
            }
        }
        if (symbols.size() < 2) {
            symbols.add(Map.entry("★", 12));
            symbols.add(Map.entry("♦", 18));
            symbols.add(Map.entry("♣", 24));
        }
        Collections.shuffle(symbols, ThreadLocalRandom.current());

        String symA = symbols.get(0).getKey();
        int valA = symbols.get(0).getValue();

        String symB = symbols.get(1).getKey();
        int valB = symbols.get(1).getValue();

        int occurrences = countToken(pairRowTemplate, "{symbol}");
        String row1 = pairRowTemplate.replace("{symbol}", symA).replace("{sum}", String.valueOf(valA * occurrences));
        String row2 = pairRowTemplate.replace("{symbol}", symB).replace("{sum}", String.valueOf(valB * occurrences));

        int lo = Math.min(minTotal, maxTotal);
        int hi = Math.max(minTotal, maxTotal);
        int finalSum = ThreadLocalRandom.current().nextInt(lo, hi + 1);
        int unknownValue = finalSum - valA - valB;

        String row3 = finalRowTemplate
                .replace("{symbol_a}", symA)
                .replace("{symbol_b}", symB)
                .replace("{glyph}", unknownGlyph)
                .replace("{total}", String.valueOf(finalSum));

        return new SymbolicEquation(List.of(row1, row2, row3), unknownGlyph, unknownValue);
    }

    private static int countToken(String template, String token) {
        if (template == null || token == null || token.isEmpty()) {
            return 3;
        }
        int count = 0;
        int pos = 0;
        while ((pos = template.indexOf(token, pos)) != -1) {
            count++;
            pos += token.length();
        }
        return Math.max(1, count);
    }

    public record ArithmeticProblem(String expression, String solution) {
    }
}
