package ohi.andre.consolelauncher.managers.themes;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import ohi.andre.consolelauncher.tuils.Tuils;
import okhttp3.CacheControl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Finds themes in three places, in this order:
 * <ol>
 *     <li>saved: themes you exported or imported, in the "themes" folder next to theme.xml</li>
 *     <li>built-in: the gallery themes bundled into the app, so they work offline</li>
 *     <li>online: the gallery in the HelCLI GitHub repository, for themes added after this version</li>
 * </ol>
 */
public class ThemeGallery {

    /** The /themes folder of the HelCLI repository. index.json lists every theme in it. */
    public static final String ONLINE_BASE = "https://raw.githubusercontent.com/Garza304/HelCLI/master/themes/";
    public static final String ONLINE_INDEX = ONLINE_BASE + "index.json";
    /** Human readable gallery page, with a preview of every theme. */
    public static final String GALLERY_PAGE = "https://github.com/Garza304/HelCLI/tree/master/themes";

    private static final String ASSETS_DIR = "themes";
    private static final String SAVED_DIR = "themes";
    private static final String INDEX_CACHE = ".online-index.json";
    private static final long MAX_DOWNLOAD = 256 * 1024;

    public enum Source {
        SAVED("saved"), BUILT_IN("built-in"), ONLINE("online");

        public final String label;

        Source(String label) {
            this.label = label;
        }
    }

    /** A theme in a listing. {@link #spec} is null for online themes that haven't been downloaded. */
    public static class Entry {
        public final String name, title, description;
        public final Source source;
        public final ThemeSpec spec;
        /** Background and input color, used to color the listing. Can be null. */
        public final String bg, accent;

        Entry(ThemeSpec spec, Source source) {
            this.name = spec.name;
            this.title = spec.title;
            this.description = spec.description;
            this.source = source;
            this.spec = spec;
            this.bg = spec.theme.get("bg_color");
            this.accent = spec.theme.get("input_color");
        }

        Entry(String name, String title, String description, String bg, String accent) {
            this.name = name;
            this.title = title;
            this.description = description;
            this.source = Source.ONLINE;
            this.spec = null;
            this.bg = bg;
            this.accent = accent;
        }
    }

    private static List<ThemeSpec> builtIn;
    /** Every theme name we know about, used for command suggestions. */
    private static final Set<String> knownNames = Collections.synchronizedSet(new TreeSet<String>());

    private ThemeGallery() {}

    public static File savedFolder() {
        File f = new File(Tuils.getFolder(), SAVED_DIR);
        if (!f.exists()) f.mkdirs();
        return f;
    }

    public static synchronized List<ThemeSpec> builtIn(Context context) {
        if (builtIn != null) return builtIn;

        List<ThemeSpec> list = new ArrayList<>();
        try {
            String[] files = context.getAssets().list(ASSETS_DIR);
            if (files != null) {
                for (String f : files) {
                    if (!f.endsWith(".json")) continue;
                    try (InputStream in = context.getAssets().open(ASSETS_DIR + "/" + f)) {
                        list.add(ThemeSpec.parse(Tuils.inputStreamToString(in)));
                    } catch (Exception e) {
                        Tuils.log(e);
                    }
                }
            }
        } catch (IOException e) {
            Tuils.log(e);
        }

        Collections.sort(list, (a, b) -> a.name.compareTo(b.name));
        builtIn = Collections.unmodifiableList(list);
        for (ThemeSpec s : builtIn) knownNames.add(s.name);
        return builtIn;
    }

    public static List<ThemeSpec> saved() {
        List<ThemeSpec> list = new ArrayList<>();
        File[] files = savedFolder().listFiles();
        if (files == null) return list;

        for (File f : files) {
            if (!f.getName().endsWith(".json") || f.getName().startsWith(".")) continue;
            try {
                list.add(ThemeSpec.parse(readFile(f)));
            } catch (Exception e) {
                Tuils.log(e);
            }
        }

        Collections.sort(list, (a, b) -> a.name.compareTo(b.name));
        for (ThemeSpec s : list) knownNames.add(s.name);
        return list;
    }

