package ohi.andre.consolelauncher.managers.sync;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ohi.andre.consolelauncher.managers.AliasManager;
import ohi.andre.consolelauncher.managers.themes.ThemeGallery;
import ohi.andre.consolelauncher.managers.themes.ThemeSpec;
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager;
import ohi.andre.consolelauncher.tuils.Tuils;
import ohi.andre.consolelauncher.tuils.interfaces.Reloadable;
import okhttp3.CacheControl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * "git pull" for HelCLI: copies settings from a folder in a GitHub repository onto the phone.
 *
 * It isn't real git (there's no git on Android): it asks GitHub for the newest commit, downloads
 * the files in the folder at exactly that commit, and applies them:
 * <ul>
 *     <li>theme.xml, ui.xml, behavior.xml, suggestions.xml, toolbar.xml, cmd.xml: the settings in the
 *     file are applied one by one; settings the file doesn't mention stay as they are</li>
 *     <li>alias.txt: replaces your aliases</li>
 *     <li>themes/*.json: saved as themes, ready for "theme -apply"</li>
 * </ul>
 * Before changing anything the old files are copied to "sync-backup", so "git undo" can restore them.
 * Works with public repositories, without an account.
 */
public class GitSync {

    private static final String PREFS = "helcli_git";
    private static final String KEY_REMOTE = "remote", KEY_SHA = "last_sha", KEY_TIME = "last_time";
    private static final String BACKUP_DIR = "sync-backup";
    private static final String THEMES_DIR = "themes";
    private static final int MAX_FILE = 256 * 1024;
    private static final int MAX_FILES = 60;

    private final Context context;
    private final OkHttpClient client;
    private final Reloadable reloadable;

    public GitSync(Context context, OkHttpClient client, Reloadable reloadable) {
        this.context = context;
        this.client = client;
        this.reloadable = reloadable;
    }

    private SharedPreferences prefs() {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public RepoSync.Remote remote() {
        return RepoSync.parseRemote(prefs().getString(KEY_REMOTE, null));
    }

    public void setRemote(RepoSync.Remote remote) {
        prefs().edit().putString(KEY_REMOTE, remote.toString()).remove(KEY_SHA).remove(KEY_TIME).apply();
    }

    private void out(CharSequence s) {
        Tuils.sendOutput(context, s);
    }

    private void err(CharSequence s) {
        Tuils.sendOutput(Color.RED, context, s);
    }

    public String status() {
        RepoSync.Remote r = remote();
        if (r == null) return noRemoteMessage();

        StringBuilder b = new StringBuilder();
        b.append("Remote: ").append(r).append(Tuils.NEWLINE).append(r.webUrl());
        String sha = prefs().getString(KEY_SHA, null);
        long time = prefs().getLong(KEY_TIME, 0);
        if (sha != null && time > 0) {
            b.append(Tuils.NEWLINE).append("Last pull: commit ").append(RepoSync.shortSha(sha)).append(", ")
                    .append(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(time)));
        } else {
            b.append(Tuils.NEWLINE).append("Not pulled yet. Run: git pull");
        }
        if (new File(Tuils.getFolder(), BACKUP_DIR).isDirectory()) {
            b.append(Tuils.NEWLINE).append("git undo restores the settings from before the last pull");
        }
        return b.toString();
    }

    public static String noRemoteMessage() {
        return "No remote set. Point HelCLI at a folder in a public GitHub repository, for example:"
                + Tuils.NEWLINE + "  git remote Garza304/HelCLI/my-config"
                + Tuils.NEWLINE + "  git remote https://github.com/you/dotfiles/tree/main/helcli";
    }

//    ---------------------------------------------------------------- pull

