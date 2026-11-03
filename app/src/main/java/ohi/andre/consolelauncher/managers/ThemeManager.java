package ohi.andre.consolelauncher.managers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.net.Uri;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;

import androidx.core.content.FileProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ohi.andre.consolelauncher.BuildConfig;
import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.managers.themes.ThemeGallery;
import ohi.andre.consolelauncher.managers.themes.ThemeSpec;
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager;
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave;
import ohi.andre.consolelauncher.managers.xml.options.Suggestions;
import ohi.andre.consolelauncher.managers.xml.options.Theme;
import ohi.andre.consolelauncher.tuils.GenericFileProvider;
import ohi.andre.consolelauncher.tuils.Tuils;
import ohi.andre.consolelauncher.tuils.interfaces.Reloadable;
import okhttp3.OkHttpClient;

/**
 * Applies, previews, imports, exports and shares themes. The "theme" command talks to this class
 * through local broadcasts, and the slow parts (network, files) run on a background thread.
 */
public class ThemeManager {

    public static String ACTION_APPLY = BuildConfig.APPLICATION_ID + ".theme_apply";
    public static String ACTION_REVERT = BuildConfig.APPLICATION_ID + ".theme_revert";
    public static String ACTION_STANDARD = BuildConfig.APPLICATION_ID + ".theme_standard";
    public static String ACTION_LIST = BuildConfig.APPLICATION_ID + ".theme_list";
    public static String ACTION_PREVIEW = BuildConfig.APPLICATION_ID + ".theme_preview";
    public static String ACTION_IMPORT = BuildConfig.APPLICATION_ID + ".theme_import";
    public static String ACTION_EXPORT = BuildConfig.APPLICATION_ID + ".theme_export";
    public static String ACTION_SHARE = BuildConfig.APPLICATION_ID + ".theme_share";
    public static String ACTION_DELETE = BuildConfig.APPLICATION_ID + ".theme_delete";

    /** A theme name, a https:// link to a theme file, or (for ACTION_IMPORT) a content:// Uri. */
    public static String NAME = "name";

    private static final int MAX_IMPORT = 256 * 1024;
    private static final int PREVIEW_WIDTH = 34;

    private final OkHttpClient client;
    private final Context context;
    private final Reloadable reloadable;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent intent) {
            final String action = intent.getAction();
            final String name = intent.getStringExtra(NAME);
            if (action == null) return;

            new Thread(() -> {
                try {
                    if (action.equals(ACTION_APPLY)) apply(name);
                    else if (action.equals(ACTION_REVERT)) revert();
                    else if (action.equals(ACTION_STANDARD)) standard();
                    else if (action.equals(ACTION_LIST)) list();
                    else if (action.equals(ACTION_PREVIEW)) preview(name);
                    else if (action.equals(ACTION_IMPORT)) importTheme(name);
                    else if (action.equals(ACTION_EXPORT)) export(name);
                    else if (action.equals(ACTION_SHARE)) share(name);
                    else if (action.equals(ACTION_DELETE)) delete(name);
                } catch (Exception e) {
                    Tuils.log(e);
                    error(e.toString());
                }
            }).start();
        }
    };

    public ThemeManager(OkHttpClient client, Context context, Reloadable reloadable) {
        this.client = client;
        this.context = context;
        this.reloadable = reloadable;

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_APPLY);
        filter.addAction(ACTION_REVERT);
        filter.addAction(ACTION_STANDARD);
        filter.addAction(ACTION_LIST);
        filter.addAction(ACTION_PREVIEW);
        filter.addAction(ACTION_IMPORT);
        filter.addAction(ACTION_EXPORT);
        filter.addAction(ACTION_SHARE);
        filter.addAction(ACTION_DELETE);

        LocalBroadcastManager.getInstance(context.getApplicationContext()).registerReceiver(receiver, filter);

