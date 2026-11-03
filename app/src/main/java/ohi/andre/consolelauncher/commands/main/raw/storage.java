package ohi.andre.consolelauncher.commands.main.raw;

import android.Manifest;
import android.app.AppOpsManager;
import android.app.usage.ExternalStorageStats;
import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Process;
import android.os.StatFs;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.Settings;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.main.specific.SimpleParamCommand;
import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.managers.sysinfo.Breakdown;
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager;
import ohi.andre.consolelauncher.managers.xml.options.Ui;
import ohi.andre.consolelauncher.tuils.Tuils;

/**
 * A breakdown of storage: apps, photos, videos, audio, other files, system, free space, SD cards,
 * and the biggest apps.
 *
 *   storage            overview and the 5 biggest apps
 *   storage -apps      the 20 biggest apps ("storage -apps all" for every app)
 *   storage -cache     apps with the most cache, which is safe to clear
 *   storage -access    opens the settings page to give HelCLI Usage access
 *
 * Sizes of other apps and the media categories need Usage access (Android 8 and later).
 */
public class storage extends SimpleParamCommand {

    public storage() {
        optionWithText("apps", (pack, t) -> run(pack.context, "apps " + t));
        optionWithText("cache", (pack, t) -> run(pack.context, "cache " + t));
        option("access", (pack, t) -> openUsageAccess(pack.context));
    }

    @Override
    public String onEmpty(ExecutePack pack) {
        return run(pack.context, "");
    }

    private static String run(final Context context, String words) {
        final String w = words == null ? "" : words.trim().toLowerCase(Locale.ROOT).replaceAll("^-+", "");

        if (w.startsWith("access")) return openUsageAccess(context);

        final boolean all = w.endsWith("all");
        final String view = w.startsWith("app") ? "apps" : w.startsWith("cache") ? "cache" : "overview";
        if (w.length() > 0 && view.equals("overview")) return context.getString(R.string.help_storage);

        final boolean access = hasUsageAccess(context);
        if (!view.equals("overview") && !access) {
            return "HelCLI needs Usage access to see other apps' sizes. Run: storage -access";
        }

//        measuring every app can take a few seconds, so it runs in the background
        new Thread(() -> {
            try {
                String style = XMLPrefsManager.get(Ui.bar_style);
                List<Breakdown.App> apps = access ? apps(context) : null;
                String out;
                if (view.equals("overview")) {
                    out = Breakdown.storage(internal(context, access), otherVolumes(context), apps, 5, style);
                } else {
                    out = Breakdown.biggestApps(apps, all ? Integer.MAX_VALUE : 20, view.equals("cache"), style);
                }
                Tuils.sendOutput(context, out);
            } catch (Exception e) {
                Tuils.log(e);
                Tuils.sendOutput(context, "storage: " + e);
            }
        }).start();

        return access ? "Measuring storage..." : null;
    }

//    ---------------------------------------------------------------- volumes

    private static Breakdown.Volume internal(Context context, boolean access) {
        Breakdown.Volume v = null;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            matches the totals in Android's Settings, system partition included
            StorageStatsManager ssm = (StorageStatsManager) context.getSystemService(Context.STORAGE_STATS_SERVICE);
            try {
                v = new Breakdown.Volume("Internal", ssm.getTotalBytes(StorageManager.UUID_DEFAULT), ssm.getFreeBytes(StorageManager.UUID_DEFAULT));
            } catch (Exception e) {
                Tuils.log(e);
            }

            if (v != null && access) {
                try {
                    ExternalStorageStats es = ssm.queryExternalStatsForUser(StorageManager.UUID_DEFAULT, Process.myUserHandle());
                    v.images = es.getImageBytes();
                    v.videos = es.getVideoBytes();
                    v.audio = es.getAudioBytes();
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//                        app folders on shared storage are already counted with the apps
                        v.otherFiles = Math.max(0, es.getTotalBytes() - v.images - v.videos - v.audio - es.getAppBytes());
                    }
                } catch (Exception e) {
                    Tuils.log(e);
                }
            }
        }

        if (v == null) {
            StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
            v = new Breakdown.Volume("Internal", fs.getTotalBytes(), fs.getAvailableBytes());
        }
        return v;
    }

    /** SD cards and USB drives. */
    private static List<Breakdown.Volume> otherVolumes(Context context) {
        List<Breakdown.Volume> out = new ArrayList<>();
        File[] dirs = context.getExternalFilesDirs(null);
        if (dirs == null) return out;

        StorageManager sm = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
//        the first one is the phone's own shared storage, already counted as internal
        for (int i = 1; i < dirs.length; i++) {
            if (dirs[i] == null) continue;
            try {
                StatFs fs = new StatFs(dirs[i].getPath());
                String name = "SD card";
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && sm != null) {
                    StorageVolume sv = sm.getStorageVolume(dirs[i]);
                    if (sv != null) name = sv.getDescription(context);
                }
                out.add(new Breakdown.Volume(name, fs.getTotalBytes(), fs.getAvailableBytes()));
            } catch (Exception e) {
                Tuils.log(e);
            }
        }
        return out;
    }

//    ---------------------------------------------------------------- apps

    private static List<Breakdown.App> apps(Context context) {
        List<Breakdown.App> out = new ArrayList<>();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return out;

        StorageStatsManager ssm = (StorageStatsManager) context.getSystemService(Context.STORAGE_STATS_SERVICE);
        PackageManager pm = context.getPackageManager();
        for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
            try {
                UUID uuid = ai.storageUuid != null ? ai.storageUuid : StorageManager.UUID_DEFAULT;
                StorageStats st = ssm.queryStatsForPackage(uuid, ai.packageName, Process.myUserHandle());
                out.add(new Breakdown.App(pm.getApplicationLabel(ai).toString(), st.getAppBytes(), st.getDataBytes(), st.getCacheBytes()));
            } catch (Exception e) {
//                apps can disappear while we count
            }
        }
        return out;
    }

    /** Whether HelCLI has Usage access, which Android requires to read other apps' sizes. */
    public static boolean hasUsageAccess(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false;
        try {
            AppOpsManager ops = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            int mode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    ? ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.getPackageName())
                    : ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.getPackageName());
            if (mode == AppOpsManager.MODE_DEFAULT) {
                return context.checkCallingOrSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED;
            }
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return false;
        }
    }

    private static String openUsageAccess(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return "Per-app sizes need Android 8 or later";
        }
        if (hasUsageAccess(context)) return "HelCLI already has Usage access. Try: storage -apps";

        Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//        some phones open HelCLI's own switch directly when given the package
        intent.setData(Uri.parse("package:" + context.getPackageName()));
        try {
            context.startActivity(intent);
        } catch (Exception e) {
            intent.setData(null);
            try {
                context.startActivity(intent);
            } catch (Exception e2) {
                return "Couldn't open the settings. Look for Usage access (Settings > Apps > Special app access) and turn it on for HelCLI.";
            }
        }
        return "Turn on Usage access for HelCLI, then come back and run: storage" + Tuils.NEWLINE
                + "It lets HelCLI read app sizes and app usage. HelCLI doesn't send it anywhere.";
    }


    @Override
    public int priority() {
        return 3;
    }

    @Override
    public int helpRes() {
        return R.string.help_storage;
    }

}
