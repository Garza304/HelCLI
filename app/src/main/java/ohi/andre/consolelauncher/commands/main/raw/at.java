package ohi.andre.consolelauncher.commands.main.raw;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import java.util.Locale;
import java.util.TimeZone;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.managers.at.AtJobs;
import ohi.andre.consolelauncher.managers.at.AtScheduler;
import ohi.andre.consolelauncher.managers.status.StatusFormats;
import ohi.andre.consolelauncher.tuils.Tuils;

/** Runs a HelCLI command later: at 22:00 theme -apply oled, at +30m flash, at daily 7am cal today. */
public class at implements CommandAbstraction {

    @Override
    public String exec(ExecutePack pack) {
        String input = pack.getString().trim();
        String lower = input.toLowerCase(Locale.ROOT).replaceAll("^-+", "");
        boolean use24h = android.text.format.DateFormat.is24HourFormat(pack.context);

        if (lower.equals("list")) return onNotArgEnough(pack, 0);
        if (lower.matches("(cancel|rm) \\d+")) {
            int id = Integer.parseInt(lower.replaceAll("\\D", ""));
            return AtScheduler.cancel(pack.context, id) ? "Cancelled job " + id : "There's no job " + id + ". See them with: at";
        }
        if (lower.equals("clear")) {
            int n = AtScheduler.clear(pack.context);
            return "Cancelled " + n + (n == 1 ? " job" : " jobs");
        }
        if (lower.equals("exact")) return openExactSettings(pack);

        AtJobs.Parsed p = AtJobs.parse(input, AtJobs.nextId(AtScheduler.jobs(pack.context)), System.currentTimeMillis(), TimeZone.getDefault());
        if (p.error != null) return p.error;

        AtJobs.Job j = AtScheduler.add(pack.context, p.job);
        String when = new java.text.SimpleDateFormat(use24h ? "HH:mm" : "h:mm a", Locale.US).format(new java.util.Date(j.at));
        String out = "Job " + j.id + ": " + j.command + Tuils.NEWLINE + "runs at " + when + (j.daily ? " every day" : "")
                + " (in " + StatusFormats.duration(j.at - System.currentTimeMillis()) + ")";
        if (!AtScheduler.exact(pack.context)) {
            out += Tuils.NEWLINE + "It may run a few minutes late. To run on time: at exact";
        }
        return out;
    }

    @Override
    public String onNotArgEnough(ExecutePack pack, int nArgs) {
        return AtJobs.list(AtScheduler.jobs(pack.context), System.currentTimeMillis(), TimeZone.getDefault(),
                android.text.format.DateFormat.is24HourFormat(pack.context));
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
    public int[] argType() {
        return new int[] {CommandAbstraction.PLAIN_TEXT};
    }

    @Override
    public int priority() {
        return 3;
    }

    @Override
    public int helpRes() {
        return R.string.help_at;
    }

    @Override
    public String onArgNotFound(ExecutePack info, int index) {
        return null;
    }
}
