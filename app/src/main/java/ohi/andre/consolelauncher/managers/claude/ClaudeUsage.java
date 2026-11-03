package ohi.andre.consolelauncher.managers.claude;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ohi.andre.consolelauncher.tuils.StatusBars;

/**
 * The Claude usage line on the home screen. It combines two sources, either of which can be missing:
 *
 * <ul>
 *     <li><b>Plan limits</b> (claude.ai Pro/Max/Ultra): the 5-hour and 7-day percentages. Claude Code
 *     gives these to its status line command; tools/claude-usage/helcli_usage.py publishes them to a
 *     secret GitHub gist, and the phone reads that gist. Anthropic has no other supported way to read them.</li>
 *     <li><b>API spend</b>: this month's cost from the Usage and Cost Analytics API
 *     (GET /v1/organizations/analytics/cost), which needs an Admin API key.</li>
 * </ul>
 *
 * Plain Java (only org.json), so it can be unit tested.
 */
public class ClaudeUsage {

    public static final String ANALYTICS_COST_URL = "https://api.anthropic.com/v1/organizations/analytics/cost";
    public static final String ANTHROPIC_VERSION = "2023-06-01";
    public static final String ANALYTICS_BETA = "analytics-usage-cost-2026-08-24";
    public static final String PLAN_FILE = "claude-usage.json";

    private ClaudeUsage() {}

//    ---------------------------------------------------------------- plan limits

    /** Plan limit percentages. Any value can be null when Claude Code didn't report it. */
    public static class Plan {
        public final Double fiveHour, sevenDay;
        /** Unix seconds. */
        public final Long fiveHourReset, sevenDayReset, updatedAt;

        public Plan(Double fiveHour, Long fiveHourReset, Double sevenDay, Long sevenDayReset, Long updatedAt) {
            this.fiveHour = fiveHour;
            this.fiveHourReset = fiveHourReset;
            this.sevenDay = sevenDay;
            this.sevenDayReset = sevenDayReset;
            this.updatedAt = updatedAt;
        }
    }

    /**
     * Reads the file published by helcli_usage.py: Claude Code's rate_limits object plus updated_at.
     * A window whose reset time has passed counts as 0% (a fresh window), the way Claude Code drops it.
     */
    public static Plan parsePlan(String json, long nowSeconds) throws JSONException {
        JSONObject o = new JSONObject(json);
        JSONObject limits = o.optJSONObject("rate_limits");
        if (limits == null) limits = o;

        Long updated = o.has("updated_at") ? o.optLong("updated_at") : null;

        Double five = null, seven = null;
        Long fiveReset = null, sevenReset = null;

        JSONObject f = limits.optJSONObject("five_hour");
        if (f != null && f.has("used_percentage")) {
            fiveReset = f.has("resets_at") ? f.optLong("resets_at") : null;
            five = fiveReset != null && fiveReset <= nowSeconds ? 0d : f.optDouble("used_percentage");
        }
        JSONObject s = limits.optJSONObject("seven_day");
        if (s != null && s.has("used_percentage")) {
            sevenReset = s.has("resets_at") ? s.optLong("resets_at") : null;
            seven = sevenReset != null && sevenReset <= nowSeconds ? 0d : s.optDouble("used_percentage");
        }
        if (five != null && five.isNaN()) five = null;
        if (seven != null && seven.isNaN()) seven = null;

        return new Plan(five, fiveReset, seven, sevenReset, updated);
    }

    private static final Pattern GIST_SHORT = Pattern.compile("^([A-Za-z0-9-]{1,39})/([0-9a-fA-F]{20,40})$");
    private static final Pattern GIST_PAGE = Pattern.compile("^https://gist\\.github\\.com/([A-Za-z0-9-]{1,39})/([0-9a-fA-F]{20,40})/?$");
    private static final Pattern GIST_RAW = Pattern.compile("^https://gist\\.githubusercontent\\.com/[A-Za-z0-9-]{1,39}/[0-9a-fA-F]{20,40}/raw/[A-Za-z0-9._/-]+$");

