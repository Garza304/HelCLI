package ohi.andre.consolelauncher.commands.main.raw;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;


import ohi.andre.consolelauncher.LauncherActivity;
import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.commands.main.MainPack;
import ohi.andre.consolelauncher.commands.main.specific.ParamCommand;
import ohi.andre.consolelauncher.managers.ThemeManager;
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager;
import ohi.andre.consolelauncher.managers.xml.options.Theme;
import ohi.andre.consolelauncher.tuils.Tuils;
import ohi.andre.consolelauncher.tuils.interfaces.Reloadable;

public class theme extends ParamCommand {

    private enum Param implements ohi.andre.consolelauncher.commands.main.Param {

        list {
            @Override
            public String exec(ExecutePack pack) {
                send(pack, ThemeManager.ACTION_LIST, null);
                return "Loading the theme gallery...";
            }
        },
        preview {
            @Override
            public int[] args() {
                return new int[] {CommandAbstraction.THEME_PRESET};
            }

            @Override
            public String exec(ExecutePack pack) {
                return send(pack, ThemeManager.ACTION_PREVIEW, pack.getString());
            }
        },
        apply {
            @Override
            public int[] args() {
                return new int[] {CommandAbstraction.THEME_PRESET};
            }

            @Override
            public String exec(ExecutePack pack) {
                return send(pack, ThemeManager.ACTION_APPLY, pack.getString());
            }
        },
        import_theme {
            @Override
            public int[] args() {
                return new int[] {CommandAbstraction.PLAIN_TEXT};
            }

            @Override
            public String exec(ExecutePack pack) {
                return send(pack, ThemeManager.ACTION_IMPORT, pack.getString());
            }

            @Override
            public String onNotArgEnough(ExecutePack pack, int n) {
//                no link given: let the user pick a theme file
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {"application/json", "text/plain", "application/octet-stream"});
                try {
                    ((Activity) pack.context).startActivityForResult(intent, LauncherActivity.THEME_IMPORT_REQUEST);
                } catch (Exception e) {
                    return "Couldn't open the file picker. You can also import from a link: theme -import https://...";
                }
                return "Pick a theme file (.json)";
            }

            @Override
            public String label() {
                return Tuils.MINUS + "import";
            }
        },
        export {
            @Override
            public int[] args() {
                return new int[] {CommandAbstraction.PLAIN_TEXT};
            }

            @Override
            public String exec(ExecutePack pack) {
                return send(pack, ThemeManager.ACTION_EXPORT, pack.getString());
            }
        },
        share {
            @Override
            public int[] args() {
                return new int[] {CommandAbstraction.THEME_PRESET};
            }

            @Override
            public String exec(ExecutePack pack) {
                return send(pack, ThemeManager.ACTION_SHARE, pack.getString());
            }
        },
        delete {
            @Override
            public int[] args() {
                return new int[] {CommandAbstraction.THEME_PRESET};
            }

            @Override
            public String exec(ExecutePack pack) {
                return send(pack, ThemeManager.ACTION_DELETE, pack.getString());
            }
        },
        set {
            @Override
            public int[] args() {
                return new int[] {CommandAbstraction.CONFIG_ENTRY, CommandAbstraction.PLAIN_TEXT};
            }

            @Override
            public String exec(ExecutePack pack) {
                Object o = pack.get();
                if (!(o instanceof Theme)) {
                    return "Invalid theme element.";
                }
                Theme element = (Theme) o;
                String color = pack.getString();

                try {
                    Color.parseColor(color);
                } catch (Exception e) {
                    return "Invalid color format. Use #RRGGBB or #AARRGGBB";
                }

                XMLPrefsManager.XMLPrefsRoot.THEME.write(element, color);

                try {
                    if (pack.context instanceof Reloadable) {
                        ((Reloadable) pack.context).reload();
                    }
                } catch (Exception e) {}

                return element.label() + " updated to " + color;
            }
        },
        preset {
            @Override
            public int[] args() {
                return new int[] {CommandAbstraction.THEME_PRESET};
            }

            @Override
            public String exec(ExecutePack pack) {
//                the old presets (blue, red, green, pink, bw, cyberpunk) are now gallery themes
                return send(pack, ThemeManager.ACTION_APPLY, pack.getString());
            }
        },
        standard {
            @Override
            public int[] args() {
                return new int[] {};
            }

            @Override
            public String exec(ExecutePack pack) {
                return send(pack, ThemeManager.ACTION_STANDARD, null);
            }
        },
        old {
            @Override
            public String exec(ExecutePack pack) {
                return send(pack, ThemeManager.ACTION_REVERT, null);
            }
        };

        static String send(ExecutePack pack, String action, String name) {
            Intent intent = new Intent(action);
            if (name != null) intent.putExtra(ThemeManager.NAME, name.trim());
            LocalBroadcastManager.getInstance(pack.context.getApplicationContext()).sendBroadcast(intent);
            return null;
        }

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

            for (int count = 0; count < ps.length; count++) {
                ss[count] = ps[count].label();
            }

            return ss;
        }

        @Override
        public String label() {
            return Tuils.MINUS + name();
        }

        @Override
        public String onNotArgEnough(ExecutePack pack, int n) {
            return pack.context.getString(R.string.help_theme);
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
    };

    @Override
    protected ohi.andre.consolelauncher.commands.main.Param paramForString(MainPack pack, String param) {
        return Param.get(param);
    }

    @Override
    public int priority() {
        return 4;
    }

    @Override
    public int helpRes() {
        return R.string.help_theme;
    }

    @Override
    protected String doThings(ExecutePack pack) {
        return null;
    }
}
