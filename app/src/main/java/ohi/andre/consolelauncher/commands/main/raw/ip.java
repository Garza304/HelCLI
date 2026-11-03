package ohi.andre.consolelauncher.commands.main.raw;

import java.util.Locale;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.main.specific.SimpleParamCommand;
import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.managers.sysinfo.NetTools;
import ohi.andre.consolelauncher.tuils.Tuils;

/** Network details: type, addresses, gateway, DNS. "ip -public" also asks api.ipify.org for the public address. */
public class ip extends SimpleParamCommand {

    public ip() {
        option("public", (pack, t) -> {
            new Thread(() -> Tuils.sendOutput(pack.context, NetTools.publicIp())).start();
            return null;
        });
    }

    @Override
    public String onEmpty(ExecutePack pack) {
        return NetTools.ip(pack.context);
    }

    @Override
    public int priority() {
        return 3;
    }

    @Override
    public int helpRes() {
        return R.string.help_ip;
    }

}
