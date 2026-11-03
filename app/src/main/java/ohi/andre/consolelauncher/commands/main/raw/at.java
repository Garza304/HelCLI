package ohi.andre.consolelauncher.commands.main.raw;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import java.util.Locale;
import java.util.TimeZone;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.main.specific.SimpleParamCommand;
import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.managers.at.AtJobs;
import ohi.andre.consolelauncher.managers.at.AtScheduler;
import ohi.andre.consolelauncher.managers.status.StatusFormats;
import ohi.andre.consolelauncher.tuils.Tuils;

/** Runs a HelCLI command later: at 22:00 theme -apply oled, at +30m flash, at daily 7am cal -today. */
public class at extends SimpleParamCommand {

    public at() {
        option("list", (pack, t) -> list(pack));
        optionWithText("cancel", (pack, t) -> cancel(pack, t));
        option("clear", (pack, t) -> {
            int n = AtScheduler.clear(pack.context);
            return "Cancelled " + n + (n == 1 ? " job" : " jobs");
        });
        option("exact", (pack, t) -> openExactSettings(pack));
    }

//    "at 22:00 theme -apply oled": the time and the command are free text, so only "-words" are options
    @Override
    public boolean paramsNeedDash() {
        return true;
    }

    @Override
    public boolean takesText() {
        return true;
    }

    @Override
    public String onEmpty(ExecutePack pack) {
        return list(pack);
    }

    @Override
    public String onText(ExecutePack pack, String input) {
        String lower = input.toLowerCase(Locale.ROOT);
//        the older forms without a dash still work
        if (lower.equals("list")) return list(pack);
        if (lower.matches("(cancel|rm) \\d+")) return cancel(pack, lower.replaceAll("\\D", ""));
        if (lower.equals("clear")) return AtScheduler.clear(pack.context) + " jobs cancelled";
        if (lower.equals("exact")) return openExactSettings(pack);

        boolean use24h = android.text.format.DateFormat.is24HourFormat(pack.context);
        AtJobs.Parsed p = AtJobs.parse(input, AtJobs.nextId(AtScheduler.jobs(pack.context)), System.currentTimeMillis(), TimeZone.getDefault());
        if (p.error != null) return p.error;

        AtJobs.Job j = AtScheduler.add(pack.context, p.job);
        String when = new java.text.SimpleDateFormat(use24h ? "HH:mm" : "h:mm a", Locale.US).format(new java.util.Date(j.at));
        String out = "Job " + j.id + ": " + j.command + Tuils.NEWLINE + "runs at " + when + (j.daily ? " every day" : "")
                + " (in " + StatusFormats.duration(j.at - System.currentTimeMillis()) + ")";
        if (!AtScheduler.exact(pack.context)) {
            out += Tuils.NEWLINE + "It may run a few minutes late. To run on time: at -exact";
        }
        return out;
    }

    private static String list(ExecutePack pack) {
        return AtJobs.list(AtScheduler.jobs(pack.context), System.currentTimeMillis(), TimeZone.getDefault(),
                android.text.format.DateFormat.is24HourFormat(pack.context));
    }

    private static String cancel(ExecutePack pack, String number) {
        if (!number.matches("\\d+")) return "Which job? Like: at -cancel 2 (at -list shows the numbers)";
        int id = Integer.parseInt(number);
        return AtScheduler.cancel(pack.context, id) ? "Cancelled job " + id : "There's no job " + id + ". See them with: at -list";
    }

    private static String openExactSettings(ExecutePack pack) {
        if (AtScheduler.exact(pack.context)) return "Jobs already run on time";
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return "Jobs already run on time";
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + pack.context.getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            pack.context.startActivity(i);
            return "Turn on \"Alarms & reminders\" for HelCLI, so scheduled commands run on time";
        } catch (Exception e) {
            return "Look for Alarms & reminders (Settings > Apps > Special app access) and turn it on for HelCLI";
        }
    }


    @Override
    public int priority() {
        return 3;
    }

    @Override
    public int helpRes() {
        return R.string.help_at;
    }

}
