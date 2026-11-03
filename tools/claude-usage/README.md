# Claude usage on the HelCLI home screen

HelCLI can show a line like this at the top of the home screen:

```
Claude 5h 24% · week 41%  API $12.40 of $50.00 (25%)
```

It has two halves. Set up either one, or both.

## Plan limits (claude.ai Pro, Max, Ultra)

The 5-hour and weekly percentages you see on claude.ai. Anthropic doesn't offer an API for these,
but Claude Code hands them to its [status line](https://code.claude.com/docs/en/statusline) command.
`helcli_usage.py` is a status line command that copies them to a secret GitHub gist, and HelCLI reads that gist.

On the computer where you use Claude Code (needs Python 3 and the [GitHub CLI](https://cli.github.com), signed in with `gh auth login`):

```
python3 tools/claude-usage/helcli_usage.py --setup
```

It creates the gist and prints what to do next:

1. Add the `statusLine` line it shows to `~/.claude/settings.json`. If you already have a status
   line, use the `--then` form it prints. Your status line keeps working, and the numbers are published first.
2. On the phone: `usage -plan yourname/gist_id`, then `usage -show`.

Good to know:

- The gist only holds the two percentages, their reset times and when they were published. No prompts,
  code or account details. Secret gists aren't listed anywhere, but anyone with the link can open them.
- The numbers update when Claude Code answers you, at most once a minute (or every 15 minutes if
  nothing changed). Usage on claude.ai or the Claude apps shows up the next time Claude Code answers.
- When a window's reset time passes, HelCLI shows it as 0% until new numbers arrive.
- The status line is a feature of Claude Code in the terminal.

## API spend (Claude API)

This month's Claude API cost, from Anthropic's
[Usage and Cost Analytics API](https://platform.claude.com/docs/en/manage-claude/usage-cost-analytics-api).
Its data is usually up to about 4 hours behind.

1. Create a key:
   - **Claude Console:** an Admin API key (`sk-ant-admin01-...`) on the
     [Admin keys](https://platform.claude.com/settings/admin-keys) page. You need an organization (not
     an individual account) and the admin role. **These keys can manage your whole organization** (members,
     API keys), not just read costs. Make one just for HelCLI, give it a short expiration date, and revoke
     it if you lose the phone.
   - **Claude Enterprise:** a key with only the `read:analytics` scope (claude.ai > Organization settings > API).
   `usage -link` in HelCLI opens that page for you.
2. Copy it and go back to HelCLI, which picks it up from the clipboard (or run `usage -key`). It never shows on
   screen or in your command history, and clears the clipboard. The key is kept in HelCLI's private
   storage, never in the config files, so `git pull` and theme sharing can't expose it.
3. Optional: `usage -budget 50` sets a monthly budget, to show a percentage.

`usage -forget` deletes the key from the phone.

## Changing the line

All in `behavior.xml`, `ui.xml` and `theme.xml` (so they work with `git pull` and `my-config/`):

| Setting | What it does |
| --- | --- |
| `show_claude` (ui) | Show the line |
| `claude_index` (ui) | Which line it's on. The default, 7.5, puts it after the weather |
| `claude_size` (ui) | Text size |
| `claude_color` (theme) | Text color. It switches to `battery_color_low` when anything reaches 90% |
| `claude_format` (behavior) | The text. Empty = automatic. Codes: `%5h` `%7d` (plan %), `%5hreset` `%7dreset` (reset times), `%updated` (age of the plan numbers), `%spent` `%budget` `%api` (dollars this month, budget, % of budget) |
| `claude_plan_source` (behavior) | The gist (`usage -plan` sets it) |
| `claude_budget_usd` (behavior) | Monthly API budget (`usage -budget` sets it) |
| `claude_update_time` (behavior) | Seconds between updates (at least 300) |

Bars: `%5hbar`, `%7dbar` and `%apibar` draw a loading bar for each value, and `%bar` for the highest.
A bar fills the rest of its line, or `%5hbar:12` makes it 12 characters wide.

Examples:

```xml
<claude_format value="Claude %5h% (resets %5hreset) · week %7d%"/>
<claude_format value="Claude 5h %5h% %5hbar"/>
```

shows `Claude 5h 24% [████░░░░░░░░░░░░░░░░░]`, filling the line.