    /** Call off the main thread. */
    public void pull() {
        RepoSync.Remote remote = remote();
        if (remote == null) {
            err(noRemoteMessage());
            return;
        }
        if (!Tuils.hasInternetAccess()) {
            err("git pull: no internet connection");
            return;
        }

        try {
            String sha = get(remote.commitUrl(), "application/vnd.github.sha", "the repository or branch").trim();
            if (!sha.matches("[0-9a-f]{40}")) throw new GitException("GitHub sent an unexpected answer");

            String previous = prefs().getString(KEY_SHA, null);
            File folder = Tuils.getFolder();

            Map<File, String> fileWrites = new LinkedHashMap<>();
            Map<XMLPrefsManager.XMLPrefsRoot, List<RepoSync.Change>> prefChanges = new LinkedHashMap<>();
            List<String> warnings = new ArrayList<>();
            List<String> report = new ArrayList<>();
            List<ThemeSpec> themes = new ArrayList<>();
            int files = 0;

            for (Entry e : list(remote, "", sha)) {
                if (++files > MAX_FILES) {
                    warnings.add("more than " + MAX_FILES + " files: the rest were skipped");
                    break;
                }

                if (e.isDir) {
                    if (e.name.equals(THEMES_DIR)) {
                        for (Entry t : list(remote, THEMES_DIR, sha)) {
                            if (t.isDir || !t.name.endsWith(".json")) continue;
                            String json = download(remote.rawUrl(THEMES_DIR + "/" + t.name, sha), t);
                            try {
                                ThemeSpec spec = ThemeSpec.parse(json);
                                File existing = new File(ThemeGallery.savedFolder(), spec.name + ".json");
                                if (!existing.exists() || !sameTheme(existing, spec)) {
                                    themes.add(spec);
                                    report.add("themes/" + t.name + " -> theme \"" + spec.name + "\"");
                                }
                            } catch (ThemeSpec.InvalidThemeException ex) {
                                warnings.add("themes/" + t.name + ": " + ex.getMessage() + ", skipped");
                            }
                        }
                    }
                    continue;
                }

                String lower = e.name.toLowerCase(java.util.Locale.ROOT);
                if (lower.endsWith(".md") || lower.startsWith(".")) continue;

                XMLPrefsManager.XMLPrefsRoot root = RepoSync.prefsRootFor(e.name);
                if (root != null) {
                    String xml = download(remote.rawUrl(e.name, sha), e);
                    Map<String, String> incoming;
                    try {
                        incoming = RepoSync.readEntries(xml);
                    } catch (Exception ex) {
                        warnings.add(e.name + " isn't valid XML (" + ex.getMessage() + "), skipped");
                        continue;
                    }

                    File local = new File(folder, root.path);
                    Map<String, String> current = new HashMap<>();
                    if (local.exists()) {
                        try {
                            current = RepoSync.readEntries(read(local));
                        } catch (Exception ex) {
                            Tuils.log(ex);
                        }
                    }

                    RepoSync.Plan plan = RepoSync.plan(root, current, incoming);
                    warnings.addAll(plan.warnings);
                    if (!plan.changes.isEmpty()) {
                        prefChanges.put(root, plan.changes);
                        for (RepoSync.Change c : plan.changes) {
                            report.add(c.file + "  " + c.key + ": " + show(c.oldValue) + " -> " + show(c.newValue));
                        }
                    }
                } else if (e.name.equals(AliasManager.PATH)) {
                    String text = download(remote.rawUrl(e.name, sha), e);
                    File local = new File(folder, AliasManager.PATH);
                    if (!local.exists() || !read(local).equals(text)) {
                        fileWrites.put(local, text);
                        report.add(AliasManager.PATH + " replaced (" + text.split("\n").length + " lines)");
                    }
                } else {
                    warnings.add(e.name + ": HelCLI doesn't sync this file, skipped");
                }
            }

            StringBuilder b = new StringBuilder();
            b.append("From ").append(remote.owner).append('/').append(remote.repo);
            if (remote.path.length() > 0) b.append(" (").append(remote.path).append(')');
            b.append(Tuils.NEWLINE);
            if (previous != null && !previous.equals(sha)) {
                b.append("Updating ").append(RepoSync.shortSha(previous)).append("..").append(RepoSync.shortSha(sha));
            } else {
                b.append("At commit ").append(RepoSync.shortSha(sha));
            }

            for (String w : warnings) b.append(Tuils.NEWLINE).append("warning: ").append(w);

            if (report.isEmpty()) {
                b.append(Tuils.NEWLINE).append("Already up to date.");
                out(b.toString());
                remember(sha);
                return;
            }

            for (String r : report) b.append(Tuils.NEWLINE).append(' ').append(r);
            int settings = 0;
            for (List<RepoSync.Change> l : prefChanges.values()) settings += l.size();
            b.append(Tuils.NEWLINE).append(settings).append(settings == 1 ? " setting" : " settings").append(" changed");
            if (!fileWrites.isEmpty()) b.append(", aliases replaced");
            if (!themes.isEmpty()) b.append(", ").append(themes.size()).append(themes.size() == 1 ? " theme" : " themes").append(" saved");
            out(b.toString());

//            apply
            List<File> touched = new ArrayList<>();
            for (XMLPrefsManager.XMLPrefsRoot root : prefChanges.keySet()) touched.add(new File(folder, root.path));
            touched.addAll(fileWrites.keySet());
            backup(touched);

            for (Map.Entry<XMLPrefsManager.XMLPrefsRoot, List<RepoSync.Change>> entry : prefChanges.entrySet()) {
                applyPrefs(new File(folder, entry.getKey().path), entry.getKey(), entry.getValue());
            }
            for (Map.Entry<File, String> w : fileWrites.entrySet()) write(w.getKey(), w.getValue());
            for (ThemeSpec t : themes) ThemeGallery.save(t);

            remember(sha);

            if (!touched.isEmpty()) {
                String header = "git pull " + RepoSync.shortSha(sha) + ": " + settings + (settings == 1 ? " setting" : " settings") + " changed (git undo reverts)";
                reloadable.addMessage(header, null);
                reloadable.reload();
            }
        } catch (GitException ex) {
            err("git pull: " + ex.getMessage());
        } catch (Exception ex) {
            Tuils.log(ex);
            err("git pull: " + ex);
        }
    }