    /**
     * The address of the published plan file, from what's typed in claude_plan_source:
     * user/gist_id, a gist.github.com link, or a gist raw link. Null if it's none of these.
     */
    public static String planUrl(String source) {
        if (source == null) return null;
        source = source.trim();
        Matcher m = GIST_SHORT.matcher(source);
        if (!m.matches()) m = GIST_PAGE.matcher(source);
        if (m.matches()) {
            return "https://gist.githubusercontent.com/" + m.group(1) + "/" + m.group(2).toLowerCase(Locale.ROOT) + "/raw/" + PLAN_FILE;
        }
        return GIST_RAW.matcher(source).matches() ? source : null;
    }

//    ---------------------------------------------------------------- API spend

    /** starting_at and ending_at for the calendar month so far (UTC), as the analytics API expects. */
    public static String[] monthRange(long nowMillis) {
        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        iso.setTimeZone(TimeZone.getTimeZone("UTC"));

        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US);
        c.setTimeInMillis(nowMillis);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        String end = iso.format(c.getTime());

        c.set(Calendar.DAY_OF_MONTH, 1);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        String start = iso.format(c.getTime());
        return new String[] {start, end};
    }

    public static String costUrl(long nowMillis) {
        String[] range = monthRange(nowMillis);
        return ANALYTICS_COST_URL + "?starting_at=" + range[0] + "&ending_at=" + range[1];
    }

    /**
     * Total dollars in a cost answer. Amounts are decimal strings in cents ("41280.000000" is $412.80);
     * without group_by or time buckets there's one row, but every row is added up to be safe.
     */
    public static double parseCostDollars(String json) throws JSONException {
        JSONArray data = new JSONObject(json).optJSONArray("data");
        BigDecimal cents = BigDecimal.ZERO;
        if (data != null) {
            for (int i = 0; i < data.length(); i++) {
                JSONObject cost = data.getJSONObject(i).optJSONObject("cost");
                if (cost == null) continue;
                String amount = cost.optString("amount", "0");
                try {
                    cents = cents.add(new BigDecimal(amount));
                } catch (NumberFormatException e) {
                    throw new JSONException("unexpected amount " + amount);
                }
            }
        }
        return cents.movePointLeft(2).doubleValue();
    }

    /**
     * A Claude Console Admin API key (sk-ant-admin01-...), or a Claude Enterprise key (sk-ant-api01-...)
     * created with the read:analytics scope. Ordinary API keys (sk-ant-api03-...) can't read costs.
     */
    public static boolean looksLikeAdminKey(String key) {
        return key != null && key.trim().matches("sk-ant-(admin[0-9]*|api01)-[A-Za-z0-9_-]{20,}");
    }

    /** "sk-ant-admin01-…a1b2", safe to print. */
    public static String maskKey(String key) {
        if (key == null || key.length() < 20) return "(none)";
        return key.substring(0, 15) + "…" + key.substring(key.length() - 4);
    }

