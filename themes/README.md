# HelCLI theme gallery

Every theme here is built into HelCLI. Themes added here later show up in the app too,
under "New in the online gallery", without updating the app.

## Using a theme

```
theme -list              show every theme
theme -preview dracula   see it before applying it
theme -apply dracula     use it
theme -old               go back to the theme you had before
```

## Sharing your own

```
theme -export mytheme    save your current colors as a theme
theme -share mytheme     send the file to another app
theme -import            pick a theme file someone sent you
theme -import https://example.com/theme.json
```

To add a theme to this gallery, put its `.json` file in this folder, run
`python3 themes/tools/build_gallery.py` to make its preview, and open a pull request.
The [theme file format](#theme-file-format) is described at the end of this page.

## Themes

### Abyss

`theme -apply abyss` · by HelCLI

Bioluminescent teal and blue from the deep sea

<img src="previews/abyss.svg" width="360" alt="Abyss theme preview">

### Amber CRT

`theme -apply amber` · by HelCLI

Warm monochrome, like an old amber monitor

<img src="previews/amber.svg" width="360" alt="Amber CRT theme preview">

### Aurora

`theme -apply aurora` · by HelCLI

Northern lights: green, teal and violet over a dark sky

<img src="previews/aurora.svg" width="360" alt="Aurora theme preview">

### Ayu Mirage

`theme -apply ayu-mirage` · by after Ayu

Bright, warm accents on slate blue

<img src="previews/ayu-mirage.svg" width="360" alt="Ayu Mirage theme preview">

### Blue

`theme -apply blue` · by HelCLI

Deep navy with bright sky-blue text

<img src="previews/blue.svg" width="360" alt="Blue theme preview">

### Black & White

`theme -apply bw` · by HelCLI

Pure black and white, nothing else

<img src="previews/bw.svg" width="360" alt="Black &amp; White theme preview">

### Catppuccin Latte

`theme -apply catppuccin-latte` · by after Catppuccin

The light flavor of Catppuccin

<img src="previews/catppuccin-latte.svg" width="360" alt="Catppuccin Latte theme preview">

### Catppuccin Mocha

`theme -apply catppuccin-mocha` · by HelCLI

Soothing pastels on a deep mocha base

<img src="previews/catppuccin-mocha.svg" width="360" alt="Catppuccin Mocha theme preview">

### Classic

`theme -apply classic` · by HelCLI

The original look: green prompt, white text, orange device name

<img src="previews/classic.svg" width="360" alt="Classic theme preview">

### Cotton Candy

`theme -apply cotton-candy` · by HelCLI

A treat: pink, blue and grape on spun sugar

<img src="previews/cotton-candy.svg" width="360" alt="Cotton Candy theme preview">

### Cyberpunk

`theme -apply cyberpunk` · by HelCLI

Neon yellow, cyan and red on a dark violet night

<img src="previews/cyberpunk.svg" width="360" alt="Cyberpunk theme preview">

### Deep Space

`theme -apply deep-space` · by HelCLI

Starlight white and nebula colors on true black

<img src="previews/deep-space.svg" width="360" alt="Deep Space theme preview">

### Dracula

`theme -apply dracula` · by HelCLI

The dark purple palette loved by coders

<img src="previews/dracula.svg" width="360" alt="Dracula theme preview">

### Ember

`theme -apply ember` · by HelCLI

Glowing coals: orange, red and gold on charcoal

<img src="previews/ember.svg" width="360" alt="Ember theme preview">

### Espresso

`theme -apply espresso` · by HelCLI

Coffee browns and cream, with a little caramel

<img src="previews/espresso.svg" width="360" alt="Espresso theme preview">

### Everforest

`theme -apply everforest` · by after Everforest

Soft greens and earth tones, easy on the eyes

<img src="previews/everforest.svg" width="360" alt="Everforest theme preview">

### Forest

`theme -apply forest` · by HelCLI

Moss green and bark brown, calm and earthy

<img src="previews/forest.svg" width="360" alt="Forest theme preview">

### Graphite

`theme -apply graphite` · by HelCLI

Shades of gray with one mint accent

<img src="previews/graphite.svg" width="360" alt="Graphite theme preview">

### Green

`theme -apply green` · by HelCLI

Hacker green on near-black

<img src="previews/green.svg" width="360" alt="Green theme preview">

### Gruvbox

`theme -apply gruvbox` · by HelCLI

Retro, warm and easy on the eyes

<img src="previews/gruvbox.svg" width="360" alt="Gruvbox theme preview">

### High Contrast

`theme -apply high-contrast` · by HelCLI

Maximum readability: white and yellow on black

<img src="previews/high-contrast.svg" width="360" alt="High Contrast theme preview">

### Kanagawa

`theme -apply kanagawa` · by after Kanagawa

Ink and woodblock colors from The Great Wave

<img src="previews/kanagawa.svg" width="360" alt="Kanagawa theme preview">

### Lemonade

`theme -apply lemonade` · by HelCLI

Fresh squeezed: citrus colors on pale yellow

<img src="previews/lemonade.svg" width="360" alt="Lemonade theme preview">

### Midnight Ink

`theme -apply midnight` · by HelCLI

Ink blue and silver on near-black

<img src="previews/midnight.svg" width="360" alt="Midnight Ink theme preview">

### Mint Chip

`theme -apply mint-chip` · by HelCLI

Mint ice cream with chocolate chips

<img src="previews/mint-chip.svg" width="360" alt="Mint Chip theme preview">

### Monokai

`theme -apply monokai` · by HelCLI

Bright pink, green and orange on charcoal

<img src="previews/monokai.svg" width="360" alt="Monokai theme preview">

### Night Owl

`theme -apply night-owl` · by after Night Owl

Made for coding late at night

<img src="previews/night-owl.svg" width="360" alt="Night Owl theme preview">

### Nord

`theme -apply nord` · by HelCLI

Cool arctic blues and soft frost

<img src="previews/nord.svg" width="360" alt="Nord theme preview">

### Obsidian

`theme -apply obsidian` · by HelCLI

Volcanic glass: violet and lilac on black

<img src="previews/obsidian.svg" width="360" alt="Obsidian theme preview">

### Ocean

`theme -apply ocean` · by HelCLI

Deep sea blue with teal and coral

<img src="previews/ocean.svg" width="360" alt="Ocean theme preview">

### OLED Black

`theme -apply oled` · by HelCLI

True black to save battery, with gentle gray text

<img src="previews/oled.svg" width="360" alt="OLED Black theme preview">

### One Dark

`theme -apply one-dark` · by after Atom One Dark

The classic editor dark theme

<img src="previews/one-dark.svg" width="360" alt="One Dark theme preview">

### Paper

`theme -apply paper` · by HelCLI

Dark ink on warm paper, for daylight

<img src="previews/paper.svg" width="360" alt="Paper theme preview">

### Green Phosphor

`theme -apply phosphor` · by HelCLI

Glowing green on black, like an 80s terminal

<img src="previews/phosphor.svg" width="360" alt="Green Phosphor theme preview">

### Pink

`theme -apply pink` · by HelCLI

Plum background with candy-pink text

<img src="previews/pink.svg" width="360" alt="Pink theme preview">

### Red

`theme -apply red` · by HelCLI

Dark crimson with hot orange-red text

<img src="previews/red.svg" width="360" alt="Red theme preview">

### Rosé Pine Dawn

`theme -apply rose-pine-dawn` · by after Rosé Pine

Rosé Pine for the morning: warm paper and soft ink

<img src="previews/rose-pine-dawn.svg" width="360" alt="Rosé Pine Dawn theme preview">

### Rosé Pine

`theme -apply rose-pine` · by after Rosé Pine

Soho vibes: muted pine, rose and gold on deep purple

<img src="previews/rose-pine.svg" width="360" alt="Rosé Pine theme preview">

### Sakura

`theme -apply sakura` · by HelCLI

Cherry blossom pink on soft night blue

<img src="previews/sakura.svg" width="360" alt="Sakura theme preview">

### Solarized Dark

`theme -apply solarized-dark` · by HelCLI

The classic low-contrast palette, dark version

<img src="previews/solarized-dark.svg" width="360" alt="Solarized Dark theme preview">

### Solarized Light

`theme -apply solarized-light` · by HelCLI

The classic low-contrast palette, light version

<img src="previews/solarized-light.svg" width="360" alt="Solarized Light theme preview">

### Tokyo Night

`theme -apply tokyo-night` · by HelCLI

City lights at night: soft blue and purple

<img src="previews/tokyo-night.svg" width="360" alt="Tokyo Night theme preview">

### Vaporwave

`theme -apply vaporwave` · by HelCLI

Pink and cyan neon on a purple sunset

<img src="previews/vaporwave.svg" width="360" alt="Vaporwave theme preview">

## Theme file format

A theme is a JSON file. `theme` holds colors from `theme.xml` and `suggestions` holds colors
from `suggestions.xml`. Colors are `#RRGGBB` or `#AARRGGBB` (`AA` is opacity: `ff` is solid,
`00` is invisible). Any color you leave out goes back to its default when the theme is applied.

```json
{
  "name": "mytheme",
  "title": "My Theme",
  "author": "you",
  "description": "One line about how it looks",
  "theme": {
    "bg_color": "#ff101010",
    "overlay_color": "#b0101010",
    "input_color": "#ff00ff00",
    "output_color": "#ffffffff",
    "device_color": "#ffff9800"
  },
  "suggestions": {
    "apps_bg_color": "#ff00897b",
    "default_text_color": "#ff000000"
  }
}
```

`overlay_color` is drawn over your wallpaper when `system_wallpaper` is on.

Colors a theme can set:

- **theme**: `input_color`, `output_color`, `bg_color`, `device_color`, `battery_color_high`, `battery_color_medium`, `battery_color_low`, `time_color`, `storage_color`, `ram_color`, `network_info_color`, `toolbar_bg`, `toolbar_color`, `enter_color`, `cursor_color`, `overlay_color`, `alias_content_color`, `statusbar_color`, `navigationbar_color`, `app_installed_color`, `app_uninstalled_color`, `hint_color`, `mark_color`, `notes_color`, `notes_locked_color`, `link_color`, `restart_message_color`, `weather_color`, `unlock_counter_color`, `session_info_color`, `status_lines_bgrectcolor`, `input_bgrectcolor`, `output_bgrectcolor`, `toolbar_bgrectcolor`, `suggestions_bgrectcolor`, `status_lines_bg`, `input_bg`, `output_bg`, `suggestions_bg`, `status_lines_shadow_color`, `input_shadow_color`, `output_shadow_color`, `claude_color`, `alarm_color`, `music_color`, `calendar_color`
- **suggestions**: `default_text_color`, `default_bg_color`, `apps_text_color`, `apps_bg_color`, `alias_text_color`, `alias_bg_color`, `cmd_text_color`, `cmd_bg_color`, `song_text_color`, `song_bg_color`, `contact_text_color`, `contact_bg_color`, `file_text_color`, `file_bg_color`
