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

    /** Private use characters around a marker: START percent "," width END. Width 0 = fill. */
    public static final char START = '', END = '';
    public static final String DEFAULT_STYLE = "[█░]";
    public static final int MIN_WIDTH = 6;
    /** A filling bar when the line width isn't known yet. */
    public static final int FALLBACK_WIDTH = 12;

    private static final Pattern MARKER = Pattern.compile(START + "(-|\\d{1,3}),(\\d{1,3})" + END);

    private StatusBars() {}

    /** A marker for a bar. percent can be NaN when the value isn't known (the bar is drawn empty). */
    public static String marker(double percent, int width) {
        String p = Double.isNaN(percent) ? "-" : String.valueOf(clamp((int) Math.round(percent), 0, 100));
        return START + p + "," + clamp(width, 0, 200) + END;
    }

    /**
     * Replaces {@code %<code>} and {@code %<code>:N} in a format with a marker for the given percentage.
     * For example replaceCode("RAM %bar:10", "bar", 42).
     */
    public static String replaceCode(String text, String code, double percent) {
        if (text == null || text.indexOf('%') < 0) return text;
        Matcher m = Pattern.compile("%" + Pattern.quote(code) + "(?::(\\d{1,3}))?").matcher(text);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            int width = m.group(1) != null ? Integer.parseInt(m.group(1)) : 0;
            m.appendReplacement(out, Matcher.quoteReplacement(marker(percent, width)));
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
            int markerChars = 0, fixed = 0, fills = 0;
            while (m.find()) {
                int percent = m.group(1).equals("-") ? -1 : Integer.parseInt(m.group(1));
                int width = Integer.parseInt(m.group(2));
                markers.add(new int[] {m.start(), m.end(), percent, width});
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
                for (int[] mk : markers) {
                    int width = mk[3] > 0 ? mk[3] : fillWidth;
                    out.add(new Replacement(lineStart + mk[0], lineStart + mk[1], bar(mk[2], width, style)));
                }
            }

            lineStart = lineEnd + 1;
        }
        return out;
    }

    /** Applies {@link #layout} to plain text. */
    public static String render(String text, int widthChars, String style) {
        List<Replacement> rs = layout(text, widthChars, style);
        StringBuilder b = new StringBuilder(text);
        for (int i = rs.size() - 1; i >= 0; i--) {
            Replacement r = rs.get(i);
            b.replace(r.start, r.end, r.bar);
        }
        return b.toString();
    }

    /**
     * One bar. The style is the characters to draw with: 4 characters are left end, filled, empty,
     * right end ("[█░]", "[#-]", "|=.|"); 2 characters are filled and empty, with no ends ("█░").
     *
     * @param percent 0 to 100, or below 0 when unknown (drawn empty)
     */
    public static String bar(int percent, int width, String style) {
        String st = style == null ? DEFAULT_STYLE : style;
        int[] cps = codePoints(st);
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
