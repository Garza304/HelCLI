package ohi.andre.consolelauncher.managers.ask;

import android.content.Context;
import android.content.SharedPreferences;

/** Where "ask" keeps the Claude API key: HelCLI's private storage, never the config files. */
public class AskKey {

    private AskKey() {}

    private static final String PREFS = "helcli_ask", KEY = "api_key", WORKSPACE = "workspace_id";

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String get(Context c) {
        return prefs(c).getString(KEY, null);
    }

    public static void save(Context c, String key) {
        prefs(c).edit().putString(KEY, key.trim()).apply();
    }

    public static void forget(Context c) {
        prefs(c).edit().remove(KEY).apply();
    }

    /**
     * A Claude key that can ask questions: a personal key (sk-ant-usr-...), a service account key, or an
     * older workspace key (sk-ant-api03-...). Not an Admin key, which only manages the organization.
     */
    public static boolean looksLikeApiKey(String key) {
        return key != null && key.trim().matches("sk-ant-(?!admin)[a-z]+[0-9]*-[A-Za-z0-9_-]{20,}");
    }

    /**
     * The workspace to use (wrkspc_...), for a key that isn't tied to one workspace: Claude refuses
     * those keys unless each request names a workspace. Null when not set.
     */
    public static String workspace(Context c) {
        return prefs(c).getString(WORKSPACE, null);
    }

    public static void saveWorkspace(Context c, String id) {
        if (id == null || id.length() == 0) prefs(c).edit().remove(WORKSPACE).apply();
        else prefs(c).edit().putString(WORKSPACE, id.trim()).apply();
    }

    public static boolean looksLikeWorkspace(String id) {
        return id != null && id.trim().matches("wrkspc_[A-Za-z0-9]{8,}");
    }
}
