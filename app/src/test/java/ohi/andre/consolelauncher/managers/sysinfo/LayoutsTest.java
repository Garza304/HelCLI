package ohi.andre.consolelauncher.managers.sysinfo;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LayoutsTest {

    @Test
    public void sysinfoPutsTheLogoBesideTheInfo() {
        List<String[]> info = new ArrayList<>();
        info.add(new String[] {"OS", "Android 14 (SDK 34)"});
        info.add(new String[] {"Kernel", "5.15.123-android14-11-g1234567890abcdef"});
        List<Layouts.Row> rows = Layouts.sysinfo("user@pixel", info);

        assertEquals("┌─────────┐  user@pixel", rows.get(0).toString());
        assertEquals("│ >_      │  ----------", rows.get(1).toString());
        assertEquals("│         │  OS: Android 14 (SDK 34)", rows.get(2).toString());
//        long values are shortened to fit
        assertEquals("│  HelCLI │  Kernel: 5.15.123-android14-11-…", rows.get(3).toString());
        assertEquals(Layouts.LOGO.length, rows.size());
        for (Layouts.Row r : rows) assertTrue(r.toString(), r.toString().length() <= Layouts.WIDTH);
    }

    @Test
    public void moreInfoThanLogoKeepsTheColumn() {
        List<String[]> info = new ArrayList<>();
        for (int i = 0; i < 12; i++) info.add(new String[] {"Row" + i, "value"});
        List<Layouts.Row> rows = Layouts.sysinfo("me@phone", info);
        assertEquals(14, rows.size());
        assertEquals("             Row11: value", rows.get(13).toString());
    }

    @Test
    public void uptime() {
        assertEquals("5m", Layouts.uptime(5 * 60_000L));
        assertEquals("2h 3m", Layouts.uptime((2 * 60 + 3) * 60_000L));
        assertEquals("3d 4h 12m", Layouts.uptime(((3 * 24 + 4) * 60 + 12) * 60_000L));
    }

    private static final TimeZone CHI = TimeZone.getTimeZone("America/Chicago");
    // Tue 2026-11-03 08:00 in Chicago
    private static final long NOW = 1793714400000L;
    private static final long H = 3_600_000L;

    @Test
    public void agendaGroupsByDay() {
        long nov4Utc = 1793750400000L; // 2026-11-04T00:00Z, an all-day event's date
        List<Layouts.Event> events = Arrays.asList(
                new Layouts.Event("Standup", NOW + H, NOW + H + 1_800_000L, false, 0xff4285f4),
                new Layouts.Event("Already started", NOW - H, NOW + H, false, 0xff0b8043),
                new Layouts.Event("Holiday", nov4Utc, nov4Utc + 24 * H, true, 0xffd50000),
                new Layouts.Event("Dentist with a really long title that will not fit", NOW + 30 * H, NOW + 31 * H, false, 0xff8e24aa));

        List<Layouts.AgendaLine> lines = Layouts.agenda(events, NOW, 7, CHI, false);
        List<String> text = new ArrayList<>();
        for (Layouts.AgendaLine l : lines) text.add(l.toString());

        assertEquals(Arrays.asList(
                "Today · Tue Nov 3",
                "  ■ 9:00 AM   Standup",
                "  ■ now       Already started",
                "Tomorrow · Wed Nov 4",
                "  ■ all day   Holiday",
                "  ■ 2:00 PM   Dentist with a really long ti…"), text);
        assertTrue(lines.get(0).heading);
        assertEquals(0xff4285f4, lines.get(1).color);
    }

    @Test
    public void agenda24h() {
        List<Layouts.Event> events = Arrays.asList(new Layouts.Event("Gym", NOW + 10 * H, NOW + 11 * H, false, 0));
        assertEquals("  ■ 18:00  Gym", Layouts.agenda(events, NOW, 1, CHI, true).get(1).toString());
    }

    @Test
    public void agendaStartingTomorrow() {
        List<Layouts.Event> events = Arrays.asList(
                new Layouts.Event("Today's thing", NOW + H, NOW + 2 * H, false, 0),
                new Layouts.Event("Tomorrow's thing", NOW + 26 * H, NOW + 27 * H, false, 0));
        List<Layouts.AgendaLine> lines = Layouts.agenda(events, NOW, 1, 1, CHI, true);
        assertEquals(2, lines.size());
        assertEquals("Tomorrow · Wed Nov 4", lines.get(0).toString());
        assertEquals("  ■ 10:00  Tomorrow's thing", lines.get(1).toString());
    }

    @Test
    public void emptyAgenda() {
        assertTrue(Layouts.agenda(new ArrayList<Layouts.Event>(), NOW, 7, CHI, true).isEmpty());
    }
}
