package ohi.andre.consolelauncher.commands.main.raw;

import android.content.Context;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.commands.main.MainPack;
import ohi.andre.consolelauncher.commands.main.specific.SimpleParamCommand;
import ohi.andre.consolelauncher.managers.update.Updater;
import ohi.andre.consolelauncher.tuils.Tuils;
import okhttp3.OkHttpClient;

/** update: checks GitHub for a newer HelCLI; update -install: downloads, checks and installs it. */
public class update extends SimpleParamCommand {

    public update() {
        option("check", (pack, t) -> check(pack));
        option("install", (pack, t) -> install(pack, false));
        option("reinstall", (pack, t) -> install(pack, true));
    }

    @Override
    public String onEmpty(ExecutePack pack) {
        return check(pack);
    }

    private static String check(ExecutePack pack) {
        final Context c = pack.context;
        final OkHttpClient client = ((MainPack) pack).client;
        new Thread(() -> Updater.check(c, client, line -> Tuils.sendOutput(c, line))).start();
        return "Checking for updates...";
    }

    private static String install(ExecutePack pack, boolean force) {
        final Context c = pack.context;
        final OkHttpClient client = ((MainPack) pack).client;
        new Thread(() -> Updater.install(c, client, force, line -> Tuils.sendOutput(c, line))).start();
        return null;
    }

    @Override
    public int priority() {
        return 3;
    }

    @Override
    public int helpRes() {
        return R.string.help_update;
    }
}
