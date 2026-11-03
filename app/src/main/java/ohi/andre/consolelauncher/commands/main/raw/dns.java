package ohi.andre.consolelauncher.commands.main.raw;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.managers.sysinfo.NetTools;
import ohi.andre.consolelauncher.tuils.Tuils;

/** dns [host]: the addresses a name points to, how long the lookup took, and which DNS server answered. */
public class dns implements CommandAbstraction {

    @Override
    public String exec(ExecutePack pack) {
        final String host = pack.getString().trim();
        new Thread(() -> Tuils.sendOutput(pack.context, NetTools.dns(pack.context, host))).start();
        return null;
    }

    @Override
    public String onNotArgEnough(ExecutePack pack, int nArgs) {
        return pack.context.getString(R.string.help_dns);
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
        return R.string.help_dns;
    }

    @Override
    public String onArgNotFound(ExecutePack info, int index) {
        return null;
    }
}