    /** Puts the files saved before the last pull back. */
    public void undo() {
        File backups = new File(Tuils.getFolder(), BACKUP_DIR);
        File[] files = backups.listFiles();
        if (files == null || files.length == 0) {
            err("Nothing to undo: no pull has changed your settings yet");
            return;
        }

        int restored = 0;
        for (File f : files) {
            File target = new File(Tuils.getFolder(), f.getName());
            try {
                if (f.getName().endsWith(".missing")) {
//                    the file didn't exist before the pull
                    new File(Tuils.getFolder(), f.getName().substring(0, f.getName().length() - ".missing".length())).delete();
                } else {
                    copy(f, target);
                }
                restored++;
            } catch (IOException e) {
                err("Couldn't restore " + f.getName() + ": " + e.getMessage());
            }
        }
        Tuils.delete(backups);

        reloadable.addMessage("git undo: restored " + restored + (restored == 1 ? " file" : " files") + " from before the last pull", null);
        reloadable.reload();
    }

//    ---------------------------------------------------------------- applying

    private static void applyPrefs(File file, XMLPrefsManager.XMLPrefsRoot root, List<RepoSync.Change> changes) {
        if (!file.exists()) XMLPrefsManager.resetFile(file, root.name());

        String[] names = new String[changes.size()];
        String[][] values = new String[changes.size()][1];
        for (int i = 0; i < changes.size(); i++) {
            names[i] = changes.get(i).key;
            values[i][0] = changes.get(i).newValue;
        }
        XMLPrefsManager.setMany(file, names, new String[] {XMLPrefsManager.VALUE_ATTRIBUTE}, values);
    }

