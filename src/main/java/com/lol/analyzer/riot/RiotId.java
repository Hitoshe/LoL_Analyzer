package com.lol.analyzer.riot;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Riot ID ({@code GameName#TAG}) parsed from whatever the user pasted: lobby chat lines,
 * client copies with invisible bidi marks, {@code Name #TAG}, or a bare name (default tag of the region).
 */
public record RiotId(String gameName, String tagLine) {

    private static final Pattern INVISIBLE = Pattern.compile("[\\p{Cf}\\u00A0\\u3000]");
    private static final Pattern LOBBY_SUFFIX = Pattern.compile(
            "\\s+(joined the lobby|left the lobby|has joined the lobby|присоединил(ся|ась) к лобби|покинул(а)? лобби|вош(е|ё)л в лобби)\\.?$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern LIST_SEPARATORS = Pattern.compile("[\\r\\n,;]+");
    private static final int MAX_NAME = 16;
    private static final int MAX_TAG = 5;

    public static Optional<RiotId> parse(String raw, String defaultTag) {
        if (raw == null) {
            return Optional.empty();
        }
        String text = INVISIBLE.matcher(raw).replaceAll(" ").trim();
        text = LOBBY_SUFFIX.matcher(text).replaceAll("").trim();
        if (text.isEmpty()) {
            return Optional.empty();
        }
        int hash = text.indexOf('#');
        String name;
        String tag;
        if (hash >= 0) {
            name = text.substring(0, hash).trim();
            String rest = text.substring(hash + 1).trim();
            int space = rest.indexOf(' ');
            tag = space >= 0 ? rest.substring(0, space) : rest;
        } else {
            name = text;
            tag = defaultTag;
        }
        name = name.replaceAll("\\s+", " ");
        if (name.isEmpty() || name.length() > MAX_NAME || tag == null || tag.isBlank() || tag.length() > MAX_TAG) {
            return Optional.empty();
        }
        return Optional.of(new RiotId(name, tag));
    }

    /** Splits a pasted block (one player per line, or comma separated as in multi-search links). */
    public static List<RiotId> parseMany(String raw, String defaultTag) {
        List<RiotId> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        for (String part : LIST_SEPARATORS.split(raw)) {
            parse(part, defaultTag).ifPresent(out::add);
        }
        return out;
    }

    public String display() {
        return gameName + "#" + tagLine;
    }

    public boolean sameAs(RiotId other) {
        return other != null && gameName.equalsIgnoreCase(other.gameName) && tagLine.equalsIgnoreCase(other.tagLine);
    }
}