//    ---------------------------------------------------------------- the line

    /** What the line shows when claude_format is empty, depending on which sources are set up. */
    public static String defaultFormat(boolean hasPlan, boolean hasApi, boolean hasBudget) {
//        a heading with the reset time, then one line per limit; the bars line up with ram and disk
        String plan = "Claude · 5h resets %5hreset%n5h    %5h% %5hbar%nweek  %7d% %7dbar";
        String api = hasBudget ? "API $%spent of $%budget (%api%)" : "API $%spent this month";
        if (hasPlan && hasApi) return plan + (hasBudget ? "%nAPI   %api% %apibar" : "%nAPI   $%spent this month");
        if (hasApi) return "Claude " + api;
        if (hasPlan) return plan;
        return "Claude usage: not set up (run: usage)";
    }

    /**
     * Fills in the codes of a format. Values that aren't available show as "-".
     *
     * @param plan       may be null
     * @param spentUsd   may be null
     * @param budgetUsd  0 or less means no budget
     */
    public static String format(String format, Plan plan, Double spentUsd, double budgetUsd, long nowMillis, TimeZone zone) {
        return format(format, plan, spentUsd, budgetUsd, nowMillis, zone, true);
    }

    /** @param use24h reset times as 23:20 (true) or 11:20 PM (false), like the phone's clock */
    public static String format(String format, Plan plan, Double spentUsd, double budgetUsd, long nowMillis, TimeZone zone, boolean use24h) {
//        bars first: their codes start like the others (%5hbar would otherwise read as %5h + "bar")
        double fiveP = plan != null && plan.fiveHour != null ? plan.fiveHour : Double.NaN;
        double sevenP = plan != null && plan.sevenDay != null ? plan.sevenDay : Double.NaN;
        double apiP = spentUsd != null && budgetUsd > 0 ? spentUsd / budgetUsd * 100 : Double.NaN;
        double top = highest(plan, spentUsd, budgetUsd);
        format = StatusBars.replaceCode(format, "5hbar", fiveP);
        format = StatusBars.replaceCode(format, "7dbar", sevenP);
        format = StatusBars.replaceCode(format, "apibar", apiP);
        format = StatusBars.replaceCode(format, "bar", top < 0 ? Double.NaN : top);

        String five = plan != null && plan.fiveHour != null ? percent(plan.fiveHour) : "-";
        String seven = plan != null && plan.sevenDay != null ? percent(plan.sevenDay) : "-";
        String fiveReset = plan != null && plan.fiveHourReset != null ? time(plan.fiveHourReset, use24h ? "HH:mm" : "h:mm a", zone) : "-";
        String sevenReset = plan != null && plan.sevenDayReset != null ? time(plan.sevenDayReset, use24h ? "EEE HH:mm" : "EEE h:mm a", zone) : "-";
        String updated = plan != null && plan.updatedAt != null ? age(nowMillis / 1000 - plan.updatedAt) : "-";

        String spent = spentUsd != null ? String.format(Locale.US, "%.2f", spentUsd) : "-";
        String budget = budgetUsd > 0 ? String.format(Locale.US, "%.2f", budgetUsd) : "-";
        String api = spentUsd != null && budgetUsd > 0 ? percent(spentUsd / budgetUsd * 100) : "-";

//        longer codes first, so %5hreset isn't read as %5h + "reset"
        return format
                .replace("%5hreset", fiveReset)
                .replace("%7dreset", sevenReset)
                .replace("%updated", updated)
                .replace("%5h", five)
                .replace("%7d", seven)
                .replace("%spent", spent)
                .replace("%budget", budget)
                .replace("%api", api)
                .replace("%n", "\n");
    }

    /** The highest percentage on the line, for warning colors. -1 if none. */
    public static double highest(Plan plan, Double spentUsd, double budgetUsd) {
        double max = -1;
        if (plan != null && plan.fiveHour != null) max = Math.max(max, plan.fiveHour);
        if (plan != null && plan.sevenDay != null) max = Math.max(max, plan.sevenDay);
        if (spentUsd != null && budgetUsd > 0) max = Math.max(max, spentUsd / budgetUsd * 100);
        return max;
    }

    private static String percent(double p) {
        return String.valueOf(Math.round(p));
    }

    private static String time(long epochSeconds, String pattern, TimeZone zone) {
        SimpleDateFormat f = new SimpleDateFormat(pattern, Locale.US);
        f.setTimeZone(zone);
        return f.format(new Date(epochSeconds * 1000));
    }

    static String age(long seconds) {
        if (seconds < 0) seconds = 0;
        if (seconds < 90) return "just now";
        if (seconds < 90 * 60) return (seconds / 60) + "m ago";
        if (seconds < 36 * 3600) return (seconds / 3600) + "h ago";
        return (seconds / 86400) + "d ago";
    }
}