//        learn the theme names in the background, so command suggestions can offer them
        new Thread(() -> {
            try {
                ThemeGallery.builtIn(context);
                ThemeGallery.saved();
                ThemeGallery.onlineOnly(context, null, new HashSet<String>());
            } catch (Exception e) {
                Tuils.log(e);
            }
        }).start();
    }

    public void dispose() {
        LocalBroadcastManager.getInstance(context.getApplicationContext()).unregisterReceiver(receiver);
    }

    private void output(CharSequence s) {
        Tuils.sendOutput(context, s);
    }

    private void error(CharSequence s) {
        Tuils.sendOutput(Color.RED, context, s);
    }

//    ---------------------------------------------------------------- finding themes

    /** A theme by name, or from a https:// link (which is then saved, so it works offline). */
    private ThemeSpec resolve(String name) throws ThemeSpec.InvalidThemeException, IOException {
        if (name == null || name.trim().length() == 0) return null;
        name = name.trim();

        if (name.startsWith("http://")) {
            throw new ThemeSpec.InvalidThemeException("only https:// links are supported");
        }
        if (name.startsWith("https://")) {
            if (!Tuils.hasInternetAccess()) throw new ThemeSpec.InvalidThemeException(context.getString(R.string.no_internet));
            String json = ThemeGallery.download(client, name);
            if (json == null) throw new ThemeSpec.InvalidThemeException("couldn't download " + name);
            ThemeSpec spec = ThemeSpec.parse(json);
            ThemeGallery.save(spec);
            return spec;
        }

        return ThemeGallery.find(context, client, name);
    }

    /** Like {@link #resolve(String)}, but prints the problem and returns null when it fails. */
    private ThemeSpec resolveOrComplain(String name) throws IOException {
        try {
            ThemeSpec spec = resolve(name);
            if (spec == null) {
                error(context.getString(R.string.theme_not_found) + ": " + name + Tuils.NEWLINE + "See every theme with: theme -list");
            }
            return spec;
        } catch (ThemeSpec.InvalidThemeException e) {
            error(context.getString(R.string.theme_invalid) + ": " + e.getMessage());
            return null;
        }
    }

