package ohi.andre.consolelauncher.managers.claude;

import org.junit.Test;

import java.util.TimeZone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ClaudeUsageTest {

    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

//    the shape Claude Code gives its status line (code.claude.com/docs/en/statusline), plus updated_at
    private static final String PLAN = "{\"updated_at\":1738420000,\"rate_limits\":{"
            + "\"five_hour\":{\"used_percentage\":23.5,\"resets_at\":1738425600},"
            + "\"seven_day\":{\"used_percentage\":41.2,\"resets_at\":1738857600}}}";

    @Test
    public void readsPlanLimits() throws Exception {
        ClaudeUsage.Plan p = ClaudeUsage.parsePlan(PLAN, 1738421000L);
        assertEquals(23.5, p.fiveHour, 0.001);
        assertEquals(41.2, p.sevenDay, 0.001);
        assertEquals(1738425600L, (long) p.fiveHourReset);
        assertEquals(1738420000L, (long) p.updatedAt);
    }

    @Test
    public void aWindowThatResetCountsAsZero() throws Exception {
//        an hour after the 5-hour window reset, before the 7-day one
        ClaudeUsage.Plan p = ClaudeUsage.parsePlan(PLAN, 1738429200L);
        assertEquals(0, p.fiveHour, 0.001);
        assertEquals(41.2, p.sevenDay, 0.001);
    }

    @Test
    public void missingWindowsAreNull() throws Exception {
        ClaudeUsage.Plan p = ClaudeUsage.parsePlan("{\"rate_limits\":{\"seven_day\":{\"used_percentage\":5}}}", 0);
        assertNull(p.fiveHour);
        assertEquals(5, p.sevenDay, 0.001);
        assertNull(p.updatedAt);
    }

    @Test
    public void findsTheGist() {
        String raw = "https://gist.githubusercontent.com/Garza304/0123456789abcdef0123456789abcdef/raw/claude-usage.json";
        assertEquals(raw, ClaudeUsage.planUrl("Garza304/0123456789abcdef0123456789abcdef"));
        assertEquals(raw, ClaudeUsage.planUrl("https://gist.github.com/Garza304/0123456789ABCDEF0123456789abcdef"));
        assertEquals(raw, ClaudeUsage.planUrl(raw));
        assertNull(ClaudeUsage.planUrl("http://gist.githubusercontent.com/a/0123456789abcdef0123456789abcdef/raw/x"));
        assertNull(ClaudeUsage.planUrl("https://evil.example.com/claude-usage.json"));
        assertNull(ClaudeUsage.planUrl(""));
    }

    @Test
    public void asksForThisMonth() {
        long nov3 = 1793727000000L; // 2026-11-03T17:30:00Z
        String[] r = ClaudeUsage.monthRange(nov3);
        assertEquals("2026-11-01T00:00:00Z", r[0]);
        assertEquals("2026-11-03T17:30:00Z", r[1]);
        assertEquals("https://api.anthropic.com/v1/organizations/analytics/cost?starting_at=2026-11-01T00:00:00Z&ending_at=2026-11-03T17:30:00Z",
                ClaudeUsage.costUrl(nov3));
    }

    @Test
    public void readsCostInCents() throws Exception {
//        the documented example: "41280.000000" is $412.80
        String json = "{\"data\":[{\"starting_at\":\"2026-09-01T00:00:00Z\",\"ending_at\":\"2026-10-01T00:00:00Z\",\"group\":{},"
                + "\"cost\":{\"amount\":\"41280.000000\",\"list_amount\":\"45000.000000\",\"currency\":\"USD\",\"requests\":10}}],"
                + "\"has_more\":false,\"next_page\":null,\"data_refreshed_at\":\"2026-09-18T16:02:11Z\"}";
        assertEquals(412.80, ClaudeUsage.parseCostDollars(json), 0.0001);
        assertEquals(0, ClaudeUsage.parseCostDollars("{\"data\":[],\"data_refreshed_at\":null}"), 0.0001);
    }

    @Test
    public void formatsTheLine() throws Exception {
        ClaudeUsage.Plan p = ClaudeUsage.parsePlan(PLAN, 1738421000L);
        long now = 1738421000L * 1000;

        String both = ClaudeUsage.defaultFormat(true, true, true);
        assertEquals("Claude 5h 24% · week 41%  API $12.40 of $50.00 (25%)",
                ClaudeUsage.format(both, p, 12.40, 50, now, UTC));

        assertEquals("Claude API $3.10 this month",
                ClaudeUsage.format(ClaudeUsage.defaultFormat(false, true, false), null, 3.10, 0, now, UTC));

        assertEquals("5h resets 16:00 · week resets Thu 16:00 · 16m ago",
                ClaudeUsage.format("5h resets %5hreset · week resets %7dreset · %updated", p, null, 0, now, UTC));

        assertEquals("plan - / API -",
                ClaudeUsage.format("plan %5h / API %api", null, null, 0, now, UTC));
    }

    @Test
    public void picksTheHighestPercentage() {
        ClaudeUsage.Plan p = new ClaudeUsage.Plan(10d, null, 85d, null, null);
        assertEquals(85, ClaudeUsage.highest(p, 20d, 100), 0.001);
        assertEquals(95, ClaudeUsage.highest(p, 95d, 100), 0.001);
        assertEquals(-1, ClaudeUsage.highest(null, null, 0), 0.001);
    }

    @Test
    public void checksAdminKeys() {
        assertTrue(ClaudeUsage.looksLikeAdminKey("sk-ant-admin01-abcdefghijklmnopqrstuvwxyz0123"));
        assertTrue(ClaudeUsage.looksLikeAdminKey("sk-ant-api01-abcdefghijklmnopqrstuvwxyz0123")); // Enterprise, read:analytics
        assertFalse(ClaudeUsage.looksLikeAdminKey("sk-ant-api03-abcdefghijklmnopqrstuvwxyz0123")); // ordinary API key
        assertFalse(ClaudeUsage.looksLikeAdminKey("hello"));
        assertEquals("sk-ant-admin01-…0123", ClaudeUsage.maskKey("sk-ant-admin01-abcdefghijklmnopqrstuvwxyz0123"));
    }
}
