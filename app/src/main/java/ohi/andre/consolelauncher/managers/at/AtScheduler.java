package ohi.andre.consolelauncher.managers.at;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

import ohi.andre.consolelauncher.MainManager;
import ohi.andre.consolelauncher.tuils.Tuils;

/**
 * Keeps "at" jobs: saves them, sets an alarm for each, and runs the command when it goes off.
 *
 * Commands run in the launcher. When Android has closed it, the command waits and runs the next
 * time the launcher opens (which, as the home screen, is usually right away). Opening the launcher
 * also sets the alarms again, which covers restarts of the phone.
 */
public class AtScheduler {

    private static final String PREFS = "helcli_at", JOBS = "jobs", PENDING = "pending";
    public static final String EXTRA_ID = "at_id";

    private AtScheduler() {}

    public static synchronized List<AtJobs.Job> jobs(Context c) {
        return AtJobs.fromJson(prefs(c).getString(JOBS, null));
    }

    private static synchronized void save(Context c, List<AtJobs.Job> jobs) {
        prefs(c).edit().putString(JOBS, AtJobs.toJson(jobs)).apply();
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static synchronized AtJobs.Job add(Context c, AtJobs.Job job) {
        List<AtJobs.Job> jobs = jobs(c);
        jobs.add(job);
        save(c, jobs);
        arm(c, job);
        return job;
    }

    public static synchronized boolean cancel(Context c, int id) {
        List<AtJobs.Job> jobs = jobs(c);
        boolean found = false;
        for (int i = jobs.size() - 1; i >= 0; i--) {
            if (jobs.get(i).id == id) {
                jobs.remove(i);
                found = true;
            }
        }
        save(c, jobs);
        alarms(c).cancel(intent(c, id));
        return found;
    }

    public static synchronized int clear(Context c) {
        List<AtJobs.Job> jobs = jobs(c);
        for (AtJobs.Job j : jobs) alarms(c).cancel(intent(c, j.id));
        save(c, new ArrayList<AtJobs.Job>());
        return jobs.size();
    }

    /** Whether alarms go off on time. Without it Android may run them several minutes late. */
    public static boolean exact(Context c) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms(c).canScheduleExactAlarms();
    }

    private static AlarmManager alarms(Context c) {
        return (AlarmManager) c.getApplicationContext().getSystemService(Context.ALARM_SERVICE);
    }

    private static PendingIntent intent(Context c, int id) {
        Intent i = new Intent(c.getApplicationContext(), Receiver.class).putExtra(EXTRA_ID, id);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);
        return PendingIntent.getBroadcast(c.getApplicationContext(), 7000 + id, i, flags);
    }

    private static void arm(Context c, AtJobs.Job job) {
        AlarmManager am = alarms(c);
        PendingIntent pi = intent(c, job.id);
        try {
            if (exact(c) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, job.at, pi);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, job.at, pi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, job.at, pi);
            }
        } catch (SecurityException e) {
            am.set(AlarmManager.RTC_WAKEUP, job.at, pi);
        }
    }

    /**
     * Called when the launcher opens: runs anything that waited or was missed (the phone was off),
     * and sets the alarms again.
     */
    public static synchronized void onLauncherStart(Context c) {
        long now = System.currentTimeMillis();
        List<AtJobs.Job> jobs = jobs(c);
        List<AtJobs.Job> keep = new ArrayList<>();
        for (AtJobs.Job j : jobs) {
            if (j.at <= now) {
                queue(c, j.command);
                if (j.daily) keep.add(j.next(now, TimeZone.getDefault()));
            } else {
                keep.add(j);
            }
        }
        save(c, keep);
        for (AtJobs.Job j : keep) arm(c, j);
        runPending(c);
    }

    private static synchronized void fire(Context c, int id) {
        long now = System.currentTimeMillis();
        List<AtJobs.Job> jobs = jobs(c);
        for (int i = 0; i < jobs.size(); i++) {
            AtJobs.Job j = jobs.get(i);
            if (j.id != id) continue;
            if (j.daily) {
                AtJobs.Job next = j.next(now, TimeZone.getDefault());
                jobs.set(i, next);
                arm(c, next);
            } else {
                jobs.remove(i);
            }
            save(c, jobs);
            queue(c, j.command);
            runPending(c);
            return;
        }
    }

    private static synchronized void queue(Context c, String command) {
        List<String> pending = new ArrayList<>();
        String old = prefs(c).getString(PENDING, "");
        if (old.length() > 0) for (String s : old.split("\n")) pending.add(s);
        pending.add(command.replace('\n', ' '));
        prefs(c).edit().putString(PENDING, android.text.TextUtils.join("\n", pending)).apply();
    }

    /** Runs waiting commands, if the launcher is running. */
    public static synchronized void runPending(Context c) {
        if (!MainManager.isRunning()) return;
        String pending = prefs(c).getString(PENDING, "");
        if (pending.length() == 0) return;
        prefs(c).edit().remove(PENDING).apply();

        for (String cmd : pending.split("\n")) {
            if (cmd.trim().length() == 0) continue;
            Tuils.sendOutput(c, "at: " + cmd);
            Intent i = new Intent(MainManager.ACTION_EXEC);
            i.putExtra(MainManager.CMD, cmd);
            i.putExtra(MainManager.CMD_COUNT, MainManager.commandCount);
            LocalBroadcastManager.getInstance(c.getApplicationContext()).sendBroadcast(i);
        }
    }

    /** Receives the alarms. */
    public static class Receiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            int id = intent.getIntExtra(EXTRA_ID, -1);
            if (id >= 0) fire(context, id);
        }
    }
}
