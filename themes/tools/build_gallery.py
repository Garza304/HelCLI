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


def preview_svg(data):
    """A small picture of the launcher in this theme: status line, two commands, suggestion buttons."""
    bg, alpha = rgb(color(data, "theme", "bg_color"))
    if alpha < 0.25:
        bg = "#000000"
    c = lambda k: rgb(color(data, "theme", k))[0]
    default_text = rgb(color(data, "suggestions", "default_text_color"))[0]

    def chip_colors(kind):
        text = color(data, "suggestions", kind + "_text_color")
        return rgb(color(data, "suggestions", kind + "_bg_color"))[0], rgb(text)[0] if text else default_text

    w, h = 360, 200
    font = 'font-family="ui-monospace,SFMono-Regular,Menlo,Consolas,monospace" font-size="13"'
    lines = [
        '<svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d" viewBox="0 0 %d %d" role="img" aria-label="%s theme preview">'
        % (w, h, w, h, html.escape(data["title"])),
        '<rect width="%d" height="%d" rx="12" fill="%s"/>' % (w, h, bg),
        '<g %s>' % font,
        '<text x="16" y="28"><tspan fill="%s">12:45</tspan><tspan fill="%s">  Battery 87%%</tspan><tspan fill="%s">  RAM 2.1 GB</tspan></text>'
        % (c("time_color"), c("battery_color_high"), c("ram_color")),
        '<text x="16" y="66"><tspan fill="%s">user@helcli</tspan><tspan fill="%s">: </tspan><tspan fill="%s">$ ls</tspan></text>'
        % (c("device_color"), c("output_color"), c("input_color")),
        '<text x="16" y="86" fill="%s">Documents  Music  notes.txt</text>' % c("output_color"),
        '<text x="16" y="110"><tspan fill="%s">user@helcli</tspan><tspan fill="%s">: </tspan><tspan fill="%s">$ open github.com</tspan></text>'
        % (c("device_color"), c("output_color"), c("input_color")),
        '<text x="16" y="130" fill="%s">https://github.com</text>' % c("link_color"),
    ]
    x = 16
    for label, kind in (("Camera", "apps"), ("help", "cmd"), ("ll", "alias"), ("notes", "file")):
        chip_bg, chip_fg = chip_colors(kind)
        width = 16 + 8 * len(label)
        lines.append('<rect x="%d" y="156" width="%d" height="26" rx="4" fill="%s"/>' % (x, width, chip_bg))
        lines.append('<text x="%d" y="174" fill="%s">%s</text>' % (x + 8, chip_fg, label))
        x += width + 8
    lines += ["</g>", "</svg>", ""]
    return "\n".join(lines)


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