    private static void backup(List<File> files) throws IOException {
        File dir = new File(Tuils.getFolder(), BACKUP_DIR);
        if (dir.exists()) Tuils.delete(dir);
        if (!dir.mkdirs()) throw new IOException("couldn't create " + dir);

        for (File f : files) {
            if (f.exists()) copy(f, new File(dir, f.getName()));
            else new File(dir, f.getName() + ".missing").createNewFile();
        }
    }

    private void remember(String sha) {
        prefs().edit().putString(KEY_SHA, sha).putLong(KEY_TIME, System.currentTimeMillis()).apply();
    }

    private static boolean sameTheme(File existing, ThemeSpec incoming) {
        try {
            ThemeSpec old = ThemeSpec.parse(read(existing));
            return old.title.equals(incoming.title) && old.description.equals(incoming.description)
                    && old.theme.equals(incoming.theme) && old.suggestions.equals(incoming.suggestions);
        } catch (Exception e) {
            return false;
        }
    }

    private static String show(String value) {
        return value == null || value.length() == 0 ? "\"\"" : value;
    }

//    ---------------------------------------------------------------- GitHub

    private static class Entry {
        final String name;
        final boolean isDir;
        final long size;

        Entry(String name, boolean isDir, long size) {
            this.name = name;
            this.isDir = isDir;
            this.size = size;
        }
    }

    static class GitException extends Exception {
        GitException(String message) {
            super(message);
        }
    }

    private List<Entry> list(RepoSync.Remote remote, String subPath, String sha) throws IOException, GitException {
        String body = get(remote.contentsUrl(subPath, sha), "application/vnd.github+json", "the folder \"" + (remote.path + "/" + subPath).replaceAll("^/|/$", "") + "\"");
        List<Entry> out = new ArrayList<>();
        try {
            if (!body.trim().startsWith("[")) {
                throw new GitException("the remote has to be a folder, not a single file");
            }
            JSONArray a = new JSONArray(body);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                out.add(new Entry(o.getString("name"), "dir".equals(o.optString("type")), o.optLong("size", 0)));
            }
        } catch (org.json.JSONException e) {
            throw new GitException("GitHub sent an unexpected answer");
        }
        return out;
    }

    private String download(String url, Entry e) throws IOException, GitException {
        if (e.size > MAX_FILE) throw new GitException(e.name + " is too big (over " + (MAX_FILE / 1024) + " KB)");
        return get(url, null, e.name);
    }

    private String get(String url, String accept, String what) throws IOException, GitException {
        Request.Builder builder = new Request.Builder().url(url).cacheControl(CacheControl.FORCE_NETWORK)
                .header("User-Agent", "HelCLI").get();
        if (accept != null) builder.header("Accept", accept);
        if (url.startsWith("https://api.github.com/")) builder.header("X-GitHub-Api-Version", "2022-11-28");

        try (Response r = client.newCall(builder.build()).execute()) {
            if (r.code() == 404) {
                throw new GitException("couldn't find " + what + ". Check \"git remote\" (the repository has to be public)");
            }
            if ((r.code() == 403 || r.code() == 429) && "0".equals(r.header("x-ratelimit-remaining"))) {
                String reset = r.header("x-ratelimit-reset");
                String when = "in a while";
                try {
                    when = "after " + DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(Long.parseLong(reset) * 1000));
                } catch (Exception ignored) {
                }
                throw new GitException("GitHub allows 60 requests an hour without an account, and they're used up. Try again " + when);
            }
            if (!r.isSuccessful() || r.body() == null) throw new GitException("GitHub answered " + r.code() + " for " + what);

            byte[] bytes = r.body().bytes();
            if (bytes.length > MAX_FILE) throw new GitException(what + " is too big (over " + (MAX_FILE / 1024) + " KB)");
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

//    ---------------------------------------------------------------- files

    private static String read(File f) throws IOException {
        return ThemeGallery.readFile(f);
    }

    private static void write(File f, String text) throws IOException {
        try (OutputStream out = new FileOutputStream(f)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void copy(File from, File to) throws IOException {
        try (InputStream in = new FileInputStream(from); OutputStream out = new FileOutputStream(to)) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
        }
    }
}
