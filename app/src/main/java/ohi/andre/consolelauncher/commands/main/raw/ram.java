package ohi.andre.consolelauncher.commands.main.raw;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.Debug;

import java.io.FileInputStream;
import java.io.InputStream;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.managers.sysinfo.Breakdown;
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager;
import ohi.andre.consolelauncher.managers.xml.options.Ui;
import ohi.andre.consolelauncher.tuils.Tuils;

/**
 * A breakdown of the phone's memory: in use, cached (freed when apps need it), free, and swap.
 * Android doesn't let apps see other apps' memory, so there's no per-app list.
 */
public class ram implements CommandAbstraction {

    @Override
    public String exec(ExecutePack pack) {
        Breakdown.Ram r = new Breakdown.Ram();

//        /proc/meminfo has the most detail; ActivityManager fills in what it can't give
        try (InputStream in = new FileInputStream("/proc/meminfo")) {
            r.fromMeminfo(Breakdown.parseMeminfo(Tuils.inputStreamToString(in)));
        } catch (Exception e) {
            Tuils.log(e);
        }

        ActivityManager am = (ActivityManager) pack.context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am != null) {
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            if (r.total <= 0) r.total = mi.totalMem;
            if (r.available < 0) r.available = mi.availMem;
            r.threshold = mi.threshold;
            r.low = mi.lowMemory;
            if (Build.VERSION.SDK_INT >= 34) r.advertised = mi.advertisedMem;
        }

        try {
            Debug.MemoryInfo own = new Debug.MemoryInfo();
            Debug.getMemoryInfo(own);
            r.self = own.getTotalPss() * 1024L;
        } catch (Exception e) {
            Tuils.log(e);
        }

        return Breakdown.ram(r, XMLPrefsManager.get(Ui.bar_style));
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
        return R.string.help_ram;
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
