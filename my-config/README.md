# My HelCLI settings

The settings in this folder can be pulled onto the phone with `git pull`, so you can change how
HelCLI looks from GitHub (or any computer) and see it on the phone a few seconds later.

## Setup (once, on the phone)

```
git remote Garza304/HelCLI/my-config
git pull
```

## Changing settings

1. Edit a file here. On GitHub, open the file and click the pencil icon.
2. Remove the `<!--` and `-->` around a setting to turn it on, and change its value.
3. Commit, then run `git pull` on the phone. HelCLI shows what changed and reloads.

Made a mistake? `git undo` puts back the settings from before the last pull.

## How it works

- Only the settings written in a file are changed. Everything else stays as it is on the phone,
  so a file can be as short as one line.
- Each setting is checked first. A typo, an unknown setting, or a value of the wrong kind (like
  `maybe` for a true/false setting) is skipped with a warning instead of breaking the launcher.
- Files HelCLI pulls:
  - `theme.xml` (colors), `ui.xml` (what the home screen shows, where, and how big), `behavior.xml` (the text of each line: clock, RAM, WiFi, battery, weather), `suggestions.xml`, `toolbar.xml`, `cmd.xml`
  - `alias.txt` (replaces all your aliases)
  - `themes/*.json`, saved as themes for `theme -apply` (same format as the [theme gallery](../themes/README.md))
- Every setting and what it does: run `config -ls ui.xml` (or `theme.xml`, ...) on the phone,
  and `config -info [setting]` for one setting.
- Other commands: `git status` shows the remote and the last pull, and `git remote` changes the folder.
  Any public GitHub repository works, for example `git remote you/dotfiles/helcli@main`.
- It uses GitHub without an account, which allows 60 requests an hour. Each pull uses two or three.
