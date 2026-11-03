package ohi.andre.consolelauncher.managers.ask;

import android.content.Context;
import android.content.SharedPreferences;

/** Where "ask" keeps the Claude API key: HelCLI's private storage, never the config files. */
public class AskKey {

    private AskKey() {}

    private static final String PREFS = "helcli_ask", KEY = "api_key";

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

    /** A Claude API key (sk-ant-api03-...). */
    public static boolean looksLikeApiKey(String key) {
        return key != null && key.trim().matches("sk-ant-api\\d+-[A-Za-z0-9_-]{20,}");
    }
}
