package ohi.andre.consolelauncher.commands.main.raw;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.commands.main.specific.SimpleParamCommand;
import ohi.andre.consolelauncher.managers.sync.SettingsReset;
import ohi.andre.consolelauncher.tuils.interfaces.Reloadable;

/** reset: shows what it does; reset -now: every setting back to the defaults; reset -undo: puts them back. */
public class reset extends SimpleParamCommand {

    public reset() {
        option("now", (pack, t) -> SettingsReset.reset(pack.context, (Reloadable) pack.context));
        option("undo", (pack, t) -> SettingsReset.undo(pack.context, (Reloadable) pack.context));
    }

    @Override
    public String onEmpty(ExecutePack pack) {
        return SettingsReset.explain();
    }

    @Override
    public int priority() {
        return 2;
    }

    @Override
    public int helpRes() {
        return R.string.help_reset;
    }
}
