package ohi.andre.consolelauncher.managers.themes;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave;
import ohi.andre.consolelauncher.managers.xml.options.Suggestions;
import ohi.andre.consolelauncher.managers.xml.options.Theme;

/**
 * One HelCLI theme: a set of colors for theme.xml and suggestions.xml.
 *
 * Themes are stored as small JSON files, for example:
 * <pre>
 * {
 *   "name": "amber",
 *   "title": "Amber CRT",
 *   "author": "HelCLI",
 *   "description": "Warm monochrome, like an old amber monitor",
 *   "theme": { "bg_color": "#ff140b00", "input_color": "#ffffb000" },
 *   "suggestions": { "apps_bg_color": "#ffffb000" }
 * }
 * </pre>
 * Any color a theme leaves out falls back to its default value when the theme is applied,
 * so a theme always looks the same no matter what was applied before it.
 *
 * This class is plain Java (no Android APIs) so it can be unit tested.
 */
public class ThemeSpec {

    public static final String KEY_NAME = "name", KEY_TITLE = "title", KEY_AUTHOR = "author",
            KEY_DESCRIPTION = "description", KEY_THEME = "theme", KEY_SUGGESTIONS = "suggestions";

    private static final Pattern NAME_PATTERN = Pattern.compile("[a-z0-9][a-z0-9_-]{0,39}");
    private static final Pattern COLOR_PATTERN = Pattern.compile("#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})");

    public final String name;
    public final String title;
    public final String author;
    public final String description;
    public final Map<String, String> theme;
    public final Map<String, String> suggestions;

    public ThemeSpec(String name, String title, String author, String description,
                     Map<String, String> theme, Map<String, String> suggestions) {
        this.name = name;
        this.title = title == null || title.length() == 0 ? name : title;
        this.author = author == null ? "" : author;
        this.description = description == null ? "" : description;
        this.theme = Collections.unmodifiableMap(new LinkedHashMap<>(theme));
        this.suggestions = Collections.unmodifiableMap(new LinkedHashMap<>(suggestions));
    }

    /**
     * Parses a theme file. Problems that make the theme unusable throw; unknown color names are
     * skipped (and reported by {@link #validate(String)}) so themes from newer versions still load.
     */
    public static ThemeSpec parse(String json) throws InvalidThemeException {
        JSONObject o;
        try {
            o = new JSONObject(json);
        } catch (JSONException e) {
            throw new InvalidThemeException("not a valid theme file (" + e.getMessage() + ")");
        }

        String name = normalizeName(o.optString(KEY_NAME, ""));
        if (!isValidName(name)) {
            throw new InvalidThemeException("the theme needs a \"name\" made of letters, numbers, - or _");
        }

        Map<String, String> theme = readColors(o.optJSONObject(KEY_THEME), themeColorKeys());
        Map<String, String> suggestions = readColors(o.optJSONObject(KEY_SUGGESTIONS), suggestionColorKeys());
        if (theme.isEmpty() && suggestions.isEmpty()) {
            throw new InvalidThemeException("the theme \"" + name + "\" doesn't set any colors");
        }

        return new ThemeSpec(name, o.optString(KEY_TITLE, name), o.optString(KEY_AUTHOR, ""),
                o.optString(KEY_DESCRIPTION, ""), theme, suggestions);
    }

    /** Returns a list of problems in a theme file. An empty list means the file is fully valid. */
    public static List<String> validate(String json) {
        List<String> problems = new ArrayList<>();
        JSONObject o;
        try {
            o = new JSONObject(json);
        } catch (JSONException e) {
            problems.add("invalid JSON: " + e.getMessage());
            return problems;
        }

        if (!isValidName(o.optString(KEY_NAME, ""))) problems.add("invalid or missing name");

        checkColors(o.optJSONObject(KEY_THEME), themeColorKeys(), KEY_THEME, problems);
        checkColors(o.optJSONObject(KEY_SUGGESTIONS), suggestionColorKeys(), KEY_SUGGESTIONS, problems);
        return problems;
    }

    private static void checkColors(JSONObject colors, List<String> allowed, String section, List<String> problems) {
        if (colors == null) return;
        Iterator<String> keys = colors.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            String value = colors.optString(key, "");
            if (!allowed.contains(key)) problems.add(section + "." + key + " is not a known color");
            else if (!isValidColor(value)) problems.add(section + "." + key + " has an invalid color: " + value);
        }
    }

    private static Map<String, String> readColors(JSONObject colors, List<String> allowed) throws InvalidThemeException {
        Map<String, String> out = new LinkedHashMap<>();
        if (colors == null) return out;

        Iterator<String> keys = colors.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (!allowed.contains(key)) continue;

            String value = colors.optString(key, "").trim();
            if (!isValidColor(value)) {
                throw new InvalidThemeException(key + " has an invalid color \"" + value + "\" (use #RRGGBB or #AARRGGBB)");
            }
            out.put(key, value.replace(" ", "").toLowerCase(Locale.ROOT));
        }
        return out;
    }

    public JSONObject toJson() {
        try {
            JSONObject o = new JSONObject();
            o.put(KEY_NAME, name);
            o.put(KEY_TITLE, title);
            if (author.length() > 0) o.put(KEY_AUTHOR, author);
            if (description.length() > 0) o.put(KEY_DESCRIPTION, description);

            JSONObject t = new JSONObject();
            for (Map.Entry<String, String> e : theme.entrySet()) t.put(e.getKey(), e.getValue());
            o.put(KEY_THEME, t);

            JSONObject s = new JSONObject();
            for (Map.Entry<String, String> e : suggestions.entrySet()) s.put(e.getKey(), e.getValue());
            o.put(KEY_SUGGESTIONS, s);
            return o;
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The color this theme gives a theme.xml entry, or its default if the theme doesn't set it. */
    public String themeColor(Theme key) {
        String v = theme.get(key.label());
        return v != null ? v : key.defaultValue();
    }

    /** The color this theme gives a suggestions.xml entry, or its default if the theme doesn't set it. */
    public String suggestionColor(Suggestions key) {
        String v = suggestions.get(key.label());
        return v != null ? v : key.defaultValue();
    }

    /**
     * A color is #RRGGBB or #AARRGGBB. A few entries (status_lines_bg and friends) hold one color
     * per status line, so a comma-separated list of colors is accepted too.
     */
    public static boolean isValidColor(String color) {
        if (color == null || color.trim().length() == 0) return false;
        for (String part : color.split(",", -1)) {
            if (!COLOR_PATTERN.matcher(part.trim()).matches()) return false;
        }
        return true;
    }

    public static boolean isValidName(String name) {
        return name != null && NAME_PATTERN.matcher(name).matches();
    }

    /** Lowercases a name and turns spaces into dashes: "Tokyo Night" becomes "tokyo-night". */
    public static String normalizeName(String name) {
        if (name == null) return "";
        return name.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "-");
    }

    public static List<String> themeColorKeys() {
        List<String> keys = new ArrayList<>();
        for (Theme t : Theme.values()) {
            if (XMLPrefsSave.COLOR.equals(t.type())) keys.add(t.label());
        }
        return keys;
    }

    public static List<String> suggestionColorKeys() {
        List<String> keys = new ArrayList<>();
        for (Suggestions s : Suggestions.values()) {
            if (XMLPrefsSave.COLOR.equals(s.type())) keys.add(s.label());
        }
        return keys;
    }

    public static class InvalidThemeException extends Exception {
        public InvalidThemeException(String message) {
            super(message);
        }
    }
}
