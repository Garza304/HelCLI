package ohi.andre.consolelauncher.commands.main.raw;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.commands.main.MainPack;
import ohi.andre.consolelauncher.commands.main.specific.ParamCommand;
import ohi.andre.consolelauncher.managers.sync.GitSync;
import ohi.andre.consolelauncher.managers.sync.RepoSync;
import ohi.andre.consolelauncher.tuils.Tuils;
import ohi.andre.consolelauncher.tuils.interfaces.Reloadable;

/**
 * git pull for your HelCLI settings: see {@link GitSync}.
 */
public class git extends ParamCommand {

    private static GitSync sync(ExecutePack pack) {
        return new GitSync(pack.context.getApplicationContext(), ((MainPack) pack).client, (Reloadable) pack.context);
    }

    private enum Param implements ohi.andre.consolelauncher.commands.main.Param {

        pull {
            @Override
            public String exec(ExecutePack pack) {
                final GitSync sync = sync(pack);
                RepoSync.Remote remote = sync.remote();
                if (remote == null) return GitSync.noRemoteMessage();

                new Thread(sync::pull).start();
                return "Pulling from " + remote + "...";
            }
        },
        status {
            @Override
            public String exec(ExecutePack pack) {
                return sync(pack).status();
            }
        },
        remote {
            @Override
            public int[] args() {
                return new int[] {CommandAbstraction.PLAIN_TEXT};
            }

            @Override
            public String exec(ExecutePack pack) {
                String text = pack.getString();
                RepoSync.Remote remote = RepoSync.parseRemote(text);
                if (remote == null) {
                    return "That isn't a GitHub folder HelCLI understands: " + text + Tuils.NEWLINE + GitSync.noRemoteMessage();
                }
                sync(pack).setRemote(remote);
                return "Remote set to " + remote + Tuils.NEWLINE + remote.webUrl() + Tuils.NEWLINE + "Now run: git pull";
            }

            @Override
            public String onNotArgEnough(ExecutePack pack, int n) {
                RepoSync.Remote remote = sync(pack).remote();
                return remote == null ? GitSync.noRemoteMessage() : remote + Tuils.NEWLINE + remote.webUrl();
            }
        },
        undo {
            @Override
            public String exec(ExecutePack pack) {
                final GitSync sync = sync(pack);
                new Thread(sync::undo).start();
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
            return pack.context.getString(R.string.help_git);
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
        return R.string.help_git;
    }

    @Override
    protected String doThings(ExecutePack pack) {
        return null;
    }
}
