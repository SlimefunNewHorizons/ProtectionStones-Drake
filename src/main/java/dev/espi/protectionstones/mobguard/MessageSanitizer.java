package dev.espi.protectionstones.mobguard;

import java.util.regex.Pattern;

/** Validates greeting/farewell texts typed by players. Pure logic, unit tested. Returns null error when ok. */
public final class MessageSanitizer {
    public static final int MAX_LEN = 100;
    private static final Pattern LINK = Pattern.compile(
            "(?i)(https?:|www\\.|\\b[a-z0-9-]{2,}\\.(com|net|org|cl|io|gg|xyz|me|tv|ly|co|es|dev|app|link|ru|info)\\b)");
    private static final Pattern BAD_AMP = Pattern.compile("&(?![0-9a-fk-orA-FK-OR])");
    private static final Pattern CODES = Pattern.compile("&[0-9a-fk-orA-FK-OR]");

    public record Result(String text, String errorEs, String errorEn) {
        public boolean ok() { return errorEs == null; }
    }

    private static Result err(String es, String en) { return new Result(null, es, en); }

    public static String visible(String s) { return CODES.matcher(s).replaceAll(""); }

    public static Result clean(String in) {
        if (in == null) return err("Texto vacio.", "Empty text.");
        String s = in.trim();
        if (s.isEmpty()) return err("Texto vacio.", "Empty text.");
        if (s.startsWith("-")) return err("No puede empezar con '-'.", "Can't start with '-'.");
        if (s.indexOf('§') >= 0 || s.indexOf('<') >= 0 || s.indexOf('>') >= 0 || s.indexOf('{') >= 0
                || s.indexOf('}') >= 0 || s.indexOf('\\') >= 0 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0)
            return err("Caracteres no permitidos (< > { } \\ §).", "Characters not allowed (< > { } \\ §).");
        if (BAD_AMP.matcher(s).find()) return err("Solo se permiten codigos de color &0-9 a-f y formato &k-o &r.", "Only color codes &0-9 a-f and format &k-o &r are allowed.");
        String noPlayer = s.replace("%player%", "");
        if (noPlayer.indexOf('%') >= 0) return err("Solo se permite el marcador %player%.", "Only the %player% placeholder is allowed.");
        String vis = visible(s);
        if (vis.isBlank()) return err("Texto vacio.", "Empty text.");
        if (vis.length() > MAX_LEN) return err("Maximo " + MAX_LEN + " caracteres.", "Maximum " + MAX_LEN + " characters.");
        if (LINK.matcher(vis).find()) return err("No se permiten enlaces.", "Links are not allowed.");
        return new Result(s, null, null);
    }
}