//    ---------------------------------------------------------------- commands

    private void apply(String name) throws IOException {
        ThemeSpec spec = resolveOrComplain(name);
        if (spec != null) applySpec(spec);
    }

    private void list() {
        List<ThemeGallery.Entry> local = ThemeGallery.local(context);
        Set<String> localNames = new HashSet<>();
        for (ThemeGallery.Entry e : local) localNames.add(e.name);

        List<ThemeGallery.Entry> online = ThemeGallery.onlineOnly(context, client, localNames);

        int nameWidth = 0;
        for (ThemeGallery.Entry e : local) nameWidth = Math.max(nameWidth, e.name.length());
        for (ThemeGallery.Entry e : online) nameWidth = Math.max(nameWidth, e.name.length());

        int outputColor = XMLPrefsManager.getColor(Theme.output_color);

        SpannableStringBuilder b = new SpannableStringBuilder();
        appendColored(b, "Theme gallery: " + (local.size() + online.size()) + " themes", outputColor, null);

        appendSection(b, "Saved on this phone", local, ThemeGallery.Source.SAVED, nameWidth, outputColor);
        appendSection(b, "Built in", local, ThemeGallery.Source.BUILT_IN, nameWidth, outputColor);
        appendSection(b, "New in the online gallery", online, ThemeGallery.Source.ONLINE, nameWidth, outputColor);

        appendColored(b, Tuils.NEWLINE + Tuils.NEWLINE
                + "theme -preview [name]  see a theme first" + Tuils.NEWLINE
                + "theme -apply [name]    use it (theme -old goes back)" + Tuils.NEWLINE
                + "Pictures of every theme: " + ThemeGallery.GALLERY_PAGE, outputColor, null);

        Tuils.sendOutput(context, b, TerminalManager.CATEGORY_NO_COLOR);
    }

    private void appendSection(SpannableStringBuilder b, String title, List<ThemeGallery.Entry> entries,
                               ThemeGallery.Source source, int nameWidth, int outputColor) {
        boolean header = false;

        for (ThemeGallery.Entry e : entries) {
            if (e.source != source) continue;
            if (!header) {
                appendColored(b, Tuils.NEWLINE + Tuils.NEWLINE + title, outputColor, null);
                header = true;
            }

            b.append(Tuils.NEWLINE);
            appendColored(b, "  ", outputColor, null);
            appendColored(b, " " + pad(e.name, nameWidth) + " ", parse(e.accent, outputColor), solidBackground(e.bg));
            appendColored(b, "  " + (e.description.length() > 0 ? e.description : e.title), outputColor, null);
        }
    }

    private void preview(String name) throws IOException {
        ThemeSpec spec = resolveOrComplain(name);
        if (spec == null) return;

        int bg = solidBackground(spec.themeColor(Theme.bg_color));
        int output = parse(spec.themeColor(Theme.output_color), Color.WHITE);
        int input = parse(spec.themeColor(Theme.input_color), Color.GREEN);
        int device = parse(spec.themeColor(Theme.device_color), Color.YELLOW);
        int time = parse(spec.themeColor(Theme.time_color), output);
        int battery = parse(spec.themeColor(Theme.battery_color_high), output);
        int ram = parse(spec.themeColor(Theme.ram_color), output);
        int link = parse(spec.themeColor(Theme.link_color), output);
        int defaultText = parse(spec.suggestionColor(Suggestions.default_text_color), Color.BLACK);
        int currentOutput = XMLPrefsManager.getColor(Theme.output_color);

        SpannableStringBuilder b = new SpannableStringBuilder();
        appendColored(b, spec.title + (spec.author.length() > 0 ? "  by " + spec.author : ""), currentOutput, null);
        if (spec.description.length() > 0) appendColored(b, Tuils.NEWLINE + spec.description, currentOutput, null);
        b.append(Tuils.NEWLINE);

        previewLine(b, bg, "12:45", time, "  ", output, "Battery 87%", battery, "  ", output, "RAM 2.1 GB", ram);
        previewLine(b, bg);
        previewLine(b, bg, "user@helcli", device, ": ", output, "$ ls", input);
        previewLine(b, bg, "Documents  Music  notes.txt", output);
        previewLine(b, bg, "user@helcli", device, ": ", output, "$ open github.com", input);
        previewLine(b, bg, "https://github.com", link);
        previewLine(b, bg);

//        suggestion buttons, the way they look above the keyboard
        int start = b.length();
        appendColored(b, " ", Color.WHITE, bg);
        appendChip(b, "Camera", spec, Suggestions.apps_bg_color, Suggestions.apps_text_color, defaultText, bg);
        appendChip(b, "help", spec, Suggestions.cmd_bg_color, Suggestions.cmd_text_color, defaultText, bg);
        appendChip(b, "ll", spec, Suggestions.alias_bg_color, Suggestions.alias_text_color, defaultText, bg);
        appendChip(b, "notes", spec, Suggestions.file_bg_color, Suggestions.file_text_color, defaultText, bg);
        int used = b.length() - start;
        if (used < PREVIEW_WIDTH) appendColored(b, spaces(PREVIEW_WIDTH - used), Color.WHITE, bg);

        appendColored(b, Tuils.NEWLINE + "Use it with: theme -apply " + spec.name, currentOutput, null);

        Tuils.sendOutput(context, b, TerminalManager.CATEGORY_NO_COLOR);
    }

    private void importTheme(String what) throws IOException {
        if (what == null || what.length() == 0) return;

        ThemeSpec spec;
        if (what.startsWith("content://")) {
            String json;
            try (InputStream in = context.getContentResolver().openInputStream(Uri.parse(what))) {
                if (in == null) {
                    error(context.getString(R.string.theme_unable));
                    return;
                }
                json = readLimited(in);
            }
            if (json == null) {
                error("That file is too big to be a theme");
                return;
            }

            try {
                spec = ThemeSpec.parse(json);
            } catch (ThemeSpec.InvalidThemeException e) {
                error(context.getString(R.string.theme_invalid) + ": " + e.getMessage());
                return;
            }
        } else {
            spec = resolveOrComplain(what);
            if (spec == null) return;
        }

        ThemeGallery.save(spec);
        reloadable.addMessage("Imported \"" + spec.title + "\", saved as \"" + spec.name + "\"", null);
        applySpec(spec);
    }

    private void export(String name) throws IOException {
        name = ThemeSpec.normalizeName(name);
        if (!ThemeSpec.isValidName(name)) {
            error("Theme names can use letters, numbers, - and _ (up to 40 characters)");
            return;
        }

        File f = ThemeGallery.save(currentTheme(name));

        output("Saved your current colors as the theme \"" + name + "\"" + Tuils.NEWLINE
                + f.getAbsolutePath() + Tuils.NEWLINE
                + "Apply it any time with: theme -apply " + name + Tuils.NEWLINE
                + "Send it to someone with: theme -share " + name);
    }

    private void share(String name) throws IOException {
        ThemeSpec spec = resolveOrComplain(name);
        if (spec == null) return;

//        write a copy to the cache, so built-in and online themes can be shared too
        File cacheRoot = context.getExternalCacheDir() != null ? context.getExternalCacheDir() : context.getCacheDir();
        File dir = new File(cacheRoot, "shared-themes");
        if (!dir.exists()) dir.mkdirs();
        File f = new File(dir, spec.name + ".helcli-theme.json");
        try (OutputStream out = new FileOutputStream(f)) {
            out.write(spec.toJson().toString(2).getBytes("UTF-8"));
        } catch (org.json.JSONException e) {
            throw new IOException(e);
        }

        Uri uri = FileProvider.getUriForFile(context, GenericFileProvider.PROVIDER_NAME, f);

        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("application/json");
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.putExtra(Intent.EXTRA_SUBJECT, "HelCLI theme: " + spec.title);
        send.putExtra(Intent.EXTRA_TEXT, "A theme for HelCLI. Save the file, then run: theme -import");
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        Intent chooser = Intent.createChooser(send, "Share the theme " + spec.name);
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(chooser);
    }

    private void delete(String name) {
        if (ThemeGallery.delete(name)) {
            output("Deleted the saved theme \"" + ThemeSpec.normalizeName(name) + "\"");
        } else {
            error("There's no saved theme called \"" + name + "\" (built-in themes can't be deleted)");
        }
    }

