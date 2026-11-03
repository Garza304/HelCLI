package ohi.andre.consolelauncher.commands.main.raw;

import android.content.Context;

import java.util.Locale;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.commands.main.MainPack;
import ohi.andre.consolelauncher.managers.update.Updater;
import ohi.andre.consolelauncher.tuils.Tuils;
import okhttp3.OkHttpClient;

/** update: checks GitHub for a newer HelCLI; update install: downloads, checks and installs it. */
public class update implements CommandAbstraction {

    @Override
    public String exec(ExecutePack pack) {
        String a = pack.getString().trim().toLowerCase(Locale.ROOT).replaceAll("^-+", "");
        final Context c = pack.context;
        final OkHttpClient client = ((MainPack) pack).client;
        if (a.equals("install") || a.equals("reinstall")) {
            final boolean force = a.equals("reinstall");
            new Thread(() -> Updater.install(c, client, force, line -> Tuils.sendOutput(c, line))).start();
            return null;
        }
        return c.getString(R.string.help_update);
    }

    @Override
    public String onNotArgEnough(ExecutePack pack, int nArgs) {
        final Context c = pack.context;
        final OkHttpClient client = ((MainPack) pack).client;
        new Thread(() -> Updater.check(c, client, line -> Tuils.sendOutput(c, line))).start();
        return "Checking for updates...";
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
        return R.string.help_update;
    }

    @Override
    public String onArgNotFound(ExecutePack info, int index) {
        return null;
    }
}
