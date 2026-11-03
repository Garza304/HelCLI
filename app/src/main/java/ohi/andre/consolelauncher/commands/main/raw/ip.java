package ohi.andre.consolelauncher.commands.main.raw;

import java.util.Locale;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.managers.sysinfo.NetTools;
import ohi.andre.consolelauncher.tuils.Tuils;

/** Network details: type, addresses, gateway, DNS. "ip public" also asks api.ipify.org for the public address. */
public class ip implements CommandAbstraction {

    @Override
    public String exec(ExecutePack pack) {
        String a = pack.getString().trim().toLowerCase(Locale.ROOT);
        if (a.equals("public") || a.equals("-public")) {
            new Thread(() -> Tuils.sendOutput(pack.context, NetTools.publicIp())).start();
            return null;
        }
        return pack.context.getString(R.string.help_ip);
    }

    @Override
    public String onNotArgEnough(ExecutePack pack, int nArgs) {
        return NetTools.ip(pack.context);
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
        return R.string.help_ip;
    }

    @Override
    public String onArgNotFound(ExecutePack info, int index) {
        return null;
    }
}
