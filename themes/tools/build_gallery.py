#!/usr/bin/env python3
"""Builds the HelCLI theme gallery from the theme files in /themes.

For every themes/<name>.json it:
  * checks the file (known color names, valid #RRGGBB / #AARRGGBB colors, name matches file name)
  * draws a preview picture, themes/previews/<name>.svg
and then writes:
  * themes/index.json  - the list the app downloads for "theme -list"
  * themes/README.md   - the gallery page, with every preview

Run it after adding or changing a theme:
    python3 themes/tools/build_gallery.py
Use --check to only validate (CI does this, and fails if the generated files are out of date).
"""

import html
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
THEMES = os.path.join(ROOT, "themes")
PREVIEWS = os.path.join(THEMES, "previews")
OPTIONS = os.path.join(ROOT, "app", "src", "main", "java", "ohi", "andre", "consolelauncher",
                       "managers", "xml", "options")

NAME_RE = re.compile(r"^[a-z0-9][a-z0-9_-]{0,39}$")
COLOR_RE = re.compile(r"^#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")
REPO_RAW = "https://raw.githubusercontent.com/Garza304/HelCLI/master/themes/"


def color_keys(java_file, all_colors):
    """Reads the option names (and defaults) from the app's Java enum, so this never goes stale."""
    src = open(os.path.join(OPTIONS, java_file), encoding="utf-8").read()
    keys = {}
    for m in re.finditer(r"\n    (\w+) \{(.*?)\n    \},?", src, re.S):
        body = m.group(2)
        is_color = all_colors or "type()" not in body
        default = re.search(r'defaultValue\(\) \{\s*return "([^"]*)";', body)
        if is_color:
            keys[m.group(1)] = default.group(1) if default else None
    return keys


THEME_KEYS = color_keys("Theme.java", True)
SUGGESTION_KEYS = color_keys("Suggestions.java", False)


def valid_color(value):
    return isinstance(value, str) and value and all(COLOR_RE.match(p.strip()) for p in value.split(","))


def check(path, data):
    problems = []
    name = data.get("name", "")
    if not NAME_RE.match(name):
        problems.append("invalid name %r" % name)
    elif os.path.basename(path) != name + ".json":
        problems.append("file should be called %s.json" % name)
    for field in ("title", "description"):
        if not data.get(field):
            problems.append("missing %s" % field)
    for section, known in (("theme", THEME_KEYS), ("suggestions", SUGGESTION_KEYS)):
        for key, value in (data.get(section) or {}).items():
            if key not in known:
                problems.append("%s.%s is not a known color" % (section, key))
            elif not valid_color(value):
                problems.append("%s.%s has an invalid color %r" % (section, key, value))
    if not data.get("theme") and not data.get("suggestions"):
        problems.append("sets no colors")
    return problems


def rgb(value, fallback="#000000"):
    """#AARRGGBB or #RRGGBB (first of a list) -> (#RRGGBB, opacity)."""
    if not value:
        return fallback, 1.0
    value = value.split(",")[0].strip()
    if len(value) == 9:
        return "#" + value[3:], int(value[1:3], 16) / 255
    return value, 1.0


def color(data, section, key):
    known = THEME_KEYS if section == "theme" else SUGGESTION_KEYS
    value = (data.get(section) or {}).get(key)
    if value is None:
        value = known.get(key) or ""
    return value


WIDTH_CHARS = 44   # one status line at the default text size on a phone


def bar(percent, width, style="[\u2588\u2591]"):
    """A loading bar like the app draws: [\u2588\u2588\u2591\u2591], or a moving marker for 3-character styles."""
    if len(style) == 3:
        played, mark, rest = style
        at = round((width - 1) * percent / 100)
        return played * at + mark + rest * (width - 1 - at)
    left, full, empty, right = style
    inner = width - 2
    n = round(inner * percent / 100)
    return left + full * n + empty * (inner - n) + right


