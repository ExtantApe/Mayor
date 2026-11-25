package com.extantape.posse.rules;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds blocked words and phrases in what players write. It matches whole words only, so "class" and "Scunthorpe"
 * pass. It sees through letter case, accents, common number swaps (sh1t) and stretched letters (shiiit). A trailing
 * {@code *} matches word endings too (swear* matches swearing). Words on the allow list are never flagged.
 */
public final class WordFilter {
    /** Players with this permission aren't filtered anywhere. Off for everyone by default, ops included. */
    public static final String BYPASS = "posse.filter.bypass";
    public static final WordFilter NONE = new WordFilter(List.of(), Set.of());
    private static final Pattern SPLIT = Pattern.compile("[^\\p{L}\\p{N}@$]+");
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    /** The same words {@link #SPLIT} leaves between its separators, with their places in the text. */
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}@$]+");
    /** What a blocked word becomes, whatever its length, so the length gives nothing away. */
    public static final String MASK = "****";

    private final List<List<Pattern>> phrases;
    private final Set<String> allow;

    private WordFilter(List<List<Pattern>> phrases, Set<String> allow) {
        this.phrases = phrases;
        this.allow = allow;
    }

    public static WordFilter of(List<String> words, List<String> allowed) {
        List<List<Pattern>> phrases = new ArrayList<>();
        for (String entry : words) {
            List<Pattern> tokens = new ArrayList<>();
            String[] parts = entry.trim().split("\\s+");
            for (int i = 0; i < parts.length; i++) {
                boolean prefix = i == parts.length - 1 && parts[i].endsWith("*");
                String word = normalize(prefix ? parts[i].substring(0, parts[i].length() - 1) : parts[i]);
                if (!word.isEmpty()) tokens.add(pattern(word, prefix));
            }
            if (!tokens.isEmpty()) phrases.add(List.copyOf(tokens));
        }
        Set<String> allow = new HashSet<>();
        for (String word : allowed) allow.add(normalize(word));
        return new WordFilter(List.copyOf(phrases), Set.copyOf(allow));
    }

    public boolean isEmpty() {
        return phrases.isEmpty();
    }

    /** True if the text contains a blocked word or phrase. The text itself is never stored or logged. */
    public boolean flags(String text) {
        if (phrases.isEmpty() || text == null || text.isBlank()) return false;
        List<String> tokens = tokens(text);
        for (int start = 0; start < tokens.size(); start++) {
            for (List<Pattern> phrase : phrases) {
                if (start + phrase.size() > tokens.size()) continue;
                boolean hit = true;
                for (int i = 0; i < phrase.size() && hit; i++) {
                    String token = tokens.get(start + i);
                    hit = !allow.contains(token) && phrase.get(i).matcher(token).matches();
                }
                if (hit) return true;
            }
        }
        return false;
    }

    /**
     * The text with every blocked word or phrase replaced by {@link #MASK}, and everything else left exactly as
     * written. Text with nothing blocked comes back unchanged. Used to show an adult's words to players who are
     * filtered.
     */
    public String mask(String text) {
        if (phrases.isEmpty() || text == null || text.isBlank()) return text;
        List<int[]> spans = new ArrayList<>();
        List<String> tokens = new ArrayList<>();
        Matcher m = WORD.matcher(text);
        while (m.find()) {
            String token = normalize(m.group());
            if (token.isEmpty()) continue;
            spans.add(new int[]{m.start(), m.end()});
            tokens.add(token);
        }
        boolean[] hit = new boolean[tokens.size()];
        boolean any = false;
        for (int start = 0; start < tokens.size(); start++) {
            for (List<Pattern> phrase : phrases) {
                if (start + phrase.size() > tokens.size()) continue;
                boolean match = true;
                for (int i = 0; i < phrase.size() && match; i++) {
                    String token = tokens.get(start + i);
                    match = !allow.contains(token) && phrase.get(i).matcher(token).matches();
                }
                if (match) {
                    for (int i = 0; i < phrase.size(); i++) hit[start + i] = true;
                    any = true;
                }
            }
        }
        if (!any) return text;
        StringBuilder out = new StringBuilder(text.length());
        int at = 0;
        for (int i = 0; i < spans.size(); i++) {
            if (!hit[i]) continue;
            out.append(text, at, spans.get(i)[0]).append(MASK);
            at = spans.get(i)[1];
        }
        return out.append(text.substring(at)).toString();
    }

    static List<String> tokens(String text) {
        List<String> out = new ArrayList<>();
        for (String raw : SPLIT.split(text)) {
            String token = normalize(raw);
            if (!token.isEmpty()) out.add(token);
        }
        return out;
    }

    /** Lower case, no accents, and the usual number and symbol swaps turned back into letters. */
    static String normalize(String word) {
        String plain = MARKS.matcher(Normalizer.normalize(word, Normalizer.Form.NFKD)).replaceAll("").toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(plain.length());
        for (int i = 0; i < plain.length(); i++) {
            char c = plain.charAt(i);
            out.append(switch (c) {
                case '0' -> 'o';
                case '1' -> 'i';
                case '3' -> 'e';
                case '4', '@' -> 'a';
                case '5', '$' -> 's';
                case '7' -> 't';
                case '8' -> 'b';
                default -> c;
            });
        }
        return out.toString();
    }

    /** Each run of a letter may be stretched but not shortened: "ass" matches "asss" but never "as". */
    private static Pattern pattern(String word, boolean prefix) {
        StringBuilder regex = new StringBuilder();
        int i = 0;
        while (i < word.length()) {
            int j = i;
            while (j < word.length() && word.charAt(j) == word.charAt(i)) j++;
            regex.append(Pattern.quote(String.valueOf(word.charAt(i)))).append('{').append(j - i).append(",}");
            i = j;
        }
        if (prefix) regex.append(".*");
        return Pattern.compile(regex.toString());
    }
}
