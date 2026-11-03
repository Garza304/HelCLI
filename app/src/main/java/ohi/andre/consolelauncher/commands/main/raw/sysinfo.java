package ohi.andre.consolelauncher.commands.main.raw;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import ohi.andre.consolelauncher.BuildConfig;
import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.managers.TerminalManager;
import ohi.andre.consolelauncher.managers.ThemeManager;
import ohi.andre.consolelauncher.managers.sysinfo.Breakdown;
import ohi.andre.consolelauncher.managers.sysinfo.Layouts;
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager;
import ohi.andre.consolelauncher.managers.xml.options.Theme;
import ohi.andre.consolelauncher.managers.xml.options.Ui;
import ohi.andre.consolelauncher.tuils.Tuils;

/** A neofetch-style summary of the phone, in the current theme's colors. */
public class sysinfo implements CommandAbstraction {

    @Override
    public String exec(ExecutePack pack) {
        Context c = pack.context;
        List<String[]> info = new ArrayList<>();

        info.add(new String[] {"OS", "Android " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")"});
        info.add(new String[] {"Model", capitalize(Build.MANUFACTURER) + " " + Build.MODEL});
        String kernel = System.getProperty("os.version");
        if (kernel != null) info.add(new String[] {"Kernel", kernel});
        info.add(new String[] {"Uptime", Layouts.uptime(SystemClock.elapsedRealtime())});
        info.add(new String[] {"Launcher", "HelCLI " + BuildConfig.VERSION_NAME});
        info.add(new String[] {"Theme", ThemeManager.currentThemeName(c)});
        info.add(new String[] {"Font", XMLPrefsManager.getBoolean(Ui.system_font) ? "system" : "Lucida Console"});

        WindowManager wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
        if (wm != null) {
            DisplayMetrics dm = new DisplayMetrics();
            wm.getDefaultDisplay().getRealMetrics(dm);
            info.add(new String[] {"Screen", dm.widthPixels + "x" + dm.heightPixels + " @ " + dm.densityDpi + "dpi"});
        }

        String cpu = Build.HARDWARE;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !Build.SOC_MODEL.equals(Build.UNKNOWN)) {
            cpu = (Build.SOC_MANUFACTURER.equals(Build.UNKNOWN) ? "" : Build.SOC_MANUFACTURER + " ") + Build.SOC_MODEL;
        }
        info.add(new String[] {"CPU", cpu + " (" + Runtime.getRuntime().availableProcessors() + " cores)"});

        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        if (am != null) {
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            info.add(new String[] {"RAM", Breakdown.size(mi.totalMem - mi.availMem, true) + " / " + Breakdown.size(mi.totalMem, true)});
        }

        StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
        info.add(new String[] {"Storage", Breakdown.size(fs.getTotalBytes() - fs.getAvailableBytes(), false) + " / " + Breakdown.size(fs.getTotalBytes(), false)});

        Intent battery = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery != null) {
            int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            int plugged = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
            if (level >= 0) info.add(new String[] {"Battery", Math.round(level * 100f / scale) + "%" + (plugged != 0 ? ", charging" : "")});
        }

        String title = XMLPrefsManager.get(Ui.username) + "@" + XMLPrefsManager.get(Ui.deviceName);

//        colors: the logo in the device color, labels in the input color, values in the output color
        int logoColor = XMLPrefsManager.getColor(Theme.device_color);
        int labelColor = XMLPrefsManager.getColor(Theme.input_color);
        int valueColor = XMLPrefsManager.getColor(Theme.output_color);

        SpannableStringBuilder b = new SpannableStringBuilder();
        List<Layouts.Row> rows = Layouts.sysinfo(title, info);
        for (int i = 0; i < rows.size(); i++) {
            Layouts.Row r = rows.get(i);
            if (i > 0) b.append('\n');
            append(b, r.logo, logoColor);
            append(b, r.label, labelColor);
            append(b, r.value, i == 0 ? labelColor : valueColor);
        }

//        a row of the theme's colors, like neofetch's palette
        b.append('\n').append(Layouts.LOGO.length > 0 ? "             " : "");
        Theme[] palette = {Theme.input_color, Theme.output_color, Theme.device_color, Theme.time_color,
                Theme.battery_color_high, Theme.ram_color, Theme.storage_color, Theme.network_info_color};
        for (Theme t : palette) append(b, "███", XMLPrefsManager.getColor(t));

        Tuils.sendOutput(c, b, TerminalManager.CATEGORY_NO_COLOR);
        return null;
    }

    private static void append(SpannableStringBuilder b, String text, int color) {
        if (text.length() == 0) return;
        int start = b.length();
        b.append(text);
        b.setSpan(new ForegroundColorSpan(color), start, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static String capitalize(String s) {
        return s == null || s.length() == 0 ? "" : s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    @Override
    public int[] argType() {
        return new int[0];
    }

    @Override
    public int priority() {
        return 3;
    }

    @Override
    public int helpRes() {
        return R.string.help_sysinfo;
    }

    @Override
    public String onArgNotFound(ExecutePack info, int index) {
        return null;
    }

    @Override
    public String onNotArgEnough(ExecutePack info, int nArgs) {
        return null;
    }
}
