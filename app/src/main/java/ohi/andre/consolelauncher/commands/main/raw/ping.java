package ohi.andre.consolelauncher.commands.main.raw;

import android.content.Context;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.managers.sysinfo.NetTools;
import ohi.andre.consolelauncher.tuils.Tuils;

/**
 * ping [host]. With no host it checks your router, then the internet (one.one.one.one),
 * which tells whether a problem is the WiFi or the connection beyond it.
 */
public class ping implements CommandAbstraction {

    @Override
    public String exec(ExecutePack pack) {
        final String host = pack.getString().trim();
        final Context c = pack.context;
        new Thread(() -> NetTools.ping(host, line -> Tuils.sendOutput(c, line))).start();
        return null;
    }

    @Override
    public String onNotArgEnough(ExecutePack pack, int nArgs) {
        final Context c = pack.context;
        new Thread(() -> {
            String gw = NetTools.gateway(c);
            if (gw != null) {
                Tuils.sendOutput(c, "Your router (" + gw + "):");
                NetTools.ping(gw, line -> Tuils.sendOutput(c, line));
            }
            Tuils.sendOutput(c, "The internet (one.one.one.one):");
            NetTools.ping("one.one.one.one", line -> Tuils.sendOutput(c, line));
        }).start();
        return null;
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
        return R.string.help_ping;
    }

    @Override
    public String onArgNotFound(ExecutePack info, int index) {
        return null;
    }
}