def home_lines(title, name):
    """The default home screen, as lines of (text, color key) pieces."""
    def fill(prefix, percent, suffix="", style="[\u2588\u2591]"):
        return bar(percent, WIDTH_CHARS - len(prefix) - len(suffix), style)
    return [
        [("Tue Nov 3 \u00b7 9:41 PM  ", "time_color"), ("87% " + fill("Tue Nov 3 \u00b7 9:41 PM  87% ", 87), "battery_color_high")],
        [("Alarm 6:45 AM (in 9h 4m)  ", "alarm_color"), ("HomeNet\u00b75G\u00b7bt", "network_info_color")],
        [("San Antonio 64\u00b0F clear \u219171 \u219352 \u00b7 rain 10%", "weather_color")],
        [("ram   39% " + fill("ram   39% ", 39), "ram_color")],
        [("disk  72% " + fill("disk  72% ", 72), "storage_color")],
        [("Claude \u00b7 5h resets 11:20 PM", "claude_color")],
        [("5h    42% " + fill("5h    42% ", 42), "claude_color")],
        [("week  18% " + fill("week  18% ", 18), "claude_color")],
        [("Next: Dentist \u00b7 Thu 2:30 PM (in 1d 16h)", "calendar_color")],
        [("\u266a Midnight City \u2014 M83", "music_color")],
        [("2:41 " + fill("2:41 ", 66, " 4:03", "~\u266b\u00b7") + " 4:03", "music_color")],
        [],
        [("user@helcli", "device_color"), (": ", "output_color"), ("$ theme -apply " + name, "input_color")],
        [("Theme applied: " + title, "output_color")],
    ]


def preview_svg(data):
    """A picture of the home screen in this theme: the status lines, a command, suggestion buttons."""
    bg, alpha = rgb(color(data, "theme", "bg_color"))
    if alpha < 0.25:
        bg = "#000000"
    c = lambda k: rgb(color(data, "theme", k))[0]
    default_text = rgb(color(data, "suggestions", "default_text_color"))[0]

    def chip_colors(kind):
        text = color(data, "suggestions", kind + "_text_color")
        return rgb(color(data, "suggestions", kind + "_bg_color"))[0], rgb(text)[0] if text else default_text

    w, h, step = 360, 312, 15
    font = 'font-family="ui-monospace,SFMono-Regular,Menlo,Consolas,monospace" font-size="11.5"'
    out = [
        '<svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d" viewBox="0 0 %d %d" role="img" aria-label="%s theme preview">'
        % (w, h, w, h, html.escape(data["title"])),
        '<rect width="%d" height="%d" rx="12" fill="%s"/>' % (w, h, bg),
        '<g %s>' % font,
    ]
    y = 26
    for line in home_lines(data["title"], data["name"]):
        if line:
#            non-breaking spaces: SVG viewers collapse runs of ordinary spaces, which would shift the bars
            spans = "".join('<tspan fill="%s">%s</tspan>' % (c(key), html.escape(text).replace(" ", "\u00a0")) for text, key in line)
            out.append('<text x="14" y="%d">%s</text>' % (y, spans))
        y += step
    x, y = 14, h - 38
    for label, kind in (("Camera", "apps"), ("help", "cmd"), ("ll", "alias"), ("notes", "file")):
        chip_bg, chip_fg = chip_colors(kind)
        width = 16 + 7 * len(label)
        out.append('<rect x="%d" y="%d" width="%d" height="24" rx="4" fill="%s"/>' % (x, y, width, chip_bg))
        out.append('<text x="%d" y="%d" fill="%s">%s</text>' % (x + 8, y + 16, chip_fg, label))
        x += width + 8
    out += ["</g>", "</svg>", ""]
    return "\n".join(out)


