package ohi.andre.consolelauncher.commands.main.raw;

import java.util.Locale;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.managers.sync.SettingsReset;
import ohi.andre.consolelauncher.tuils.interfaces.Reloadable;

/** reset: shows what it does; reset now: every setting back to the defaults; reset undo: puts them back. */
public class reset implements CommandAbstraction {

    @Override
    public String exec(ExecutePack pack) {
        String a = pack.getString().trim().toLowerCase(Locale.ROOT).replaceAll("^-+", "");
        Reloadable reloadable = (Reloadable) pack.context;
        if (a.equals("now")) return SettingsReset.reset(pack.context, reloadable);
        if (a.equals("undo")) return SettingsReset.undo(pack.context, reloadable);
        return SettingsReset.explain();
    }

    @Override
    public String onNotArgEnough(ExecutePack pack, int nArgs) {
        return SettingsReset.explain();
    }

    @Override
    public int[] argType() {
        return new int[] {CommandAbstraction.PLAIN_TEXT};
    }

    @Override
    public int priority() {
        return 2;
    }

    @Override
    public int helpRes() {
        return R.string.help_reset;
    }

    @Override
    public String onArgNotFound(ExecutePack info, int index) {
        return null;
    }
}