    /** Saved and built-in themes. Works offline. */
    public static List<Entry> local(Context context) {
        Map<String, Entry> out = new LinkedHashMap<>();
        for (ThemeSpec s : saved()) out.put(s.name, new Entry(s, Source.SAVED));
        for (ThemeSpec s : builtIn(context)) {
            if (!out.containsKey(s.name)) out.put(s.name, new Entry(s, Source.BUILT_IN));
        }
        return new ArrayList<>(out.values());
    }

    /**
     * Themes in the online gallery that aren't saved or built in. Uses the last downloaded index
     * when there's no connection. Call this off the main thread.
     */
    public static List<Entry> onlineOnly(Context context, OkHttpClient client, Set<String> exclude) {
        String json = null;
        File cache = new File(savedFolder(), INDEX_CACHE);

        if (client != null && Tuils.hasInternetAccess()) {
            json = download(client, ONLINE_INDEX);
            if (json != null) writeQuietly(cache, json);
        }
        if (json == null && cache.exists()) {
            try {
                json = readFile(cache);
            } catch (Exception e) {
                Tuils.log(e);
            }
        }

        List<Entry> list = new ArrayList<>();
        if (json == null) return list;

        try {
            JSONArray themes = new JSONObject(json).getJSONArray("themes");
            for (int i = 0; i < themes.length(); i++) {
                JSONObject t = themes.getJSONObject(i);
                String name = t.optString("name", "");
                if (!ThemeSpec.isValidName(name)) continue;
                knownNames.add(name);
                if (exclude.contains(name)) continue;

                JSONObject swatch = t.optJSONObject("swatch");
                list.add(new Entry(name, t.optString("title", name), t.optString("description", ""),
                        swatch == null ? null : swatch.optString("bg", null),
                        swatch == null ? null : swatch.optString("input", null)));
            }
        } catch (Exception e) {
            Tuils.log(e);
        }
        return list;
    }

    /** Looks a theme up by name: saved first, then built-in, then online. Call off the main thread. */
    public static ThemeSpec find(Context context, OkHttpClient client, String name) throws ThemeSpec.InvalidThemeException {
        name = ThemeSpec.normalizeName(name);
        if (!ThemeSpec.isValidName(name)) return null;

        File saved = new File(savedFolder(), name + ".json");
        if (saved.exists()) {
            try {
                return ThemeSpec.parse(readFile(saved));
            } catch (IOException e) {
                Tuils.log(e);
            }
        }

        for (ThemeSpec s : builtIn(context)) {
            if (s.name.equals(name)) return s;
        }

        if (client != null && Tuils.hasInternetAccess()) {
            String json = download(client, ONLINE_BASE + name + ".json");
            if (json != null) return ThemeSpec.parse(json);
        }
        return null;
    }

    /** Saves a theme to the themes folder, so it's available offline and can be shared. */
    public static File save(ThemeSpec spec) throws IOException {
        File f = new File(savedFolder(), spec.name + ".json");
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(spec.toJson().toString(2).getBytes(StandardCharsets.UTF_8));
        } catch (org.json.JSONException e) {
            throw new IOException(e);
        }
        knownNames.add(spec.name);
        return f;
    }

    /** Deletes a saved theme. Built-in themes can't be deleted. */
    public static boolean delete(String name) {
        File f = new File(savedFolder(), ThemeSpec.normalizeName(name) + ".json");
        return ThemeSpec.isValidName(ThemeSpec.normalizeName(name)) && f.exists() && f.delete();
    }

    public static List<String> knownNames() {
        synchronized (knownNames) {
            return new ArrayList<>(knownNames);
        }
    }

    /** Downloads a small text file, or returns null. Call off the main thread. */
    public static String download(OkHttpClient client, String url) {
        try {
            Request request = new Request.Builder().url(url).cacheControl(CacheControl.FORCE_NETWORK).get().build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return null;
                long length = response.body().contentLength();
                if (length > MAX_DOWNLOAD) return null;
                String s = response.body().string();
                return s.length() > MAX_DOWNLOAD ? null : s;
            }
        } catch (Exception e) {
            Tuils.log(e);
            return null;
        }
    }

    public static String readFile(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            return Tuils.inputStreamToString(in);
        }
    }

    private static void writeQuietly(File f, String s) {
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(s.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            Tuils.log(e);
        }
    }
}
