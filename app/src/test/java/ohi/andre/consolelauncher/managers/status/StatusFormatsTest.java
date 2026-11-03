package ohi.andre.consolelauncher.managers.status;

import org.junit.Test;

import java.util.TimeZone;

import ohi.andre.consolelauncher.tuils.StatusBars;

import static org.junit.Assert.assertEquals;

public class StatusFormatsTest {

    private static final TimeZone CHICAGO = TimeZone.getTimeZone("America/Chicago");
    // 2026-11-03 23:25 in Chicago
    private static final long NOW = 1793769900000L;
    private static final long MIN = 60_000L;

    @Test
    public void alarmTonightAndTomorrow() {
        long tomorrow645 = NOW + (7 * 60 + 20) * MIN;   // 06:45 the next morning
        assertEquals("Alarm 6:45 AM tomorrow (in 7h 20m)",
                StatusFormats.alarm("Alarm %t %day (in %in)", tomorrow645, NOW, CHICAGO, false, null));
        assertEquals("06:45 Nov 4 · Clock",
                StatusFormats.alarm("%t %date · %app", tomorrow645, NOW, CHICAGO, true, "Clock"));

        long in10 = NOW + 10 * MIN;
        assertEquals("23:35 today", StatusFormats.alarm("%t %day", in10, NOW, CHICAGO, true, null));
    }

    @Test
    public void alarmLaterThisWeek() {
        long saturday = NOW + (3 * 24 * 60 + 7 * 60 + 20) * MIN;
        assertEquals("Sat 6:45 AM, in 3d 7h", StatusFormats.alarm("%day %t, in %in", saturday, NOW, CHICAGO, false, null));
    }

    @Test
    public void noAlarmMeansAnEmptyLine() {
        assertEquals("", StatusFormats.alarm("Alarm %t", 0, NOW, CHICAGO, true, null));
    }

    @Test
    public void durations() {
        assertEquals("1m", StatusFormats.duration(1));          // rounds up
        assertEquals("45m", StatusFormats.duration(45 * MIN));
        assertEquals("2h 0m", StatusFormats.duration(120 * MIN));
        assertEquals("1d 1h", StatusFormats.duration(25 * 60 * MIN));
    }

    @Test
    public void nowPlaying() {
        assertEquals("► Bohemian Rhapsody — Queen",
                StatusBars.plain(StatusFormats.nowPlaying("%state %title — %artist", "Bohemian Rhapsody", "Queen", "A Night at the Opera", "Spotify", true, 0, 0)));
        assertEquals("|| Podcast ep. 12 2:05/1:02:09",
                StatusBars.plain(StatusFormats.nowPlaying("%state %title %pos/%dur", "Podcast ep. 12", null, null, null, false, 125_000, 3_729_000)));
    }

    @Test
    public void missingArtistDoesntLeaveADash() {
        assertEquals("♪ Voice memo", StatusBars.plain(StatusFormats.nowPlaying("♪ %title — %artist", "Voice memo", null, null, null, true, 0, 0)));
        assertEquals("♪ Voice memo\n1:00",
                StatusBars.plain(StatusFormats.nowPlaying("♪ %title — %artist%n%pos", "Voice memo", null, null, null, true, 60_000, 0)));
    }

    @Test
    public void musicLineWithAMovingNote() {
        String line = StatusFormats.nowPlaying("♪ %title — %artist%n%pos %bar %dur", "~♫·",
                "Midnight City", "M83", null, null, true, 161_000, 243_000);
        assertEquals("♪ Midnight City — M83\n"
                        + "2:41 ~~~~~~~~~~~~~~~~~~~~~~♫··········· 4:03",
                StatusBars.render(line, 44, "[█░]"));
        assertEquals("-1:22", StatusFormats.nowPlaying("%left", "Song", null, null, null, true, 161_000, 243_000));
    }

    @Test
    public void longSongsAreShortenedNotWrapped() {
        String line = StatusFormats.nowPlaying("♪ %title — %artist", "A Really Long Song Title That Goes On", "Some Band", null, null, true, 0, 0);
        assertEquals("♪ A Really Long Song Ti… — Some Band", StatusBars.render(line, 36, "[█░]"));
    }

    @Test
    public void nextCalendarEvent() {
        String f = "Next: %title · %day %t (in %in)", now = "Now: %title (until %end)";
//        Thursday Nov 5, 2:30 PM: 1 day and 15 hours 5 minutes from now
        long thu = NOW + (39 * 60 + 5) * MIN;
        assertEquals("Next: Dentist · Thu 2:30 PM (in 1d 15h)",
                StatusBars.plain(StatusFormats.event(f, now, "Dentist", thu, thu + 60 * MIN, false, NOW, CHICAGO, false)));
        assertEquals("Next: Dentist · Thu 14:30 (in 1d 15h)",
                StatusBars.plain(StatusFormats.event(f, now, "Dentist", thu, thu + 60 * MIN, false, NOW, CHICAGO, true)));

        assertEquals("Now: Standup (until 11:45 PM)",
                StatusBars.plain(StatusFormats.event(f, now, "Standup", NOW - 10 * MIN, NOW + 20 * MIN, false, NOW, CHICAGO, false)));

        long midnight = NOW + 35 * MIN;
        assertEquals("Next: Holiday · tomorrow all day (in 35m)",
                StatusBars.plain(StatusFormats.event(f, now, "Holiday", midnight, midnight + 1440 * MIN, true, NOW, CHICAGO, false)));

        assertEquals("", StatusFormats.event(f, now, null, 0, 0, false, NOW, CHICAGO, false));
    }

    @Test
    public void longEventTitlesAreShortened() {
        long soon = NOW + 30 * MIN;
        String line = StatusFormats.event("Next: %title (in %in)", null, "Quarterly planning with the whole team", soon, soon + MIN, false, NOW, CHICAGO, false);
        assertEquals("Next: Quarterly planning wi… (in 30m)", StatusBars.render(line, 37, "[█░]"));
    }

    @Test
    public void nothingPlayingMeansAnEmptyLine() {
        assertEquals("", StatusFormats.nowPlaying("♪ %title", null, "x", null, null, true, 0, 0));
    }

    @Test
    public void progressBar() {
        String line = StatusFormats.nowPlaying("%title %bar:8", "Song", null, null, null, true, 90_000, 180_000);
        assertEquals("Song [###---]", StatusBars.render(line, 40, "[#-]"));
    }
}
