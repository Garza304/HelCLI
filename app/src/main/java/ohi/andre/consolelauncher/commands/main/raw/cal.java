package ohi.andre.consolelauncher.commands.main.raw;

import android.Manifest;
import android.app.Activity;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import ohi.andre.consolelauncher.LauncherActivity;
import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.managers.TerminalManager;
import ohi.andre.consolelauncher.managers.sysinfo.Layouts;
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager;
import ohi.andre.consolelauncher.managers.xml.options.Theme;
import ohi.andre.consolelauncher.tuils.Tuils;

/**
 * Calendar agenda from every calendar on the phone.
 *   cal            today and the next 6 days
 *   cal today      just today        cal tomorrow
 *   cal 14         the next 14 days (up to 60)
 */
public class cal implements CommandAbstraction {

    @Override
    public String exec(ExecutePack pack) {
        return run(pack.context, pack.getString());
    }

    @Override
    public String onNotArgEnough(ExecutePack pack, int nArgs) {
        return run(pack.context, "");
    }

    private static String run(Context context, String arg) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            if (context instanceof Activity) {
                ActivityCompat.requestPermissions((Activity) context, new String[] {Manifest.permission.READ_CALENDAR}, LauncherActivity.CALENDAR_PERMISSION_REQUEST);
            }
            return "HelCLI needs to read your calendar. Allow it, then run cal again.";
        }

        String a = arg == null ? "" : arg.trim().toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();
        int days = 7, skip = 0;
        if (a.equals("today")) days = 1;
        else if (a.equals("tomorrow")) {
            days = 1;
            skip = 1;
        } else if (a.matches("\\d{1,2}")) days = Math.max(1, Math.min(60, Integer.parseInt(a)));
        else if (a.length() > 0) return context.getString(R.string.help_cal);

        List<Layouts.Event> events = query(context, now - 86_400_000L, now + (skip + days + 1) * 86_400_000L);
        List<Layouts.AgendaLine> lines = Layouts.agenda(events, now, skip, days, TimeZone.getDefault(),
                android.text.format.DateFormat.is24HourFormat(context));

        if (lines.isEmpty()) {
            return days == 1 ? (skip == 1 ? "Nothing on the calendar tomorrow" : "Nothing on the calendar today")
                    : "Nothing on the calendar in the next " + days + " days";
        }

        int headingColor = XMLPrefsManager.getColor(Theme.input_color);
        int textColor = XMLPrefsManager.getColor(Theme.output_color);
        SpannableStringBuilder b = new SpannableStringBuilder();
        for (Layouts.AgendaLine l : lines) {
            if (b.length() > 0) b.append('\n');
            int start = b.length();
            b.append(l.text);
            if (l.heading) {
                b.setSpan(new ForegroundColorSpan(headingColor), start, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                b.setSpan(new ForegroundColorSpan(textColor), start, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
//                the marker takes the calendar's own color
                int m = start + l.text.indexOf(Layouts.MARKER);
                if (l.color != 0) b.setSpan(new ForegroundColorSpan(l.color | 0xff000000), m, m + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        Tuils.sendOutput(context, b, TerminalManager.CATEGORY_NO_COLOR);
        return null;
    }

    private static List<Layouts.Event> query(Context context, long begin, long end) {
        List<Layouts.Event> out = new ArrayList<>();
        Uri.Builder uri = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(uri, begin);
        ContentUris.appendId(uri, end);

        String[] projection = {
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.DISPLAY_COLOR,
        };
        try (Cursor cur = context.getContentResolver().query(uri.build(), projection,
                CalendarContract.Instances.VISIBLE + " = 1", null, CalendarContract.Instances.BEGIN + " ASC")) {
            if (cur == null) return out;
            while (cur.moveToNext()) {
                out.add(new Layouts.Event(cur.getString(0), cur.getLong(1), cur.getLong(2), cur.getInt(3) == 1, cur.getInt(4)));
            }
        } catch (Exception e) {
            Tuils.log(e);
        }
        return out;
    }

    @Override
    public int[] argType() {
        return new int[] {CommandAbstraction.PLAIN_TEXT};
    }

    @Override
    public int priority() {
        return 3;
    }

    @Override
    public int helpRes() {
        return R.string.help_cal;
    }

    @Override
    public String onArgNotFound(ExecutePack info, int index) {
        return null;
    }
}
