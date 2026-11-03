package ohi.andre.consolelauncher.commands.main.specific;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.commands.main.MainPack;
import ohi.andre.consolelauncher.commands.main.Param;

/**
 * A command whose options are -words ("update -install"), listed with {@link #option} in the
 * constructor. The options show up in the suggestion buttons above the input, like every other
 * -param command. Typing them without the dash ("update install") works too, unless
 * {@link #paramsNeedDash()} says otherwise.
 */
public abstract class SimpleParamCommand extends ParamCommand {

    public interface Action {
        /** @param text what was typed after the option ("" when nothing), or null for options without text */
        String run(ExecutePack pack, String text);
    }

    private static final class Option implements Param {
        final String label;
        final boolean takesText;
        final Action action;

        Option(String label, boolean takesText, Action action) {
            this.label = label;
            this.takesText = takesText;
            this.action = action;
        }

        @Override
        public int[] args() {
            return takesText ? new int[] {CommandAbstraction.PLAIN_TEXT} : new int[0];
        }

        @Override
        public String exec(ExecutePack pack) {
            return action.run(pack, takesText ? pack.getString().trim() : null);
        }

        @Override
        public String label() {
            return label;
        }

        @Override
        public String onNotArgEnough(ExecutePack pack, int n) {
//            the text is optional: the option decides what nothing means
            return action.run(pack, "");
        }

        @Override
        public String onArgNotFound(ExecutePack pack, int index) {
            return null;
        }
    }

    private final Map<String, Option> options = new LinkedHashMap<>();

    /** An option with nothing after it: "reset -undo". */
    protected final void option(String name, Action action) {
        options.put("-" + name, new Option("-" + name, false, action));
    }

    /** An option followed by text, which may be left out: "at -cancel 3", "storage -apps all". */
    protected final void optionWithText(String name, Action action) {
        options.put("-" + name, new Option("-" + name, true, action));
    }

    @Override
    public String[] params() {
        return options.keySet().toArray(new String[0]);
    }

    @Override
    protected Param paramForString(MainPack pack, String param) {
        return param == null ? null : options.get(param.toLowerCase(Locale.ROOT));
    }

    @Override
    protected String doThings(ExecutePack pack) {
        return null;
    }
}
