package ohi.andre.consolelauncher.commands.main.raw;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.commands.main.MainPack;
import ohi.andre.consolelauncher.commands.main.specific.ParamCommand;
import ohi.andre.consolelauncher.managers.claude.ClaudeUsage;
import ohi.andre.consolelauncher.managers.claude.ClaudeUsageUpdater;
import ohi.andre.consolelauncher.managers.claude.KeyLink;
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager;
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave;
import ohi.andre.consolelauncher.managers.xml.options.Behavior;
import ohi.andre.consolelauncher.managers.xml.options.Ui;
import ohi.andre.consolelauncher.tuils.Tuils;
import ohi.andre.consolelauncher.tuils.interfaces.Reloadable;

/**
 * Sets up the Claude usage line on the home screen. Named "usage" rather than "claude" so typing
 * "claude" still opens the Claude app.
 */
public class usage extends ParamCommand {

    private static String write(ExecutePack pack, XMLPrefsSave save, String value) {
        save.parent().write(save, value);
        ((Reloadable) pack.context).addMessage(save.parent().path(), save.label() + " -> " + value);
        ((Reloadable) pack.context).reload();
        return null;
    }

    private enum Param implements ohi.andre.consolelauncher.commands.main.Param {

        status {
            @Override
            public String exec(ExecutePack pack) {
                String source = XMLPrefsManager.get(Behavior.claude_plan_source);
                String key = ClaudeUsageUpdater.adminKey(pack.context);
                String budget = XMLPrefsManager.get(Behavior.claude_budget_usd);

                return "Claude usage line: " + (XMLPrefsManager.getBoolean(Ui.show_claude) ? "shown" : "hidden (usage -show)") + Tuils.NEWLINE
                        + "Plan limits from: " + (source == null || source.length() == 0 ? "not set (usage -plan user/gist_id)" : source) + Tuils.NEWLINE
                        + "API spend: " + (key == null ? "not linked (usage -link)" : "key " + ClaudeUsage.maskKey(key)) + Tuils.NEWLINE
                        + "Monthly API budget: " + (budget == null || budget.equals("0") ? "none (usage -budget 50)" : "$" + budget);
            }
        },
        show {
            @Override
            public String exec(ExecutePack pack) {
                return write(pack, Ui.show_claude, "true");
            }
        },
        hide {
            @Override
            public String exec(ExecutePack pack) {
                return write(pack, Ui.show_claude, "false");
            }
        },
        plan {
            @Override
            public int[] args() {
                return new int[] {CommandAbstraction.PLAIN_TEXT};
            }

            @Override
            public String exec(ExecutePack pack) {
                String source = pack.getString().trim();
                if (source.equalsIgnoreCase("off")) return write(pack, Behavior.claude_plan_source, "");
                if (ClaudeUsage.planUrl(source) == null) {
                    return "Use the gist that helcli_usage.py printed, like: usage -plan yourname/0123abcd...";
                }
                return write(pack, Behavior.claude_plan_source, source);
            }
        },
        budget {
            @Override
            public int[] args() {
                return new int[] {CommandAbstraction.PLAIN_TEXT};
            }

            @Override
            public String exec(ExecutePack pack) {
                String value = pack.getString().trim().replace("$", "");
                try {
                    if (Double.parseDouble(value) < 0) throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    return "The budget is a dollar amount, like: usage -budget 50 (0 = no budget)";
                }
                return write(pack, Behavior.claude_budget_usd, value);
            }
        },
        link {
            @Override
            public String exec(ExecutePack pack) {
                return KeyLink.start(pack.context, KeyLink.Kind.ADMIN);
            }
        },
        key {
            @Override
            public String exec(ExecutePack pack) {
//                read the key from the clipboard, so it never appears on screen or in the command history
                ClipboardManager clipboard = (ClipboardManager) pack.context.getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = clipboard == null ? null : clipboard.getPrimaryClip();
                String key = clip != null && clip.getItemCount() > 0 && clip.getItemAt(0).getText() != null
                        ? clip.getItemAt(0).getText().toString().trim() : null;

                if (!ClaudeUsage.looksLikeAdminKey(key)) {
                    return "Copy the key first, then run usage -key again (or run usage -link to make one and link it)." + Tuils.NEWLINE
                            + "Claude Console: an Admin API key (sk-ant-admin01-...) from platform.claude.com/settings/admin-keys."
                            + " It needs an organization and the admin role, and it can manage your whole organization,"
                            + " so give HelCLI its own key with a short expiration date." + Tuils.NEWLINE
                            + "Claude Enterprise: a key with only the read:analytics scope, from claude.ai > Organization settings > API.";
                }

                ClaudeUsageUpdater.saveAdminKey(pack.context, key);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    try {
                        clipboard.clearPrimaryClip();
                    } catch (Exception ignored) {
                    }
                }
                LocalBroadcastManager.getInstance(pack.context.getApplicationContext()).sendBroadcast(new Intent(ClaudeUsageUpdater.ACTION_UPDATE));
                return "Saved key " + ClaudeUsage.maskKey(key) + " (kept in HelCLI's private storage, not in the config files)."
                        + (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? " The clipboard was cleared." : " Clear your clipboard now.")
                        + (XMLPrefsManager.getBoolean(Ui.show_claude) ? "" : Tuils.NEWLINE + "Show the line with: usage -show");
            }
        },
        forget {
            @Override
            public String exec(ExecutePack pack) {
                boolean had = ClaudeUsageUpdater.forgetAdminKey(pack.context);
                LocalBroadcastManager.getInstance(pack.context.getApplicationContext()).sendBroadcast(new Intent(ClaudeUsageUpdater.ACTION_UPDATE));
                return had ? "Deleted the Admin API key from HelCLI. Revoke it in the Claude Console too if you no longer need it." : "No Admin API key was saved.";
            }
        },
        update {
            @Override
            public String exec(ExecutePack pack) {
                if (!XMLPrefsManager.getBoolean(Ui.show_claude)) return "The Claude usage line is hidden. Show it with: usage -show";
                LocalBroadcastManager.getInstance(pack.context.getApplicationContext()).sendBroadcast(new Intent(ClaudeUsageUpdater.ACTION_UPDATE));
                return null;
            }
        };

        static Param get(String p) {
            p = p.toLowerCase();
            Param[] ps = values();
            for (Param p1 : ps)
                if (p.endsWith(p1.label()))
                    return p1;
            return null;
        }

        static String[] labels() {
            Param[] ps = values();
            String[] ss = new String[ps.length];
            for (int count = 0; count < ps.length; count++) ss[count] = ps[count].label();
            return ss;
        }

        @Override
        public String label() {
            return Tuils.MINUS + name();
        }

        @Override
        public String onNotArgEnough(ExecutePack pack, int n) {
            return pack.context.getString(R.string.help_usage);
        }

        @Override
        public String onArgNotFound(ExecutePack pack, int index) {
            return null;
        }

        @Override
        public int[] args() {
            return new int[0];
        }
    }

    @Override
    public String[] params() {
        return Param.labels();
    }

    @Override
    protected ohi.andre.consolelauncher.commands.main.Param paramForString(MainPack pack, String param) {
        return Param.get(param);
    }

    @Override
    public int priority() {
        return 3;
    }

    @Override
    public int helpRes() {
        return R.string.help_usage;
    }

    @Override
    protected String doThings(ExecutePack pack) {
        return null;
    }
}