def build(check_only):
    files = sorted(f for f in os.listdir(THEMES) if f.endswith(".json") and f != "index.json")
    themes, failed = [], False
    for f in files:
        path = os.path.join(THEMES, f)
        try:
            data = json.load(open(path, encoding="utf-8"))
        except ValueError as e:
            print("%s: invalid JSON: %s" % (f, e))
            failed = True
            continue
        problems = check(path, data)
        for p in problems:
            print("%s: %s" % (f, p))
        failed = failed or bool(problems)
        themes.append(data)

    if failed:
        return 1

    outputs = {}
    for t in themes:
        outputs[os.path.join(PREVIEWS, t["name"] + ".svg")] = preview_svg(t)

    index = {
        "version": 1,
        "themes": [
            {
                "name": t["name"],
                "title": t["title"],
                "author": t.get("author", ""),
                "description": t.get("description", ""),
                "url": REPO_RAW + t["name"] + ".json",
                "swatch": {
                    "bg": color(t, "theme", "bg_color"),
                    "input": color(t, "theme", "input_color"),
                    "output": color(t, "theme", "output_color"),
                },
            }
            for t in themes
        ],
    }
    outputs[os.path.join(THEMES, "index.json")] = json.dumps(index, indent=2) + "\n"
    outputs[os.path.join(THEMES, "README.md")] = readme(themes)

    stale = []
    for path, content in outputs.items():
        old = open(path, encoding="utf-8").read() if os.path.exists(path) else None
        if old != content:
            stale.append(os.path.relpath(path, ROOT))
            if not check_only:
                os.makedirs(os.path.dirname(path), exist_ok=True)
                with open(path, "w", encoding="utf-8") as fh:
                    fh.write(content)

    if check_only and stale:
        print("The gallery is out of date. Run: python3 themes/tools/build_gallery.py")
        for s in stale:
            print("  " + s)
        return 1

    print("%d themes OK%s" % (len(themes), "" if check_only else ", %d files updated" % len(stale)))
    return 0


def readme(themes):
    out = [
        "# HelCLI theme gallery",
        "",
        "Every theme here is built into HelCLI. Themes added here later show up in the app too,",
        "under \"New in the online gallery\", without updating the app.",
        "",
        "## Using a theme",
        "",
        "```",
        "theme -list              show every theme",
        "theme -preview dracula   see it before applying it",
        "theme -apply dracula     use it",
        "theme -old               go back to the theme you had before",
        "```",
        "",
        "## Sharing your own",
        "",
        "```",
        "theme -export mytheme    save your current colors as a theme",
        "theme -share mytheme     send the file to another app",
        "theme -import            pick a theme file someone sent you",
        "theme -import https://example.com/theme.json",
        "```",
        "",
        "To add a theme to this gallery, put its `.json` file in this folder, run",
        "`python3 themes/tools/build_gallery.py` to make its preview, and open a pull request.",
        "The [theme file format](#theme-file-format) is described at the end of this page.",
        "",
        "## Themes",
        "",
    ]
    for t in themes:
        out += [
            "### %s" % t["title"],
            "",
            "`theme -apply %s`%s" % (t["name"], (" · by " + t["author"]) if t.get("author") else ""),
            "",
            t.get("description", ""),
            "",
            '<img src="previews/%s.svg" width="360" alt="%s theme preview">' % (t["name"], html.escape(t["title"])),
            "",
        ]
    out += [
        "## Theme file format",
        "",
        "A theme is a JSON file. `theme` holds colors from `theme.xml` and `suggestions` holds colors",
        "from `suggestions.xml`. Colors are `#RRGGBB` or `#AARRGGBB` (`AA` is opacity: `ff` is solid,",
        "`00` is invisible). Any color you leave out goes back to its default when the theme is applied.",
        "",
        "```json",
        "{",
        '  "name": "mytheme",',
        '  "title": "My Theme",',
        '  "author": "you",',
        '  "description": "One line about how it looks",',
        '  "theme": {',
        '    "bg_color": "#ff101010",',
        '    "overlay_color": "#b0101010",',
        '    "input_color": "#ff00ff00",',
        '    "output_color": "#ffffffff",',
        '    "device_color": "#ffff9800"',
        "  },",
        '  "suggestions": {',
        '    "apps_bg_color": "#ff00897b",',
        '    "default_text_color": "#ff000000"',
        "  }",
        "}",
        "```",
        "",
        "`overlay_color` is drawn over your wallpaper when `system_wallpaper` is on.",
        "",
        "Colors a theme can set:",
        "",
        "- **theme**: " + ", ".join("`%s`" % k for k in THEME_KEYS),
        "- **suggestions**: " + ", ".join("`%s`" % k for k in SUGGESTION_KEYS),
        "",
    ]
    return "\n".join(out)


if __name__ == "__main__":
    sys.exit(build("--check" in sys.argv))