//    ---------------------------------------------------------------- applying

    /** Writes every color of the theme (defaults for the ones it leaves out), then reloads the launcher. */
    private void applySpec(ThemeSpec spec) {
        File themeFile = new File(Tuils.getFolder(), XMLPrefsManager.XMLPrefsRoot.THEME.path);
        File suggestionsFile = new File(Tuils.getFolder(), XMLPrefsManager.XMLPrefsRoot.SUGGESTIONS.path);

        backup(themeFile);
        backup(suggestionsFile);

        Map<String, String> themeValues = new LinkedHashMap<>();
        for (Theme t : Theme.values()) {
            if (XMLPrefsSave.COLOR.equals(t.type())) themeValues.put(t.label(), spec.themeColor(t));
        }
        Map<String, String> suggestionValues = new LinkedHashMap<>();
        for (Suggestions s : Suggestions.values()) {
            if (XMLPrefsSave.COLOR.equals(s.type())) suggestionValues.put(s.label(), spec.suggestionColor(s));
        }

        String problem = writeAll(themeFile, XMLPrefsManager.XMLPrefsRoot.THEME.name(), themeValues);
        if (problem == null) problem = writeAll(suggestionsFile, XMLPrefsManager.XMLPrefsRoot.SUGGESTIONS.name(), suggestionValues);
        if (problem != null) {
            error(context.getString(R.string.theme_unable) + ": " + problem);
            return;
        }

        rememberName(context, spec.title);
        reloadable.addMessage(context.getString(R.string.theme_applied) + Tuils.SPACE + spec.title, null);
        reloadable.reload();
    }

    private static final String NAME_PREFS = "helcli_theme", NAME_KEY = "current";

    private static void rememberName(Context context, String name) {
        context.getSharedPreferences(NAME_PREFS, Context.MODE_PRIVATE).edit().putString(NAME_KEY, name).apply();
    }

    /** Forgets the applied theme's name (after "reset", the theme is HelCLI's own again). */
    public static void forgetName(Context context) {
        context.getSharedPreferences(NAME_PREFS, Context.MODE_PRIVATE).edit().remove(NAME_KEY).apply();
    }

    /** The name of the theme applied last, for sysinfo. */
    public static String currentThemeName(Context context) {
        return context.getSharedPreferences(NAME_PREFS, Context.MODE_PRIVATE).getString(NAME_KEY, "Classic");
    }

    /** Sets many entries of a config file in one write. Returns an error message, or null. */
    private static String writeAll(File file, String rootName, Map<String, String> values) {
        if (!file.exists()) XMLPrefsManager.resetFile(file, rootName);

        String[] names = values.keySet().toArray(new String[0]);
        String[][] attributes = new String[names.length][1];
        for (int i = 0; i < names.length; i++) attributes[i][0] = values.get(names[i]);

        String result = XMLPrefsManager.setMany(file, names, new String[] {XMLPrefsManager.VALUE_ATTRIBUTE}, attributes);
//        setMany returns "" when it had to add the entries instead of updating them, which is fine here
        return result == null || result.length() == 0 ? null : result;
    }

    /** Copies a file into the "old" folder, so "theme -old" can bring it back. */
    private static void backup(File file) {
        if (!file.exists()) return;
        File oldFolder = new File(Tuils.getFolder(), "old");
        if (!oldFolder.exists()) oldFolder.mkdirs();

        try (InputStream in = new FileInputStream(file);
             OutputStream out = new FileOutputStream(new File(oldFolder, file.getName()))) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
        } catch (IOException e) {
            Tuils.log(e);
        }
    }

    /** The colors in use right now, as a theme. Only colors that differ from the defaults are kept. */
    private static ThemeSpec currentTheme(String name) {
        Map<String, String> theme = new LinkedHashMap<>();
        for (Theme t : Theme.values()) {
            if (!XMLPrefsSave.COLOR.equals(t.type())) continue;
            String v = XMLPrefsManager.get(t);
            if (ThemeSpec.isValidColor(v) && !v.equalsIgnoreCase(t.defaultValue())) theme.put(t.label(), v.replace(" ", ""));
        }

        Map<String, String> suggestions = new LinkedHashMap<>();
        for (Suggestions s : Suggestions.values()) {
            if (!XMLPrefsSave.COLOR.equals(s.type())) continue;
            String v = XMLPrefsManager.get(s);
            if (ThemeSpec.isValidColor(v) && !v.equalsIgnoreCase(s.defaultValue())) suggestions.put(s.label(), v.replace(" ", ""));
        }

//        a theme needs at least one color
        if (theme.isEmpty() && suggestions.isEmpty()) {
            theme.put(Theme.input_color.label(), Theme.input_color.defaultValue());
            theme.put(Theme.output_color.label(), Theme.output_color.defaultValue());
        }

        return new ThemeSpec(name, name, "", "Exported from HelCLI", theme, suggestions);
    }

    private void revert() {
        File oldTheme = Tuils.getOld(XMLPrefsManager.XMLPrefsRoot.THEME.path);
        File oldSuggestions = Tuils.getOld(XMLPrefsManager.XMLPrefsRoot.SUGGESTIONS.path);
        if (oldTheme == null && oldSuggestions == null) {
            error(context.getString(R.string.theme_old_not_found));
            return;
        }

//        swap the files, so running "theme -old" twice goes back and forth
        File tmpDir = new File(Tuils.getFolder(), "old-swap");
        tmpDir.mkdirs();
        swap(oldTheme, new File(Tuils.getFolder(), XMLPrefsManager.XMLPrefsRoot.THEME.path), tmpDir);
        swap(oldSuggestions, new File(Tuils.getFolder(), XMLPrefsManager.XMLPrefsRoot.SUGGESTIONS.path), tmpDir);
        Tuils.delete(tmpDir);

        rememberName(context, "previous theme");
        reloadable.addMessage(context.getString(R.string.theme_reverted), null);
        reloadable.reload();
    }

    private static void swap(File old, File current, File tmpDir) {
        if (old == null) return;
        File tmp = new File(tmpDir, current.getName());
        boolean hadCurrent = current.exists() && current.renameTo(tmp);
        old.renameTo(current);
        if (hadCurrent) tmp.renameTo(old);
    }

    private void standard() {
        File themeFile = new File(Tuils.getFolder(), XMLPrefsManager.XMLPrefsRoot.THEME.path);
        File suggestionsFile = new File(Tuils.getFolder(), XMLPrefsManager.XMLPrefsRoot.SUGGESTIONS.path);
        backup(themeFile);
        backup(suggestionsFile);

//        the files are rebuilt with default values when the launcher reloads
        themeFile.delete();
        suggestionsFile.delete();

        rememberName(context, "Classic");
        reloadable.addMessage(context.getString(R.string.theme_applied) + Tuils.SPACE + "standard", null);
        reloadable.reload();
    }

