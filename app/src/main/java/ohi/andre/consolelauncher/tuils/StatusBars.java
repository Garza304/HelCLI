package ohi.andre.consolelauncher.tuils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Text loading bars for the lines at the top of the home screen, like {@code [████░░░░░░]}.
 *
 * A format such as ram_format can contain {@code %bar} (fill the rest of the line) or
 * {@code %bar:20} (20 characters wide). Each label replaces the code with a marker holding its
 * percentage. The width of a filling bar is only known when the whole line is put together (other
 * items can share the line), so the line's TextView turns markers into bars at that point:
 * see {@link #layout(CharSequence, int, String)}.
 *
 * Plain Java, so it can be unit tested.
 */
public class StatusBars {

    /**
     * Private use characters around a marker: START percent "," width [";" style] END. Width 0 = fill.
     * A marker with its own style ignores the line's bar_style (the music line has its own).
     */
    public static final char START = '', END = '';
    public static final String DEFAULT_STYLE = "[█░]";
    public static final int MIN_WIDTH = 6;
    /** A filling bar when the line width isn't known yet. */
    public static final int FALLBACK_WIDTH = 12;

    private static final Pattern MARKER = Pattern.compile(START + "(-|\\d{1,3}),(\\d{1,3})(?:;([^" + END + "]{1,16}))?" + END);

    private StatusBars() {}

    /** A marker for a bar. percent can be NaN when the value isn't known (the bar is drawn empty). */
    public static String marker(double percent, int width) {
        return marker(percent, width, null);
    }

    /** A marker drawn with its own style (null or "" = the line's style). */
    public static String marker(double percent, int width, String style) {
        String p = Double.isNaN(percent) ? "-" : String.valueOf(clamp((int) Math.round(percent), 0, 100));
        String st = style == null || style.length() == 0 || style.indexOf(END) >= 0 ? "" : ";" + style;
        return START + p + "," + clamp(width, 0, 200) + st + END;
    }

    /**
     * Replaces {@code %<code>} and {@code %<code>:N} in a format with a marker for the given percentage.
     * For example replaceCode("RAM %bar:10", "bar", 42).
     */
    public static String replaceCode(String text, String code, double percent) {
        return replaceCode(text, code, percent, null);
    }

    /** Like {@link #replaceCode(String, String, double)}, with a style for these bars only. */
    public static String replaceCode(String text, String code, double percent, String style) {
        if (text == null || text.indexOf('%') < 0) return text;
        Matcher m = Pattern.compile("%" + Pattern.quote(code) + "(?::(\\d{1,3}))?").matcher(text);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            int width = m.group(1) != null ? Integer.parseInt(m.group(1)) : 0;
            m.appendReplacement(out, Matcher.quoteReplacement(marker(percent, width, style)));
        }
        m.appendTail(out);
        return out.toString();
    }

    public static boolean hasMarkers(CharSequence s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == START) return true;
        }
        return false;
    }

    /** Where one marker goes and what replaces it. */
    public static class Replacement {
        public final int start, end;
        public final String bar;

        Replacement(int start, int end, String bar) {
            this.start = start;
            this.end = end;
            this.bar = bar;
        }
    }

    /**
     * Works out every bar in a status line. Bars with a fixed width keep it; bars that fill share
     * what's left of their line (lines are split at line breaks). Apply the replacements from the
     * last one to the first, so earlier positions stay valid.
     *
     * @param widthChars how many characters fit on a line, or 0 or less if not known yet
     */
    public static List<Replacement> layout(CharSequence text, int widthChars, String style) {
        List<Replacement> out = new ArrayList<>();
        String s = text.toString();

        int lineStart = 0;
        while (lineStart <= s.length()) {
            int lineEnd = s.indexOf('\n', lineStart);
            if (lineEnd < 0) lineEnd = s.length();

            String line = s.substring(lineStart, lineEnd);
            Matcher m = MARKER.matcher(line);

            List<int[]> markers = new ArrayList<>();   // start, end, percent (-1 unknown), width
            List<String> styles = new ArrayList<>();
            int markerChars = 0, fixed = 0, fills = 0;
            while (m.find()) {
                int percent = m.group(1).equals("-") ? -1 : Integer.parseInt(m.group(1));
                int width = Integer.parseInt(m.group(2));
                markers.add(new int[] {m.start(), m.end(), percent, width});
                styles.add(m.group(3) != null ? m.group(3) : style);
                markerChars += m.end() - m.start();
                if (width > 0) fixed += width;
                else fills++;
            }

            if (!markers.isEmpty()) {
                int visible = line.length() - markerChars;
                int fillWidth = FALLBACK_WIDTH;
                if (widthChars > 0 && fills > 0) {
                    fillWidth = Math.max(MIN_WIDTH, (widthChars - visible - fixed) / fills);
                }
                for (int i = 0; i < markers.size(); i++) {
                    int[] mk = markers.get(i);
                    int width = mk[3] > 0 ? mk[3] : fillWidth;
                    out.add(new Replacement(lineStart + mk[0], lineStart + mk[1], bar(mk[2], width, styles.get(i))));
                }
            }

            lineStart = lineEnd + 1;
        }
        return out;
    }

    /** Applies {@link #layout} to plain text. */
    private static String renderBars(String text, int widthChars, String style) {
        List<Replacement> rs = layout(text, widthChars, style);
        StringBuilder b = new StringBuilder(text);
        for (int i = rs.size() - 1; i >= 0; i--) {
            Replacement r = rs.get(i);
            b.replace(r.start, r.end, r.bar);
        }
        return b.toString();
    }

    /**
     * One bar. The style is the characters to draw with:
     * <ul>
     *   <li>4 characters: left end, filled, empty, right end ("[█░]", "[#-]", "|=.|")</li>
     *   <li>2 characters: filled and empty, with no ends ("█░")</li>
     *   <li>3 characters: played, marker, left to play ("~♫·"): a marker that moves along the bar</li>
     *   <li>5 characters: the same with ends ("[=>-]" is left end, played, marker, left, right end)</li>
     * </ul>
     *
     * @param percent 0 to 100, or below 0 when unknown (drawn empty)
     */
    public static String bar(int percent, int width, String style) {
        String st = style == null ? DEFAULT_STYLE : style;
        int[] cps = codePoints(st);
        if (cps.length == 3 || cps.length == 5) return marked(percent, width, cps);
        String left = "", right = "", full, empty;
        if (cps.length >= 4) {
            left = new String(cps, 0, 1);
            full = new String(cps, 1, 1);
            empty = new String(cps, 2, 1);
            right = new String(cps, 3, 1);
        } else if (cps.length >= 2) {
            full = new String(cps, 0, 1);
            empty = new String(cps, 1, 1);
        } else {
            return bar(percent, width, DEFAULT_STYLE);
        }

        int inner = Math.max(1, width - left.length() - right.length());
        int filled = percent < 0 ? 0 : clamp((int) Math.round(inner * percent / 100.0), 0, inner);
//        a value above zero always shows at least one filled cell
        if (percent > 0 && filled == 0) filled = 1;

        StringBuilder b = new StringBuilder(left);
        for (int i = 0; i < inner; i++) b.append(i < filled ? full : empty);
        return b.append(right).toString();
    }

    /**
     * Text that may be shortened with "…" when its line is too long for the screen, such as a city,
     * an event title or a song. Lines are shortened from their longest such text first; other text
     * is never cut, so it's only used where shortening is better than wrapping onto a new line.
     */
    public static final char SHRINK_START = '\ue002', SHRINK_END = '\ue003';
    private static final Pattern SHRINK = Pattern.compile(SHRINK_START + "([^" + SHRINK_END + "]*)" + SHRINK_END);
    public static final String ELLIPSIS = "…";

    public static String shrinkable(String s) {
        return s == null || s.length() == 0 ? s : SHRINK_START + s + SHRINK_END;
    }

    /** Removes the shrink marks without shortening anything. */
    public static String plain(String s) {
        return s == null ? null : s.replace(String.valueOf(SHRINK_START), "").replace(String.valueOf(SHRINK_END), "");
    }

    /**
     * Shortens shrinkable text on lines wider than widthChars and removes the marks. Bars that fill
     * count as {@link #MIN_WIDTH}. Apply the replacements from the last one to the first.
     *
     * @param widthChars how many characters fit on a line, or 0 or less if not known (nothing is shortened)
     */
    public static List<Replacement> fit(CharSequence text, int widthChars) {
        List<Replacement> out = new ArrayList<>();
        String s = text.toString();

        int lineStart = 0;
        while (lineStart <= s.length()) {
            int lineEnd = s.indexOf('\n', lineStart);
            if (lineEnd < 0) lineEnd = s.length();
            String line = s.substring(lineStart, lineEnd);

            List<int[]> segs = new ArrayList<>();   // start, end (marks included)
            List<String> texts = new ArrayList<>();
            Matcher m = SHRINK.matcher(line);
            int marks = 0;
            while (m.find()) {
                segs.add(new int[] {m.start(), m.end()});
                texts.add(m.group(1));
                marks += 2;
            }

            if (!segs.isEmpty()) {
                int visible = line.length() - marks;
                Matcher b = MARKER.matcher(line);
                while (b.find()) {
                    int w = Integer.parseInt(b.group(2));
                    visible += (w > 0 ? w : MIN_WIDTH) - (b.end() - b.start());
                }

                int over = widthChars > 0 ? visible - widthChars : 0;
                while (over > 0) {
                    int longest = -1;
                    for (int i = 0; i < texts.size(); i++) {
                        if (texts.get(i).length() > 2 && (longest < 0 || texts.get(i).length() > texts.get(longest).length())) longest = i;
                    }
                    if (longest < 0) break;
                    String t = texts.get(longest);
//                    shorten this one by what's over, but leave others something to give if it gets too short
                    int keep = Math.max(2, t.length() - over);
                    String shorter = t.substring(0, keep - 1).replaceAll("[\\s·,—-]+$", "") + ELLIPSIS;
                    if (shorter.length() >= t.length()) break;
                    over -= t.length() - shorter.length();
                    texts.set(longest, shorter);
                }

                for (int i = 0; i < segs.size(); i++) {
                    out.add(new Replacement(lineStart + segs.get(i)[0], lineStart + segs.get(i)[1], texts.get(i)));
                }
            }

            lineStart = lineEnd + 1;
        }
        return out;
    }

    /** Applies {@link #fit} and then {@link #layout} to plain text. */
    public static String render(String text, int widthChars, String style) {
        List<Replacement> rs = fit(text, widthChars);
        StringBuilder b = new StringBuilder(text);
        for (int i = rs.size() - 1; i >= 0; i--) {
            Replacement r = rs.get(i);
            b.replace(r.start, r.end, r.bar);
        }
        return renderBars(b.toString(), widthChars, style);
    }

    /** A bar with a marker at the current position: played cells before it, the rest after. */
    private static String marked(int percent, int width, int[] cps) {
        int o = cps.length == 5 ? 1 : 0;
        String left = o == 1 ? new String(cps, 0, 1) : "", right = o == 1 ? new String(cps, 4, 1) : "";
        String played = new String(cps, o, 1), mark = new String(cps, o + 1, 1), rest = new String(cps, o + 2, 1);

        int inner = Math.max(1, width - left.length() - right.length());
        int at = percent < 0 ? 0 : clamp((int) Math.round((inner - 1) * percent / 100.0), 0, inner - 1);

        StringBuilder b = new StringBuilder(left);
        for (int i = 0; i < inner; i++) b.append(i < at ? played : i == at ? mark : rest);
        return b.append(right).toString();
    }

    /** The separator between items that share a line, or "" if the previous one ends a line. */
    public static String gapBetween(CharSequence before, CharSequence next, String gap) {
        if (gap == null || gap.length() == 0 || before.length() == 0 || next.length() == 0) return "";
        if (before.charAt(before.length() - 1) == '\n' || next.charAt(0) == '\n') return "";
        return gap;
    }

    /** String.codePoints() needs Android 7; HelCLI supports Android 5. */
    private static int[] codePoints(String s) {
        int[] out = new int[s.codePointCount(0, s.length())];
        for (int i = 0, n = 0; i < s.length(); n++) {
            int cp = s.codePointAt(i);
            out[n] = cp;
            i += Character.charCount(cp);
        }
        return out;
    }

    private static int clamp(int v, int min, int max) {
        return v < min ? min : (v > max ? max : v);
    }
}
