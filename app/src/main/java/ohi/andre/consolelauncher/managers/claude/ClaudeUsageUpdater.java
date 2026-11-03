package ohi.andre.consolelauncher.managers.claude;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;

import java.util.TimeZone;

import ohi.andre.consolelauncher.BuildConfig;
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager;
import ohi.andre.consolelauncher.managers.xml.options.Behavior;
import ohi.andre.consolelauncher.tuils.Tuils;
import okhttp3.CacheControl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Keeps the Claude usage line up to date: every claude_update_time seconds it reads the plan limits
 * (from the gist set in claude_plan_source) and this month's API spend (with the Admin API key saved by
 * "usage -key"), then hands the finished line to the listener on the main thread.
 *
 * The Admin API key is kept in the app's private storage, never in the config files, so "git pull",
 * "config -edit" and theme exports can't expose it.
 */
public class ClaudeUsageUpdater {

    public static final String ACTION_UPDATE = BuildConfig.APPLICATION_ID + ".claude_usage_update";

    private static final String PREFS = "helcli_claude";
    private static final String KEY_ADMIN = "admin_key";

    public interface Listener {
        /** @param highest the highest percentage on the line (-1 if none), for warning colors */
        void onUsage(CharSequence line, double highest);
    }

    private final Context context;
    private final OkHttpClient client;
    private final Handler handler;
    private final Listener listener;

//    the last good values, kept when one source fails so the line doesn't flicker to "-"
    private volatile ClaudeUsage.Plan plan;
    private volatile Double spent;
    private volatile String planProblem, apiProblem;
    private volatile boolean running, stopped;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, Math.max(XMLPrefsManager.getInt(Behavior.claude_update_time), 300) * 1000L);
        }
    };

    public ClaudeUsageUpdater(Context context, OkHttpClient client, Handler handler, Listener listener) {
        this.context = context.getApplicationContext();
        this.client = client;
        this.handler = handler;
        this.listener = listener;
    }

    public void start() {
        stopped = false;
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    public void stop() {
        stopped = true;
        handler.removeCallbacks(tick);
    }

//    ---------------------------------------------------------------- the key

    public static String adminKey(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ADMIN, null);
    }

    public static void saveAdminKey(Context context, String key) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_ADMIN, key.trim()).apply();
    }

    public static boolean forgetAdminKey(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        boolean had = p.contains(KEY_ADMIN);
        p.edit().remove(KEY_ADMIN).apply();
        return had;
    }

//    ---------------------------------------------------------------- fetching

    /** Fetches both sources off the main thread, then updates the line. */
    public void refresh() {
        if (running) return;
        running = true;

        new Thread(() -> {
            try {
                final String planSource = XMLPrefsManager.get(Behavior.claude_plan_source);
                final String key = adminKey(context);
                final boolean hasPlan = planSource != null && planSource.trim().length() > 0;
                final boolean hasApi = key != null;

                if (hasPlan) fetchPlan(planSource);
                if (hasApi) fetchSpent(key);

                final String line = buildLine(hasPlan, hasApi);
                final double highest = ClaudeUsage.highest(plan, spent, budget());
                if (!stopped) handler.post(() -> {
                    if (!stopped) listener.onUsage(line, highest);
                });
            } catch (Exception e) {
                Tuils.log(e);
            } finally {
                running = false;
            }
        }).start();
    }

    private void fetchPlan(String source) {
        String url = ClaudeUsage.planUrl(source);
        if (url == null) {
            planProblem = "claude_plan_source should be user/gist_id";
            return;
        }
        try {
            String body = get(new Request.Builder().url(url));
            plan = ClaudeUsage.parsePlan(body, System.currentTimeMillis() / 1000);
            planProblem = null;
        } catch (HttpProblem e) {
            planProblem = e.code == 404 ? "plan gist not found" : "plan: GitHub answered " + e.code;
        } catch (Exception e) {
            Tuils.log(e);
            if (plan == null) planProblem = "plan: no connection";
        }
    }

    private void fetchSpent(String key) {
        try {
            String body = get(new Request.Builder()
                    .url(ClaudeUsage.costUrl(System.currentTimeMillis()))
                    .header("x-api-key", key)
                    .header("anthropic-version", ClaudeUsage.ANTHROPIC_VERSION)
                    .header("anthropic-beta", ClaudeUsage.ANALYTICS_BETA));
            spent = ClaudeUsage.parseCostDollars(body);
            apiProblem = null;
        } catch (HttpProblem e) {
            if (e.code == 401) apiProblem = "API key rejected (usage -link)";
            else if (e.code == 403) apiProblem = "API key can't read costs";
            else if (e.code == 429) apiProblem = null; // keep the last value, try again next time
            else apiProblem = "API: Anthropic answered " + e.code;
        } catch (Exception e) {
            Tuils.log(e);
            if (spent == null) apiProblem = "API: no connection";
        }
    }

    private String buildLine(boolean hasPlan, boolean hasApi) {
        String format = XMLPrefsManager.get(Behavior.claude_format);
        double budget = budget();
        if (format == null || format.trim().length() == 0) {
            format = ClaudeUsage.defaultFormat(hasPlan, hasApi, budget > 0);
        }

        String line = ClaudeUsage.format(format, plan, spent, budget, System.currentTimeMillis(), TimeZone.getDefault(),
                android.text.format.DateFormat.is24HourFormat(context));
        if (hasPlan && planProblem != null) line += "  (" + planProblem + ")";
        if (hasApi && apiProblem != null) line += "  (" + apiProblem + ")";
        return line;
    }

    private static double budget() {
        try {
            return Double.parseDouble(XMLPrefsManager.get(Behavior.claude_budget_usd).trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private static class HttpProblem extends Exception {
        final int code;

        HttpProblem(int code) {
            super("HTTP " + code);
            this.code = code;
        }
    }

    private String get(Request.Builder builder) throws Exception {
        Request r = builder.cacheControl(CacheControl.FORCE_NETWORK).header("User-Agent", "HelCLI").get().build();
        try (Response response = client.newCall(r).execute()) {
            if (!response.isSuccessful() || response.body() == null) throw new HttpProblem(response.code());
            return response.body().string();
        }
    }
}