//    ---------------------------------------------------------------- helpers

    private static String readLimited(InputStream in) throws IOException {
        byte[] buffer = new byte[8192];
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int n;
        while ((n = in.read(buffer)) > 0) {
            out.write(buffer, 0, n);
            if (out.size() > MAX_IMPORT) return null;
        }
        return out.toString("UTF-8");
    }

    private static int parse(String color, int fallback) {
        if (color == null || color.length() == 0) return fallback;
        try {
//            lists (one color per status line) use their first color
            return Color.parseColor(color.split(",")[0].trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    /** Theme backgrounds are often transparent (the launcher draws black behind them): preview them as opaque. */
    private static int solidBackground(String color) {
        int bg = parse(color, Color.BLACK);
        if (Color.alpha(bg) < 0x40) return Color.BLACK;
        return bg | 0xff000000;
    }

    private static void appendColored(SpannableStringBuilder b, String text, int fg, Integer bg) {
        int start = b.length();
        b.append(text);
        b.setSpan(new ForegroundColorSpan(fg), start, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (bg != null) b.setSpan(new BackgroundColorSpan(bg), start, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    /** One preview line: pairs of (text, color) on the theme background, padded to a fixed width. */
    private static void previewLine(SpannableStringBuilder b, int bg, Object... textAndColor) {
        int start = b.length();
        appendColored(b, " ", Color.WHITE, bg);
        for (int i = 0; i + 1 < textAndColor.length; i += 2) {
            appendColored(b, (String) textAndColor[i], (Integer) textAndColor[i + 1], bg);
        }
        int used = b.length() - start;
        if (used < PREVIEW_WIDTH) appendColored(b, spaces(PREVIEW_WIDTH - used), Color.WHITE, bg);
        b.append(Tuils.NEWLINE);
    }

    private static void appendChip(SpannableStringBuilder b, String text, ThemeSpec spec, Suggestions bgKey,
                                   Suggestions textKey, int defaultText, int bg) {
        appendColored(b, " " + text + " ", parse(spec.suggestionColor(textKey), defaultText),
                parse(spec.suggestionColor(bgKey), Color.WHITE));
        appendColored(b, " ", Color.WHITE, bg);
    }

    private static String pad(String s, int width) {
        return s.length() >= width ? s : s + spaces(width - s.length());
    }

    private static String spaces(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(' ');
        return sb.toString();
    }
}
